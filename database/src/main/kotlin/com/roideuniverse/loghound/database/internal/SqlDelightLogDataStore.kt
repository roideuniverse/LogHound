package com.roideuniverse.loghound.database.internal

import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogPriority
import com.roideuniverse.loghound.database.LogDataStore
import com.roideuniverse.loghound.database.sqldelight.LogHoundDb
import com.roideuniverse.loghound.database.sqldelight.Logs as LogsRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Every method dispatches to `Dispatchers.IO` so SQLite/JDBC work never runs on the
 * caller's thread. This is load-bearing for UI callers: rememberCoroutineScope() in
 * Compose is bound to the Main dispatcher, and a synchronous executeAsList() on Main
 * blocks the next recomposition until it returns. With the dispatch in place, every
 * caller — Main, Default, IO — automatically gets off-thread DB work.
 */
internal class SqlDelightLogDataStore(private val db: LogHoundDb) : LogDataStore {

    private val queries get() = db.logsQueries

    override suspend fun insert(batch: List<LogEntry>): List<LogEntry> = withContext(Dispatchers.IO) {
        if (batch.isEmpty()) return@withContext emptyList()
        var lastId = 0L
        db.transaction {
            for (entry in batch) {
                queries.insert(
                    timestamp = entry.timestamp,
                    pid = entry.pid.toLong(),
                    tid = entry.tid.toLong(),
                    priority = entry.priority.name,
                    tag = entry.tag,
                    message = entry.message,
                    package_name = entry.packageName,
                )
            }
            lastId = queries.lastInsertRowid().executeAsOne()
        }
        // SQLite's auto-increment assigns ids monotonically within a transaction with
        // no concurrent writers, so the inserted ids form a contiguous range ending at
        // `lastId` of size `batch.size`.
        val firstId = lastId - batch.size + 1
        batch.mapIndexed { i, entry -> entry.copy(id = firstId + i) }
    }

    override suspend fun selectTail(limit: Int): List<LogEntry> = withContext(Dispatchers.IO) {
        queries.selectTail(limit.toLong()).executeAsList().map { it.toLogEntry() }
    }

    override suspend fun selectBefore(beforeId: Long, limit: Int): List<LogEntry> = withContext(Dispatchers.IO) {
        queries.selectBefore(beforeId, limit.toLong()).executeAsList().map { it.toLogEntry() }
    }

    override suspend fun selectAfter(afterId: Long, limit: Int): List<LogEntry> = withContext(Dispatchers.IO) {
        queries.selectAfter(afterId, limit.toLong()).executeAsList().map { it.toLogEntry() }
    }

    override suspend fun selectByIds(ids: Collection<Long>): List<LogEntry> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        queries.selectByIds(ids).executeAsList().map { it.toLogEntry() }
    }

    override suspend fun countAll(): Long = withContext(Dispatchers.IO) {
        queries.countAll().executeAsOne()
    }
}

private fun LogsRow.toLogEntry() = LogEntry(
    id = id,
    timestamp = timestamp,
    pid = pid.toInt(),
    tid = tid.toInt(),
    priority = LogPriority.valueOf(priority),
    tag = tag,
    message = message,
    packageName = package_name,
)
