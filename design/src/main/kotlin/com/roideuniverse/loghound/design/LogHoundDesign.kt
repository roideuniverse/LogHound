package com.roideuniverse.loghound.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.unit.sp
import com.roideuniverse.loghound.core.LogPriority

/**
 * Single source of truth for LogHound's visual design tokens.
 *
 * Values follow the Modern Swiss Utility direction
 * ([`specs/design/swiss-direction.md`](../../../../../specs/design/swiss-direction.md)) —
 * one shared surface tone (#F7F7F7), one shared border tone (#E5E5E5), softer
 * text (#1A1A1A on near-white), a 4-accent palette (blue / orange / red /
 * green) reused across priority colors, device identifiers, and informational
 * states, and bundled Instrument Sans + JetBrains Mono families.
 *
 * Built-in plugins reference these directly. The DSL plugin theme
 * (`PluginTheme`) seeds its defaults from these, so script-loaded plugins
 * inherit the same look unless they explicitly override via `theme { ... }`.
 *
 * Layout values (paddings, dp spacing) are not tokenised here — they live
 * alongside the composables that use them.
 *
 * Fonts live in `design/src/main/resources/fonts/` and are loaded from the
 * classpath at first reference. Licenses (SIL OFL 1.1) ship alongside the
 * `.ttf` files in that directory.
 *
 * Users can override either family by pointing `font.ui.path` and/or
 * `font.mono.path` at a TTF/OTF in `~/.loghound/config.properties`. When an
 * override is present, that single file is used for every weight of that
 * family (Compose's font resolver snaps requested weights to the closest
 * available); when absent, the bundled multi-weight family is used.
 */
object LogHoundDesign {

    object Fonts {
        /** Instrument Sans (or user override) — UI text. */
        val Ui: FontFamily = buildUiFamily()

        /** JetBrains Mono (or user override) — log rows and tabular data. */
        val Mono: FontFamily = buildMonoFamily()

        private fun buildUiFamily(): FontFamily {
            UserFontConfig.uiOverride?.let { return FontFamily(Font(it, FontWeight.Normal)) }
            return FontFamily(
                Font("fonts/InstrumentSans-Regular.ttf", FontWeight.Normal),
                Font("fonts/InstrumentSans-Medium.ttf", FontWeight.Medium),
                Font("fonts/InstrumentSans-SemiBold.ttf", FontWeight.SemiBold),
            )
        }

        private fun buildMonoFamily(): FontFamily {
            UserFontConfig.monoOverride?.let { return FontFamily(Font(it, FontWeight.Normal)) }
            return FontFamily(
                Font("fonts/JetBrainsMono-Regular.ttf", FontWeight.Normal),
                Font("fonts/JetBrainsMono-Medium.ttf", FontWeight.Medium),
            )
        }
    }

    object Colors {
        val Background = Color.White
        val Surface = Color(0xFFF7F7F7)
        val Border = Color(0xFFE5E5E5)
        val Placeholder = Color(0xFF8C8C8C)

        // Semantic aliases — same value as Background/Border/Surface today, but
        // named distinctly so a future theme can split them (e.g. a tinted
        // focused-field background, an active pill that's brighter than the
        // page) without a sweeping rename. Callers reach for the semantic
        // name that matches their intent.
        val ActiveTabBackground = Color.White
        val TextFieldBackground = Background
        val TextFieldBorder = Border

        /** Row-hover background — a hair darker than Background so the mouse
         * target is visible without distracting from log content. */
        val HoverBackground = Color(0xFFFAFAFA)

        /** Selected/pressed-state background for clickable surfaces (sidebar
         * active item, etc.). One step darker than HoverBackground. */
        val PressedBackground = Color(0xFFEBEBEB)

        /** Per-device tint palette. Auto-assigned round-robin in the order
         * devices are first seen; persists in-memory for the session (the
         * state.json key-by-serial persistence from the spec is a follow-up).
         * The first two values match the Swiss spec's `--color-dev-1` (blue)
         * and `--color-dev-2` (orange); the remaining four extend it without
         * straying outside the existing priority palette tones. */
        val DevicePalette: List<Color> = listOf(
            Color(0xFF0066FF), // Primary blue
            Color(0xFFFF6B00), // Warn orange
            Color(0xFF00A04C), // Verbose green
            Color(0xFFE52E2E), // Error red
            Color(0xFF8000FF), // purple
            Color(0xFF00B5D8), // teal
        )

