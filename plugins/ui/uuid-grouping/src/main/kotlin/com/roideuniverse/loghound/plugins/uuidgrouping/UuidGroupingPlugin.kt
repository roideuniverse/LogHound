package com.roideuniverse.loghound.plugins.uuidgrouping

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roideuniverse.loghound.core.DataPlugin
import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.core.UIPlugin
import com.roideuniverse.loghound.design.LogHoundDesign
import com.roideuniverse.loghound.design.PriorityBadge
import androidx.compose.ui.text.style.TextOverflow
import com.roideuniverse.loghound.plugins.uuidgrouping.internal.UuidDetailController
import com.roideuniverse.loghound.plugins.uuidgrouping.internal.openUuidGroupingDb
import com.roideuniverse.loghound.plugins.uuidgrouping.sqldelight.UuidGroupingDb
import com.roideuniverse.loghound.plugins.uuidgrouping.sqldelight.Uuids
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.withContext
import java.io.File

class UuidGroupingPlugin(
    private val databaseFile: File,
    private val repository: LogRepository,
) : UIPlugin, DataPlugin {

    override val id: String = "core.uuid-grouping"
    override val name: String = "UUID Grouping"

    private val db: UuidGroupingDb by lazy { openUuidGroupingDb(databaseFile) }

    private val _progress = MutableStateFlow(BackfillProgress())
    val progress: StateFlow<BackfillProgress> = _progress.asStateFlow()

    /** DataPlugin side: backfill from checkpoint, then collect ingested. Always runs. */
    override suspend fun run(repository: LogRepository) {
        require(repository === this.repository) {
            "UuidGroupingPlugin was constructed with a different LogRepository than passed to run()"
        }
        backfill(repository, Dispatchers.Default)
        collectIngested(repository, Dispatchers.Default)
    }

    private suspend fun backfill(repository: LogRepository, dispatcher: CoroutineDispatcher) {
        val q = db.uuidsQueries
        val startId = q.getMeta(META_LAST_SCANNED_ID).executeAsOneOrNull()?.toLongOrNull() ?: 0L
        var cursor: Long? = startId.takeIf { it > 0 }
        var scannedThisRun = 0L

        while (true) {
            val page = repository.query(afterId = cursor, limit = PAGE_SIZE)
            if (page.entries.isEmpty()) break

            val maxId = page.entries.last().id
            withContext(dispatcher) {
                db.transaction {
                    for (entry in page.entries) {
                        for (u in UuidExtractor.findAll(entry.message)) {
                            q.upsert(uuid = u, delta = 1, logId = entry.id)
                            q.insertOccurrence(uuid = u, logId = entry.id)
                        }
                    }
                    q.setMeta(META_LAST_SCANNED_ID, maxId.toString())
                }
            }

            scannedThisRun += page.entries.size
            val totalUuids = q.countAll().executeAsOne()
            _progress.value = BackfillProgress(scanning = page.hasMore, scanned = scannedThisRun, uuidCount = totalUuids)

            if (!page.hasMore) break
            cursor = maxId
        }
        _progress.value = _progress.value.copy(scanning = false)
    }

    private suspend fun collectIngested(
        repository: LogRepository,
        dispatcher: CoroutineDispatcher,
    ) {
        val q = db.uuidsQueries
        repository.ingested.collect { batch ->
            if (batch.isEmpty()) return@collect
            val maxId = batch.maxOf { it.id }
            withContext(dispatcher) {
                db.transaction {
                    for (entry in batch) {
                        for (u in UuidExtractor.findAll(entry.message)) {
                            q.upsert(uuid = u, delta = 1, logId = entry.id)
                            q.insertOccurrence(uuid = u, logId = entry.id)
                        }
                    }
                    q.setMeta(META_LAST_SCANNED_ID, maxId.toString())
                }
            }
            _progress.value = _progress.value.copy(uuidCount = q.countAll().executeAsOne())
        }
    }

    /** UIPlugin side: renders current state of the plugin's DB plus per-UUID detail tabs. */
    @Composable
    override fun content(modifier: Modifier) {
        var search by remember { mutableStateOf("") }
        var sortByCount by remember { mutableStateOf(true) }
        val progressState by progress.collectAsState()

        var rows by remember { mutableStateOf<List<Uuids>>(emptyList()) }
        var totalMatching by remember { mutableStateOf(0L) }

        // Sub-tab state
        val openedUuids = remember { mutableStateListOf<String>() }
        val controllers = remember { mutableStateMapOf<String, UuidDetailController>() }
        var activeUuid: String? by remember { mutableStateOf(null) }
        val coroutineScope = rememberCoroutineScope()

        fun openDetail(uuid: String) {
            if (uuid !in openedUuids) {
                openedUuids.add(uuid)
                val controller = UuidDetailController(uuid, repository, db.uuidsQueries)
                controllers[uuid] = controller
                controller.start(coroutineScope)
            }
            activeUuid = uuid
        }

        fun closeDetail(uuid: String) {
            controllers[uuid]?.stop()
            controllers.remove(uuid)
            openedUuids.remove(uuid)
            if (activeUuid == uuid) activeUuid = null
        }

        // Refresh visible rows from the DB on a tick. Cheap because it's a paged query.
        LaunchedEffect(search, sortByCount) {
            while (true) {
                val q = db.uuidsQueries
                val pattern = search.lowercase()
                rows = if (sortByCount) {
                    q.selectByCountDesc(search = pattern, lim = VISIBLE_LIMIT.toLong(), off = 0).executeAsList()
                } else {
                    q.selectByUuidAsc(search = pattern, lim = VISIBLE_LIMIT.toLong(), off = 0).executeAsList()
                }
                totalMatching = q.countMatching(search = pattern).executeAsOne()
                delay(REFRESH_INTERVAL_MS)
            }
        }

        Column(modifier = modifier.fillMaxSize()) {
            UuidTabStrip(
                openedUuids = openedUuids,
                activeUuid = activeUuid,
                onSelectList = { activeUuid = null },
                onSelectUuid = { activeUuid = it },
                onClose = ::closeDetail,
            )
            HorizontalDivider(color = LogHoundDesign.Colors.Border)
            if (activeUuid == null) {
                Toolbar(
                    search = search,
                    onSearchChange = { search = it },
                    sortByCount = sortByCount,
                    onToggleSort = { sortByCount = !sortByCount },
                    progress = progressState,
                    visibleCount = rows.size,
                    totalCount = totalMatching,
                )
                HorizontalDivider(color = LogHoundDesign.Colors.Border)
                UuidList(rows = rows, onUuidClick = ::openDetail)
            } else {
                val controller = controllers[activeUuid]
                if (controller != null) {
                    UuidDetailView(controller)
                }
            }
        }
    }

    data class BackfillProgress(
        val scanning: Boolean = true,
        val scanned: Long = 0,
        val uuidCount: Long = 0,
    )

    private companion object {
        const val PAGE_SIZE = 1_000
        const val VISIBLE_LIMIT = 200
        const val REFRESH_INTERVAL_MS = 500L
        const val META_LAST_SCANNED_ID = "last_scanned_log_id"
    }
}

