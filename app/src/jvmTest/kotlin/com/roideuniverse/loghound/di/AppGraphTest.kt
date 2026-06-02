package com.roideuniverse.loghound.di

import com.roideuniverse.loghound.core.Device
import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogFilter
import com.roideuniverse.loghound.core.LogPage
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.plugins.logviewer.LogViewerPlugin
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraph
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * A LogRepository that touches no disk — proves that Metro resolves a plugin's
 * @Inject constructor against an overridden dependency, without spinning up the
 * real SQLite-backed graph.
 */
internal class FakeLogRepository : LogRepository {
    override suspend fun append(batch: List<LogEntry>) = Unit
    override suspend fun query(filter: LogFilter, beforeId: Long?, afterId: Long?, limit: Int): LogPage =
        LogPage(entries = emptyList(), hasMore = false)
    override suspend fun count(filter: LogFilter): Long = 0L
    override suspend fun queryByIds(ids: Collection<Long>): List<LogEntry> = emptyList()
    override suspend fun clearStore() = Unit
    override val ingested: Flow<List<LogEntry>> = emptyFlow()
    override val devices: StateFlow<Set<Device>> = MutableStateFlow(emptySet())
    override fun publishDevices(set: Set<Device>) = Unit
}

/**
 * Stand-in graph that supplies a fake LogRepository. If Metro can satisfy
 * LogViewerPlugin's @Inject constructor from this graph, the same wiring works
 * inside AppGraph against the real repository.
 */
@DependencyGraph
internal interface TestPluginGraph {
    val logViewer: LogViewerPlugin
    val repository: LogRepository

    @Provides fun provideRepository(impl: FakeLogRepository): LogRepository = impl
    @Provides fun provideFake(): FakeLogRepository = FakeLogRepository()
}

class AppGraphTest {
    @Test
    fun `plugin resolves against an overridden LogRepository`() {
        val graph = createGraph<TestPluginGraph>()
        val plugin = graph.logViewer
        assertNotNull(plugin)
        assertEquals("core.log-viewer", plugin.id)
    }
}
