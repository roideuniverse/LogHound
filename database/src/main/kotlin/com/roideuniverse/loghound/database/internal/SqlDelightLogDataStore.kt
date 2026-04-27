package com.roideuniverse.loghound.database.internal

import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogPriority
import com.roideuniverse.loghound.database.LogDataStore
import com.roideuniverse.loghound.database.sqldelight.LogHoundDb
import com.roideuniverse.loghound.database.sqldelight.Logs as LogsRow

internal class SqlDelightLogDataStore(private val db: LogHoundDb) : LogDataStore {

    private val queries get() = db.logsQueries

    override suspend fun insert(batch: List<LogEntry>): List<LogEntry> {
        if (batch.isEmpty()) return emptyList()
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
        return batch.mapIndexed { i, entry -> entry.copy(id = firstId + i) }
    }

    override suspend fun selectTail(limit: Int): List<LogEntry> =
        queries.selectTail(limit.toLong()).executeAsList().map { it.toLogEntry() }

    override suspend fun selectBefore(beforeId: Long, limit: Int): List<LogEntry> =
        queries.selectBefore(beforeId, limit.toLong()).executeAsList().map { it.toLogEntry() }

    override suspend fun selectAfter(afterId: Long, limit: Int): List<LogEntry> =
        queries.selectAfter(afterId, limit.toLong()).executeAsList().map { it.toLogEntry() }

    override suspend fun countAll(): Long = queries.countAll().executeAsOne()
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
