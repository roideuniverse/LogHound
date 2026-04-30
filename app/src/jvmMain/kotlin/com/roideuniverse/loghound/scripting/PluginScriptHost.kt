package com.roideuniverse.loghound.scripting

import com.roideuniverse.loghound.plugindsl.DslPlugin
import java.io.File
import kotlin.script.experimental.annotations.KotlinScript
import kotlin.script.experimental.api.ResultValue
import kotlin.script.experimental.api.ResultWithDiagnostics
import kotlin.script.experimental.api.ScriptCompilationConfiguration
import kotlin.script.experimental.api.defaultImports
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvm.dependenciesFromCurrentContext
import kotlin.script.experimental.jvm.jvm
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate

@KotlinScript(
    fileExtension = "kts",
    compilationConfiguration = LogHoundScriptConfig::class,
)
abstract class LogHoundScript

object LogHoundScriptConfig : ScriptCompilationConfiguration({
    defaultImports(
        "com.roideuniverse.loghound.plugindsl.plugin",
        "com.roideuniverse.loghound.plugindsl.stateOf",
        "com.roideuniverse.loghound.plugindsl.tabController",
        "com.roideuniverse.loghound.plugindsl.PluginTheme",
        "com.roideuniverse.loghound.design.LogHoundDesign",
        "com.roideuniverse.loghound.core.LogEntry",
        "com.roideuniverse.loghound.core.LogPriority",
        "com.roideuniverse.loghound.core.LogFilter",
        "androidx.compose.ui.graphics.Color",
        "androidx.compose.ui.text.TextStyle",
        "androidx.compose.ui.text.font.FontFamily",
        "androidx.compose.ui.text.font.FontWeight",
        "androidx.compose.ui.unit.sp",
        "androidx.compose.ui.unit.dp",
        "androidx.compose.runtime.snapshotFlow",
        "androidx.compose.runtime.MutableState",
        "kotlinx.coroutines.coroutineScope",
        "kotlinx.coroutines.launch",
    )
    jvm {
        dependenciesFromCurrentContext(wholeClasspath = true)
    }
})

object PluginScriptHost {

    fun loadAll(dir: File): List<DslPlugin> {
        if (!dir.exists()) return emptyList()
        val files = dir.listFiles { f -> f.isFile && f.extension == "kts" }
            ?.sorted()
            .orEmpty()
        if (files.isEmpty()) return emptyList()

        val host = BasicJvmScriptingHost()
        val config = createJvmCompilationConfigurationFromTemplate<LogHoundScript>()
        return files.mapNotNull { file -> evalOne(host, config, file) }
    }

    private fun evalOne(
        host: BasicJvmScriptingHost,
        config: ScriptCompilationConfiguration,
        file: File,
    ): DslPlugin? {
        val result = host.eval(file.toScriptSource(), config, null)
        return when (result) {
            is ResultWithDiagnostics.Success -> {
                val ret = result.value.returnValue
                val value = (ret as? ResultValue.Value)?.value
                if (value is DslPlugin) {
                    value
                } else {
                    System.err.println(
                        "[plugin-script] ${file.name}: did not return a DslPlugin (got ${value?.javaClass?.simpleName ?: "null"})",
                    )
                    null
                }
            }
            is ResultWithDiagnostics.Failure -> {
                System.err.println("[plugin-script] ${file.name} failed to load:")
                result.reports.forEach { System.err.println("  ${it.severity}: ${it.message}") }
                null
            }
        }
    }
}
