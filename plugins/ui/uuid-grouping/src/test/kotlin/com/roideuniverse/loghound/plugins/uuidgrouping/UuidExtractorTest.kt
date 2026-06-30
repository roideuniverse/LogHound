package com.roideuniverse.loghound.plugins.uuidgrouping

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class UuidExtractorTest {

    @Test
    fun finds_single_uuid() {
        val u = "550e8400-e29b-41d4-a716-446655440000"
        assertEquals(listOf(u), UuidExtractor.findAll("session=$u started"))
    }

    @Test
    fun finds_multiple_uuids() {
        val a = "11111111-1111-1111-1111-111111111111"
        val b = "22222222-2222-2222-2222-222222222222"
        assertEquals(listOf(a, b), UuidExtractor.findAll("from $a to $b"))
    }

    @Test
    fun normalizes_to_lowercase() {
        val u = "AAAAAAAA-BBBB-CCCC-DDDD-EEEEEEEEEEEE"
        assertEquals(listOf(u.lowercase()), UuidExtractor.findAll("uppercase $u"))
    }

    @Test
    fun returns_empty_when_no_uuid() {
        assertEquals(emptyList(), UuidExtractor.findAll("nothing of note here"))
    }

    @Test
    fun ignores_lines_without_hyphen_via_prefilter() {
        // The pre-filter requires a '-'. Without one, no regex runs.
        val out = UuidExtractor.findAll("nohypheninthislineatall")
        assertEquals(emptyList(), out)
    }

    @Test
    fun ignores_hyphens_that_are_not_part_of_uuid() {
        val out = UuidExtractor.findAll("dashes-but-not-a-uuid: foo-bar-baz")
        assertEquals(emptyList(), out)
    }

    @Test
    fun ignores_partial_uuid_shape() {
        // Wrong segment length (last block 11 hex chars instead of 12).
        val out = UuidExtractor.findAll("11111111-1111-1111-1111-11111111111")
        assertEquals(emptyList(), out)
    }

    @Test
    fun finds_uuid_at_start_and_end_of_message() {
        val a = "550e8400-e29b-41d4-a716-446655440000"
        assertEquals(listOf(a), UuidExtractor.findAll("$a is at start"))
        assertEquals(listOf(a), UuidExtractor.findAll("at end is $a"))
    }

    @Test
    fun finds_uuid_with_non_hex_chars_around() {
        val u = "550e8400-e29b-41d4-a716-446655440000"
        val msg = "{\"id\":\"$u\",\"x\":1}"
        assertTrue(UuidExtractor.findAll(msg).contains(u))
    }
}
