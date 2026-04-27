package com.roideuniverse.loghound.core

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

interface UIPlugin {
    val id: String
    val name: String

    @Composable
    fun content(modifier: Modifier)
}