@Composable
private fun UuidTabStrip(
    openedUuids: List<String>,
    activeUuid: String?,
    onSelectList: () -> Unit,
    onSelectUuid: (String) -> Unit,
    onClose: (String) -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = LogHoundDesign.Colors.Surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TabButton(
                text = "UUIDs",
                selected = activeUuid == null,
                onClick = onSelectList,
                onClose = null,
            )
            for (u in openedUuids) {
                TabButton(
                    text = u.take(8) + "…",
                    selected = activeUuid == u,
                    onClick = { onSelectUuid(u) },
                    onClose = { onClose(u) },
                )
            }
        }
    }
}

@Composable
private fun TabButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    onClose: (() -> Unit)?,
) {
    val bg = if (selected) LogHoundDesign.Colors.ActiveTabBackground else Color.Transparent
    Row(
        modifier = Modifier
            .padding(horizontal = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp)
            .testTag(UuidTestTags.UUID_TAB),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = LogHoundDesign.Text.Tab,
        )
        if (onClose != null) {
            Spacer(Modifier.width(6.dp))
            Text(
                "✕",
                style = LogHoundDesign.Text.TabClose,
                modifier = Modifier
                    .clickable { onClose() }
                    .testTag(UuidTestTags.UUID_TAB_CLOSE),
            )
        }
    }
}

