package com.roideuniverse.loghound.database

import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogPriority
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SqlDelightLogDataStoreTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun newStore(): LogDataStore =
        createLogDataStore(File(tmp.newFolder(), "logs.db"))

    private fun entry(
        timestamp: String = "01-15 12:00:00.000",
        pid: Int = 1234,
        tid: Int = 5678,
        priority: LogPriority = LogPriority.Info,
        tag: String = "T",
        message: String = "m",
        packageName: String? = null,
    ) = LogEntry(0L, timestamp, pid, tid, priority, tag, message, packageName)

    @Test
    fun insert_and_count_match() = runTest {
        val store = newStore()
        assertEquals(0L, store.countAll())
        store.insert(List(10) { entry(message = "msg-$it") })
        assertEquals(10L, store.countAll())
    }

    @Test
    fun selectTail_returns_latest_first_in_db_order_then_caller_reverses() = runTest {
        val store = newStore()
        val items = List(20) { entry(message = "m-$it") }
        store.insert(items)

        // selectTail returns rows ordered DESC by id; ids are auto-increment so the
        // last inserted row should be first.
        val tail = store.selectTail(5)
        assertEquals(5, tail.size)
        // Last inserted message (m-19) should appear first.
        assertEquals("m-19", tail.first().message)
        assertEquals("m-15", tail.last().message)
    }

    @Test
    fun selectAfter_returns_strictly_greater_ids_in_ascending_order() = runTest {
        val store = newStore()
        store.insert(List(5) { entry(message = "m-$it") })

        val all = store.selectAfter(0L, 100)
        assertEquals(5, all.size)
        // Ascending ids
        assertEquals(all.map { it.id }, all.map { it.id }.sorted())

        val cursor = all[1].id
        val rest = store.selectAfter(cursor, 100)
        assertEquals(3, rest.size)
        assertTrue(rest.all { it.id > cursor })
    }

    @Test
    fun selectBefore_returns_strictly_smaller_ids() = runTest {
        val store = newStore()
        store.insert(List(5) { entry(message = "m-$it") })

        val all = store.selectAfter(0L, 100)
        val cursor = all[3].id

        val before = store.selectBefore(cursor, 100)
        assertEquals(3, before.size)
        assertTrue(before.all { it.id < cursor })
    }

    @Test
    fun preserves_all_LogEntry_fields_across_round_trip() = runTest {
        val store = newStore()
        val original = entry(
            timestamp = "12-31 23:59:59.999",
            pid = 42,
            tid = 99,
            priority = LogPriority.Fatal,
            tag = "Crashy",
            message = "kaboom: \"quoted\" with /slashes/ and -hyphens-",
            packageName = "com.example.crash",
        )
        store.insert(listOf(original))
        val read = store.selectTail(1).single()
        assertEquals(original.timestamp, read.timestamp)
        assertEquals(original.pid, read.pid)
        assertEquals(original.tid, read.tid)
        assertEquals(original.priority, read.priority)
        assertEquals(original.tag, read.tag)
        assertEquals(original.message, read.message)
        assertEquals(original.packageName, read.packageName)
    }

    @Test
    fun two_stores_in_different_files_are_isolated() = runTest {
        val a = createLogDataStore(File(tmp.newFolder(), "a.db"))
        val b = createLogDataStore(File(tmp.newFolder(), "b.db"))
        a.insert(List(3) { entry(message = "in-a") })
        assertEquals(3L, a.countAll())
        assertEquals(0L, b.countAll())
    }

    @Test
    fun limit_caps_returned_rows() = runTest {
        val store = newStore()
        store.insert(List(50) { entry(message = "m-$it") })
        assertEquals(10, store.selectTail(10).size)
        assertEquals(7, store.selectAfter(0L, 7).size)
        // selectBefore needs a cursor; pick the highest id.
        val all = store.selectAfter(0L, 100)
        val highest = all.last().id
        assertEquals(8, store.selectBefore(highest + 1, 8).size)
    }
}
