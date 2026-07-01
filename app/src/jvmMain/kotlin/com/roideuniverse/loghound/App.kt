package com.roideuniverse.loghound

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.LocalWindow
import com.roideuniverse.loghound.core.DeviceId
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.core.UIPlugin
import com.roideuniverse.loghound.design.LocalActiveDevice
import com.roideuniverse.loghound.design.LocalDeviceColorMap
import com.roideuniverse.loghound.design.LocalDisplaySettings
import com.roideuniverse.loghound.design.LocalLogHoundColors
import com.roideuniverse.loghound.design.LogHoundDesign
import com.roideuniverse.loghound.plugins.sessions.SessionsManager
import kotlinx.coroutines.launch

private val TITLE_BAR_HEIGHT = 38.dp
private val SIDEBAR_WIDTH = 180.dp
private val isMac = System.getProperty("os.name")?.startsWith("Mac") == true

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
        LocalDisplaySettings provides settings.toDisplaySettings(),
        LocalAppSettings provides settings,
    ) {
        val colors = LocalLogHoundColors.current
        MaterialTheme {
            // On macOS: make the native title bar transparent so our TitleBar
            // renders in its area. The OS still draws traffic lights over ours.
            val window = LocalWindow.current
            LaunchedEffect(Unit) {
                if (isMac) {
                    (window as? javax.swing.JFrame)?.rootPane?.let { rp ->
                        rp.putClientProperty("apple.awt.fullWindowContent", true)
                        rp.putClientProperty("apple.awt.transparentTitleBar", true)
                        rp.putClientProperty("apple.awt.windowTitleVisible", false)
                    }
                }
            }

            val activeSession by sessionsManager.active.collectAsState()
            val coroutineScope = rememberCoroutineScope()
            var activePluginId by remember(plugins) { mutableStateOf(plugins.firstOrNull()?.id) }
            var activeDevice by remember { mutableStateOf<DeviceId?>(null) }
            val detectedDevices by repository.devices.collectAsState()
            if (activeDevice != null && detectedDevices.none { it.id == activeDevice }) {
                activeDevice = null
            }

            // Build stable device-id → palette-color map for the whole session.
            val deviceColorMap =
                remember(detectedDevices, colors.devicePalette) {
                    detectedDevices
                        .sortedBy { it.id.value }
                        .mapIndexed { i, dev ->
                            dev.id.value to colors.devicePalette[i % colors.devicePalette.size]
                        }
                        .toMap()
                }

            CompositionLocalProvider(LocalDeviceColorMap provides deviceColorMap) {
                Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        TitleBar(
                            settings = settings,
                            onOpenSettings = { settingsOpen = true },
                            onSelectTheme = { updateSettings(settings.copy(theme = it)) },
                        )
                        HorizontalDivider(color = colors.border)
                        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (sidebarVisible) {
                                AppSidebar(
                                    plugins = plugins,
                                    activePluginId = activePluginId,
                                    onSelect = { activePluginId = it.id },
                                )
                                VerticalDivider(color = colors.border)
                            }
                            Box(
                                modifier =
                                    Modifier.weight(1f)
                                        .fillMaxHeight()
                                        .background(colors.background)
                            ) {
                                val active = plugins.firstOrNull { it.id == activePluginId }
                                if (active != null) {
                                    CompositionLocalProvider(
                                        LocalActiveDevice provides activeDevice
                                    ) {
                                        active.content(Modifier.fillMaxSize())
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "Pick a plugin from the sidebar.",
                                            style =
                                                LogHoundDesign.Text.Status.copy(
                                                    color = colors.secondary
                                                ),
                                        )
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
            } // LocalDeviceColorMap
        }
    }
}

@Composable
private fun TitleBar(
    settings: AppSettings,
    onOpenSettings: () -> Unit,
    onSelectTheme: (AppTheme) -> Unit,
) {
    val colors = LocalLogHoundColors.current
    val macTopPad = if (isMac) 6.dp else 0.dp
    var themeMenuOpen by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth().height(TITLE_BAR_HEIGHT).background(colors.surface)) {
        // Centered logo + title
        Row(
            modifier = Modifier.align(Alignment.Center).padding(top = macTopPad),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(13.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(colors.onSurface),
                contentAlignment = Alignment.Center,
            ) {
                Box(modifier = Modifier.size(5.dp).clip(CircleShape).background(colors.background))
            }
            Text(
                "LogHound",
                style =
                    LogHoundDesign.Text.Tab.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colors.onSurface,
                        fontSize = 13.sp,
                        letterSpacing = (-0.1).sp,
                    ),
            )
        }

        // Right: theme toggle popover + settings gear
        Row(
            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 12.dp, top = macTopPad),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Row(
                    modifier =
                        Modifier.height(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.input)
                            .clickable { themeMenuOpen = true }
                            .padding(horizontal = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("◑", style = LogHoundDesign.Text.Status.copy(color = colors.secondary))
                    Text(
                        settings.theme.displayName,
                        style =
                            LogHoundDesign.Text.Status.copy(
                                color = colors.onSurface,
                                fontWeight = FontWeight.Medium,
                            ),
                    )
                    Text(
                        "▾",
                        style =
                            LogHoundDesign.Text.Status.copy(
                                color = colors.secondary,
                                fontSize = 9.sp,
                            ),
                    )
                }
                DropdownMenu(
                    expanded = themeMenuOpen,
                    onDismissRequest = { themeMenuOpen = false },
                ) {
                    Text(
                        "APPEARANCE",
                        style =
                            LogHoundDesign.Text.Status.copy(
                                fontWeight = FontWeight.SemiBold,
                                letterSpacing = 0.5.sp,
                                color = colors.secondary,
                            ),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                    AppTheme.entries.forEach { theme ->
                        val tc = theme.colors
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                                ) {
                                    // 4 swatches: bg, surface, accent, error
                                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                        listOf(
                                                tc.background,
                                                tc.surface,
                                                tc.primary,
                                                tc.priorityError,
                                            )
                                            .forEach { c ->
                                                Box(
                                                    modifier =
                                                        Modifier.size(8.dp)
                                                            .clip(CircleShape)
                                                            .background(c)
                                                )
                                            }
                                    }
                                    Text(
                                        theme.displayName,
                                        style =
                                            LogHoundDesign.Text.Tab.copy(
                                                color = colors.onSurface,
                                                fontWeight = FontWeight.Medium,
                                                fontSize = 13.sp,
                                            ),
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (settings.theme == theme) {
                                        Text(
                                            "✓",
                                            style =
                                                LogHoundDesign.Text.Status.copy(
                                                    color = colors.primary,
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                        )
                                    }
                                }
                            },
                            onClick = {
                                onSelectTheme(theme)
                                themeMenuOpen = false
                            },
                        )
                    }
                }
            }
            Box(
                modifier =
                    Modifier.size(width = 26.dp, height = 24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.input)
                        .clickable(onClick = onOpenSettings),
                contentAlignment = Alignment.Center,
            ) {
                Text("⚙", style = LogHoundDesign.Text.Status.copy(color = colors.secondary))
            }
        }
    }
}

