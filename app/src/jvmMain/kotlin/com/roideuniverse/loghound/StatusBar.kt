package com.roideuniverse.loghound

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.roideuniverse.loghound.core.Device
import com.roideuniverse.loghound.core.DeviceId
import com.roideuniverse.loghound.design.LocalLogHoundColors
import com.roideuniverse.loghound.design.LogHoundDesign
import com.roideuniverse.loghound.plugins.sessions.Session
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Stable test tags for the status-bar UI. Loadbearing for E2E — renames
 * require updating tests.
 */
object StatusBarTestTags {
    const val DEVICE_PILL = "statusBar.devicePill"
    const val DEVICE_MENU_ITEM = "statusBar.devicePillItem"
    const val SESSION_PILL = "statusBar.sessionPill"
    const val SESSION_END_AND_NEW = "statusBar.sessionEndAndNew"
}

/**
 * Bottom-of-window status bar. Currently hosts the device scope pill;
 * a session pill ([Sessions]) lives next to it once issue #21 lands.
 */
@Composable
fun StatusBar(
    devices: Set<Device>,
    activeDevice: DeviceId?,
    onSelectDevice: (DeviceId?) -> Unit,
    activeSession: Session?,
    onEndAndStartNewSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLogHoundColors.current
    Surface(
        modifier = modifier.fillMaxWidth().height(28.dp),
        color = colors.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            DevicePill(
                devices = devices,
                activeDevice = activeDevice,
                onSelectDevice = onSelectDevice,
            )
            Spacer(Modifier.width(8.dp))
            SessionPill(
                session = activeSession,
                onEndAndStartNew = onEndAndStartNewSession,
            )
        }
    }
}

@Composable
private fun DevicePill(
    devices: Set<Device>,
    activeDevice: DeviceId?,
    onSelectDevice: (DeviceId?) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    // Stable serial -> palette-index assignment so a device keeps its color
    // for the lifetime of this status bar's recomposition. First-seen wins;
    // disconnects don't free the slot (the persistence spec says state.json
    // keyed by serial — for the in-memory layer here, FIFO is good enough).
    val colors = LocalLogHoundColors.current
    val deviceColors = remember { mutableMapOf<String, Color>() }
    val orderedDevices = devices.sortedBy { it.id.value }
    orderedDevices.forEach { dev ->
        deviceColors.getOrPut(dev.id.value) {
            colors.devicePalette[deviceColors.size % colors.devicePalette.size]
        }
    }
    val activeColor = activeDevice?.let { deviceColors[it.value] }
    val label = pillLabel(orderedDevices, activeDevice)

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(colors.pressedBackground)
                .clickable { menuOpen = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag(StatusBarTestTags.DEVICE_PILL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(activeColor ?: colors.secondary),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = LogHoundDesign.Text.Status.copy(color = colors.onSurface))
            Spacer(Modifier.width(4.dp))
            Text("▾", style = LogHoundDesign.Text.Status.copy(color = colors.secondary))
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(colors.secondary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "All devices",
                            style = if (activeDevice == null) {
                                LogHoundDesign.Text.Tab.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = colors.onSurface)
                            } else LogHoundDesign.Text.Tab.copy(color = colors.onSurface),
                        )
                    }
                },
                onClick = {
                    onSelectDevice(null)
                    menuOpen = false
                },
                modifier = Modifier.testTag(StatusBarTestTags.DEVICE_MENU_ITEM),
            )
            if (orderedDevices.isNotEmpty()) {
                HorizontalDivider(color = colors.border)
            }
            for (device in orderedDevices) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(deviceColors[device.id.value] ?: colors.secondary),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                device.label,
                                style = if (activeDevice == device.id) {
                                    LogHoundDesign.Text.Tab.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = colors.onSurface)
                                } else LogHoundDesign.Text.Tab.copy(color = colors.onSurface),
                            )
                        }
                    },
                    onClick = {
                        onSelectDevice(device.id)
                        menuOpen = false
                    },
                    modifier = Modifier.testTag(StatusBarTestTags.DEVICE_MENU_ITEM),
                )
            }
        }
    }
}

private fun pillLabel(devices: List<Device>, active: DeviceId?): String {
    if (devices.isEmpty()) return "No devices"
    return when {
        active == null -> if (devices.size == 1) devices[0].label else "${devices.size} devices"
        else -> devices.find { it.id == active }?.label ?: active.value
    }
}

@Composable
private fun SessionPill(
    session: Session?,
    onEndAndStartNew: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        val colors = LocalLogHoundColors.current
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(colors.pressedBackground)
                .clickable(enabled = session != null) { menuOpen = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag(StatusBarTestTags.SESSION_PILL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⏱", style = LogHoundDesign.Text.Status.copy(color = colors.secondary))
            Spacer(Modifier.width(6.dp))
            Text(
                sessionLabel(session),
                style = LogHoundDesign.Text.Status.copy(color = colors.onSurface),
            )
            if (session != null) {
                Spacer(Modifier.width(4.dp))
                Text("▾", style = LogHoundDesign.Text.Status.copy(color = colors.secondary))
            }
        }
        if (session != null) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
            ) {
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(session.name, style = LogHoundDesign.Text.Tab.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, color = colors.onSurface))
                            Text(
                                "Started " + formatTimestamp(session.startedAt),
                                style = LogHoundDesign.Text.Status.copy(color = colors.secondary),
                            )
                        }
                    },
                    enabled = false,
                    onClick = {},
                )
                HorizontalDivider(color = colors.border)
                DropdownMenuItem(
                    text = { Text("End & Start New Session", style = LogHoundDesign.Text.Tab.copy(color = colors.onSurface)) },
                    onClick = {
                        menuOpen = false
                        onEndAndStartNew()
                    },
                    modifier = Modifier.testTag(StatusBarTestTags.SESSION_END_AND_NEW),
                )
            }
        }
    }
}

private fun sessionLabel(session: Session?): String {
    if (session == null) return "No session"
    return session.name
}

private fun formatTimestamp(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
    return fmt.format(Date(millis))
}
