package com.roideuniverse.loghound.plugins.logviewer

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogFilter
import com.roideuniverse.loghound.core.LogPriority
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.core.UIPlugin
import com.roideuniverse.loghound.design.LogHoundDesign

class LogViewerPlugin(
    private val repository: LogRepository,
) : UIPlugin {
    override val id: String = "core.log-viewer"
    override val name: String = "Log Viewer"

    @Composable
    override fun content(modifier: Modifier) {
        var queryText by remember { mutableStateOf("") }
        val filter: LogFilter = remember(queryText) { FilterQueryParser.parse(queryText) }
        val entries = remember { mutableStateListOf<LogEntry>() }
        val listState = rememberLazyListState()
        var loadingOlder by remember { mutableStateOf(false) }
        var olderExhausted by remember { mutableStateOf(false) }

        LaunchedEffect(filter) {
            // Reset load-older state on filter change so the trigger can fire afresh
            // against the new filter scope.
            olderExhausted = false
            val initial = repository.query(filter = filter, limit = 500)
            entries.clear()
            entries.addAll(initial.entries)
            if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
        }

        LaunchedEffect(filter) {
            repository.ingested.collect { batch ->
                val matched = batch.filter(filter::matches)
                if (matched.isEmpty()) return@collect
                val wasAtBottom = listState.run {
                    val info = layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    last >= entries.lastIndex - 2
                }
                entries.addAll(matched)
                if (wasAtBottom) {
                    // Tail-following: trim from the front to keep memory bounded. The
                    // evicted rows are still on disk; load-older fetches them back if
                    // the user scrolls up later.
                    while (entries.size > MAX_IN_MEMORY) {
                        entries.removeAt(0)
                    }
                    if (entries.isNotEmpty()) listState.scrollToItem(entries.lastIndex)
                }
                // While the user is scrolled up reading history we deliberately don't
                // evict — they keep their context. The list returns to the cap on the
                // next at-bottom append.
            }
        }

        // Load-older-on-scroll trigger.
        LaunchedEffect(filter) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { firstVisible ->
                    if (firstVisible <= LOAD_OLDER_THRESHOLD &&
                        !loadingOlder &&
                        !olderExhausted &&
                        entries.isNotEmpty()
                    ) {
                        loadingOlder = true
                        try {
                            val oldestId = entries.first().id
                            val older = repository.query(
                                filter = filter,
                                beforeId = oldestId,
                                limit = LOAD_OLDER_PAGE_SIZE,
                            )
                            if (older.entries.isEmpty()) {
                                olderExhausted = true
                            } else {
                                entries.addAll(0, older.entries)
                            }
                        } finally {
                            loadingOlder = false
                        }
                    }
                }
        }

        val coroutineScope = rememberCoroutineScope()
        // True when the user is within a couple rows of the tail (or the list is empty).
        val isAtBottom by remember {
            derivedStateOf {
                if (entries.isEmpty()) return@derivedStateOf true
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                last >= entries.lastIndex - 2
            }
        }

        Column(modifier = modifier.fillMaxSize()) {
            PackageUidLookupBar()
            HorizontalDivider(color = LogHoundDesign.Colors.Border)
            FilterBar(query = queryText, onQueryChange = { queryText = it })
            HorizontalDivider(color = LogHoundDesign.Colors.Border)
            Box(modifier = Modifier.fillMaxSize()) {
                SelectionContainer(modifier = Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().testTag(TestTags.LOG_LIST),
                    ) {
                        if (loadingOlder) {
                            item(key = "loadingOlder") { LoadingOlderRow() }
                        }
                        items(items = entries, key = { it.id }) { entry -> LogRow(entry) }
                    }
                }
                VerticalScrollbar(
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight(),
                    adapter = rememberScrollbarAdapter(listState),
                )
                if (!isAtBottom) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                if (entries.isNotEmpty()) {
                                    listState.scrollToItem(entries.lastIndex)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF424242),
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 24.dp, bottom = 16.dp)
                            .testTag(TestTags.JUMP_TO_BOTTOM),
                    ) {
                        Text("Jump to bottom ↓")
                    }
                }
            }
        }
    }
}

private const val LOAD_OLDER_THRESHOLD = 10
private const val LOAD_OLDER_PAGE_SIZE = 500
private const val MAX_IN_MEMORY = 10_000

@Composable
private fun LoadingOlderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag(TestTags.LOADING_OLDER),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.width(14.dp))
        Spacer(Modifier.width(8.dp))
        Text("Loading older…", style = LogHoundDesign.Text.Status)
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val weight = if (entry.priority == LogPriority.Fatal) FontWeight.Bold else FontWeight.Normal
    val style = LogHoundDesign.Text.Row.copy(
        color = LogHoundDesign.colorFor(entry.priority),
        fontWeight = weight,
    )
    val line = "${entry.timestamp}  ${entry.pid} ${entry.tid} ${entry.priority.label}  ${entry.tag}: ${entry.message}"
    Text(
        line,
        style = style,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 1.dp)
            .testTag(TestTags.LOG_ROW),
    )
}

@Composable
private fun FilterBar(query: String, onQueryChange: (String) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = LogHoundDesign.Colors.Surface) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            val shape = RoundedCornerShape(4.dp)
            val style: TextStyle = LocalTextStyle.current.merge(LogHoundDesign.Text.Field)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = style,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(LogHoundDesign.Colors.TextFieldBackground)
                    .border(1.dp, LogHoundDesign.Colors.TextFieldBorder, shape)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag(TestTags.FILTER_INPUT),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "tag:Activity level:W package:mine",
                                color = LogHoundDesign.Colors.Placeholder,
                                style = style,
                            )
                        }
                        inner()
                    }
                },
            )
        }
    }
}
