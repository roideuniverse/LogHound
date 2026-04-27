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
import com.roideuniverse.loghound.plugins.synthetic.SyntheticDataPlugin
import com.roideuniverse.loghound.plugins.uuidgrouping.UuidGroupingPlugin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

fun main() = application {
    val dbFile = File(System.getProperty("user.home"), ".loghound/logs.db")
    val dataStore = remember { createLogDataStore(dbFile) }
    val repository = remember { LogRepositoryImpl(dataStore) }

    val uuidGrouping = remember {
        UuidGroupingPlugin(File(System.getProperty("user.home"), ".loghound/plugins/uuid-grouping.db"))
    }

    val dataPlugins: List<DataPlugin> = remember {
        listOf(LogcatDataPlugin(), SyntheticDataPlugin(), uuidGrouping)
    }
    val uiPlugins: List<UIPlugin> = remember { listOf(LogViewerPlugin(repository), uuidGrouping) }

    val backgroundScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
    remember(dataPlugins) {
        dataPlugins.forEach { plugin -> backgroundScope.launch { plugin.run(repository) } }
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
        App(plugins = uiPlugins, sidebarVisible = sidebarVisible)
    }
}
