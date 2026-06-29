package com.roideuniverse.loghound.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.roideuniverse.loghound.core.LogPriority

/**
 * Complete per-theme color token set.
 *
 * The four predefined instances live in [LogHoundThemes]. Composables read
 * the active theme via [LocalLogHoundColors].current rather than reaching
 * into [LogHoundDesign.Colors] directly — the latter stays as a Light-theme
 * alias for non-composable callers (DSL plugins, unit tests, etc.).
 *
 * Token naming follows the Claude Design prototype (2026-06-28). The mapping
 * from prototype CSS variables to Kotlin names is documented in
 * specs/design/swiss-direction.md → Claude Design Revision.
 */
data class LogHoundColors(
    // ---- surfaces ----
    val background: Color,
    val surface: Color,
    val elevated: Color,

    // ---- borders / dividers ----
    val border: Color,
    val borderSoft: Color,
    val rowDivider: Color,

    // ---- interactive backgrounds ----
    val hoverBackground: Color,
    val hover2: Color,
    val pressedBackground: Color,
    val input: Color,

    // ---- text ----
    val onSurface: Color,
    val text2: Color,
    val secondary: Color,
    val dim2: Color,

    // ---- accent ----
    val primary: Color,
    val accentTint: Color,
    val accentSoft: Color,
    val onAccent: Color,

    // ---- buttons ----
    val button: Color,
    val buttonText: Color,
    val buttonHover: Color,

    // ---- priority foregrounds ----
    val priorityVerbose: Color,
    val priorityDebug: Color,
    val priorityInfo: Color,
    val priorityWarn: Color,
    val priorityError: Color,

    // ---- priority badge backgrounds ----
    val priorityBgVerbose: Color,
    val priorityBgDebug: Color,
    val priorityBgInfo: Color,
    val priorityBgWarn: Color,
    val priorityBgError: Color,

    // ---- search highlights ----
    val hlBackground: Color,
    val hlForeground: Color,

    // ---- chrome ----
    val pulse: Color,
    val scrollbar: Color,
    val scrollbarHover: Color,

    // ---- device palette (round-robin assignment) ----
    val devicePalette: List<Color>,
)

fun LogHoundColors.colorFor(priority: LogPriority): Color = when (priority) {
    LogPriority.Verbose -> priorityVerbose
    LogPriority.Debug   -> priorityDebug
    LogPriority.Info    -> priorityInfo
    LogPriority.Warn    -> priorityWarn
    LogPriority.Error   -> priorityError
    LogPriority.Fatal   -> priorityError
    LogPriority.Silent  -> secondary
}

fun LogHoundColors.badgeBgFor(priority: LogPriority): Color = when (priority) {
    LogPriority.Verbose -> priorityBgVerbose
    LogPriority.Debug   -> priorityBgDebug
    LogPriority.Info    -> priorityBgInfo
    LogPriority.Warn    -> priorityBgWarn
    LogPriority.Error   -> priorityBgError
    LogPriority.Fatal   -> priorityBgError
    LogPriority.Silent  -> priorityBgDebug
}

/** Active theme injected by the window shell via [androidx.compose.runtime.CompositionLocalProvider]. */
val LocalLogHoundColors = staticCompositionLocalOf { LogHoundThemes.Light }

object LogHoundThemes {

