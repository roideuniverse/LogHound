package com.roideuniverse.loghound.plugins.uuidgrouping.internal

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogFilter
import com.roideuniverse.loghound.core.LogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Per-UUID state for an opened detail sub-tab inside the UUID Grouping panel.
 *
 * Owns the matching log entries, a `LazyListState` for scroll position retention across
 * tab switches, observable flags for the UI's loading and load-older states, and the
 * coroutine `Job` that streams matching entries into the list.
 *
 * `start` is idempotent — safe to call once when the tab is opened. `stop` cancels the
 * collection cleanly when the tab is closed.
 */
internal class UuidDetailController(
    private val uuid: String,
    private val repository: LogRepository,
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
                // 1. Initial query — backfill what's already persisted.
                val initial = repository.query(filter = LogFilter(textSearch = uuid), limit = INITIAL_LIMIT)
                entries.addAll(initial.entries)
                if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
            } finally {
                // Always settle the loading flag, even on cancellation or error, so the
                // UI never gets stuck on the spinner.
                loading = false
            }

            // 2. Live updates — append matching entries as they arrive.
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
                    // Tail-following: trim from the front to keep memory bounded. The
                    // evicted rows are still on disk; loadOlder fetches them back if
                    // the user scrolls up later.
                    while (entries.size > MAX_IN_MEMORY) {
                        entries.removeAt(0)
                    }
                    if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
                }
                // While the user is scrolled up we don't evict — they keep their
                // context. The list returns to the cap on the next at-bottom append.
            }
        }
    }

    /**
     * Fetch the next page of older entries (matching this controller's UUID) and prepend
     * to [entries]. Reentry guarded — concurrent calls collapse into a single fetch.
     * Idempotent once exhausted.
     */
    suspend fun loadOlder() {
        if (loadingOlder || olderExhausted || loading || entries.isEmpty()) return
        loadingOlder = true
        try {
            val oldestId = entries.first().id
            val older = repository.query(
                filter = LogFilter(textSearch = uuid),
                beforeId = oldestId,
                limit = LOAD_OLDER_PAGE_SIZE,
            )
            if (older.entries.isEmpty()) {
                olderExhausted = true
            } else {
                entries.addAll(0, older.entries)
            }
        } finally {
            loadingOlder = false
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val INITIAL_LIMIT = 500
        const val LOAD_OLDER_PAGE_SIZE = 500
        const val MAX_IN_MEMORY = 10_000
    }
}
