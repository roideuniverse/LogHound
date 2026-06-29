package com.roideuniverse.loghound.design

import androidx.compose.ui.graphics.Color  // kept: colorFor/badgeBgFor return Color
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

    /**
     * Light-theme color aliases — delegates to [LogHoundThemes.Light] so
     * there is a single source of truth for every token value.
     *
     * Non-composable callers (DSL plugins, unit tests) continue to use
     * `LogHoundDesign.Colors.*`. Composables that need to honour the active
     * theme should read `LocalLogHoundColors.current` instead.
     */
    object Colors {
        // ---- surfaces ----
        val Background        get() = LogHoundThemes.Light.background
        val Surface           get() = LogHoundThemes.Light.surface
        val Elevated          get() = LogHoundThemes.Light.elevated

        // ---- borders / dividers ----
        val Border            get() = LogHoundThemes.Light.border
        val BorderSoft        get() = LogHoundThemes.Light.borderSoft
        val RowDivider        get() = LogHoundThemes.Light.rowDivider

        // ---- interactive backgrounds ----
        val HoverBackground   get() = LogHoundThemes.Light.hoverBackground
        val Hover2            get() = LogHoundThemes.Light.hover2
        val PressedBackground get() = LogHoundThemes.Light.pressedBackground
        val Input             get() = LogHoundThemes.Light.input

        // ---- semantic aliases (same value, intent-named for callers) ----
        val ActiveTabBackground get() = LogHoundThemes.Light.background
        val TextFieldBackground get() = LogHoundThemes.Light.background
        val TextFieldBorder     get() = LogHoundThemes.Light.border

        // ---- text ----
        val OnSurface         get() = LogHoundThemes.Light.onSurface
        val Text2             get() = LogHoundThemes.Light.text2
        val Secondary         get() = LogHoundThemes.Light.secondary
        val Placeholder       get() = LogHoundThemes.Light.secondary
        val Dim2              get() = LogHoundThemes.Light.dim2

        // ---- accent ----
        val Primary           get() = LogHoundThemes.Light.primary
        val AccentTint        get() = LogHoundThemes.Light.accentTint
        val AccentSoft        get() = LogHoundThemes.Light.accentSoft
        val OnAccent          get() = LogHoundThemes.Light.onAccent

        // ---- buttons ----
        val Button            get() = LogHoundThemes.Light.button
        val ButtonText        get() = LogHoundThemes.Light.buttonText
        val ButtonHover       get() = LogHoundThemes.Light.buttonHover

        // ---- priority foregrounds ----
        val PriorityVerbose   get() = LogHoundThemes.Light.priorityVerbose
        val PriorityDebug     get() = LogHoundThemes.Light.priorityDebug
        val PriorityInfo      get() = LogHoundThemes.Light.priorityInfo
        val PriorityWarn      get() = LogHoundThemes.Light.priorityWarn
        val PriorityError     get() = LogHoundThemes.Light.priorityError
        val PriorityFatal     get() = LogHoundThemes.Light.priorityError
        val PrioritySilent    get() = LogHoundThemes.Light.secondary

        // ---- priority badge backgrounds ----
        val PriorityBgVerbose get() = LogHoundThemes.Light.priorityBgVerbose
        val PriorityBgDebug   get() = LogHoundThemes.Light.priorityBgDebug
        val PriorityBgInfo    get() = LogHoundThemes.Light.priorityBgInfo
        val PriorityBgWarn    get() = LogHoundThemes.Light.priorityBgWarn
        val PriorityBgError   get() = LogHoundThemes.Light.priorityBgError
        val PriorityBgFatal   get() = LogHoundThemes.Light.priorityBgError
        val PriorityBgSilent  get() = LogHoundThemes.Light.priorityBgDebug

        // ---- search highlights ----
        val HlBackground      get() = LogHoundThemes.Light.hlBackground
        val HlForeground      get() = LogHoundThemes.Light.hlForeground

        // ---- chrome ----
        val Pulse             get() = LogHoundThemes.Light.pulse
        val Scrollbar         get() = LogHoundThemes.Light.scrollbar
        val ScrollbarHover    get() = LogHoundThemes.Light.scrollbarHover

        /** Per-device tint palette. Auto-assigned round-robin; first two entries
         *  match the Swiss spec's device-1 (blue) and device-2 (orange). */
        val DevicePalette     get() = LogHoundThemes.Light.devicePalette
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
