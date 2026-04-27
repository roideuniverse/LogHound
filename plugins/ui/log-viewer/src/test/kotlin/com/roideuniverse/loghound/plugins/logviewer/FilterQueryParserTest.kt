package com.roideuniverse.loghound.plugins.logviewer

import com.roideuniverse.loghound.core.LogFilter
import com.roideuniverse.loghound.core.LogPriority
import org.junit.Test
import kotlin.test.assertEquals

class FilterQueryParserTest {

    @Test
    fun empty_query_returns_empty_filter() {
        assertEquals(LogFilter(), FilterQueryParser.parse(""))
        assertEquals(LogFilter(), FilterQueryParser.parse("   "))
    }

    @Test
    fun parses_tag_clause() {
        assertEquals(LogFilter(tag = "Activity"), FilterQueryParser.parse("tag:Activity"))
    }

    @Test
    fun parses_quoted_tag_value_with_spaces() {
        assertEquals(LogFilter(tag = "My Service"), FilterQueryParser.parse("""tag:"My Service""""))
    }

    @Test
    fun parses_level_short_label() {
        assertEquals(LogFilter(minPriority = LogPriority.Warn), FilterQueryParser.parse("level:W"))
    }

    @Test
    fun parses_level_full_name_case_insensitive() {
        assertEquals(LogFilter(minPriority = LogPriority.Error), FilterQueryParser.parse("level:Error"))
        assertEquals(LogFilter(minPriority = LogPriority.Warn), FilterQueryParser.parse("level:warn"))
    }

    @Test
    fun parses_pid_and_tid_as_ints() {
        assertEquals(LogFilter(pid = 1234), FilterQueryParser.parse("pid:1234"))
        assertEquals(LogFilter(tid = 5678), FilterQueryParser.parse("tid:5678"))
    }

    @Test
    fun parses_package_clause() {
        assertEquals(LogFilter(packageName = "com.example"), FilterQueryParser.parse("package:com.example"))
    }

    @Test
    fun bare_words_become_text_search() {
        assertEquals(LogFilter(textSearch = "crash"), FilterQueryParser.parse("crash"))
    }

    @Test
    fun multiple_bare_words_join_with_space() {
        assertEquals(LogFilter(textSearch = "out of memory"), FilterQueryParser.parse("out of memory"))
    }

    @Test
    fun unknown_key_falls_through_to_text_search() {
        // `xyz:foo` is unrecognized; the whole token becomes free text.
        assertEquals(LogFilter(textSearch = "xyz:foo"), FilterQueryParser.parse("xyz:foo"))
    }

    @Test
    fun key_with_invalid_value_falls_through_to_text_search() {
        // `pid:` requires a number — non-numeric falls through.
        assertEquals(LogFilter(textSearch = "pid:abc"), FilterQueryParser.parse("pid:abc"))
    }

    @Test
    fun mixed_clauses_combine() {
        val result = FilterQueryParser.parse("tag:Activity level:W package:mine crash")
        assertEquals(
            LogFilter(
                tag = "Activity",
                minPriority = LogPriority.Warn,
                packageName = "mine",
                textSearch = "crash",
            ),
            result,
        )
    }

    @Test
    fun leading_slash_treats_text_as_regex() {
        val result = FilterQueryParser.parse("""/^E\/.*timeout/""")
        assertEquals(LogFilter(regexSearch = """^E\/.*timeout"""), result)
    }

    @Test
    fun leading_slash_with_clauses_keeps_clauses_and_makes_regex() {
        val result = FilterQueryParser.parse("""tag:Activity /timeout/""")
        assertEquals(LogFilter(tag = "Activity", regexSearch = "timeout"), result)
    }
}
