package com.roideuniverse.loghound.plugins.logcat

import com.roideuniverse.loghound.core.LogPriority
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LogcatThreadtimeParserTest {

    @Test
    fun parses_well_formed_info_line() {
        val line = "01-15 12:34:56.789  1234  5678 I MyTag: hello world"
        val entry = LogcatThreadtimeParser.parse(line)!!
        assertEquals("01-15 12:34:56.789", entry.timestamp)
        assertEquals(1234, entry.pid)
        assertEquals(5678, entry.tid)
        assertEquals(LogPriority.Info, entry.priority)
        assertEquals("MyTag", entry.tag)
        assertEquals("hello world", entry.message)
    }

    @Test
    fun parses_each_priority_label() {
        val cases = mapOf(
            'V' to LogPriority.Verbose,
            'D' to LogPriority.Debug,
            'I' to LogPriority.Info,
            'W' to LogPriority.Warn,
            'E' to LogPriority.Error,
            'F' to LogPriority.Fatal,
            'S' to LogPriority.Silent,
        )
        for ((label, expected) in cases) {
            val line = "01-15 12:34:56.789  1234  5678 $label Tag: msg"
            val entry = LogcatThreadtimeParser.parse(line)!!
            assertEquals(expected, entry.priority, "priority for label '$label'")
        }
    }

    @Test
    fun parses_message_with_special_characters() {
        val line = "01-15 12:34:56.789  1 2 I T: /path/to/file -> result {key:val} 100% done"
        val entry = LogcatThreadtimeParser.parse(line)!!
        assertEquals("/path/to/file -> result {key:val} 100% done", entry.message)
    }

    @Test
    fun parses_tag_with_spaces_around_colon_safely() {
        // Tag may have spaces; the parser trims it.
        val line = "01-15 12:34:56.789  1234  5678 I  MyTag : hello"
        val entry = LogcatThreadtimeParser.parse(line)!!
        assertEquals("MyTag", entry.tag)
        assertEquals("hello", entry.message)
    }

    @Test
    fun parses_empty_message() {
        val line = "01-15 12:34:56.789  1234  5678 I MyTag: "
        val entry = LogcatThreadtimeParser.parse(line)!!
        assertEquals("", entry.message)
    }

    @Test
    fun parses_message_with_no_space_after_colon() {
        val line = "01-15 12:34:56.789  1234  5678 I MyTag:hello"
        val entry = LogcatThreadtimeParser.parse(line)!!
        assertEquals("hello", entry.message)
    }

    @Test
    fun returns_null_for_blank_line() {
        assertNull(LogcatThreadtimeParser.parse(""))
        assertNull(LogcatThreadtimeParser.parse("   "))
    }

    @Test
    fun returns_null_for_garbage_line() {
        assertNull(LogcatThreadtimeParser.parse("not a logcat line at all"))
    }

    @Test
    fun returns_null_for_invalid_priority_label() {
        val line = "01-15 12:34:56.789  1234  5678 X Tag: msg"
        assertNull(LogcatThreadtimeParser.parse(line))
    }

    @Test
    fun returns_null_for_non_numeric_pid() {
        val line = "01-15 12:34:56.789  abc  5678 I Tag: msg"
        assertNull(LogcatThreadtimeParser.parse(line))
    }

    @Test
    fun returns_null_for_missing_colon_separator() {
        val line = "01-15 12:34:56.789  1234  5678 I MyTag hello"
        assertNull(LogcatThreadtimeParser.parse(line))
    }

    @Test
    fun returns_null_for_non_threadtime_format() {
        // brief: "I/Tag(  1234): msg" — different format, shouldn't match threadtime
        assertNull(LogcatThreadtimeParser.parse("I/MyTag(  1234): hello"))
    }
}
