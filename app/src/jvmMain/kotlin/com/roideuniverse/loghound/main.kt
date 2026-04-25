package com.roideuniverse.loghound

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.roideuniverse.loghound.core.UIPlugin

private class PlaceholderPlugin(
    override val id: String,
    override val name: String,
    private val message: String,
) : UIPlugin {
    @Composable
    override fun content(modifier: Modifier) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(message)
        }
    }
}

private val plugins: List<UIPlugin> = listOf(
    LogViewerPlugin(),
    PlaceholderPlugin(
        id = "core.uuid-grouping",
        name = "UUID Grouping",
        message = "UUID Grouping (placeholder)",
    ),
)

fun main() = application {
    var sidebarVisible by remember { mutableStateOf(true) }

    Window(
        onCloseRequest = ::exitApplication,
        title = "LogHound",
    ) {
        MenuBar {
            Menu("File") {
                Item("Exit", onClick = ::exitApplication)
            }
            Menu("Edit") {
                Item("Preferences", onClick = {})
            }
            Menu("View") {
                Item("Toggle Sidebar", onClick = { sidebarVisible = !sidebarVisible })
            }
            Menu("Help") {
                Item("About", onClick = {})
            }
        }
        App(plugins = plugins, sidebarVisible = sidebarVisible)
    }
}
