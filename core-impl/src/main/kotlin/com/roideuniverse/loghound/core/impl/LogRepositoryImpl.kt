package com.roideuniverse.loghound.core.impl

import com.roideuniverse.loghound.core.Device
import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogFilter
import com.roideuniverse.loghound.core.LogPage
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.database.LogDataStore
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@SingleIn(AppScope::class)
class LogRepositoryImpl @Inject constructor(private val dataStore: LogDataStore) : LogRepository {

    private val _ingested = MutableSharedFlow<List<LogEntry>>(extraBufferCapacity = 64)
    override val ingested: Flow<List<LogEntry>> = _ingested.asSharedFlow()

    private val _devices = MutableStateFlow<Set<Device>>(emptySet())
    override val devices: StateFlow<Set<Device>> = _devices.asStateFlow()

    override fun publishDevices(set: Set<Device>) {
        _devices.value = set
    }

    override suspend fun append(batch: List<LogEntry>) {
        if (batch.isEmpty()) return
        // insert() returns the entries populated with their auto-assigned ids.
        // Subscribers of `ingested` need real ids — without them, UI keys (LazyColumn)
        // collide and Compose throws "key 0 was already used".
        val withIds = dataStore.insert(batch)
        _ingested.emit(withIds)
    }

    override suspend fun query(
        filter: LogFilter,
        beforeId: Long?,
        afterId: Long?,
        limit: Int,
    ): LogPage {
        require(beforeId == null || afterId == null) {
            "beforeId and afterId are mutually exclusive"
        }

        // Empty filter: single SQL call, return up to `limit` rows directly.
        if (filter == LogFilter()) {
            val rawRows =
                when {
                    afterId != null -> dataStore.selectAfter(afterId, limit)
                    beforeId != null -> dataStore.selectBefore(beforeId, limit)
                    else -> dataStore.selectTail(limit)
                }
            return LogPage(entries = rawRows.sortedBy { it.id }, hasMore = rawRows.size >= limit)
        }

        // Non-empty filter: scan page-by-page until we collect `limit` matches or
        // exhaust the store. Without this, queries that match historical entries
        // outside the latest `limit` rows incorrectly return empty.
        val matched = mutableListOf<LogEntry>()
        val pageSize = SCAN_PAGE_SIZE

        suspend fun consumePage(rows: List<LogEntry>): Boolean {
            for (entry in rows) {
                if (filter.matches(entry)) {
                    matched += entry
                    if (matched.size >= limit) return true
                }
            }
            return false
        }

        when {
            afterId != null -> {
                var cursor: Long = afterId
                while (matched.size < limit) {
                    val page = dataStore.selectAfter(cursor, pageSize)
                    if (page.isEmpty()) break
                    if (consumePage(page)) break
                    if (page.size < pageSize) break
                    cursor = page.last().id
                }
            }

            else -> {
                // No cursor → start from the tail; cursor → start before that id.
                val firstPage: List<LogEntry> =
                    beforeId?.let { dataStore.selectBefore(it, pageSize) }
                        ?: dataStore.selectTail(pageSize)
                val firstFull = firstPage.size == pageSize
                val firstFilled = consumePage(firstPage)

                if (!firstFilled && firstFull) {
                    // Both selectTail and selectBefore return rows ordered DESC by id;
                    // the last element is the oldest in the page, so the next backward
                    // cursor is its id.
                    var cursor: Long = firstPage.last().id
                    while (matched.size < limit) {
                        val page = dataStore.selectBefore(cursor, pageSize)
                        if (page.isEmpty()) break
                        if (consumePage(page)) break
                        if (page.size < pageSize) break
                        cursor = page.last().id
                    }
                }
            }
        }

        return LogPage(entries = matched.sortedBy { it.id }, hasMore = matched.size >= limit)
    }

    private companion object {
        const val SCAN_PAGE_SIZE = 1_000
    }

    override suspend fun queryByIds(ids: Collection<Long>): List<LogEntry> =
        dataStore.selectByIds(ids)

    override suspend fun count(filter: LogFilter): Long {
        if (filter == LogFilter()) return dataStore.countAll()
        // Non-empty filter: stream forward via cursor and count matches in Kotlin.
        var total = 0L
        var cursor = 0L
        val pageSize = 1_000
        while (true) {
            val rows = dataStore.selectAfter(cursor, pageSize)
            if (rows.isEmpty()) break
            total += rows.count(filter::matches)
            cursor = rows.last().id
            if (rows.size < pageSize) break
        }
        return total
    }

    override suspend fun clearStore() {
        dataStore.clearAll()
    }
}
