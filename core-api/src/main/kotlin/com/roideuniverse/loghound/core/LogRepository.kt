package com.roideuniverse.loghound.core

import kotlinx.coroutines.flow.Flow

interface LogRepository {
    suspend fun append(batch: List<LogEntry>)

    suspend fun query(
        filter: LogFilter = LogFilter(),
        beforeId: Long? = null,
        afterId: Long? = null,
        limit: Int = 200,
    ): LogPage

    suspend fun count(filter: LogFilter = LogFilter()): Long

    val ingested: Flow<List<LogEntry>>
}
