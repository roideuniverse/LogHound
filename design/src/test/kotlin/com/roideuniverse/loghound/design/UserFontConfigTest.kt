package com.roideuniverse.loghound.design

import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.Properties
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserFontConfigTest {

    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun `parseOverride returns null when properties is null`() {
        assertNull(UserFontConfig.parseOverride(null, UserFontConfig.KEY_UI) { it })
    }

    @Test
    fun `parseOverride returns null when key is missing`() {
        val props = Properties()
        assertNull(UserFontConfig.parseOverride(props, UserFontConfig.KEY_UI) { it })
    }

    @Test
    fun `parseOverride returns null when value is blank`() {
        val props = Properties().apply { setProperty(UserFontConfig.KEY_UI, "   ") }
        assertNull(UserFontConfig.parseOverride(props, UserFontConfig.KEY_UI) { it })
    }

    @Test
    fun `parseOverride returns null when path does not exist`() {
        val props = Properties().apply { setProperty(UserFontConfig.KEY_UI, "/does/not/exist.ttf") }
        // Stderr noise on bad path is intentional; we don't assert it here.
        assertNull(UserFontConfig.parseOverride(props, UserFontConfig.KEY_UI) { it })
    }

    @Test
    fun `parseOverride returns file when path is readable`() {
        val ttf = tmp.newFile("custom.ttf").apply { writeText("not really a font") }
        val props = Properties().apply { setProperty(UserFontConfig.KEY_UI, ttf.absolutePath) }
        // We intentionally don't try to render the font here — `parseOverride`
        // only validates filesystem reachability; the Compose `Font(File)`
        // factory does its own load-time validation later.
        assertEquals(ttf.absolutePath, UserFontConfig.parseOverride(props, UserFontConfig.KEY_UI) { it }?.absolutePath)
    }

    @Test
    fun `parseOverride applies tilde expansion through the provided expander`() {
        val ttf = tmp.newFile("expanded.ttf")
        val props = Properties().apply { setProperty(UserFontConfig.KEY_UI, "~/expanded.ttf") }
        val resolved = UserFontConfig.parseOverride(props, UserFontConfig.KEY_UI) { raw ->
            raw.replace("~", tmp.root.absolutePath)
        }
        assertEquals(ttf.absolutePath, resolved?.absolutePath)
    }

    @Test
    fun `expandTilde leaves non-tilde paths alone`() {
        assertEquals("/abs/path.ttf", UserFontConfig.expandTilde("/abs/path.ttf"))
    }

    @Test
    fun `expandTilde rewrites leading tilde to user home`() {
        val home = System.getProperty("user.home")
        assertEquals("$home/font.ttf", UserFontConfig.expandTilde("~/font.ttf"))
        assertEquals(home, UserFontConfig.expandTilde("~"))
        // "~user/..." is intentionally not supported; treat it as a literal.
        assertEquals("~user/font.ttf", UserFontConfig.expandTilde("~user/font.ttf"))
    }

    @Test
    fun `loadProperties returns null when file missing`() {
        val missing = File(tmp.root, "nope.properties")
        assertNull(UserFontConfig.loadProperties(missing))
    }

    @Test
    fun `loadProperties returns parsed Properties when file present`() {
        val file = tmp.newFile("config.properties").apply {
            writeText("${UserFontConfig.KEY_UI}=/foo/bar.ttf\n${UserFontConfig.KEY_MONO}=/baz.ttf\n")
        }
        val props = UserFontConfig.loadProperties(file)
        assertEquals("/foo/bar.ttf", props?.getProperty(UserFontConfig.KEY_UI))
        assertEquals("/baz.ttf", props?.getProperty(UserFontConfig.KEY_MONO))
    }
}
