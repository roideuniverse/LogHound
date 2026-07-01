package com.roideuniverse.loghound.database

import com.roideuniverse.loghound.core.LogEntry

interface LogDataStore {
    /**
     * Inserts each entry and returns the same entries with their auto-assigned ids filled in, in
     * the same order as `batch`. An empty input returns an empty list.
     */
    suspend fun insert(batch: List<LogEntry>): List<LogEntry>

    suspend fun selectTail(limit: Int): List<LogEntry>

    suspend fun selectBefore(beforeId: Long, limit: Int): List<LogEntry>

    suspend fun selectAfter(afterId: Long, limit: Int): List<LogEntry>

    /**
     * Fetch the entries with the given ids, ordered ASC by id. Empty input returns empty. Missing
     * ids are silently skipped.
     */
    suspend fun selectByIds(ids: Collection<Long>): List<LogEntry>

    suspend fun countAll(): Long

    /**
     * Truncate every row from the underlying `logs` store. ID auto-increment counters reset so the
     * next inserted entry starts at id 1 again. The underlying file stays open and connected — only
     * the rows are gone.
     */
    suspend fun clearAll()
}
