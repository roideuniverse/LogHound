package com.roideuniverse.loghound

import androidx.compose.runtime.staticCompositionLocalOf
import com.roideuniverse.loghound.design.LogHoundColors
import com.roideuniverse.loghound.design.LogHoundThemes
import java.io.File
import java.util.Properties

enum class AppTheme(val displayName: String, val colors: LogHoundColors) {
    Light("Light", LogHoundThemes.Light),
    Darcula("Darcula", LogHoundThemes.Darcula),
    Nord("Nord", LogHoundThemes.Nord),
    HighContrast("High Contrast", LogHoundThemes.HighContrast),
}

enum class Density { Compact, Comfortable }

enum class TimestampFormat(val displayName: String) {
    Full("Full"),
    Short("Short"),
    Seconds("Seconds"),
}

data class AppSettings(
    val theme: AppTheme = AppTheme.Light,
    val density: Density = Density.Compact,
    val fontSize: Int = 12,
    val showPid: Boolean = true,
    val zebraRows: Boolean = false,
    val wordWrap: Boolean = true,
    val timestampFormat: TimestampFormat = TimestampFormat.Full,
)

/** Active display settings injected by the window shell. Plugins read this to honour density, font size, etc. */
val LocalAppSettings = staticCompositionLocalOf { AppSettings() }

object AppSettingsStore {
    private val configFile: File
        get() {
            val home = System.getProperty("user.home") ?: return File("config.properties")
            return File(home, ".loghound/config.properties")
        }

    fun load(): AppSettings {
        val props = try {
            if (!configFile.isFile) return AppSettings()
            Properties().apply { configFile.inputStream().use { load(it) } }
        } catch (_: Exception) {
            return AppSettings()
        }
        return AppSettings(
            theme = props["ui.theme"]?.let { id ->
                AppTheme.entries.firstOrNull { it.name == id }
            } ?: AppTheme.Light,
            density = props["ui.density"]?.let { id ->
                Density.entries.firstOrNull { it.name == id }
            } ?: Density.Compact,
            fontSize = (props["ui.fontSize"] as? String)?.toIntOrNull()?.coerceIn(10, 18) ?: 12,
            showPid = (props["ui.showPid"] as? String)?.toBooleanStrictOrNull() ?: true,
            zebraRows = (props["ui.zebraRows"] as? String)?.toBooleanStrictOrNull() ?: false,
            wordWrap = (props["ui.wordWrap"] as? String)?.toBooleanStrictOrNull() ?: true,
            timestampFormat = props["ui.timestampFormat"]?.let { id ->
                TimestampFormat.entries.firstOrNull { it.name == id }
            } ?: TimestampFormat.Full,
        )
    }

    fun save(settings: AppSettings) {
        try {
            // Read existing file to preserve unknown keys (e.g. font overrides).
            val props = Properties()
            if (configFile.isFile) configFile.inputStream().use { props.load(it) }

            props["ui.theme"] = settings.theme.name
            props["ui.density"] = settings.density.name
            props["ui.fontSize"] = settings.fontSize.toString()
            props["ui.showPid"] = settings.showPid.toString()
            props["ui.zebraRows"] = settings.zebraRows.toString()
            props["ui.wordWrap"] = settings.wordWrap.toString()
            props["ui.timestampFormat"] = settings.timestampFormat.name

            configFile.parentFile?.mkdirs()
            configFile.outputStream().use { props.store(it, null) }
        } catch (_: Exception) {
            // Non-fatal — settings revert to defaults on next launch.
        }
    }
}