    val Light = LogHoundColors(
        background      = Color(0xFFFFFFFF),
        surface         = Color(0xFFF7F7F7),
        elevated        = Color(0xFFFFFFFF),
        border          = Color(0xFFE5E5E5),
        borderSoft      = Color(0xFFF0F0F0),
        rowDivider      = Color(0xFFF4F4F4),
        hoverBackground = Color(0xFFFAFAFA),
        hover2          = Color(0xFFF2F2F2),
        pressedBackground = Color(0xFFEBEBEB),
        input           = Color(0xFFEBEBEB),
        onSurface       = Color(0xFF1A1A1A),
        text2           = Color(0xFF444444),
        secondary       = Color(0xFF8C8C8C),
        dim2            = Color(0xFFA0A0A0),
        primary         = Color(0xFF0066FF),
        accentTint      = Color(0xFFE5EFFF),
        accentSoft      = Color(0xFFF2F6FF),
        onAccent        = Color(0xFFFFFFFF),
        button          = Color(0xFF1A1A1A),
        buttonText      = Color(0xFFFFFFFF),
        buttonHover     = Color(0xFF000000),
        priorityVerbose = Color(0xFF00A04C),
        priorityDebug   = Color(0xFF8C8C8C),
        priorityInfo    = Color(0xFF0066FF),
        priorityWarn    = Color(0xFFFF6B00),
        priorityError   = Color(0xFFE52E2E),
        priorityBgVerbose = Color(0xFFE6F5EC),
        priorityBgDebug   = Color(0xFFEEEEEE),
        priorityBgInfo    = Color(0xFFE5EFFF),
        priorityBgWarn    = Color(0xFFFFF1E5),
        priorityBgError   = Color(0xFFFBEAEE),
        hlBackground    = Color(0xFFFFE08A),
        hlForeground    = Color(0xFF1A1A1A),
        pulse           = Color(0xFF00A04C),
        scrollbar       = Color(0xFFD4D4D4),
        scrollbarHover  = Color(0xFFBCBCBC),
        devicePalette   = listOf(
            Color(0xFF0066FF),
            Color(0xFFFF6B00),
            Color(0xFF00A04C),
            Color(0xFFE52E2E),
            Color(0xFF8000FF),
            Color(0xFF00B5D8),
        ),
    )

    val Darcula = LogHoundColors(
        background      = Color(0xFF2B2B2B),
        surface         = Color(0xFF3C3F41),
        elevated        = Color(0xFF3C3F41),
        border          = Color(0xFF4A4D4F),
        borderSoft      = Color(0xFF323436),
        rowDivider      = Color(0xFF323436),
        hoverBackground = Color(0xFF353739),
        hover2          = Color(0xFF3C3F41),
        pressedBackground = Color(0xFF4E5254),
        input           = Color(0xFF45494A),
        onSurface       = Color(0xFFC8CDD2),
        text2           = Color(0xFFA9B0B6),
        secondary       = Color(0xFF808080),
        dim2            = Color(0xFF6A6A6A),
        primary         = Color(0xFF4DA6FF),
        accentTint      = Color(0x294DA6FF),
        accentSoft      = Color(0x1A4DA6FF),
        onAccent        = Color(0xFFFFFFFF),
        button          = Color(0xFF3574F0),
        buttonText      = Color(0xFFFFFFFF),
        buttonHover     = Color(0xFF3E7BF5),
        priorityVerbose = Color(0xFF9CA0A6),
        priorityDebug   = Color(0xFF6897BB),
        priorityInfo    = Color(0xFF6A8759),
        priorityWarn    = Color(0xFFBBB529),
        priorityError   = Color(0xFFFF6B68),
        priorityBgVerbose = Color(0x299CA0A6),
        priorityBgDebug   = Color(0x2E6897BB),
        priorityBgInfo    = Color(0x336A8759),
        priorityBgWarn    = Color(0x2EBBB529),
        priorityBgError   = Color(0x2EFF6B68),
        hlBackground    = Color(0x57BBB529),
        hlForeground    = Color(0xFFFFE9A8),
        pulse           = Color(0xFF62C56E),
        scrollbar       = Color(0xFF5A5A5A),
        scrollbarHover  = Color(0xFF6E6E6E),
        devicePalette   = listOf(
            Color(0xFF4DA6FF),
            Color(0xFFFFA657),
            Color(0xFF62C56E),
            Color(0xFFFF6B68),
            Color(0xFFB57AFF),
            Color(0xFF4DC4D8),
        ),
    )

