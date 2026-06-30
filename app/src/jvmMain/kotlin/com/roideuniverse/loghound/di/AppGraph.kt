package com.roideuniverse.loghound.di

import com.roideuniverse.loghound.core.DataPlugin
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.core.UIPlugin
import com.roideuniverse.loghound.core.impl.LogRepositoryImpl
import com.roideuniverse.loghound.database.LogDataStore
import com.roideuniverse.loghound.database.createLogDataStore
import com.roideuniverse.loghound.plugindsl.DslPlugin
import com.roideuniverse.loghound.plugins.logcat.LogcatDataPlugin
import com.roideuniverse.loghound.plugins.logviewer.LogViewerPlugin
import com.roideuniverse.loghound.plugins.sessions.SessionsConfig
import com.roideuniverse.loghound.plugins.sessions.SessionsManager
import com.roideuniverse.loghound.plugins.sessions.SessionsPlugin
import com.roideuniverse.loghound.plugins.sessions.StoreClearer
import com.roideuniverse.loghound.plugins.uuidgrouping.UuidGroupingPlugin
import com.roideuniverse.loghound.scripting.PluginScriptHost
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Binds
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.ElementsIntoSet
import dev.zacsweers.metro.IntoSet
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import java.io.File

/**
 * Every on-disk location the app reads or writes, rooted at `~/.loghound/`. Bundled so the graph
 * injects one value and tests can repoint the root at a temp dir.
 */
class AppPaths(val root: File) {
    val logsDbFile: File = File(root, "logs.db")
    val pluginDbDir: File = File(root, "plugins")
    val uuidDbFile: File = File(pluginDbDir, "uuid-grouping.db")
    val sessionsDbFile: File = File(root, "sessions.db")
    val archivesDir: File = File(root, "archives")

    companion object {
        fun default(): AppPaths = AppPaths(File(System.getProperty("user.home"), ".loghound"))
    }
}

/**
 * App-wide dependency graph. Replaces the manual constructor wiring that used to live in main.kt:
 * it owns the single LogRepository, the SessionsManager, and the two plugin multibindings (UI +
 * data).
 *
 * UuidGroupingPlugin is provided once as a scoped singleton and contributed to BOTH plugin sets, so
 * its DataPlugin side and UIPlugin side are the same instance (its run() asserts the repository
 * identity). Scripted DslPlugins are discovered at startup and folded into both sets
 * via @ElementsIntoSet.
 */
@DependencyGraph(AppScope::class)
interface AppGraph {
    val repository: LogRepository
    val sessionsManager: SessionsManager
    val uiPlugins: Set<UIPlugin>
    val dataPlugins: Set<DataPlugin>

    @Binds val LogRepositoryImpl.bindRepository: LogRepository

    @Binds @IntoSet val LogViewerPlugin.bindLogViewerUi: UIPlugin
    @Binds @IntoSet val SessionsPlugin.bindSessionsUi: UIPlugin
    @Binds @IntoSet val LogcatDataPlugin.bindLogcatData: DataPlugin

    @Provides @SingleIn(AppScope::class) fun provideAppPaths(): AppPaths = AppPaths.default()

    @Provides
    @SingleIn(AppScope::class)
    fun provideDataStore(paths: AppPaths): LogDataStore = createLogDataStore(paths.logsDbFile)

    @Provides
    @SingleIn(AppScope::class)
    fun provideUuidGrouping(paths: AppPaths, repository: LogRepository): UuidGroupingPlugin =
        UuidGroupingPlugin(databaseFile = paths.uuidDbFile, repository = repository)

    @Provides @IntoSet fun uuidGroupingUi(plugin: UuidGroupingPlugin): UIPlugin = plugin

    @Provides @IntoSet fun uuidGroupingData(plugin: UuidGroupingPlugin): DataPlugin = plugin

    @Provides
    @SingleIn(AppScope::class)
    fun provideSessionsConfig(paths: AppPaths): SessionsConfig =
        SessionsConfig(
            sessionsDbFile = paths.sessionsDbFile,
            liveLogsDbFile = paths.logsDbFile,
            pluginDbDir = paths.pluginDbDir,
            archivesDir = paths.archivesDir,
        )

    // Wipes the live logs.db then every DataPlugin's derived state, repository
    // first so plugin observers don't see ID gaps mid-clear.
    @Provides
    fun provideStoreClearer(repository: LogRepository, dataPlugins: Set<DataPlugin>): StoreClearer =
        StoreClearer {
            repository.clearStore()
            dataPlugins.forEach { runCatching { it.clearStore() } }
        }

    @Provides
    @SingleIn(AppScope::class)
    fun provideScriptedPlugins(paths: AppPaths): List<DslPlugin> =
        PluginScriptHost.loadAll(paths.pluginDbDir)

    @Provides
    @ElementsIntoSet
    fun scriptedUi(scripted: List<DslPlugin>): Set<UIPlugin> = scripted.toSet()

    @Provides
    @ElementsIntoSet
    fun scriptedData(scripted: List<DslPlugin>): Set<DataPlugin> = scripted.toSet()
}
