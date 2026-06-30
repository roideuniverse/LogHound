package com.roideuniverse.loghound

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.roideuniverse.loghound.core.UIPlugin
import com.roideuniverse.loghound.di.AppGraph
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

// Set<UIPlugin> from the graph has no defined iteration order, but the tab/
// sidebar layout expects Log Viewer, then UUID Grouping, then Sessions, with
// any scripted plugins trailing. Sort by this known order; unknown ids (scripted
// plugins) sort last in discovery order.
private val UI_PLUGIN_ORDER = listOf("core.log-viewer", "core.uuid-grouping", "core.sessions")

fun main() = application {
    val graph = remember { createGraph<AppGraph>() }
    val repository = graph.repository
    val sessionsManager = graph.sessionsManager
    val dataPlugins = remember(graph) { graph.dataPlugins.toList() }
    val uiPlugins: List<UIPlugin> =
        remember(graph) {
            graph.uiPlugins.sortedBy { plugin ->
                UI_PLUGIN_ORDER.indexOf(plugin.id).let { if (it == -1) Int.MAX_VALUE else it }
            }
        }

    val backgroundScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    remember(dataPlugins) {
        dataPlugins.forEach { plugin -> backgroundScope.launch { plugin.run(repository) } }
    }
    remember(sessionsManager) { backgroundScope.launch { sessionsManager.initialize() } }

    var sidebarVisible by remember { mutableStateOf(true) }

    Window(
        onCloseRequest = {
            backgroundScope.cancel()
            exitApplication()
        },
        title = "LogHound",
    ) {
        MenuBar {
            Menu("File") { Item("Exit", onClick = ::exitApplication) }
            Menu("Edit") { Item("Preferences", onClick = {}) }
            Menu("View") { Item("Toggle Sidebar", onClick = { sidebarVisible = !sidebarVisible }) }
            Menu("Help") { Item("About", onClick = {}) }
        }
        App(
            plugins = uiPlugins,
            repository = repository,
            sessionsManager = sessionsManager,
            sidebarVisible = sidebarVisible,
        )
    }
}
