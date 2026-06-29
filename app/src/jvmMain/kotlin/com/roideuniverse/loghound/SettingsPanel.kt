package com.roideuniverse.loghound

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roideuniverse.loghound.design.LocalLogHoundColors
import com.roideuniverse.loghound.design.LogHoundDesign

private enum class SettingsCategory(val label: String) {
    Appearance("Appearance"),
    Display("Display"),
}

@Composable
fun SettingsPanel(
    settings: AppSettings,
    onSettingsChange: (AppSettings) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalLogHoundColors.current

    // Scrim
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.3f))
            .clickable(onClick = onDismiss),
    ) {
        // Panel — stop click propagation to scrim
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .widthIn(min = 300.dp, max = 360.dp)
                .shadow(16.dp)
                .background(colors.elevated)
                .clickable(onClick = {}), // consume clicks so scrim doesn't close
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .background(colors.surface)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Settings",
                        style = LogHoundDesign.Text.Tab.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp,
                            color = colors.onSurface,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "✕",
                        style = LogHoundDesign.Text.TabClose.copy(color = colors.secondary),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(onClick = onDismiss)
                            .padding(8.dp),
                    )
                }
                HorizontalDivider(color = colors.border)

                var category by remember { mutableStateOf(SettingsCategory.Appearance) }

                // Category tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsCategory.entries.forEach { cat ->
                        val active = cat == category
                        Text(
                            cat.label,
                            style = LogHoundDesign.Text.Tab.copy(
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (active) colors.onSurface else colors.secondary,
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (active) colors.pressedBackground else Color.Transparent)
                                .clickable { category = cat }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                        )
                    }
                }
                HorizontalDivider(color = colors.border)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    when (category) {
                        SettingsCategory.Appearance -> AppearanceSettings(settings, onSettingsChange, colors)
                        SettingsCategory.Display    -> DisplaySettings(settings, onSettingsChange, colors)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceSettings(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    colors: com.roideuniverse.loghound.design.LogHoundColors,
) {
    SettingsSection("THEME") {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AppTheme.entries.forEach { theme ->
                val selected = settings.theme == theme
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (selected) colors.accentTint else Color.Transparent)
                        .clickable { onChange(settings.copy(theme = theme)) }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Theme color swatch
                    Box(
                        modifier = Modifier
                            .width(16.dp)
                            .height(16.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(theme.colors.primary),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        theme.displayName,
                        style = LogHoundDesign.Text.Field.copy(
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = colors.onSurface,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    if (selected) {
                        Text("✓", style = LogHoundDesign.Text.Status.copy(color = colors.primary))
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplaySettings(
    settings: AppSettings,
    onChange: (AppSettings) -> Unit,
    colors: com.roideuniverse.loghound.design.LogHoundColors,
) {
    SettingsSection("DENSITY") {
        SegmentedControl(
            options = Density.entries.map { it.name },
            selected = settings.density.name,
            onSelect = { onChange(settings.copy(density = Density.valueOf(it))) },
            colors = colors,
        )
    }

    SettingsSection("FONT SIZE") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StepperButton("-", enabled = settings.fontSize > 10, colors = colors) {
                onChange(settings.copy(fontSize = settings.fontSize - 1))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "${settings.fontSize}px",
                style = LogHoundDesign.Text.Field.copy(color = colors.onSurface),
            )
            Spacer(Modifier.width(12.dp))
            StepperButton("+", enabled = settings.fontSize < 18, colors = colors) {
                onChange(settings.copy(fontSize = settings.fontSize + 1))
            }
        }
    }

    SettingsSection("TIMESTAMP") {
        SegmentedControl(
            options = TimestampFormat.entries.map { it.displayName },
            selected = settings.timestampFormat.displayName,
            onSelect = { name ->
                val fmt = TimestampFormat.entries.first { it.displayName == name }
                onChange(settings.copy(timestampFormat = fmt))
            },
            colors = colors,
        )
    }

    SettingsSection("OPTIONS") {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ToggleRow("Show PID/TID", settings.showPid, colors) {
                onChange(settings.copy(showPid = it))
            }
            ToggleRow("Zebra rows", settings.zebraRows, colors) {
                onChange(settings.copy(zebraRows = it))
            }
            ToggleRow("Word wrap", settings.wordWrap, colors) {
                onChange(settings.copy(wordWrap = it))
            }
        }
    }
}

@Composable
private fun SettingsSection(label: String, content: @Composable () -> Unit) {
    val colors = LocalLogHoundColors.current
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            label,
            style = LogHoundDesign.Text.Status.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = colors.secondary,
            ),
        )
        content()
    }
}

@Composable
private fun SegmentedControl(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit,
    colors: com.roideuniverse.loghound.design.LogHoundColors,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(colors.input),
        horizontalArrangement = Arrangement.Start,
    ) {
        options.forEach { option ->
            val active = option == selected
            Text(
                option,
                style = LogHoundDesign.Text.Tab.copy(
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (active) colors.onSurface else colors.secondary,
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) colors.elevated else Color.Transparent)
                    .clickable { onSelect(option) }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    colors: com.roideuniverse.loghound.design.LogHoundColors,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .clickable { onToggle(!checked) }
            .padding(horizontal = 4.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = LogHoundDesign.Text.Field.copy(color = colors.onSurface),
            modifier = Modifier.weight(1f),
        )
        // Toggle track
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (checked) colors.primary else colors.border),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .padding(2.dp)
                    .width(16.dp)
                    .height(16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun StepperButton(
    label: String,
    enabled: Boolean,
    colors: com.roideuniverse.loghound.design.LogHoundColors,
    onClick: () -> Unit,
) {
    Text(
        label,
        style = LogHoundDesign.Text.Tab.copy(
            color = if (enabled) colors.onSurface else colors.secondary,
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(colors.input)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
