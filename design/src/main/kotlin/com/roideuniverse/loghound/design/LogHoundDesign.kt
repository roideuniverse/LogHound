package com.roideuniverse.loghound.design

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.roideuniverse.loghound.core.LogPriority

/**
 * Single source of truth for LogHound's visual design tokens.
 *
 * Built-in plugins reference these directly. The DSL plugin theme
 * (`PluginTheme`) seeds its defaults from these, so script-loaded
 * plugins inherit the same look unless they explicitly override
 * via `theme { ... }`.
 *
 * Layout values (paddings, dp spacing) are not tokenised here —
 * they're layout-specific and live alongside the composables that
 * use them.
 */
object LogHoundDesign {

    object Colors {
        val Background = Color.White
        val Divider = Color(0xFFCCCCCC)
        val RowDivider = Color(0xFFEEEEEE)
        val TabStripBackground = Color(0xFFF0F0F0)
        val ToolbarBackground = Color(0xFFF7F7F7)
        val ActiveTabBackground = Color.White
        val TextFieldBackground = Color.White
        val TextFieldBorder = Color(0xFFCCCCCC)
        val Placeholder = Color(0xFFAAAAAA)

        /** Muted gray used for status text, secondary labels, and tab close glyphs. */
        val Secondary = Color(0xFF666666)

        /** Material blue — buttons, links, the Debug priority. */
        val Primary = Color(0xFF1976D2)

        /** Body text default — near-black, slightly softer than #000. */
        val OnSurface = Color(0xFF222222)

        // Priority palette. Mirrors Android Studio's logcat colors.
        val PriorityVerbose = Color(0xFF666666)
        val PriorityDebug = Primary
        val PriorityInfo = Color(0xFF388E3C)
        val PriorityWarn = Color(0xFFF57C00)
        val PriorityError = Color(0xFFD32F2F)
        val PriorityFatal = Color(0xFFD32F2F)
        val PrioritySilent = Color(0xFF999999)
    }

    object Text {
        /** 12sp gray — toolbar status, footnotes, anything secondary. */
        val Status = TextStyle(fontSize = 12.sp, color = Colors.Secondary)

        /** Monospace 12sp near-black — log lines, UUID rows, tabular data. */
        val Row = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = Colors.OnSurface,
        )

        /** 13sp black — tab pill labels. */
        val Tab = TextStyle(fontSize = 13.sp, color = Color.Black)

        /** 11sp gray — the "✕" close glyph on closeable tab pills. */
        val TabClose = TextStyle(fontSize = 11.sp, color = Colors.Secondary)

        /** 13sp blue — text buttons. */
        val Button = TextStyle(fontSize = 13.sp, color = Colors.Primary)

        /** 13sp black — search/filter input content. */
        val Field = TextStyle(fontSize = 13.sp, color = Color.Black)
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
