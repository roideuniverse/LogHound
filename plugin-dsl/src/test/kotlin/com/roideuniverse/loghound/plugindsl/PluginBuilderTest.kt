package com.roideuniverse.loghound.plugindsl

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.junit.Test

class PluginBuilderTest {

    @Test
    fun `plugin requires id`() {
        assertFailsWith<IllegalArgumentException> {
            plugin {
                name = "n"
                ui { text("hi") }
            }
        }
    }

    @Test
    fun `plugin requires name`() {
        assertFailsWith<IllegalArgumentException> {
            plugin {
                id = "x"
                ui { text("hi") }
            }
        }
    }

    @Test
    fun `plugin without data block is still valid (UI-only)`() {
        val p = plugin {
            id = "ui-only"
            name = "UI Only"
            ui { text("hi") }
        }
        assertEquals("ui-only", p.id)
        assertEquals("UI Only", p.name)
    }

    @Test
    fun `plugin without ui block is still valid (data-only)`() {
        val p = plugin {
            id = "data-only"
            name = "Data Only"
            data { _ -> /* no-op */ }
        }
        assertEquals("data-only", p.id)
        assertEquals("Data Only", p.name)
    }
}
