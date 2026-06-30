package com.roideuniverse.loghound.plugindsl

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import org.junit.Rule
import org.junit.Test

class DslRendererTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun `text verb renders the string`() {
        val p = plugin {
            id = "test"
            name = "Test"
            ui { text("Hello, DSL") }
        }
        compose.setContent { p.content(Modifier.fillMaxSize()) }
        compose.onNodeWithText("Hello, DSL").assertExists()
    }

    @Test
    fun `column stacks children vertically`() {
        val p = plugin {
            id = "test"
            name = "T"
            ui {
                column {
                    text("first")
                    text("second")
                }
            }
        }
        compose.setContent { p.content(Modifier.fillMaxSize()) }
        compose.onNodeWithText("first").assertExists()
        compose.onNodeWithText("second").assertExists()
    }

    @Test
    fun `row places children side-by-side`() {
        val p = plugin {
            id = "test"
            name = "T"
            ui {
                row {
                    text("left")
                    text("right")
                }
            }
        }
        compose.setContent { p.content(Modifier.fillMaxSize()) }
        compose.onNodeWithText("left").assertExists()
        compose.onNodeWithText("right").assertExists()
    }

    @Test
    fun `list renders one composable per item`() {
        val items = listOf("apple", "banana", "cherry")
        val p = plugin {
            id = "test"
            name = "T"
            ui { list(items = items, key = { it }) { item -> text(item) } }
        }
        compose.setContent { p.content(Modifier.fillMaxSize()) }
        compose.onNodeWithText("apple").assertExists()
        compose.onNodeWithText("banana").assertExists()
        compose.onNodeWithText("cherry").assertExists()
    }

    @Test
    fun `clickable subtree renders without throwing`() {
        val p = plugin {
            id = "test"
            name = "T"
            ui { clickable(onClick = {}) { text("click me") } }
        }
        compose.setContent { p.content(Modifier.fillMaxSize()) }
        compose.onNodeWithText("click me").assertExists()
    }

    @Test
    fun `textField shows placeholder when state is empty`() {
        val state = stateOf("")
        val p = plugin {
            id = "test"
            name = "T"
            ui { textField(state = state, placeholder = "type here") }
        }
        compose.setContent { p.content(Modifier.fillMaxSize()) }
        compose.onNodeWithText("type here").assertExists()
    }

    @Test
    fun `textField hides placeholder when state has content`() {
        val state = stateOf("typed")
        val p = plugin {
            id = "test"
            name = "T"
            ui { textField(state = state, placeholder = "ph") }
        }
        compose.setContent { p.content(Modifier.fillMaxSize()) }
        compose.onNodeWithText("ph").assertDoesNotExist()
        compose.onNodeWithText("typed").assertExists()
    }

    @Test
    fun `tabs renders master content by default`() {
        val ctrl = tabController("Master")
        val p = plugin {
            id = "test"
            name = "T"
            ui {
                tabs(
                    controller = ctrl,
                    master = { text("master view") },
                    detail = { id -> text("detail $id") },
                )
            }
        }
        compose.setContent { p.content(Modifier.fillMaxSize()) }
        compose.onNodeWithText("master view").assertExists()
        compose.onNodeWithText("Master").assertExists() // tab pill
    }

    @Test
    fun `tabs switches to detail content when tab is opened`() {
        val ctrl = tabController("Master")
        val p = plugin {
            id = "test"
            name = "T"
            ui {
                tabs(
                    controller = ctrl,
                    master = { text("master view") },
                    detail = { id -> text("detail $id") },
                )
            }
        }
        compose.setContent { p.content(Modifier.fillMaxSize()) }
        compose.runOnIdle { ctrl.open(id = "alpha", name = "Alpha") }
        compose.onNodeWithText("detail alpha").assertExists()
        // Master content should be gone now.
        compose.onNodeWithText("master view").assertDoesNotExist()
    }

    @Test
    fun `button renders its label`() {
        val p = plugin {
            id = "test"
            name = "T"
            ui { button(label = "Click", onClick = {}) }
        }
        compose.setContent { p.content(Modifier.fillMaxSize()) }
        compose.onNodeWithText("Click").assertExists()
    }

    @Test
    fun `centered renders its children`() {
        val p = plugin {
            id = "test"
            name = "T"
            ui { centered { text("middle") } }
        }
        compose.setContent { p.content(Modifier.fillMaxSize()) }
        compose.onNodeWithText("middle").assertExists()
    }

    @Test
    fun `theme override is applied without breaking rendering`() {
        val p = plugin {
            id = "test"
            name = "T"
            theme { text = TextStyle(fontSize = 20.sp, color = Color.Red) }
            ui { text("themed") }
        }
        compose.setContent { p.content(Modifier.fillMaxSize()) }
        compose.onNodeWithText("themed").assertExists()
    }
}