@Composable
private fun AppSidebar(
    plugins: List<UIPlugin>,
    activePluginId: String?,
    onSelect: (UIPlugin) -> Unit,
) {
    val colors = LocalLogHoundColors.current
    val pulse = rememberInfiniteTransition()
    val pulseAlpha by
        pulse.animateFloat(
            initialValue = 1f,
            targetValue = 0.25f,
            animationSpec =
                infiniteRepeatable(
                    animation = tween(900, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
        )

    Column(modifier = Modifier.width(SIDEBAR_WIDTH).fillMaxHeight().background(colors.surface)) {
        Text(
            "PLUGINS",
            style =
                LogHoundDesign.Text.Status.copy(
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.6.sp,
                    color = colors.secondary,
                ),
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp),
        )

        plugins.forEach { plugin ->
            val active = plugin.id == activePluginId
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(28.dp)
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (active) colors.pressedBackground else Color.Transparent)
                        .clickable { onSelect(plugin) }
                        .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Box(
                    modifier =
                        Modifier.size(5.dp)
                            .clip(CircleShape)
                            .background(if (active) colors.onSurface else colors.secondary)
                )
                Text(
                    plugin.name,
                    style =
                        LogHoundDesign.Text.Tab.copy(
                            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (active) colors.onSurface else colors.text2,
                            fontSize = 13.sp,
                        ),
                    modifier = Modifier.weight(1f),
                )
            }
        }

        Spacer(Modifier.weight(1f))

        HorizontalDivider(color = colors.border, modifier = Modifier.padding(horizontal = 8.dp))
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Box(
                modifier =
                    Modifier.size(6.dp).clip(CircleShape).alpha(pulseAlpha).background(colors.pulse)
            )
            Text(
                "Streaming · adb",
                style =
                    LogHoundDesign.Text.Status.copy(
                        color = colors.secondary,
                        fontWeight = FontWeight.Medium,
                    ),
            )
        }
    }
}
