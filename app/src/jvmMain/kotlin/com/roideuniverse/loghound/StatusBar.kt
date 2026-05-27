package com.roideuniverse.loghound

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import com.roideuniverse.loghound.design.LogHoundDesign

/**
 * Stable test tags for the status-bar UI. Loadbearing for E2E — renames
 * require updating tests.
 */
object StatusBarTestTags {
    const val DEVICE_PILL = "statusBar.devicePill"
    const val DEVICE_MENU_ITEM = "statusBar.devicePillItem"
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
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth().height(28.dp),
        color = LogHoundDesign.Colors.Surface,
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
    val deviceColors = remember { mutableMapOf<String, Color>() }
    val orderedDevices = devices.sortedBy { it.id.value }
    orderedDevices.forEachIndexed { i, dev ->
        deviceColors.getOrPut(dev.id.value) {
            LogHoundDesign.Colors.DevicePalette[(deviceColors.size) % LogHoundDesign.Colors.DevicePalette.size]
        }
    }
    val activeColor = activeDevice?.let { deviceColors[it.value] }
    val label = pillLabel(orderedDevices, activeDevice)

    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(LogHoundDesign.Colors.PressedBackground)
                .clickable { menuOpen = true }
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag(StatusBarTestTags.DEVICE_PILL),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(activeColor ?: LogHoundDesign.Colors.Secondary),
            )
            Spacer(Modifier.width(6.dp))
            Text(label, style = LogHoundDesign.Text.Status.copy(color = LogHoundDesign.Colors.OnSurface))
            Spacer(Modifier.width(4.dp))
            Text("▾", style = LogHoundDesign.Text.Status)
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
                                .background(LogHoundDesign.Colors.Secondary),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "All devices",
                            style = if (activeDevice == null) {
                                LogHoundDesign.Text.Tab.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                            } else LogHoundDesign.Text.Tab,
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
                HorizontalDivider(color = LogHoundDesign.Colors.Border)
            }
            for (device in orderedDevices) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        deviceColors[device.id.value] ?: LogHoundDesign.Colors.Secondary,
                                    ),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                device.label,
                                style = if (activeDevice == device.id) {
                                    LogHoundDesign.Text.Tab.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                                } else LogHoundDesign.Text.Tab,
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
