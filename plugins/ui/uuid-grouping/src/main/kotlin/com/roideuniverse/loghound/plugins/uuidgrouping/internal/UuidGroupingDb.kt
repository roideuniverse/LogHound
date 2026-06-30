package com.roideuniverse.loghound.plugins.uuidgrouping.internal

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.roideuniverse.loghound.plugins.uuidgrouping.sqldelight.UuidGroupingDb
import java.io.File
import java.util.Properties

internal fun openUuidGroupingDb(databaseFile: File): UuidGroupingDb {
    val isNew = !databaseFile.exists()
    if (isNew) databaseFile.parentFile?.mkdirs()

    val driver =
        JdbcSqliteDriver(
            url = "jdbc:sqlite:${databaseFile.absolutePath}",
            properties = Properties(),
        )
    if (isNew) {
        UuidGroupingDb.Schema.create(driver)
    } else {
        // Forward-compat for DBs created before uuid_log existed. CREATE IF NOT
        // EXISTS is a no-op on fresh DBs and a one-shot migration on legacy ones.
        driver.execute(
            null,
            """
            CREATE TABLE IF NOT EXISTS uuid_log (
                uuid TEXT NOT NULL,
                log_id INTEGER NOT NULL,
                PRIMARY KEY (uuid, log_id)
            )
            """
                .trimIndent(),
            0,
        )
    }

    driver.execute(null, "PRAGMA journal_mode = WAL;", 0)
    driver.execute(null, "PRAGMA synchronous = NORMAL;", 0)

    val db = UuidGroupingDb(driver)

    // If the legacy summary table has rows but uuid_log hasn't been populated yet,
    // the prior backfill ran on the old schema and our per-occurrence index is
    // empty. Reset the checkpoint and the summary so backfill rebuilds from
    // scratch — this is a one-time cost paid the first time a user runs against
    // the new schema.
    if (!isNew) {
        val q = db.uuidsQueries
        val occurrences = q.countAllOccurrences().executeAsOne()
        val summaryRows = q.countAll().executeAsOne()
        if (occurrences == 0L && summaryRows > 0L) {
            db.transaction {
                q.clearUuids()
                q.setMeta(META_LAST_SCANNED_ID, "0")
            }
        }
    }

    return db
}

internal const val META_LAST_SCANNED_ID = "last_scanned_id"
