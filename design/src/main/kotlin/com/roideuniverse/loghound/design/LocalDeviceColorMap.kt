package com.roideuniverse.loghound.design

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Maps device-id string to its palette-assigned color. Provided by the window shell. */
val LocalDeviceColorMap = staticCompositionLocalOf<Map<String, Color>> { emptyMap() }
