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
 */
object LogHoundDesign {

    object Fonts {
        /** Instrument Sans — UI text (tabs, buttons, status, fields). Weights: 400, 500, 600. */
        val Ui: FontFamily = FontFamily(
            Font("fonts/InstrumentSans-Regular.ttf", FontWeight.Normal),
            Font("fonts/InstrumentSans-Medium.ttf", FontWeight.Medium),
            Font("fonts/InstrumentSans-SemiBold.ttf", FontWeight.SemiBold),
        )

        /** JetBrains Mono — log rows and tabular data. Weights: 400, 500. */
        val Mono: FontFamily = FontFamily(
            Font("fonts/JetBrainsMono-Regular.ttf", FontWeight.Normal),
            Font("fonts/JetBrainsMono-Medium.ttf", FontWeight.Medium),
        )
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
}
