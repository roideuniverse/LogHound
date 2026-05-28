package com.roideuniverse.loghound

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.window.MenuBar
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.roideuniverse.loghound.core.DataPlugin
import com.roideuniverse.loghound.core.UIPlugin
import com.roideuniverse.loghound.core.impl.LogRepositoryImpl
import com.roideuniverse.loghound.database.createLogDataStore
import com.roideuniverse.loghound.plugins.logcat.LogcatDataPlugin
import com.roideuniverse.loghound.plugins.logviewer.LogViewerPlugin
import com.roideuniverse.loghound.plugins.sessions.SessionsConfig
import com.roideuniverse.loghound.plugins.sessions.SessionsManager
import com.roideuniverse.loghound.plugins.sessions.SessionsPlugin
import com.roideuniverse.loghound.plugins.sessions.StoreClearer
import com.roideuniverse.loghound.plugins.uuidgrouping.UuidGroupingPlugin
import com.roideuniverse.loghound.scripting.PluginScriptHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

fun main() = application {
    val home = File(System.getProperty("user.home"))
    val dbFile = File(home, ".loghound/logs.db")
    val dataStore = remember { createLogDataStore(dbFile) }
    val repository = remember { LogRepositoryImpl(dataStore) }

    val uuidGrouping = remember(repository) {
        UuidGroupingPlugin(
            databaseFile = File(home, ".loghound/plugins/uuid-grouping.db"),
            repository = repository,
        )
    }

    val scriptedPlugins = remember {
        PluginScriptHost.loadAll(File(home, ".loghound/plugins"))
    }

    val dataPlugins: List<DataPlugin> = remember {
        listOf<DataPlugin>(LogcatDataPlugin(), uuidGrouping) + scriptedPlugins
    }

    // Sessions manager owns the live-vs-archive split. StoreClearer wipes
    // the live logs.db plus every DataPlugin's derived state in one shot
    // (the order is repository first, then plugins, so plugin observers
    // don't see ID gaps mid-clear).
    val sessionsManager = remember(repository, dataPlugins) {
        SessionsManager(
            config = SessionsConfig(
                sessionsDbFile = File(home, ".loghound/sessions.db"),
                liveLogsDbFile = dbFile,
                pluginDbDir = File(home, ".loghound/plugins"),
                archivesDir = File(home, ".loghound/archives"),
            ),
            repository = repository,
            storeClearer = StoreClearer {
                repository.clearStore()
                dataPlugins.forEach { runCatching { it.clearStore() } }
            },
        )
    }

    val sessionsPlugin = remember(sessionsManager) { SessionsPlugin(sessionsManager) }

    val uiPlugins: List<UIPlugin> = remember(sessionsPlugin) {
        listOf<UIPlugin>(LogViewerPlugin(repository), uuidGrouping, sessionsPlugin) + scriptedPlugins
    }

    val backgroundScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    remember(dataPlugins) {
        dataPlugins.forEach { plugin -> backgroundScope.launch { plugin.run(repository) } }
    }
    remember(sessionsManager) {
        backgroundScope.launch { sessionsManager.initialize() }
    }

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
