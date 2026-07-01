package com.roideuniverse.loghound.plugins.logcat.internal

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.junit.Test

class PackageResolverParserTest {

    @Test
    fun parses_typical_android_ps_output() {
        val out =
            """
            USER     PID   PPID  VSZ    RSS    WCHAN  ADDR  S NAME
            root     1     0     11000  4000   sys_a  -     S init
            u0_a123  12345 1234  500000 100000 sys_a  -     S com.app.debug
            u0_a124  12346 1234  500000 100000 sys_a  -     S com.example.other
        """
                .trimIndent()
        val result = parsePsOutput(out)
        assertEquals("init", result[1])
        assertEquals("com.app.debug", result[12345])
        assertEquals("com.example.other", result[12346])
        assertEquals(3, result.size)
    }

    @Test
    fun strips_subprocess_suffix() {
        val out =
            """
            HEADER
            u0  100 1 0 0 - - S com.app.debug:remote
            u0  101 1 0 0 - - S com.app.debug:push
        """
                .trimIndent()
        val result = parsePsOutput(out)
        assertEquals("com.app.debug", result[100])
        assertEquals("com.app.debug", result[101])
    }

    @Test
    fun drops_kernel_threads() {
        val out =
            """
            HEADER
            root  2 0 0 0 sys_a - S [kthreadd]
            root  3 2 0 0 sys_a - S [migration/0]
            u0    100 1 0 0 - - S com.real.app
        """
                .trimIndent()
        val result = parsePsOutput(out)
        assertEquals(mapOf(100 to "com.real.app"), result)
    }

    @Test
    fun ignores_lines_with_non_integer_pid() {
        val out =
            """
            HEADER
            user PIDS 1 0 0 - - S junk
            u0   200 1 0 0 - - S com.real.app
        """
                .trimIndent()
        val result = parsePsOutput(out)
        assertEquals(mapOf(200 to "com.real.app"), result)
    }

    @Test
    fun returns_empty_for_empty_input() {
        assertEquals(emptyMap(), parsePsOutput(""))
    }

    @Test
    fun returns_empty_when_only_header() {
        assertEquals(emptyMap(), parsePsOutput("USER PID PPID VSZ RSS WCHAN ADDR S NAME"))
    }

    @Test
    fun does_not_attribute_unknown_pid() {
        val out =
            """
            HEADER
            u0 100 1 0 0 - - S com.real.app
        """
                .trimIndent()
        val result = parsePsOutput(out)
        assertNull(result[999])
    }
}
