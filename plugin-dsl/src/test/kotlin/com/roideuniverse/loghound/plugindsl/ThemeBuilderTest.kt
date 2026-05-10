package com.roideuniverse.loghound.plugindsl

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ThemeBuilderTest {

    @Test
    fun `default theme matches built-in look`() {
        val t = PluginTheme()
        // Spot-check the values that scripts depend on for parity with the built-in.
        assertEquals(Color.White, t.background)
        assertEquals(Color(0xFFCCCCCC), t.divider)
        assertEquals(Color(0xFFEEEEEE), t.rowDivider)
        assertEquals(Color(0xFFF0F0F0), t.tabStripBackground)
        assertEquals(Color(0xFFF7F7F7), t.toolbarBackground)
        assertEquals(Color.White, t.activeTabBackground)
        assertEquals(Color.White, t.textFieldBackground)
        // Row text should be monospace 12sp by default — this is what
        // makes script log lists visually align with the built-in.
        assertEquals(12.sp, t.rowText.fontSize)
    }

    @Test
    fun `plugin builder defaults to PluginTheme()`() {
        val p = plugin {
            id = "x"
            name = "X"
            ui { text("hello") }
        }
        // Reflection-y assertion via a fresh comparison: the plugin should round-trip
        // the default theme (we don't have a direct getter on DslPlugin, but we can
        // verify the builder defaults match a freshly-constructed PluginTheme).
        val builder = PluginBuilder()
        assertEquals(PluginTheme(), builder.theme)
    }

    @Test
    fun `theme builder applies single override on top of defaults`() {
        val builder = PluginBuilder()
        builder.theme {
            background = Color.Black
        }
        assertEquals(Color.Black, builder.theme.background)
        // Other fields untouched.
        assertEquals(Color(0xFFCCCCCC), builder.theme.divider)
        assertEquals(Color(0xFFF0F0F0), builder.theme.tabStripBackground)
    }

    @Test
    fun `theme builder applies multiple overrides`() {
        val builder = PluginBuilder()
        builder.theme {
            background = Color(0xFF101010)
            divider = Color.Red
            rowText = TextStyle(fontSize = 14.sp, color = Color.White)
        }
        assertEquals(Color(0xFF101010), builder.theme.background)
        assertEquals(Color.Red, builder.theme.divider)
        assertEquals(14.sp, builder.theme.rowText.fontSize)
        assertEquals(Color.White, builder.theme.rowText.color)
    }

    @Test
    fun `theme builder block can be called twice and accumulates`() {
        val builder = PluginBuilder()
        builder.theme { background = Color.Red }
        builder.theme { divider = Color.Blue }
        assertEquals(Color.Red, builder.theme.background)
        assertEquals(Color.Blue, builder.theme.divider)
    }

    @Test
    fun `theme builder does not mutate the global default`() {
        val before = PluginTheme()
        val builder = PluginBuilder()
        builder.theme { background = Color.Magenta }
        val after = PluginTheme()
        assertEquals(before, after)
        assertNotEquals(before.background, builder.theme.background)
    }
}
