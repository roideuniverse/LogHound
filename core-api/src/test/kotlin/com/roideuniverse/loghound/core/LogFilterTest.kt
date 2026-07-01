package com.roideuniverse.loghound.core

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class LogFilterTest {

    private fun entry(
        tag: String = "MyTag",
        priority: LogPriority = LogPriority.Info,
        pid: Int = 1234,
        tid: Int = 5678,
        message: String = "hello world",
        packageName: String? = "com.example.app",
    ) =
        LogEntry(
            id = 1L,
            timestamp = "01-15 12:00:00.000",
            pid = pid,
            tid = tid,
            priority = priority,
            tag = tag,
            message = message,
            packageName = packageName,
        )

    @Test
    fun empty_filter_matches_everything() {
        assertTrue(LogFilter().matches(entry()))
    }

    @Test
    fun tag_filter_is_substring_case_insensitive() {
        assertTrue(LogFilter(tag = "tag").matches(entry(tag = "MyTag")))
        assertTrue(LogFilter(tag = "TAG").matches(entry(tag = "MyTag")))
        assertFalse(LogFilter(tag = "Other").matches(entry(tag = "MyTag")))
    }

    @Test
    fun min_priority_includes_equal_and_higher() {
        assertTrue(
            LogFilter(minPriority = LogPriority.Info).matches(entry(priority = LogPriority.Info))
        )
        assertTrue(
            LogFilter(minPriority = LogPriority.Info).matches(entry(priority = LogPriority.Warn))
        )
        assertFalse(
            LogFilter(minPriority = LogPriority.Info).matches(entry(priority = LogPriority.Debug))
        )
    }

    @Test
    fun pid_and_tid_match_exactly() {
        assertTrue(LogFilter(pid = 1234).matches(entry(pid = 1234)))
        assertFalse(LogFilter(pid = 9999).matches(entry(pid = 1234)))
        assertTrue(LogFilter(tid = 5678).matches(entry(tid = 5678)))
        assertFalse(LogFilter(tid = 9999).matches(entry(tid = 5678)))
    }

    @Test
    fun package_filter_requires_a_package_present() {
        assertTrue(
            LogFilter(packageName = "example").matches(entry(packageName = "com.example.app"))
        )
        assertFalse(LogFilter(packageName = "example").matches(entry(packageName = null)))
    }

    @Test
    fun text_search_is_substring_case_insensitive() {
        assertTrue(LogFilter(textSearch = "WORLD").matches(entry(message = "hello world")))
        assertFalse(LogFilter(textSearch = "absent").matches(entry(message = "hello world")))
    }

    @Test
    fun regex_search_uses_regex_match() {
        assertTrue(LogFilter(regexSearch = """hel+o""").matches(entry(message = "hello")))
        assertFalse(LogFilter(regexSearch = """\d{5}""").matches(entry(message = "no digits")))
    }

    @Test
    fun fields_combine_with_AND() {
        val f = LogFilter(tag = "MyTag", minPriority = LogPriority.Warn, textSearch = "boom")
        assertTrue(f.matches(entry(tag = "MyTag", priority = LogPriority.Error, message = "boom!")))
        // tag mismatch
        assertFalse(
            f.matches(entry(tag = "Other", priority = LogPriority.Error, message = "boom!"))
        )
        // priority too low
        assertFalse(
            f.matches(entry(tag = "MyTag", priority = LogPriority.Debug, message = "boom!"))
        )
        // message mismatch
        assertFalse(f.matches(entry(tag = "MyTag", priority = LogPriority.Error, message = "fizz")))
    }
}
