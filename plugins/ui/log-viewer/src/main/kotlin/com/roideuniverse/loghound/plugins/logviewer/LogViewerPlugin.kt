package com.roideuniverse.loghound.plugins.logviewer

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogFilter
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.core.UIPlugin
import com.roideuniverse.loghound.design.LocalActiveDevice
import com.roideuniverse.loghound.design.LogHoundDesign
import com.roideuniverse.loghound.design.PriorityBadge
import dev.zacsweers.metro.Inject

class LogViewerPlugin @Inject constructor(
    private val repository: LogRepository,
) : UIPlugin {
    override val id: String = "core.log-viewer"
    override val name: String = "Log Viewer"

    @Composable
    override fun content(modifier: Modifier) {
        var queryText by remember { mutableStateOf("") }
        val activeDevice = LocalActiveDevice.current
        val parsedFilter: LogFilter = remember(queryText) { FilterQueryParser.parse(queryText) }
        // The status-bar pill scopes every query by default; an explicit
        // `device:` clause in the filter bar overrides the pill so the user
        // can still inspect another device's stream without changing the
        // app-wide active scope.
        val filter: LogFilter = remember(parsedFilter, activeDevice) {
            if (parsedFilter.deviceId != null) parsedFilter
            else parsedFilter.copy(deviceId = activeDevice?.value)
        }
        val entries = remember { mutableStateListOf<LogEntry>() }
        val listState = rememberLazyListState()
        var loadingOlder by remember { mutableStateOf(false) }
        var olderExhausted by remember { mutableStateOf(false) }
        // Tracks which row (if any) is currently expanded to show its full detail.
        // Survives recomposition; lost on filter change (we want a fresh start on
        // a new scope) which happens naturally since this remember is unkeyed and
        // we never reset it explicitly elsewhere.
        var expandedEntryId by remember { mutableStateOf<Long?>(null) }

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
                        items(items = entries, key = { it.id }) { entry ->
                            LogRowWithExpansion(
                                entry = entry,
                                expanded = entry.id == expandedEntryId,
                                onToggle = {
                                    expandedEntryId = if (expandedEntryId == entry.id) null else entry.id
                                },
                            )
                        }
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
private fun LogRowWithExpansion(entry: LogEntry, expanded: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val rowBg = when {
        expanded || isHovered -> LogHoundDesign.Colors.HoverBackground
        else -> LogHoundDesign.Colors.Background
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(rowBg)
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle,
            ),
    ) {
        LogRow(entry)
        if (expanded) {
            ExpandedDetail(entry)
        }
    }
}

@Composable
private fun ExpandedDetail(entry: LogEntry) {
    Surface(modifier = Modifier.fillMaxWidth(), color = LogHoundDesign.Colors.Surface) {
        Column(modifier = Modifier.padding(start = 32.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
            Text(
                "${entry.timestamp} · ${entry.tag} · pid ${entry.pid} · tid ${entry.tid}",
                style = LogHoundDesign.Text.Status,
                modifier = Modifier.testTag(TestTags.LOG_ROW_DETAIL_META),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                entry.message,
                style = LogHoundDesign.Text.Row,
                modifier = Modifier.testTag(TestTags.LOG_ROW_DETAIL_MESSAGE),
            )
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
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
            .testTag(TestTags.LOG_ROW),
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

@Composable
private fun FilterBar(query: String, onQueryChange: (String) -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), color = LogHoundDesign.Colors.Surface) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val shape = RoundedCornerShape(4.dp)
            val style: TextStyle = LocalTextStyle.current.merge(LogHoundDesign.Text.Field)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = style,
                modifier = Modifier
                    .weight(1f)
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
            Spacer(Modifier.width(8.dp))
            FilterBuilderButton(
                onAppend = { clause ->
                    val sep = if (query.isBlank()) "" else " "
                    onQueryChange("$query$sep$clause")
                },
            )
        }
    }
}

private val FILTER_CLAUSE_KEYS: List<String> = listOf("tag", "level", "pid", "tid", "package", "device")

@Composable
private fun FilterBuilderButton(onAppend: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(
            onClick = { open = true },
            modifier = Modifier.testTag(TestTags.FILTER_BUILDER_BUTTON),
        ) {
            Text("[ + ]", style = LogHoundDesign.Text.Button)
        }
        if (open) {
            Popup(
                alignment = Alignment.TopEnd,
                onDismissRequest = { open = false },
                properties = PopupProperties(focusable = true),
            ) {
                FilterBuilderPanel(
                    onApply = { key, value ->
                        if (key.isNotBlank() && value.isNotBlank()) {
                            onAppend("$key:$value")
                        }
                        open = false
                    },
                    onDismiss = { open = false },
                )
            }
        }
    }
}

@Composable
private fun FilterBuilderPanel(
    onApply: (key: String, value: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedKey by remember { mutableStateOf(FILTER_CLAUSE_KEYS.first()) }
    var keyMenuOpen by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(6.dp)

    Surface(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, LogHoundDesign.Colors.Border, shape)
            .testTag(TestTags.FILTER_BUILDER_PANEL),
        color = LogHoundDesign.Colors.Background,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Add filter clause",
                style = LogHoundDesign.Text.Tab.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { keyMenuOpen = true }) {
                        Text(selectedKey, style = LogHoundDesign.Text.Button)
                        Text(" ▾", style = LogHoundDesign.Text.Status)
                    }
                    DropdownMenu(
                        expanded = keyMenuOpen,
                        onDismissRequest = { keyMenuOpen = false },
                    ) {
                        for (key in FILTER_CLAUSE_KEYS) {
                            DropdownMenuItem(
                                text = { Text(key, style = LogHoundDesign.Text.Tab) },
                                onClick = {
                                    selectedKey = key
                                    keyMenuOpen = false
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                val fieldShape = RoundedCornerShape(4.dp)
                BasicTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    textStyle = LogHoundDesign.Text.Field,
                    modifier = Modifier
                        .width(180.dp)
                        .clip(fieldShape)
                        .background(LogHoundDesign.Colors.TextFieldBackground)
                        .border(1.dp, LogHoundDesign.Colors.TextFieldBorder, fieldShape)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag(TestTags.FILTER_BUILDER_VALUE),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    placeholderFor(selectedKey),
                                    color = LogHoundDesign.Colors.Placeholder,
                                    style = LogHoundDesign.Text.Field,
                                )
                            }
                            inner()
                        }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", style = LogHoundDesign.Text.Button)
                }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = { onApply(selectedKey, value.trim()) },
                    modifier = Modifier.testTag(TestTags.FILTER_BUILDER_APPLY),
                    enabled = value.isNotBlank(),
                ) {
                    Text("Add", style = LogHoundDesign.Text.Button.copy(color = LogHoundDesign.Colors.Primary))
                }
            }
        }
    }
}

private fun placeholderFor(key: String): String = when (key) {
    "tag" -> "Activity"
    "level" -> "W"
    "pid" -> "1234"
    "tid" -> "5678"
    "package" -> "com.app.debug"
    "device" -> "Pixel 8"
    else -> ""
}
