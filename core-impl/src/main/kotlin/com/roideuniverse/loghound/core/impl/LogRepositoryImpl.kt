package com.roideuniverse.loghound.core.impl

import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogFilter
import com.roideuniverse.loghound.core.LogPage
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.database.LogDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class LogRepositoryImpl(
    private val dataStore: LogDataStore,
) : LogRepository {

    private val _ingested = MutableSharedFlow<List<LogEntry>>(extraBufferCapacity = 64)
    override val ingested: Flow<List<LogEntry>> = _ingested.asSharedFlow()

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
        // TODO: push filters into SQL once the filter set is stable.
        // v1 fetches a page by cursor, applies LogFilter in Kotlin.
        val rawRows = when {
            afterId != null -> dataStore.selectAfter(afterId, limit)
            beforeId != null -> dataStore.selectBefore(beforeId, limit)
            else -> dataStore.selectTail(limit)
        }
        val matched = rawRows.filter(filter::matches).sortedBy { it.id }
        return LogPage(entries = matched, hasMore = rawRows.size >= limit)
    }

    override suspend fun count(filter: LogFilter): Long {
        if (filter == LogFilter()) return dataStore.countAll()
        // TODO: push filter to SQL. v1 streams forward via cursor and counts matches.
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
}
