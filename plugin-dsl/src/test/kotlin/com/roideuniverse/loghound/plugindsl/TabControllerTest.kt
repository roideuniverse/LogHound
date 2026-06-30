package com.roideuniverse.loghound.plugindsl

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class TabControllerTest {

    @Test
    fun `fresh controller has no open tabs and master active`() {
        val c = tabController("Master")
        assertTrue(c.openTabs.value.isEmpty())
        assertNull(c.activeId.value)
        assertEquals("Master", c.masterName)
    }

    @Test
    fun `open adds and activates a new tab`() {
        val c = tabController("Master")
        c.open(id = "a", name = "A")
        assertEquals(1, c.openTabs.value.size)
        assertEquals("a", c.openTabs.value.first().id)
        assertEquals("a", c.activeId.value)
    }

    @Test
    fun `open is idempotent on id - just reactivates`() {
        val c = tabController("Master")
        c.open("a", "A")
        c.open("b", "B")
        c.activate(null)
        c.open("a", "A renamed") // re-open existing — name change is ignored
        assertEquals(2, c.openTabs.value.size)
        assertEquals("a", c.activeId.value)
        assertEquals("A", c.openTabs.value.first { it.id == "a" }.name)
    }

    @Test
    fun `close removes the tab and falls back to left neighbor when active`() {
        val c = tabController("Master")
        c.open("a", "A")
        c.open("b", "B")
        c.open("c", "C")
        // active = c
        c.close("c")
        assertEquals(listOf("a", "b"), c.openTabs.value.map { it.id })
        assertEquals("b", c.activeId.value)
    }

    @Test
    fun `close on inactive tab does not change active`() {
        val c = tabController("Master")
        c.open("a", "A")
        c.open("b", "B")
        // active = b
        c.close("a")
        assertEquals(listOf("b"), c.openTabs.value.map { it.id })
        assertEquals("b", c.activeId.value)
    }

    @Test
    fun `close on first tab when active falls back to next - or null if last`() {
        val c = tabController("Master")
        c.open("a", "A")
        c.activate("a")
        c.close("a")
        assertTrue(c.openTabs.value.isEmpty())
        assertNull(c.activeId.value)
    }

    @Test
    fun `activate switches between master and open tabs`() {
        val c = tabController("Master")
        c.open("a", "A")
        c.activate(null)
        assertNull(c.activeId.value)
        c.activate("a")
        assertEquals("a", c.activeId.value)
    }
}
