package com.roideuniverse.loghound.design

import androidx.compose.runtime.staticCompositionLocalOf

enum class Density { Compact, Comfortable }

enum class TimestampFormat(val displayName: String) {
    Full("Full"),
    Short("Short"),
    Seconds("Seconds"),
}

/**
 * Per-user display preferences that affect how log rows are rendered.
 * Provided by the window shell via [LocalDisplaySettings]; plugins read
 * it to honour density, font size, etc. without depending on `app`.
 */
data class DisplaySettings(
    val density: Density = Density.Compact,
    val fontSize: Int = 12,
    val showPid: Boolean = true,
    val zebraRows: Boolean = false,
    val wordWrap: Boolean = true,
    val timestampFormat: TimestampFormat = TimestampFormat.Full,
)

val LocalDisplaySettings = staticCompositionLocalOf { DisplaySettings() }
