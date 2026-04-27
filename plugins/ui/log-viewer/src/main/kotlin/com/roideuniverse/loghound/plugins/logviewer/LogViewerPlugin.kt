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
                if (wasAtBottom && entries.isNotEmpty()) {
                    listState.scrollToItem(entries.lastIndex)
                }
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
            HorizontalDivider(color = Color(0xFFCCCCCC))
            FilterBar(query = queryText, onQueryChange = { queryText = it })
            HorizontalDivider(color = Color(0xFFCCCCCC))
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

private fun colorFor(priority: LogPriority): Color = when (priority) {
    LogPriority.Verbose -> Color(0xFF666666)   // gray
    LogPriority.Debug   -> Color(0xFF1976D2)   // blue
    LogPriority.Info    -> Color(0xFF388E3C)   // green
    LogPriority.Warn    -> Color(0xFFF57C00)   // amber
    LogPriority.Error   -> Color(0xFFD32F2F)   // red
    LogPriority.Fatal   -> Color(0xFFD32F2F)   // red (bold weight applied below)
    LogPriority.Silent  -> Color(0xFF999999)
}

private const val LOAD_OLDER_THRESHOLD = 10
private const val LOAD_OLDER_PAGE_SIZE = 500

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
        Text("Loading older…", color = Color(0xFF888888), style = TextStyle(fontSize = 12.sp))
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val weight = if (entry.priority == LogPriority.Fatal) FontWeight.Bold else FontWeight.Normal
    val style = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = colorFor(entry.priority),
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
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFFF7F7F7)) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp)) {
            val shape = RoundedCornerShape(4.dp)
            val style: TextStyle = LocalTextStyle.current.copy(color = Color.Black, fontSize = 13.sp)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = style,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color.White)
                    .border(1.dp, Color(0xFFCCCCCC), shape)
                    .padding(horizontal = 8.dp, vertical = 6.dp)
                    .testTag(TestTags.FILTER_INPUT),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "tag:Activity level:W package:mine",
                                color = Color(0xFFAAAAAA),
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
