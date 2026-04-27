package com.roideuniverse.loghound.core.impl

import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogFilter
import com.roideuniverse.loghound.core.LogPriority
import com.roideuniverse.loghound.database.LogDataStore
import com.roideuniverse.loghound.database.createLogDataStore
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LogRepositoryImplTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun newRepo(): Pair<LogDataStore, LogRepositoryImpl> {
        val store = createLogDataStore(File(tmp.newFolder(), "logs.db"))
        return store to LogRepositoryImpl(store)
    }

    private fun entry(
        message: String = "m",
        priority: LogPriority = LogPriority.Info,
        tag: String = "T",
        pid: Int = 1234,
    ) = LogEntry(0L, "01-15 12:00:00.000", pid, pid + 1, priority, tag, message)

    @Test
    fun query_with_no_cursor_returns_latest_entries_in_chronological_order() = runTest {
        val (_, repo) = newRepo()
        repo.append(List(20) { entry(message = "m-$it") })

        val page = repo.query(limit = 5)
        assertEquals(5, page.entries.size)
        // entries in ascending id order regardless of which cursor was used
        assertEquals(page.entries.map { it.id }, page.entries.map { it.id }.sorted())
        // page contains the most recent rows
        assertEquals("m-19", page.entries.last().message)
        assertEquals("m-15", page.entries.first().message)
    }

    @Test
    fun query_with_afterId_returns_strictly_newer_entries() = runTest {
        val (_, repo) = newRepo()
        repo.append(List(10) { entry(message = "m-$it") })

        val all = repo.query(limit = 100).entries
        val cursor = all[3].id
        val page = repo.query(afterId = cursor, limit = 100)
        assertTrue(page.entries.all { it.id > cursor })
        assertEquals(6, page.entries.size)
    }

    @Test
    fun query_with_beforeId_returns_strictly_older_entries() = runTest {
        val (_, repo) = newRepo()
        repo.append(List(10) { entry(message = "m-$it") })

        val all = repo.query(limit = 100).entries
        val cursor = all[5].id
        val page = repo.query(beforeId = cursor, limit = 100)
        assertTrue(page.entries.all { it.id < cursor })
    }

    @Test
    fun query_rejects_both_cursors_set() = runTest {
        val (_, repo) = newRepo()
        repo.append(listOf(entry()))
        assertFailsWith<IllegalArgumentException> {
            repo.query(beforeId = 1L, afterId = 1L, limit = 10)
        }
    }

    @Test
    fun query_filter_excludes_non_matching_entries() = runTest {
        val (_, repo) = newRepo()
        repo.append(
            listOf(
                entry(message = "with crash", priority = LogPriority.Error),
                entry(message = "no issue", priority = LogPriority.Info),
                entry(message = "another crash", priority = LogPriority.Warn),
            ),
        )

        val onlyErrors = repo.query(
            filter = LogFilter(minPriority = LogPriority.Error),
            limit = 100,
        ).entries
        assertEquals(1, onlyErrors.size)
        assertEquals("with crash", onlyErrors.single().message)
    }

    @Test
    fun count_empty_filter_returns_total_rows() = runTest {
        val (_, repo) = newRepo()
        repo.append(List(42) { entry(message = "m-$it") })
        assertEquals(42L, repo.count())
    }

    @Test
    fun count_with_filter_returns_matching_rows() = runTest {
        val (_, repo) = newRepo()
        repo.append(List(20) { i ->
            entry(message = "m-$i", priority = if (i % 4 == 0) LogPriority.Error else LogPriority.Info)
        })
        // Indexes 0,4,8,12,16 → 5 errors out of 20
        val errors = repo.count(LogFilter(minPriority = LogPriority.Error))
        assertEquals(5L, errors)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun ingested_emits_each_appended_batch() = runTest(UnconfinedTestDispatcher()) {
        val (_, repo) = newRepo()
        coroutineScope {
            // UnconfinedTestDispatcher: collector attaches synchronously here, before
            // append() runs. SharedFlow has no replay so subscriber order matters.
            val collected = async { repo.ingested.take(2).toList() }
            repo.append(listOf(entry(message = "first"), entry(message = "second")))
            repo.append(listOf(entry(message = "third")))
            val batches = collected.await()
            assertEquals(2, batches.size)
            assertEquals(listOf("first", "second"), batches[0].map { it.message })
            assertEquals(listOf("third"), batches[1].map { it.message })
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun ingested_does_not_replay_history() = runTest(UnconfinedTestDispatcher()) {
        val (_, repo) = newRepo()
        repo.append(listOf(entry(message = "before-subscription")))

        coroutineScope {
            val collected = async { repo.ingested.first() }
            repo.append(listOf(entry(message = "after-subscription")))
            val batch = collected.await()
            assertEquals(listOf("after-subscription"), batch.map { it.message })
        }
    }

    @Test
    fun append_of_empty_batch_does_not_emit_or_persist() = runTest {
        val (store, repo) = newRepo()
        repo.append(emptyList())
        assertEquals(0L, store.countAll())
    }
}
