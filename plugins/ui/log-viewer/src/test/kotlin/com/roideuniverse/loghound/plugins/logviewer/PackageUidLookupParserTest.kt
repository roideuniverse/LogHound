package com.roideuniverse.loghound.plugins.logviewer

import org.junit.Test
import kotlin.test.assertEquals

class PackageUidLookupParserTest {

    @Test
    fun parses_a_single_well_formed_line() {
        val output = "package:com.app.debug uid:10231"
        assertEquals(
            listOf(PackageInfo("com.app.debug", 10231)),
            parsePackageUidLines(output),
        )
    }

    @Test
    fun parses_multiple_lines() {
        val output = """
            package:com.app.debug uid:10231
            package:com.app.debug.support uid:10232
            package:com.example.other uid:10501
        """.trimIndent()
        assertEquals(
            listOf(
                PackageInfo("com.app.debug", 10231),
                PackageInfo("com.app.debug.support", 10232),
                PackageInfo("com.example.other", 10501),
            ),
            parsePackageUidLines(output),
        )
    }

    @Test
    fun trims_surrounding_whitespace() {
        val output = "  package:com.app.debug uid:10231  "
        assertEquals(
            listOf(PackageInfo("com.app.debug", 10231)),
            parsePackageUidLines(output),
        )
    }

    @Test
    fun ignores_blank_lines_and_garbage() {
        val output = """

            this is not a package line
            package:com.app.debug uid:10231

            also garbage
        """.trimIndent()
        assertEquals(
            listOf(PackageInfo("com.app.debug", 10231)),
            parsePackageUidLines(output),
        )
    }

    @Test
    fun ignores_lines_with_non_numeric_uid() {
        val output = "package:com.app.debug uid:notanint"
        assertEquals(emptyList(), parsePackageUidLines(output))
    }

    @Test
    fun returns_empty_for_empty_input() {
        assertEquals(emptyList(), parsePackageUidLines(""))
    }
}