@Composable
private fun Toolbar(
    search: String,
    onSearchChange: (String) -> Unit,
    sortByCount: Boolean,
    onToggleSort: () -> Unit,
    progress: UuidGroupingPlugin.BackfillProgress,
    visibleCount: Int,
    totalCount: Long,
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = LogHoundDesign.Colors.Surface) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchField(search, onSearchChange, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onToggleSort) {
                    Text(if (sortByCount) "Sort: count ↓" else "Sort: A → Z")
                }
            }
            Spacer(Modifier.padding(2.dp))
            val statusLabel = buildString {
                append("$visibleCount of $totalCount UUIDs")
                if (progress.scanning) append("  •  scanning… ${progress.scanned}")
            }
            Text(statusLabel, style = LogHoundDesign.Text.Status)
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val style: TextStyle = LocalTextStyle.current.merge(LogHoundDesign.Text.Field)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        modifier = modifier
            .clip(shape)
            .background(LogHoundDesign.Colors.TextFieldBackground)
            .border(1.dp, LogHoundDesign.Colors.TextFieldBorder, shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text("Search UUIDs…", color = LogHoundDesign.Colors.Placeholder, style = style)
                }
                inner()
            }
        },
    )
}

@Composable
private fun UuidDetailView(controller: UuidDetailController) {
    Box(modifier = Modifier.fillMaxSize()) {
        when {
            controller.loading -> DetailLoading()
            controller.entries.isEmpty() -> DetailEmpty()
            else -> DetailList(controller)
        }
    }
}

@Composable
private fun DetailLoading() {
    Box(
        modifier = Modifier.fillMaxSize().testTag(UuidTestTags.UUID_DETAIL_LOADING),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(24.dp))
    }
}

@Composable
private fun DetailEmpty() {
    Box(
        modifier = Modifier.fillMaxSize().testTag(UuidTestTags.UUID_DETAIL_EMPTY),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "No log lines for this UUID",
            color = LogHoundDesign.Colors.PrioritySilent,
            style = TextStyle(fontSize = 13.sp),
        )
    }
}

@Composable
private fun DetailList(controller: UuidDetailController) {
    // Trigger load-older when the user scrolls within ~10 rows of the top.
    LaunchedEffect(controller) {
        snapshotFlow { controller.listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { firstVisible ->
                if (firstVisible <= LOAD_OLDER_THRESHOLD &&
                    !controller.loadingOlder &&
                    !controller.olderExhausted &&
                    controller.entries.isNotEmpty()
                ) {
                    controller.loadOlder()
                }
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = controller.listState,
                modifier = Modifier.fillMaxSize().testTag(UuidTestTags.UUID_DETAIL_LIST),
            ) {
                if (controller.loadingOlder) {
                    item(key = "loadingOlder") { LoadingOlderRow(UuidTestTags.UUID_DETAIL_LOADING_OLDER) }
                }
                items(items = controller.entries, key = { it.id }) { entry -> DetailLogRow(entry) }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(controller.listState),
        )
    }
}

@Composable
private fun LoadingOlderRow(testTag: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(14.dp))
        Spacer(Modifier.width(8.dp))
        Text("Loading older…", color = LogHoundDesign.Colors.Secondary, style = TextStyle(fontSize = 12.sp))
    }
}

private const val LOAD_OLDER_THRESHOLD = 10

@Composable
private fun UuidList(rows: List<Uuids>, onUuidClick: (String) -> Unit) {
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        SelectionContainer(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag(UuidTestTags.UUID_LIST),
            ) {
                items(items = rows, key = { it.uuid }) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onUuidClick(row.uuid) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag(UuidTestTags.UUID_ROW),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.count.toString().padStart(7),
                            style = LogHoundDesign.Text.Row.copy(fontWeight = FontWeight.Medium),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = row.uuid,
                            style = LogHoundDesign.Text.Row,
                        )
                    }
                    HorizontalDivider(color = LogHoundDesign.Colors.Border)
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState),
        )
    }
}

@Composable
private fun DetailLogRow(entry: LogEntry) {
    val bodyStyle = LogHoundDesign.Text.Row
    val metaStyle = LogHoundDesign.Text.Row.copy(
        fontSize = 11.sp,
        color = LogHoundDesign.Colors.Secondary,
    )
    val tagStyle = LogHoundDesign.Text.Row.copy(fontWeight = FontWeight.Medium)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag(UuidTestTags.UUID_DETAIL_ROW),
        verticalAlignment = Alignment.Top,
    ) {
        PriorityBadge(entry.priority, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(8.dp))
        Text(entry.timestamp, style = bodyStyle)
        Spacer(Modifier.width(8.dp))
        Text("${entry.pid} ${entry.tid}", style = metaStyle, modifier = Modifier.padding(top = 1.dp))
        Spacer(Modifier.width(12.dp))
        Text(
            entry.tag,
            style = tagStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(120.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(entry.message, style = bodyStyle, modifier = Modifier.weight(1f))
    }
}

