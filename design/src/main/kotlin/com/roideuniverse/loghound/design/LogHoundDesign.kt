package com.roideuniverse.loghound.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.roideuniverse.loghound.core.LogPriority

/**
 * Single source of truth for LogHound's visual design tokens.
 *
 * Values follow the Modern Swiss Utility direction
 * ([`specs/design/swiss-direction.md`](../../../../../specs/design/swiss-direction.md)) —
 * one shared surface tone (#F7F7F7), one shared border tone (#E5E5E5), softer
 * text (#1A1A1A on near-white), and a 4-accent palette (blue / orange / red /
 * green) reused across priority colors, device identifiers, and informational
 * states.
 *
 * Built-in plugins reference these directly. The DSL plugin theme
 * (`PluginTheme`) seeds its defaults from these, so script-loaded plugins
 * inherit the same look unless they explicitly override via `theme { ... }`.
 *
 * Layout values (paddings, dp spacing) are not tokenised here — they live
 * alongside the composables that use them. Fonts are also not bundled yet
 * (Instrument Sans + JetBrains Mono are planned for a follow-up slice); for
 * now the text styles fall back to the system sans-serif and Compose's
 * built-in `FontFamily.Monospace`.
 */
object LogHoundDesign {

    object Colors {
        val Background = Color.White
        val Surface = Color(0xFFF7F7F7)
        val Border = Color(0xFFE5E5E5)
        val Placeholder = Color(0xFF8C8C8C)

        // Backwards-compatible aliases — historically the codebase distinguished
        // a "heavy" divider from a row-level divider, and a tab-strip surface
        // from a toolbar surface. Swiss collapses both pairs to one tone. The
        // aliases stay so existing references in built-in plugins keep working;
        // new code should reach for Border / Surface directly.
        val Divider = Border
        val RowDivider = Border
        val TabStripBackground = Surface
        val ToolbarBackground = Surface
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
        val Status = TextStyle(fontSize = 12.sp, color = Colors.Secondary)

        /** Monospace 12sp near-black — log lines, UUID rows, tabular data. */
        val Row = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Colors.OnSurface,
        )

        /** 13sp near-black — tab pill labels. */
        val Tab = TextStyle(fontSize = 13.sp, color = Colors.OnSurface)

        /** 11sp muted — the "✕" close glyph on closeable tab pills. */
        val TabClose = TextStyle(fontSize = 11.sp, color = Colors.Secondary)

        /** 13sp near-black — text buttons. Swiss primaries are black-bg pills
         * rendered as a dedicated component; this style applies to text-only
         * actions (toggles, links). */
        val Button = TextStyle(fontSize = 13.sp, color = Colors.OnSurface)

        /** 13sp near-black — search/filter input content. */
        val Field = TextStyle(fontSize = 13.sp, color = Colors.OnSurface)
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
