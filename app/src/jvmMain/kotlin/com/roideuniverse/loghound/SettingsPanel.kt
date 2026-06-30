package com.roideuniverse.loghound

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roideuniverse.loghound.design.Density
import com.roideuniverse.loghound.design.LocalLogHoundColors
import com.roideuniverse.loghound.design.LogHoundDesign
import com.roideuniverse.loghound.design.TimestampFormat

private enum class SettingsCat(val label: String) {
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

    Box(modifier = Modifier.fillMaxSize().background(colors.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header (52dp)
            Row(
                modifier =
                    Modifier.fillMaxWidth()
                        .height(52.dp)
                        .background(colors.surface)
                        .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Text(
                    "⚙",
                    style = LogHoundDesign.Text.Tab.copy(color = colors.onSurface, fontSize = 15.sp),
                )
                Text(
                    "Settings",
                    style =
                        LogHoundDesign.Text.Tab.copy(
                            fontWeight = FontWeight.SemiBold,
                            color = colors.onSurface,
                            fontSize = 15.sp,
                        ),
                )
                Spacer(Modifier.weight(1f))
                // Done button (primary style)
                Box(
                    modifier =
                        Modifier.clip(RoundedCornerShape(6.dp))
                            .background(colors.button)
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Done",
                        style =
                            LogHoundDesign.Text.Tab.copy(
                                fontWeight = FontWeight.SemiBold,
                                color = colors.buttonText,
                                fontSize = 12.sp,
                            ),
                    )
                }
            }
            HorizontalDivider(color = colors.border)

            // Body: category rail + detail
            var category by remember { mutableStateOf(SettingsCat.Appearance) }
            Row(modifier = Modifier.fillMaxSize()) {
                // Category rail (198dp)
                Column(
                    modifier =
                        Modifier.width(198.dp)
                            .fillMaxHeight()
                            .background(colors.surface)
                            .padding(horizontal = 10.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    SettingsCat.entries.forEach { cat ->
                        val active = cat == category
                        Text(
                            cat.label,
                            style =
                                LogHoundDesign.Text.Tab.copy(
                                    fontWeight =
                                        if (active) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (active) colors.onSurface else colors.text2,
                                    fontSize = 13.sp,
                                ),
                            modifier =
                                Modifier.fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(
                                        if (active) colors.pressedBackground else Color.Transparent
                                    )
                                    .clickable { category = cat }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    Text(
                        "LogHound 1.0.0",
                        style =
                            LogHoundDesign.Text.Status.copy(color = colors.dim2, fontSize = 10.sp),
                        modifier = Modifier.padding(8.dp),
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.fillMaxHeight().width(1.dp),
                    color = colors.border,
                )

                // Detail area
                Column(
                    modifier =
                        Modifier.fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 30.dp, vertical = 26.dp)
                ) {
                    Box(modifier = Modifier.widthIn(max = 640.dp)) {
                        when (category) {
                            SettingsCat.Appearance -> AppearanceDetail(settings, onSettingsChange)
                            SettingsCat.Display -> DisplayDetail(settings, onSettingsChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppearanceDetail(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val colors = LocalLogHoundColors.current

    SectionHeader("THEME")
    Spacer(Modifier.height(12.dp))

    // 2-column grid of theme cards
    val themes = AppTheme.entries
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        themes.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { theme ->
                    ThemeCard(
                        theme = theme,
                        selected = settings.theme == theme,
                        onClick = { onChange(settings.copy(theme = theme)) },
                        modifier = Modifier.weight(1f),
                    )
                }
                // Pad last row if odd number of themes
                if (row.size < 2) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ThemeCard(
    theme: AppTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalLogHoundColors.current
    val tc = theme.colors
    val borderColor = if (selected) colors.primary else colors.border

    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(8.dp))
                .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
    ) {
        // Mini preview
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .height(70.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(tc.background)
        ) {
            // Sidebar strip
            Column(
                modifier =
                    Modifier.width(36.dp).fillMaxHeight().background(tc.surface).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                repeat(3) { i ->
                    Box(
                        modifier =
                            Modifier.fillMaxWidth(if (i == 0) 0.9f else 0.7f)
                                .height(5.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (i == 0) tc.onSurface else tc.secondary.copy(alpha = 0.5f)
                                )
                    )
                }
            }
            // Content preview
            Column(
                modifier = Modifier.fillMaxSize().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier =
                        Modifier.width(40.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tc.primary)
                )
                repeat(2) {
                    Box(
                        modifier =
                            Modifier.fillMaxWidth(0.8f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(tc.onSurface.copy(alpha = 0.4f))
                    )
                }
                Box(
                    modifier =
                        Modifier.size(width = 20.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(tc.priorityError)
                )
            }
        }
        // Label row
        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .background(colors.surface)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                theme.displayName,
                style =
                    LogHoundDesign.Text.Tab.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = colors.onSurface,
                        fontSize = 13.sp,
                    ),
                modifier = Modifier.weight(1f),
            )
            if (selected) {
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
    }
}

@Composable
private fun DisplayDetail(settings: AppSettings, onChange: (AppSettings) -> Unit) {
    val colors = LocalLogHoundColors.current

    SectionHeader("DENSITY")
    Spacer(Modifier.height(8.dp))
    SegControl(
        options = Density.entries.map { it.name },
        selected = settings.density.name,
        onSelect = { onChange(settings.copy(density = Density.valueOf(it))) },
    )

    Spacer(Modifier.height(22.dp))
    SectionHeader("FONT SIZE")
    Spacer(Modifier.height(8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StepBtn("-", settings.fontSize > 10) {
            onChange(settings.copy(fontSize = settings.fontSize - 1))
        }
        Text(
            "${settings.fontSize}px",
            style = LogHoundDesign.Text.Field.copy(color = colors.onSurface),
        )
        StepBtn("+", settings.fontSize < 18) {
            onChange(settings.copy(fontSize = settings.fontSize + 1))
        }
    }

    Spacer(Modifier.height(22.dp))
    SectionHeader("TIMESTAMP")
    Spacer(Modifier.height(8.dp))
    SegControl(
        options = TimestampFormat.entries.map { it.displayName },
        selected = settings.timestampFormat.displayName,
        onSelect = { name ->
            onChange(
                settings.copy(
                    timestampFormat = TimestampFormat.entries.first { it.displayName == name }
                )
            )
        },
    )

    Spacer(Modifier.height(22.dp))
    SectionHeader("OPTIONS")
    Spacer(Modifier.height(4.dp))
    ToggleRow("Show PID / TID", settings.showPid) { onChange(settings.copy(showPid = it)) }
    ToggleRow("Zebra rows", settings.zebraRows) { onChange(settings.copy(zebraRows = it)) }
    ToggleRow("Word wrap", settings.wordWrap) { onChange(settings.copy(wordWrap = it)) }
}

@Composable
private fun SectionHeader(label: String) {
    val colors = LocalLogHoundColors.current
    Text(
        label,
        style =
            LogHoundDesign.Text.Status.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                color = colors.secondary,
            ),
    )
}

@Composable
private fun SegControl(options: List<String>, selected: String, onSelect: (String) -> Unit) {
    val colors = LocalLogHoundColors.current
    Row(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(colors.input)) {
        options.forEach { opt ->
            val active = opt == selected
            Text(
                opt,
                style =
                    LogHoundDesign.Text.Tab.copy(
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) colors.onSurface else colors.secondary,
                        fontSize = 12.sp,
                    ),
                modifier =
                    Modifier.clip(RoundedCornerShape(6.dp))
                        .background(if (active) colors.elevated else Color.Transparent)
                        .clickable { onSelect(opt) }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val colors = LocalLogHoundColors.current
    Row(
        modifier =
            Modifier.fillMaxWidth()
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
        Box(
            modifier =
                Modifier.width(36.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (checked) colors.primary else colors.border),
            contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
        ) {
            Box(
                modifier =
                    Modifier.padding(2.dp)
                        .size(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
            )
        }
    }
}

@Composable
private fun StepBtn(label: String, enabled: Boolean, onClick: () -> Unit) {
    val colors = LocalLogHoundColors.current
    Text(
        label,
        style =
            LogHoundDesign.Text.Tab.copy(
                color = if (enabled) colors.onSurface else colors.secondary
            ),
        modifier =
            Modifier.clip(RoundedCornerShape(4.dp))
                .background(colors.input)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}
