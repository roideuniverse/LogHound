package com.roideuniverse.loghound.plugindsl

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp

data class PluginTheme(
    val background: Color = Color.White,
    val divider: Color = Color(0xFFCCCCCC),
    val rowDivider: Color = Color(0xFFEEEEEE),
    val tabStripBackground: Color = Color(0xFFF0F0F0),
    val activeTabBackground: Color = Color.White,
    val toolbarBackground: Color = Color(0xFFF7F7F7),
    val text: TextStyle = TextStyle(fontSize = 12.sp, color = Color(0xFF666666)),
    val rowText: TextStyle = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = Color(0xFF222222),
    ),
    val tabText: TextStyle = TextStyle(fontSize = 13.sp, color = Color.Black),
    val closeText: TextStyle = TextStyle(fontSize = 11.sp, color = Color(0xFF666666)),
    val buttonText: TextStyle = TextStyle(fontSize = 13.sp, color = Color(0xFF1976D2)),
    val textFieldText: TextStyle = TextStyle(color = Color.Black, fontSize = 13.sp),
    val textFieldBorder: Color = Color(0xFFCCCCCC),
    val textFieldBackground: Color = Color.White,
    val placeholderColor: Color = Color(0xFFAAAAAA),
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
