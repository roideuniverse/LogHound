package com.roideuniverse.loghound.plugins.sessions

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.plugins.sessions.sqldelight.SelectArchived
import com.roideuniverse.loghound.plugins.sessions.sqldelight.Sessions
import com.roideuniverse.loghound.plugins.sessions.sqldelight.SessionsDb
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/**
 * Where on disk every piece of session state lives. Bundled into a config record so wiring is one
 * parameter instead of four and so the test path is obvious (point each field at a temp dir).
 * - [sessionsDbFile] — the sessions registry itself. Survives clearStore.
 * - [liveLogsDbFile] — the main logs.db that gets archived + truncated.
 * - [pluginDbDir] — the directory of `~/.loghound/plugins/<id>.db` files that plugin data sources
 *   own. Each .db file in here gets copied into the archive when a session ends.
 * - [archivesDir] — root for archived sessions. Each session ends up in
 *   `<archivesDir>/<session-id>/...`.
 */
data class SessionsConfig(
    val sessionsDbFile: File,
    val liveLogsDbFile: File,
    val pluginDbDir: File,
    val archivesDir: File,
)

/**
 * Called by [SessionsManager.endAndStartNew] after the archive copy completes but before the new
 * session row is inserted. The host wires this to truncate the main logs store and call
 * `clearStore()` on every DataPlugin.
 *
 * Modeled as a functional interface so main.kt can pass a lambda.
 */
fun interface StoreClearer {
    suspend fun clearAll()
}

/**
 * Holds a [Session] for each lifecycle row in `sessions.db` so the UI doesn't have to know about
 * SQLDelight types.
 */
data class Session(
    val id: Long,
    val name: String,
    val startedAt: Long,
    val endedAt: Long?,
    val archiveDir: String?,
    val lineCount: Long,
    val notes: String?,
)

/**
 * Orchestrates the live-vs-archive split for log sessions.
 *
 * On first construction it makes sure there's exactly one active session row; if none exists, it
 * inserts one named with the current timestamp. The resulting "active" row drives the status-bar
 * session pill (line count, duration) via [active].
 *
 * The headline action — [endAndStartNew] — copies the live logs.db plus every
 * `~/.loghound/plugins/<id>.db` into `<archivesDir>/<session-id>/`, then invokes the [StoreClearer]
 * to wipe live state, then opens a fresh session row. The file copy uses Files.copy which races
 * with ingest by design (Cf. "boundaries" note in issue #21): up to a batch's worth of rows may end
 * up in both the archive and the new session. Document as known limitation; a proper SQLite online
 * backup is a follow-up.
 *
 * Deliberately not a DataPlugin — the StoreClearer would otherwise call our own clearStore() in a
 * loop, and the sessions.db is supposed to survive clears anyway.
 */
@SingleIn(AppScope::class)
class SessionsManager
@Inject
constructor(
    private val config: SessionsConfig,
    private val repository: LogRepository,
    private val storeClearer: StoreClearer,
) {
    private val db: SessionsDb by lazy { openSessionsDb(config.sessionsDbFile) }
    private val _active = MutableStateFlow<Session?>(null)
    val active: StateFlow<Session?> = _active.asStateFlow()

    private val _archived = MutableStateFlow<List<Session>>(emptyList())
    val archived: StateFlow<List<Session>> = _archived.asStateFlow()

    /**
     * Ensures the session registry has exactly one active row, then loads the current state into
     * [active] / [archived]. Call once at app startup.
     */
    suspend fun initialize() =
        withContext(Dispatchers.IO) {
            val existing = db.sessionsQueries.selectActive().executeAsOneOrNull()
            if (existing == null) {
                db.sessionsQueries.insertSession(
                    name = defaultSessionName(),
                    started_at = System.currentTimeMillis(),
                )
            }
            refresh()
        }

    /**
     * Snapshot the current session's logs.db + plugin DBs into `<archivesDir>/<session-id>/`, mark
     * the row ended, call [StoreClearer] to wipe the live data, then open a fresh active session
     * row.
     */
    suspend fun endAndStartNew(newSessionName: String? = null): Session =
        withContext(Dispatchers.IO) {
            val current =
                db.sessionsQueries.selectActive().executeAsOneOrNull()
                    ?: error("No active session — call initialize() first")
            val archiveDir = File(config.archivesDir, current.id.toString()).apply { mkdirs() }

            copyDbFamily(config.liveLogsDbFile, File(archiveDir, config.liveLogsDbFile.name))
            if (config.pluginDbDir.isDirectory) {
                val targetPluginsDir = File(archiveDir, "plugins").apply { mkdirs() }
                config.pluginDbDir.listFiles()?.forEach { plugin ->
                    if (plugin.isFile && plugin.extension == "db") {
                        copyDbFamily(plugin, File(targetPluginsDir, plugin.name))
                    }
                }
            }

            val lineCount = repository.count()
            db.sessionsQueries.endSession(
                endedAt = System.currentTimeMillis(),
                archiveDir = archiveDir.absolutePath,
                lineCount = lineCount,
                id = current.id,
            )

            storeClearer.clearAll()

            db.sessionsQueries.insertSession(
                name = newSessionName ?: defaultSessionName(),
                started_at = System.currentTimeMillis(),
            )

            refresh()
            _active.value!!
        }

    suspend fun rename(sessionId: Long, name: String) =
        withContext(Dispatchers.IO) {
            require(name.isNotBlank()) { "session name must not be blank" }
            db.sessionsQueries.renameSession(name = name, id = sessionId)
            refresh()
        }

    /**
     * Rebuilds [active] + [archived] from the registry. Called automatically after every mutator;
     * tests that poke the DB directly can call this to resync the flows.
     */
    suspend fun refresh() =
        withContext(Dispatchers.IO) {
            _active.value = db.sessionsQueries.selectActive().executeAsOneOrNull()?.toSession()
            _archived.value =
                db.sessionsQueries.selectArchived().executeAsList().map { it.toSession() }
        }

    /**
     * Copies a SQLite DB file along with its WAL/SHM sidecars if they exist. The sidecars matter
     * when the source DB is open: WAL holds uncommitted pages and -shm is the shared-memory
     * wal-index. Skipping them produces an archive that opens fine but is missing the last few
     * committed rows.
     */
    private fun copyDbFamily(source: File, dest: File) {
        if (!source.exists()) return
        Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING)
        for (suffix in listOf("-wal", "-shm")) {
            val side = File(source.parentFile, source.name + suffix)
            if (side.exists()) {
                Files.copy(
                    side.toPath(),
                    File(dest.parentFile, dest.name + suffix).toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                )
            }
        }
    }
}

private fun openSessionsDb(file: File): SessionsDb {
    file.parentFile?.mkdirs()
    val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
    SessionsDb.Schema.create(driver)
    return SessionsDb(driver)
}

private fun Sessions.toSession() =
    Session(
        id = id,
        name = name,
        startedAt = started_at,
        endedAt = ended_at,
        archiveDir = archive_dir,
        lineCount = line_count,
        notes = notes,
    )

// SQLDelight generates a distinct row class for selectArchived because the
// query's `WHERE ended_at IS NOT NULL` lets it tighten the column from Long?
// to Long. Mapping is identical otherwise.
private fun SelectArchived.toSession() =
    Session(
        id = id,
        name = name,
        startedAt = started_at,
        endedAt = ended_at,
        archiveDir = archive_dir,
        lineCount = line_count,
        notes = notes,
    )

private fun defaultSessionName(): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    return fmt.format(Date())
}
