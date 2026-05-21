package com.roideuniverse.loghound.plugindsl

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.roideuniverse.loghound.design.LogHoundDesign

data class PluginTheme(
    val background: Color = LogHoundDesign.Colors.Background,
    val divider: Color = LogHoundDesign.Colors.Border,
    val rowDivider: Color = LogHoundDesign.Colors.Border,
    val tabStripBackground: Color = LogHoundDesign.Colors.Surface,
    val activeTabBackground: Color = LogHoundDesign.Colors.ActiveTabBackground,
    val toolbarBackground: Color = LogHoundDesign.Colors.Surface,
    val text: TextStyle = LogHoundDesign.Text.Status,
    val rowText: TextStyle = LogHoundDesign.Text.Row,
    val tabText: TextStyle = LogHoundDesign.Text.Tab,
    val closeText: TextStyle = LogHoundDesign.Text.TabClose,
    val buttonText: TextStyle = LogHoundDesign.Text.Button,
    val textFieldText: TextStyle = LogHoundDesign.Text.Field,
    val textFieldBorder: Color = LogHoundDesign.Colors.TextFieldBorder,
    val textFieldBackground: Color = LogHoundDesign.Colors.TextFieldBackground,
    val placeholderColor: Color = LogHoundDesign.Colors.Placeholder,
)

internal val LocalPluginTheme = compositionLocalOf { PluginTheme() }

@PluginDslMarker
class ThemeBuilder internal constructor(base: PluginTheme) {
    var background: Color = base.background
    var divider: Color = base.divider
    var rowDivider: Color = base.rowDivider
    var tabStripBackground: Color = base.tabStripBackground
    var activeTabBackground: Color = base.activeTabBackground
    var toolbarBackground: Color = base.toolbarBackground
    var text: TextStyle = base.text
    var rowText: TextStyle = base.rowText
    var tabText: TextStyle = base.tabText
    var closeText: TextStyle = base.closeText
    var buttonText: TextStyle = base.buttonText
    var textFieldText: TextStyle = base.textFieldText
    var textFieldBorder: Color = base.textFieldBorder
    var textFieldBackground: Color = base.textFieldBackground
    var placeholderColor: Color = base.placeholderColor

    internal fun build(): PluginTheme = PluginTheme(
        background = background,
        divider = divider,
        rowDivider = rowDivider,
        tabStripBackground = tabStripBackground,
        activeTabBackground = activeTabBackground,
        toolbarBackground = toolbarBackground,
        text = text,
        rowText = rowText,
        tabText = tabText,
        closeText = closeText,
        buttonText = buttonText,
        textFieldText = textFieldText,
        textFieldBorder = textFieldBorder,
        textFieldBackground = textFieldBackground,
        placeholderColor = placeholderColor,
    )
}