    val Nord = LogHoundColors(
        background      = Color(0xFF2E3440),
        surface         = Color(0xFF3B4252),
        elevated        = Color(0xFF3B4252),
        border          = Color(0xFF434C5E),
        borderSoft      = Color(0xFF3B4252),
        rowDivider      = Color(0xFF3B4252),
        hoverBackground = Color(0xFF39404E),
        hover2          = Color(0xFF3B4252),
        pressedBackground = Color(0xFF434C5E),
        input           = Color(0xFF434C5E),
        onSurface       = Color(0xFFECEFF4),
        text2           = Color(0xFFD8DEE9),
        secondary       = Color(0xFF828B9C),
        dim2            = Color(0xFF667084),
        primary         = Color(0xFF88C0D0),
        accentTint      = Color(0x2988C0D0),
        accentSoft      = Color(0x1A88C0D0),
        onAccent        = Color(0xFF2E3440),
        button          = Color(0xFF5E81AC),
        buttonText      = Color(0xFFECEFF4),
        buttonHover     = Color(0xFF6B8FBC),
        priorityVerbose = Color(0xFF9AA5B5),
        priorityDebug   = Color(0xFF81A1C1),
        priorityInfo    = Color(0xFFA3BE8C),
        priorityWarn    = Color(0xFFEBCB8B),
        priorityError   = Color(0xFFBF616A),
        priorityBgVerbose = Color(0x299AA5B5),
        priorityBgDebug   = Color(0x3381A1C1),
        priorityBgInfo    = Color(0x33A3BE8C),
        priorityBgWarn    = Color(0x2EEBCB8B),
        priorityBgError   = Color(0x33BF616A),
        hlBackground    = Color(0x4DEBCB8B),
        hlForeground    = Color(0xFFECEFF4),
        pulse           = Color(0xFFA3BE8C),
        scrollbar       = Color(0xFF4C566A),
        scrollbarHover  = Color(0xFF5C6680),
        devicePalette   = listOf(
            Color(0xFF88C0D0),
            Color(0xFFD08770),
            Color(0xFFA3BE8C),
            Color(0xFFBF616A),
            Color(0xFFB48EAD),
            Color(0xFF8FBCBB),
        ),
    )

    val HighContrast = LogHoundColors(
        background      = Color(0xFF000000),
        surface         = Color(0xFF101012),
        elevated        = Color(0xFF101012),
        border          = Color(0xFF6F6F6F),
        borderSoft      = Color(0xFF3A3A3A),
        rowDivider      = Color(0xFF2A2A2A),
        hoverBackground = Color(0xFF1A1A1A),
        hover2          = Color(0xFF1F1F1F),
        pressedBackground = Color(0xFF173052),
        input           = Color(0xFF1A1A1A),
        onSurface       = Color(0xFFFFFFFF),
        text2           = Color(0xFFE4E4E4),
        secondary       = Color(0xFFB0B0B0),
        dim2            = Color(0xFF909090),
        primary         = Color(0xFF3FF5FF),
        accentTint      = Color(0x2E3FF5FF),
        accentSoft      = Color(0x1A3FF5FF),
        onAccent        = Color(0xFF000000),
        button          = Color(0xFF3FF5FF),
        buttonText      = Color(0xFF000000),
        buttonHover     = Color(0xFF6AF8FF),
        priorityVerbose = Color(0xFFFFFFFF),
        priorityDebug   = Color(0xFF5BC0FF),
        priorityInfo    = Color(0xFF5BFF8F),
        priorityWarn    = Color(0xFFFFE34D),
        priorityError   = Color(0xFFFF5C5C),
        priorityBgVerbose = Color(0x24FFFFFF),
        priorityBgDebug   = Color(0x335BC0FF),
        priorityBgInfo    = Color(0x2E5BFF8F),
        priorityBgWarn    = Color(0x2EFFE34D),
        priorityBgError   = Color(0x33FF5C5C),
        hlBackground    = Color(0x52FFE34D),
        hlForeground    = Color(0xFFFFFFFF),
        pulse           = Color(0xFF5BFF8F),
        scrollbar       = Color(0xFF5A5A5A),
        scrollbarHover  = Color(0xFF7A7A7A),
        devicePalette   = listOf(
            Color(0xFF5BC0FF),
            Color(0xFFFFAF52),
            Color(0xFF5BFF8F),
            Color(0xFFFF5C5C),
            Color(0xFFD07FFF),
            Color(0xFF5BFFE0),
        ),
    )
}
