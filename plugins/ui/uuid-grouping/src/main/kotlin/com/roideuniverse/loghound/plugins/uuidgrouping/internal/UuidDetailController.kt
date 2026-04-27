package com.roideuniverse.loghound.plugins.uuidgrouping.internal

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableStateListOf
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
 * tab switches, and the coroutine `Job` that streams matching entries into the list.
 *
 * `start` is idempotent — safe to call once when the tab is opened. `stop` cancels the
 * collection cleanly when the tab is closed.
 */
internal class UuidDetailController(private val uuid: String) {

    val entries = mutableStateListOf<LogEntry>()
    val listState = LazyListState()
    private var job: Job? = null

    fun start(scope: CoroutineScope, repository: LogRepository) {
        if (job?.isActive == true) return
        job = scope.launch {
            // 1. Initial query — backfill what's already persisted.
            val initial = repository.query(filter = LogFilter(textSearch = uuid), limit = INITIAL_LIMIT)
            entries.addAll(initial.entries)
            if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)

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
                if (wasAtBottom && entries.isNotEmpty()) {
                    listState.scrollToItem(entries.lastIndex)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private companion object {
        const val INITIAL_LIMIT = 500
    }
}
