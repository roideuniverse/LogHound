package com.roideuniverse.loghound

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.roideuniverse.loghound.core.DeviceId
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.core.UIPlugin
import com.roideuniverse.loghound.design.LocalActiveDevice
import com.roideuniverse.loghound.design.LocalLogHoundColors
import com.roideuniverse.loghound.design.LogHoundDesign
import com.roideuniverse.loghound.plugins.sessions.SessionsManager
import kotlinx.coroutines.launch

private const val CORE_LOG_VIEWER_ID = "core.log-viewer"
private val TAB_BAR_HEIGHT = 36.dp

@Composable
fun App(
    plugins: List<UIPlugin>,
    repository: LogRepository,
    sessionsManager: SessionsManager,
    sidebarVisible: Boolean = true,
) {
    var settings by remember { mutableStateOf(AppSettingsStore.load()) }
    var settingsOpen by remember { mutableStateOf(false) }

    fun updateSettings(new: AppSettings) {
        settings = new
        AppSettingsStore.save(new)
    }

    CompositionLocalProvider(
        LocalLogHoundColors provides settings.theme.colors,
        LocalAppSettings provides settings,
    ) {
        val colors = LocalLogHoundColors.current
        MaterialTheme {
            val activeSession by sessionsManager.active.collectAsState()
            val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()
            val openTabs = remember(plugins) {
                mutableStateListOf<UIPlugin>().apply {
                    plugins.firstOrNull { it.id == CORE_LOG_VIEWER_ID }?.let { add(it) }
                }
            }
            var activeTabId by remember(plugins) {
                mutableStateOf(plugins.firstOrNull { it.id == CORE_LOG_VIEWER_ID }?.id)
            }
            var activeDevice by remember { mutableStateOf<DeviceId?>(null) }
            val detectedDevices by repository.devices.collectAsState()
            if (activeDevice != null && detectedDevices.none { it.id == activeDevice }) {
                activeDevice = null
            }

            fun openOrFocus(plugin: UIPlugin) {
                if (openTabs.none { it.id == plugin.id }) openTabs.add(plugin)
                activeTabId = plugin.id
            }

            fun closeTab(plugin: UIPlugin) {
                val idx = openTabs.indexOfFirst { it.id == plugin.id }
                if (idx < 0) return
                openTabs.removeAt(idx)
                if (activeTabId == plugin.id) {
                    activeTabId = if (openTabs.isEmpty()) null
                    else openTabs[(idx - 1).coerceAtLeast(0)].id
                }
            }

            Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (sidebarVisible) {
                            Sidebar(
                                plugins = plugins,
                                activeTabId = activeTabId,
                                onClick = { openOrFocus(it) },
                            )
                            VerticalDivider(color = colors.border)
                        }
                        Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                            TabBar(
                                tabs = openTabs,
                                activeTabId = activeTabId,
                                onSelect = { activeTabId = it.id },
                                onClose = { closeTab(it) },
                                onOpenSettings = { settingsOpen = true },
                            )
                            HorizontalDivider(color = colors.border)
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                val active = openTabs.firstOrNull { it.id == activeTabId }
                                if (active != null) {
                                    CompositionLocalProvider(LocalActiveDevice provides activeDevice) {
                                        active.content(Modifier.fillMaxSize())
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "No tab open. Pick a plugin from the sidebar.",
                                            style = LogHoundDesign.Text.Status,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = colors.border)
                    StatusBar(
                        devices = detectedDevices,
                        activeDevice = activeDevice,
                        onSelectDevice = { activeDevice = it },
                        activeSession = activeSession,
                        onEndAndStartNewSession = {
                            coroutineScope.launch { sessionsManager.endAndStartNew() }
                        },
                    )
                }

                if (settingsOpen) {
                    SettingsPanel(
                        settings = settings,
                        onSettingsChange = { updateSettings(it) },
                        onDismiss = { settingsOpen = false },
                    )
                }
            }
        }
    }
}

@Composable
private fun Sidebar(
    plugins: List<UIPlugin>,
    activeTabId: String?,
    onClick: (UIPlugin) -> Unit,
) {
    val colors = LocalLogHoundColors.current
    Surface(
        modifier = Modifier.width(220.dp).fillMaxHeight(),
        color = colors.surface,
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
            items(plugins, key = { it.id }) { plugin ->
                val selected = plugin.id == activeTabId
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clickable { onClick(plugin) }
                        .background(
                            if (selected) colors.pressedBackground else Color.Transparent,
                        )
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text(
                        plugin.name,
                        style = LogHoundDesign.Text.Tab.copy(color = colors.onSurface),
                    )
                }
            }
        }
    }
}

@Composable
private fun TabBar(
    tabs: List<UIPlugin>,
    activeTabId: String?,
    onSelect: (UIPlugin) -> Unit,
    onClose: (UIPlugin) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val colors = LocalLogHoundColors.current
    Surface(
        modifier = Modifier.fillMaxWidth().height(TAB_BAR_HEIGHT),
        color = colors.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            tabs.forEach { tab ->
                val active = tab.id == activeTabId
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .clickable { onSelect(tab) }
                        .background(
                            if (active) colors.background else Color.Transparent,
                        )
                        .padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tab.name,
                        style = LogHoundDesign.Text.Tab.copy(
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                            color = colors.onSurface,
                        ),
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onClose(tab) }
                            .padding(horizontal = 4.dp),
                    ) {
                        Text("✕", style = LogHoundDesign.Text.TabClose.copy(color = colors.secondary))
                    }
                }
                VerticalDivider(color = colors.border)
            }
            Spacer(modifier = Modifier.weight(1f))
            // Settings gear
            Text(
                "⚙",
                style = LogHoundDesign.Text.Tab.copy(color = colors.secondary),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onOpenSettings)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}
