package com.roideuniverse.loghound.plugins.logviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogFilter
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

        LaunchedEffect(filter) {
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

        Column(modifier = modifier.fillMaxSize()) {
            FilterBar(query = queryText, onQueryChange = { queryText = it })
            HorizontalDivider(color = Color(0xFFCCCCCC))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag(TestTags.LOG_LIST),
            ) {
                items(items = entries, key = { it.id }) { entry -> LogRow(entry) }
            }
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = Color(0xFF222222))
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
