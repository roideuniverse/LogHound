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

    /**
     * Fetch the entries with the given ids, ordered ASC by id. Empty input
     * returns empty. Useful for callers that already have a precomputed list of
     * matching log ids (e.g. UUID Grouping's per-UUID index) and want to skip
     * the page-by-page substring scan that [query] performs.
     */
    suspend fun queryByIds(ids: Collection<Long>): List<LogEntry>

    val ingested: Flow<List<LogEntry>>
}
