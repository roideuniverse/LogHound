package com.roideuniverse.loghound.plugindsl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import com.roideuniverse.loghound.core.DataPlugin
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.core.UIPlugin

@DslMarker
annotation class PluginDslMarker

fun plugin(block: PluginBuilder.() -> Unit): DslPlugin {
    val builder = PluginBuilder()
    builder.block()
    return builder.build()
}

@PluginDslMarker
class PluginBuilder {
    var id: String = ""
    var name: String = ""
    var theme: PluginTheme = PluginTheme()
    internal var dataBlock: (suspend DataScope.(LogRepository) -> Unit)? = null
    internal var uiBlock: (UiScope.() -> Unit)? = null

    fun data(block: suspend DataScope.(LogRepository) -> Unit) {
        dataBlock = block
    }

    fun ui(block: UiScope.() -> Unit) {
        uiBlock = block
    }

    fun theme(block: ThemeBuilder.() -> Unit) {
        theme = ThemeBuilder(theme).apply(block).build()
    }

    internal fun build(): DslPlugin {
        require(id.isNotBlank()) { "plugin id is required" }
        require(name.isNotBlank()) { "plugin name is required" }
        return DslPlugin(id, name, theme, dataBlock, uiBlock)
    }
}

class DslPlugin internal constructor(
    override val id: String,
    override val name: String,
    private val theme: PluginTheme,
    private val dataBlock: (suspend DataScope.(LogRepository) -> Unit)?,
    private val uiBlock: (UiScope.() -> Unit)?,
) : UIPlugin, DataPlugin {

    override suspend fun run(repository: LogRepository) {
        val block = dataBlock ?: return
        val scope = DataScope(id)
        block(scope, repository)
    }

    @Composable
    override fun content(modifier: Modifier) {
        val block = uiBlock ?: return
        CompositionLocalProvider(
            LocalPluginTheme provides theme,
            LocalTextStyle provides theme.text,
        ) {
            Surface(modifier = modifier.fillMaxSize(), color = theme.background) {
                Box(modifier = Modifier.fillMaxSize()) {
                    val scope = UiScope().apply(block)
                    renderVerbs(scope.verbs)
                }
            }
        }
    }
}
