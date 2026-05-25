package com.roideuniverse.loghound.design

import java.io.File
import java.util.Properties

/**
 * Reads user font overrides from `~/.loghound/config.properties`.
 *
 * Keys (both optional):
 * - `font.ui.path` — absolute path (or `~/...`) to a TTF/OTF used as the UI typeface.
 * - `font.mono.path` — absolute path (or `~/...`) to a TTF/OTF used as the monospace
 *   typeface for log rows.
 *
 * If a key is present but the file can't be read, the override is dropped with
 * a warning on `stderr` and the bundled font is used instead — no crash, no
 * silent partial failure.
 *
 * The config file is read once at process start. Overrides land before any
 * Compose composition reads `LogHoundDesign.Fonts`, so font swaps take effect
 * on the next app launch rather than at runtime — which is the right shape
 * for a typography choice anyway.
 */
internal object UserFontConfig {
    const val KEY_UI = "font.ui.path"
    const val KEY_MONO = "font.mono.path"
    private const val CONFIG_RELATIVE_PATH = ".loghound/config.properties"

    val uiOverride: File? by lazy { resolveOverride(KEY_UI) }
    val monoOverride: File? by lazy { resolveOverride(KEY_MONO) }

    private val properties: Properties? by lazy { defaultConfigFile()?.let(::loadProperties) }

    private fun resolveOverride(key: String): File? =
        parseOverride(properties, key, ::expandTilde)

    private fun defaultConfigFile(): File? {
        val home = System.getProperty("user.home") ?: return null
        return File(home, CONFIG_RELATIVE_PATH)
    }

    /** Loads a properties file. Returns `null` on read error (logged to stderr). */
    internal fun loadProperties(file: File): Properties? {
        if (!file.isFile) return null
        return try {
            Properties().apply { file.inputStream().use { load(it) } }
        } catch (ex: Exception) {
            System.err.println("LogHound: failed to read $file (${ex.message}); using bundled fonts.")
            null
        }
    }

    /**
     * Resolves one override key to a readable file, applying tilde expansion
     * via [expand]. Returns `null` if the key is missing, blank, or points at
     * a path we can't read (writes a warning to stderr in the unreadable case).
     */
    internal fun parseOverride(
        props: Properties?,
        key: String,
        expand: (String) -> String,
    ): File? {
        if (props == null) return null
        val raw = props.getProperty(key)?.trim().orEmpty()
        if (raw.isEmpty()) return null
        val resolved = File(expand(raw))
        if (!resolved.isFile || !resolved.canRead()) {
            System.err.println("LogHound: $key='$raw' is not a readable file; using bundled font.")
            return null
        }
        return resolved
    }

    /** Expands a leading `~` to the user's home directory. */
    internal fun expandTilde(path: String): String {
        if (!path.startsWith("~")) return path
        val home = System.getProperty("user.home") ?: return path
        return when {
            path == "~" -> home
            path.startsWith("~/") -> home + path.substring(1)
            else -> path
        }
    }
}
