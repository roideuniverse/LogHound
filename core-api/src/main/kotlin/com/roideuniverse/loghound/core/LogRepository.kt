package com.roideuniverse.loghound.core

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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

    /**
     * The currently-detected set of log sources. Data plugins publish to this
     * via [publishDevices]; UI consumers read it to render the device pill,
     * per-device sub-tabs, autocomplete in the filter bar, etc.
     *
     * Emits the current value on subscription and on every change. The empty
     * set means "no sources detected yet" (e.g. adb not found or no devices
     * attached); UI callers should render a sensible empty state, not block
     * the view.
     */
    val devices: StateFlow<Set<Device>>

    /**
     * Replace the published set of sources with [set]. Called by data
     * plugins on detection / reconnect / disconnect of their sources. With
     * a single data plugin (today, logcat) the caller's set is authoritative;
     * a future world with multiple publishers needs a merge model — flagged
     * for that PR rather than designed speculatively here.
     */
    fun publishDevices(set: Set<Device>)
}
