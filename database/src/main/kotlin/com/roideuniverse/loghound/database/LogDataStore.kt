package com.roideuniverse.loghound.database

import com.roideuniverse.loghound.core.LogEntry

interface LogDataStore {
    suspend fun insert(batch: List<LogEntry>)

    suspend fun selectTail(limit: Int): List<LogEntry>
    suspend fun selectBefore(beforeId: Long, limit: Int): List<LogEntry>
    suspend fun selectAfter(afterId: Long, limit: Int): List<LogEntry>

    suspend fun countAll(): Long
}
