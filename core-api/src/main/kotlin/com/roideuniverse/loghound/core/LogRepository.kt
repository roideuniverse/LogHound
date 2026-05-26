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

    /**
     * Truncate the main `logs` store. Used by the host's clear-logs flow and
     * by the Sessions plugin's end-and-archive flow. After this returns,
     * `query(...)` reports zero entries and `ingested` continues to receive
     * fresh batches from active data plugins.
     *
     * Plugins observing `ingested` see no side-effect from clearing — they
     * just stop receiving rows tied to the old IDs. Plugins that maintain
     * derived state should additionally have `DataPlugin.clearStore()`
     * invoked by the host so the two stores stay consistent.
     */
    suspend fun clearStore()

    val ingested: Flow<List<LogEntry>>
}
