package com.roideuniverse.loghound.plugindsl

import java.io.File

@PluginDslMarker
class DataScope internal constructor(
    private val pluginId: String,
) {
    fun pluginDataDir(): File {
        val dir = File(System.getProperty("user.home"), ".loghound/plugins/$pluginId")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }
}
