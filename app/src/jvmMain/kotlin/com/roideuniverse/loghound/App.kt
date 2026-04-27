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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
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
import com.roideuniverse.loghound.core.UIPlugin

private const val CORE_LOG_VIEWER_ID = "core.log-viewer"
private val TAB_BAR_HEIGHT = 36.dp

@Composable
fun App(
    plugins: List<UIPlugin>,
    sidebarVisible: Boolean = true,
) {
    MaterialTheme {
        val openTabs = remember(plugins) {
            mutableStateListOf<UIPlugin>().apply {
                plugins.firstOrNull { it.id == CORE_LOG_VIEWER_ID }?.let { add(it) }
            }
        }
        var activeTabId by remember(plugins) {
            mutableStateOf(plugins.firstOrNull { it.id == CORE_LOG_VIEWER_ID }?.id)
        }

        fun openOrFocus(plugin: UIPlugin) {
            if (openTabs.none { it.id == plugin.id }) {
                openTabs.add(plugin)
            }
            activeTabId = plugin.id
        }

        fun closeTab(plugin: UIPlugin) {
            val idx = openTabs.indexOfFirst { it.id == plugin.id }
            if (idx < 0) return
            openTabs.removeAt(idx)
            if (activeTabId == plugin.id) {
                activeTabId = if (openTabs.isEmpty()) {
                    null
                } else {
                    openTabs[(idx - 1).coerceAtLeast(0)].id
                }
            }
        }

        Row(modifier = Modifier.fillMaxSize()) {
            if (sidebarVisible) {
                Sidebar(
                    plugins = plugins,
                    activeTabId = activeTabId,
                    onClick = { openOrFocus(it) },
                )
                VerticalDivider(color = Color(0xFFCCCCCC))
            }
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                TabBar(
                    tabs = openTabs,
                    activeTabId = activeTabId,
                    onSelect = { activeTabId = it.id },
                    onClose = { closeTab(it) },
                )
                HorizontalDivider(color = Color(0xFFCCCCCC))
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    val active = openTabs.firstOrNull { it.id == activeTabId }
                    if (active != null) {
                        active.content(Modifier.fillMaxSize())
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "No tab open. Pick a plugin from the sidebar.",
                                color = Color(0xFF666666),
                            )
                        }
                    }
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
    Surface(
        modifier = Modifier.width(220.dp).fillMaxHeight(),
        color = Color(0xFFF2F2F2),
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(vertical = 4.dp)) {
            items(plugins, key = { it.id }) { plugin ->
                val selected = plugin.id == activeTabId
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onClick(plugin) }
                        .background(
                            if (selected) Color(0xFFD9E2F3) else Color.Transparent,
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(plugin.name)
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
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(TAB_BAR_HEIGHT),
        color = Color(0xFFEDEDED),
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
                            if (active) Color.White else Color(0xFFEDEDED),
                        )
                        .padding(start = 12.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tab.name,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    )
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onClose(tab) }
                            .padding(horizontal = 4.dp),
                    ) {
                        Text("✕", color = Color(0xFF666666))
                    }
                }
                VerticalDivider(color = Color(0xFFE0E0E0))
            }
        }
    }
}
