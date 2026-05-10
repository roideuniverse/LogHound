package com.roideuniverse.loghound.scripting

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PluginScriptHostTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `empty plugins dir returns empty list`() {
        val dir = tmp.newFolder("plugins")
        val plugins = PluginScriptHost.loadAll(dir)
        assertTrue(plugins.isEmpty())
    }

    @Test
    fun `nonexistent plugins dir returns empty list`() {
        val dir = File(tmp.root, "does-not-exist")
        val plugins = PluginScriptHost.loadAll(dir)
        assertTrue(plugins.isEmpty())
    }

    @Test
    fun `non-kts files in plugins dir are ignored`() {
        val dir = tmp.newFolder("plugins")
        File(dir, "notes.txt").writeText("not a script")
        File(dir, "data.db").writeBytes(byteArrayOf(0))
        val plugins = PluginScriptHost.loadAll(dir)
        assertTrue(plugins.isEmpty())
    }

    @Test
    fun `tiny hello plugin loads and registers id and name`() {
        val dir = tmp.newFolder("plugins")
        File(dir, "hello.kts").writeText(
            """
            plugin {
                id = "hello-test"
                name = "Hello Test"
                ui {
                    text("hi")
                }
            }
            """.trimIndent()
        )
        val plugins = PluginScriptHost.loadAll(dir)
        assertEquals(1, plugins.size)
        assertEquals("hello-test", plugins.single().id)
        assertEquals("Hello Test", plugins.single().name)
    }

    @Test
    fun `example uuid grouping script loads as a valid plugin`() {
        // The example script lives in the repo at examples/plugins/uuid-grouping.kts.
        // Walk up from cwd to find it (cwd may be repo root or the app/ module dir).
        val source = findExampleScript()
        val dir = tmp.newFolder("plugins")
        File(dir, "uuid-grouping.kts").writeText(source.readText())

        val plugins = PluginScriptHost.loadAll(dir)
        assertEquals(1, plugins.size, "example script should produce exactly one plugin")
        val plugin = plugins.single()
        assertEquals("uuid-grouping-script", plugin.id)
        assertEquals("UUID Grouping (script)", plugin.name)
    }

    private fun findExampleScript(): File {
        val rel = "examples/plugins/uuid-grouping.kts"
        var dir: File? = File(System.getProperty("user.dir"))
        while (dir != null) {
            val candidate = File(dir, rel)
            if (candidate.exists()) return candidate
            dir = dir.parentFile
        }
        error("could not locate $rel from cwd ${System.getProperty("user.dir")}")
    }

    @Test
    fun `script with syntax error does not load other valid scripts`() {
        // We want one bad script to be skipped, not blow up the whole batch.
        val dir = tmp.newFolder("plugins")
        File(dir, "broken.kts").writeText("this is not valid kotlin {{{")
        File(dir, "valid.kts").writeText(
            """
            plugin {
                id = "valid"
                name = "Valid"
                ui { text("ok") }
            }
            """.trimIndent()
        )
        val plugins = PluginScriptHost.loadAll(dir)
        assertEquals(1, plugins.size)
        assertEquals("valid", plugins.single().id)
    }
}
