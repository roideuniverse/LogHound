package com.roideuniverse.loghound.database

import com.roideuniverse.loghound.core.LogEntry

interface LogDataStore {
    /**
     * Inserts each entry and returns the same entries with their auto-assigned ids
     * filled in, in the same order as `batch`. An empty input returns an empty list.
     */
    suspend fun insert(batch: List<LogEntry>): List<LogEntry>

    suspend fun selectTail(limit: Int): List<LogEntry>
    suspend fun selectBefore(beforeId: Long, limit: Int): List<LogEntry>
    suspend fun selectAfter(afterId: Long, limit: Int): List<LogEntry>

    suspend fun countAll(): Long
}
