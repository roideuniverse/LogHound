package com.roideuniverse.loghound.plugins.uuidgrouping

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roideuniverse.loghound.core.DataPlugin
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.core.UIPlugin
import com.roideuniverse.loghound.plugins.uuidgrouping.internal.openUuidGroupingDb
import com.roideuniverse.loghound.plugins.uuidgrouping.sqldelight.UuidGroupingDb
import com.roideuniverse.loghound.plugins.uuidgrouping.sqldelight.Uuids
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

class UuidGroupingPlugin(
    private val databaseFile: File,
) : UIPlugin, DataPlugin {

    override val id: String = "core.uuid-grouping"
    override val name: String = "UUID Grouping"

    private val db: UuidGroupingDb by lazy { openUuidGroupingDb(databaseFile) }

    private val _progress = MutableStateFlow(BackfillProgress())
    val progress: StateFlow<BackfillProgress> = _progress.asStateFlow()

    /** DataPlugin side: backfill from checkpoint, then collect ingested. Always runs. */
    override suspend fun run(repository: LogRepository) {
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
                        }
                    }
                    q.setMeta(META_LAST_SCANNED_ID, maxId.toString())
                }
            }
            _progress.value = _progress.value.copy(uuidCount = q.countAll().executeAsOne())
        }
    }

    /** UIPlugin side: renders current state of the plugin's DB. */
    @Composable
    override fun content(modifier: Modifier) {
        var search by remember { mutableStateOf("") }
        var sortByCount by remember { mutableStateOf(true) }
        val progressState by progress.collectAsState()

        var rows by remember { mutableStateOf<List<Uuids>>(emptyList()) }
        var totalMatching by remember { mutableStateOf(0L) }

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
            Toolbar(
                search = search,
                onSearchChange = { search = it },
                sortByCount = sortByCount,
                onToggleSort = { sortByCount = !sortByCount },
                progress = progressState,
                visibleCount = rows.size,
                totalCount = totalMatching,
            )
            HorizontalDivider(color = Color(0xFFCCCCCC))
            UuidList(rows)
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
private fun Toolbar(
    search: String,
    onSearchChange: (String) -> Unit,
    sortByCount: Boolean,
    onToggleSort: () -> Unit,
    progress: UuidGroupingPlugin.BackfillProgress,
    visibleCount: Int,
    totalCount: Long,
) {
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFFF7F7F7)) {
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
            Text(statusLabel, color = Color(0xFF666666), style = TextStyle(fontSize = 12.sp))
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(4.dp)
    val style: TextStyle = LocalTextStyle.current.copy(color = Color.Black, fontSize = 13.sp)
    BasicTextField(
        value = value,
        onValueChange = onChange,
        singleLine = true,
        textStyle = style,
        modifier = modifier
            .clip(shape)
            .background(Color.White)
            .border(1.dp, Color(0xFFCCCCCC), shape)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) {
                    Text("Search UUIDs…", color = Color(0xFFAAAAAA), style = style)
                }
                inner()
            }
        },
    )
}

@Composable
private fun UuidList(rows: List<Uuids>) {
    val clipboard = LocalClipboardManager.current
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
                            .clickable { clipboard.setText(AnnotatedString(row.uuid)) }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .testTag(UuidTestTags.UUID_ROW),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = row.count.toString().padStart(7),
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color(0xFF333333)),
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = row.uuid,
                            style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF222222)),
                        )
                    }
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                }
            }
        }
        VerticalScrollbar(
            modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
            adapter = rememberScrollbarAdapter(listState),
        )
    }
}