        /** Muted gray — timestamps, inactive tabs, secondary labels, close glyphs. */
        val Secondary = Color(0xFF8C8C8C)

        /** Swiss accent blue — links, the Debug priority, Device 1 identifier. */
        val Primary = Color(0xFF0066FF)

        /** Body text default — near-black, slightly softer than #000. */
        val OnSurface = Color(0xFF1A1A1A)

        // Priority palette. Swiss reassigns Verbose from gray to green and
        // routes Info through the accent blue; Debug falls back to Secondary
        // gray so the priority hierarchy stays visually distinguishable.
        val PriorityVerbose = Color(0xFF00A04C)
        val PriorityDebug = Secondary
        val PriorityInfo = Primary
        val PriorityWarn = Color(0xFFFF6B00)
        val PriorityError = Color(0xFFE52E2E)
        val PriorityFatal = PriorityError
        val PrioritySilent = Secondary

        // Pale "tinted-background" companions to the priority foreground
        // colors. Used by the PriorityBadge pill — colored glyph on tinted
        // square so the priority signal is visible without tinting the
        // entire row's text (the Swiss spec calls for a 16x16, 6px-radius
        // pill at #FBEAEE bg / #E52E2E fg for Error; the other tones follow
        // the same ~92%-lightness derivation).
        val PriorityBgVerbose = Color(0xFFE6F5EC)
        val PriorityBgDebug = Color(0xFFEEEEEE)
        val PriorityBgInfo = Color(0xFFE5EFFF)
        val PriorityBgWarn = Color(0xFFFFF1E5)
        val PriorityBgError = Color(0xFFFBEAEE)
        val PriorityBgFatal = PriorityBgError
        val PriorityBgSilent = PriorityBgDebug
    }

    object Text {
        /** 12sp muted — toolbar status, footnotes, anything secondary. */
        val Status = TextStyle(
            fontFamily = Fonts.Ui,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = Colors.Secondary,
        )

        /** JetBrains Mono 12sp near-black — log lines, UUID rows, tabular data.
         * 20sp line-height matches the Swiss spec for prolonged log reading. */
        val Row = TextStyle(
            fontFamily = Fonts.Mono,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 20.sp,
            color = Colors.OnSurface,
        )

        /** 13sp near-black — tab pill labels. */
        val Tab = TextStyle(
            fontFamily = Fonts.Ui,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = Colors.OnSurface,
        )

        /** 11sp muted — the "✕" close glyph on closeable tab pills. */
        val TabClose = TextStyle(
            fontFamily = Fonts.Ui,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            color = Colors.Secondary,
        )

        /** 13sp near-black — text buttons. Swiss primaries are black-bg pills
         * rendered as a dedicated component; this style applies to text-only
         * actions (toggles, links). */
        val Button = TextStyle(
            fontFamily = Fonts.Ui,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            color = Colors.OnSurface,
        )

        /** 13sp near-black — search/filter input content. */
        val Field = TextStyle(
            fontFamily = Fonts.Ui,
            fontWeight = FontWeight.Normal,
            fontSize = 13.sp,
            color = Colors.OnSurface,
        )
    }

    /** Logcat-style color for a given priority level. */
    fun colorFor(priority: LogPriority): Color = when (priority) {
        LogPriority.Verbose -> Colors.PriorityVerbose
        LogPriority.Debug -> Colors.PriorityDebug
        LogPriority.Info -> Colors.PriorityInfo
        LogPriority.Warn -> Colors.PriorityWarn
        LogPriority.Error -> Colors.PriorityError
        LogPriority.Fatal -> Colors.PriorityFatal
        LogPriority.Silent -> Colors.PrioritySilent
    }

    /** Pale tinted background for the priority badge pill. */
    fun badgeBgFor(priority: LogPriority): Color = when (priority) {
        LogPriority.Verbose -> Colors.PriorityBgVerbose
        LogPriority.Debug -> Colors.PriorityBgDebug
        LogPriority.Info -> Colors.PriorityBgInfo
        LogPriority.Warn -> Colors.PriorityBgWarn
        LogPriority.Error -> Colors.PriorityBgError
        LogPriority.Fatal -> Colors.PriorityBgFatal
        LogPriority.Silent -> Colors.PriorityBgSilent
    }
}
