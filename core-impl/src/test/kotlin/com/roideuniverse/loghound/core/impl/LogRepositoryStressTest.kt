package com.roideuniverse.loghound.core.impl

import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogFilter
import com.roideuniverse.loghound.core.LogPriority
import com.roideuniverse.loghound.database.createLogDataStore
import java.io.File
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogRepositoryStressTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun seededEntry(seed: Long, idx: Int): LogEntry {
        val r = Random(seed + idx)
        val priorities = LogPriority.entries
        return LogEntry(
            id = 0L,
            timestamp = "01-15 12:00:00.000",
            pid = 1000 + (idx % 50),
            tid = 2000 + (idx % 16),
            priority = priorities[r.nextInt(priorities.size)],
            tag = "T${idx % 32}",
            message = "msg-$idx",
        )
    }

    @Test
    fun handles_100k_entries_with_correct_count_and_pagination() = runTest {
        val store = createLogDataStore(File(tmp.newFolder(), "stress.db"))
        val repo = LogRepositoryImpl(store)

        val total = 100_000
        val batch = 1_000
        val seed = 42L
        for (start in 0 until total step batch) {
            val items = List(batch) { i -> seededEntry(seed, start + i) }
            repo.append(items)
        }

        assertEquals(total.toLong(), repo.count())

        // Tail page is exactly limit-sized and contains the most recent rows.
        val tail = repo.query(limit = 200)
        assertEquals(200, tail.entries.size)
        assertTrue(tail.hasMore)
        assertEquals("msg-${total - 1}", tail.entries.last().message)
        assertEquals("msg-${total - 200}", tail.entries.first().message)

        // Walk backward through history one page at a time. Cap pages walked so the
        // assertion is deterministic and bounded.
        val pageSize = 500
        var beforeCursor: Long? = tail.entries.first().id
        var pagesWalked = 0
        val maxPages = (total / pageSize) + 2
        var seenIds = tail.entries.size.toLong()
        while (pagesWalked < maxPages) {
            val p = repo.query(beforeId = beforeCursor, limit = pageSize)
            if (p.entries.isEmpty()) break
            assertTrue(p.entries.all { it.id < beforeCursor!! })
            seenIds += p.entries.size
            beforeCursor = p.entries.first().id
            pagesWalked++
            if (!p.hasMore) break
        }
        assertEquals(total.toLong(), seenIds)
    }

    @Test
    fun count_with_filter_traverses_full_dataset_correctly_at_50k() = runTest {
        val store = createLogDataStore(File(tmp.newFolder(), "stress-count.db"))
        val repo = LogRepositoryImpl(store)

        val total = 50_000
        val errorEvery = 17
        val items =
            List(total) { i ->
                LogEntry(
                    id = 0L,
                    timestamp = "01-15 12:00:00.000",
                    pid = 1,
                    tid = 1,
                    priority = if (i % errorEvery == 0) LogPriority.Error else LogPriority.Info,
                    tag = "T",
                    message = "m-$i",
                )
            }
        items.chunked(2_000).forEach { chunk -> repo.append(chunk) }

        val expectedErrors = (0 until total).count { it % errorEvery == 0 }.toLong()
        assertEquals(total.toLong(), repo.count())
        assertEquals(expectedErrors, repo.count(LogFilter(minPriority = LogPriority.Error)))
    }
}
