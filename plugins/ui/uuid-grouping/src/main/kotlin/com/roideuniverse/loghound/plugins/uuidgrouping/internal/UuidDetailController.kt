package com.roideuniverse.loghound.plugins.uuidgrouping.internal

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.plugins.uuidgrouping.sqldelight.UuidsQueries
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-UUID state for an opened detail sub-tab inside the UUID Grouping panel.
 *
 * Owns the matching log entries, a `LazyListState` for scroll position retention across
 * tab switches, observable flags for the UI's loading and load-older states, and the
 * coroutine `Job` that streams matching entries into the list.
 *
 * Lookup uses the plugin's per-occurrence `uuid_log` table for an indexed
 * `SELECT log_id WHERE uuid = ?`, then fetches those entries from the main
 * logs DB by id. This is constant-time per UUID; the previous implementation
 * scanned the main logs DB page-by-page until it collected enough matches,
 * which scaled with total log volume rather than per-UUID occurrence count
 * (and was pathological for low-count UUIDs that never reach the limit).
 *
 * `start` is idempotent — safe to call once when the tab is opened. `stop` cancels the
 * collection cleanly when the tab is closed.
 */
internal class UuidDetailController(
    private val uuid: String,
    private val repository: LogRepository,
    private val uuidQueries: UuidsQueries,
) {

    val entries = mutableStateListOf<LogEntry>()
    val listState = LazyListState()

    /**
     * True from construction until the initial query completes (or fails).
     * Observable Compose state — drives the loading-vs-empty-vs-loaded rendering.
     */
    var loading: Boolean by mutableStateOf(true)
        private set

    /** True while a load-older fetch is in flight. */
    var loadingOlder: Boolean by mutableStateOf(false)
        private set

    /**
     * True once a load-older fetch has returned zero entries. The trigger then stops
     * firing for the lifetime of this controller.
     */
    var olderExhausted: Boolean by mutableStateOf(false)
        private set

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            try {
                val initial = fetchInitial()
                entries.addAll(initial)
                if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
            } finally {
                loading = false
            }

            // Live updates — append new ingested entries that contain this UUID.
            // Substring match is fine here because the batch is small (one ingest tick).
            repository.ingested.collect { batch ->
                val matched = batch.filter { it.message.contains(uuid, ignoreCase = true) }
                if (matched.isEmpty()) return@collect
                val wasAtBottom = if (entries.isEmpty()) {
                    true
                } else {
                    (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= entries.lastIndex - 2
                }
                entries.addAll(matched)
                if (wasAtBottom) {
                    while (entries.size > MAX_IN_MEMORY) {
                        entries.removeAt(0)
                    }
                    if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
                }
            }
        }
    }

    /**
     * Fetch the next page of older entries for this UUID and prepend to [entries].
     * Reentry guarded — concurrent calls collapse into a single fetch. Idempotent
     * once exhausted.
     */
    suspend fun loadOlder() {
        if (loadingOlder || olderExhausted || loading || entries.isEmpty()) return
        loadingOlder = true
        try {
            val oldestId = entries.first().id
            val olderIds = withContext(Dispatchers.IO) {
                uuidQueries.selectLogIdsForUuidBefore(
                    uuid = uuid,
                    beforeId = oldestId,
                    lim = LOAD_OLDER_PAGE_SIZE.toLong(),
                ).executeAsList()
            }
            if (olderIds.isEmpty()) {
                olderExhausted = true
                return
            }
            val older = repository.queryByIds(olderIds)
            if (older.isEmpty()) {
                olderExhausted = true
            } else {
                entries.addAll(0, older)
            }
        } finally {
            loadingOlder = false
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun fetchInitial(): List<LogEntry> {
        val ids = withContext(Dispatchers.IO) {
            uuidQueries.selectLogIdsForUuidDesc(
                uuid = uuid,
                lim = INITIAL_LIMIT.toLong(),
            ).executeAsList()
        }
        if (ids.isEmpty()) return emptyList()
        return repository.queryByIds(ids)
    }

    private companion object {
        const val INITIAL_LIMIT = 500
        const val LOAD_OLDER_PAGE_SIZE = 500
        const val MAX_IN_MEMORY = 10_000
    }
}
