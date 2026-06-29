package com.roideuniverse.loghound.plugins.logviewer

import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
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
import com.roideuniverse.loghound.design.LocalLogHoundColors
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
        var tagFilters by remember { mutableStateOf<List<TagFilter>>(emptyList()) }
        val activeDevice = LocalActiveDevice.current
        val parsedFilter: LogFilter = remember(queryText) { FilterQueryParser.parse(queryText) }
        val filter: LogFilter = remember(parsedFilter, activeDevice) {
            if (parsedFilter.deviceId != null) parsedFilter
            else parsedFilter.copy(deviceId = activeDevice?.value)
        }
        val entries = remember { mutableStateListOf<LogEntry>() }
        val listState = rememberLazyListState()
        var loadingOlder by remember { mutableStateOf(false) }
        var olderExhausted by remember { mutableStateOf(false) }
        var expandedEntryId by remember { mutableStateOf<Long?>(null) }

        // Top tags from visible entries, recomputed when entries changes.
        val availableTags by remember {
            derivedStateOf {
                entries.groupingBy { it.tag }.eachCount()
                    .entries.sortedByDescending { it.value }
                    .map { it.key to it.value }
                    .take(50)
            }
        }

        LaunchedEffect(filter, tagFilters) {
            olderExhausted = false
            val initial = repository.query(filter = filter, limit = 500)
            entries.clear()
            entries.addAll(initial.entries.applyTagFilters(tagFilters))
            if (entries.isNotEmpty()) listState.requestScrollToItem(entries.lastIndex)
        }

        LaunchedEffect(filter, tagFilters) {
            repository.ingested.collect { batch ->
                val matched = batch.filter(filter::matches).applyTagFilters(tagFilters)
                if (matched.isEmpty()) return@collect
                val wasAtBottom = listState.run {
                    val last = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                    last >= entries.lastIndex - 2
                }
                entries.addAll(matched)
                if (wasAtBottom) {
                    while (entries.size > MAX_IN_MEMORY) entries.removeAt(0)
                    if (entries.isNotEmpty()) listState.requestScrollToItem(entries.lastIndex)
                }
            }
        }

        LaunchedEffect(filter, tagFilters) {
            snapshotFlow { listState.firstVisibleItemIndex }
                .distinctUntilChanged()
                .collect { firstVisible ->
                    if (firstVisible <= LOAD_OLDER_THRESHOLD &&
                        !loadingOlder && !olderExhausted && entries.isNotEmpty()
                    ) {
                        loadingOlder = true
                        try {
                            val older = repository.query(
                                filter = filter,
                                beforeId = entries.first().id,
                                limit = LOAD_OLDER_PAGE_SIZE,
                            )
                            if (older.entries.isEmpty()) {
                                olderExhausted = true
                            } else {
                                entries.addAll(0, older.entries.applyTagFilters(tagFilters))
                            }
                        } finally {
                            loadingOlder = false
                        }
                    }
                }
        }

        val coroutineScope = rememberCoroutineScope()
        val isAtBottom by remember {
            derivedStateOf {
                if (entries.isEmpty()) return@derivedStateOf true
                val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                last >= entries.lastIndex - 2
            }
        }

        val colors = LocalLogHoundColors.current
        Column(modifier = modifier.fillMaxSize()) {
            PackageUidLookupBar()
            HorizontalDivider(color = colors.border)
            FilterBar(
                query = queryText,
                onQueryChange = { queryText = it },
                tagFilters = tagFilters,
                onAddTag = { tag ->
                    if (tagFilters.none { it.tag == tag }) {
                        tagFilters = tagFilters + TagFilter(tag)
                    }
                },
                onToggleTag = { tag ->
                    tagFilters = tagFilters.map {
                        if (it.tag == tag) it.copy(mode = if (it.mode == TagMode.Include) TagMode.Exclude else TagMode.Include)
                        else it
                    }
                },
                onRemoveTag = { tag -> tagFilters = tagFilters.filter { it.tag != tag } },
                availableTags = availableTags,
            )
            HorizontalDivider(color = colors.border)
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
                            containerColor = colors.button,
                            contentColor = colors.buttonText,
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
    val colors = LocalLogHoundColors.current
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
        Text("Loading older…", style = LogHoundDesign.Text.Status.copy(color = colors.secondary))
    }
}

@Composable
private fun LogRowWithExpansion(entry: LogEntry, expanded: Boolean, onToggle: () -> Unit) {
    val colors = LocalLogHoundColors.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val rowBg = when {
        expanded || isHovered -> colors.hoverBackground
        else -> colors.background
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
    val colors = LocalLogHoundColors.current
    Surface(modifier = Modifier.fillMaxWidth(), color = colors.surface) {
        Column(modifier = Modifier.padding(start = 32.dp, end = 12.dp, top = 4.dp, bottom = 8.dp)) {
            Text(
                "${entry.timestamp} · ${entry.tag} · pid ${entry.pid} · tid ${entry.tid}",
                style = LogHoundDesign.Text.Status.copy(color = colors.secondary),
                modifier = Modifier.testTag(TestTags.LOG_ROW_DETAIL_META),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                entry.message,
                style = LogHoundDesign.Text.Row.copy(color = colors.onSurface),
                modifier = Modifier.testTag(TestTags.LOG_ROW_DETAIL_MESSAGE),
            )
        }
    }
}

@Composable
private fun LogRow(entry: LogEntry) {
    val colors = LocalLogHoundColors.current
    val bodyStyle = LogHoundDesign.Text.Row.copy(color = colors.onSurface)
    val metaStyle = LogHoundDesign.Text.Row.copy(fontSize = 11.sp, color = colors.secondary)
    val tagStyle = LogHoundDesign.Text.Row.copy(fontWeight = FontWeight.Medium, color = colors.onSurface)
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
private fun FilterBar(
    query: String,
    onQueryChange: (String) -> Unit,
    tagFilters: List<TagFilter>,
    onAddTag: (String) -> Unit,
    onToggleTag: (String) -> Unit,
    onRemoveTag: (String) -> Unit,
    availableTags: List<Pair<String, Int>>,
) {
    val colors = LocalLogHoundColors.current

    // Detect `tag:xxx` suffix in query → show autocomplete.
    val tagFragment = remember(query) {
        val last = query.trimEnd().split(" ").last()
        if (last.startsWith("tag:", ignoreCase = true)) last.drop(4) else null
    }
    val acSuggestions = remember(tagFragment, availableTags) {
        if (tagFragment == null) emptyList()
        else availableTags.filter { (tag, _) ->
            tagFragment.isEmpty() || tag.contains(tagFragment, ignoreCase = true)
        }
    }

    fun pickTag(tag: String) {
        val stripped = query.replace(Regex("""(\s|^)tag:\S*$""", RegexOption.IGNORE_CASE), "").trimEnd()
        onQueryChange(stripped)
        onAddTag(tag)
    }

    var tagsMenuOpen by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxWidth(), color = colors.surface) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shape = RoundedCornerShape(4.dp)
                val style: TextStyle = LocalTextStyle.current.merge(LogHoundDesign.Text.Field.copy(color = colors.onSurface))
                // Search field with chips
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(colors.input)
                        .border(1.dp, colors.border, shape),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tagFilters.forEach { chip ->
                            FilterChip(
                                chip = chip,
                                onToggle = { onToggleTag(chip.tag) },
                                onRemove = { onRemoveTag(chip.tag) },
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = onQueryChange,
                            singleLine = true,
                            textStyle = style,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                                .testTag(TestTags.FILTER_INPUT),
                            decorationBox = { inner ->
                                Box {
                                    if (query.isEmpty() && tagFilters.isEmpty()) {
                                        Text(
                                            "tag:Activity level:W package:mine",
                                            color = colors.secondary,
                                            style = style,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
                Spacer(Modifier.width(6.dp))
                // Tags facet button
                Box {
                    TagsButton(
                        activeCount = tagFilters.size,
                        onClick = { tagsMenuOpen = true },
                    )
                    DropdownMenu(
                        expanded = tagsMenuOpen,
                        onDismissRequest = { tagsMenuOpen = false },
                    ) {
                        if (availableTags.isEmpty()) {
                            DropdownMenuItem(
                                text = { Text("No tags in view", style = LogHoundDesign.Text.Status.copy(color = colors.secondary)) },
                                onClick = { tagsMenuOpen = false },
                                enabled = false,
                            )
                        } else {
                            availableTags.take(20).forEach { (tag, count) ->
                                val active = tagFilters.any { it.tag == tag }
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                tag,
                                                style = LogHoundDesign.Text.Tab.copy(
                                                    color = if (active) colors.primary else colors.onSurface,
                                                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                                ),
                                                modifier = Modifier.weight(1f),
                                            )
                                            Spacer(Modifier.width(12.dp))
                                            Text(
                                                count.toString(),
                                                style = LogHoundDesign.Text.Status.copy(color = colors.secondary),
                                            )
                                        }
                                    },
                                    onClick = {
                                        if (active) onRemoveTag(tag) else onAddTag(tag)
                                        tagsMenuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(4.dp))
                FilterBuilderButton(
                    onAppend = { clause ->
                        val sep = if (query.isBlank()) "" else " "
                        onQueryChange("$query$sep$clause")
                    },
                )
            }
            // Tag autocomplete dropdown
            if (acSuggestions.isNotEmpty()) {
                TagAutocomplete(
                    suggestions = acSuggestions,
                    onPick = { pickTag(it) },
                )
            }
        }
    }
}

@Composable
private fun FilterChip(chip: TagFilter, onToggle: () -> Unit, onRemove: () -> Unit) {
    val colors = LocalLogHoundColors.current
    val isExclude = chip.mode == TagMode.Exclude
    val bgColor = if (isExclude) colors.priorityBgError else colors.accentTint
    val fgColor = if (isExclude) colors.priorityError else colors.primary
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(bgColor)
            .clickable(onClick = onToggle)
            .padding(start = 6.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isExclude) "−${chip.tag}" else chip.tag,
            style = LogHoundDesign.Text.Status.copy(color = fgColor, fontWeight = FontWeight.Medium),
        )
        Spacer(Modifier.width(2.dp))
        Text(
            "✕",
            style = LogHoundDesign.Text.Status.copy(color = fgColor),
            modifier = Modifier
                .clip(RoundedCornerShape(2.dp))
                .clickable(onClick = onRemove)
                .padding(horizontal = 3.dp),
        )
    }
}

@Composable
private fun TagsButton(activeCount: Int, onClick: () -> Unit) {
    val colors = LocalLogHoundColors.current
    val active = activeCount > 0
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (active) colors.accentTint else colors.input)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            "Tags",
            style = LogHoundDesign.Text.Status.copy(
                color = if (active) colors.primary else colors.secondary,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
        if (activeCount > 0) {
            Text(
                activeCount.toString(),
                style = LogHoundDesign.Text.Status.copy(color = colors.primary, fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

@Composable
private fun TagAutocomplete(suggestions: List<Pair<String, Int>>, onPick: (String) -> Unit) {
    val colors = LocalLogHoundColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.elevated)
            .border(1.dp, colors.border),
    ) {
        Text(
            "TAGS IN VIEW · tap to filter",
            style = LogHoundDesign.Text.Status.copy(
                color = colors.secondary,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
        HorizontalDivider(color = colors.borderSoft)
        suggestions.take(8).forEach { (tag, count) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(tag) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tag,
                    style = LogHoundDesign.Text.Row.copy(color = colors.onSurface),
                    modifier = Modifier.weight(1f),
                )
                Text(
                    count.toString(),
                    style = LogHoundDesign.Text.Status.copy(color = colors.secondary),
                )
            }
            HorizontalDivider(color = colors.rowDivider)
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
    val colors = LocalLogHoundColors.current
    var selectedKey by remember { mutableStateOf(FILTER_CLAUSE_KEYS.first()) }
    var keyMenuOpen by remember { mutableStateOf(false) }
    var value by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(6.dp)

    Surface(
        modifier = Modifier
            .clip(shape)
            .border(1.dp, colors.border, shape)
            .testTag(TestTags.FILTER_BUILDER_PANEL),
        color = colors.elevated,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                "Add filter clause",
                style = LogHoundDesign.Text.Tab.copy(fontWeight = FontWeight.SemiBold, color = colors.onSurface),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    TextButton(onClick = { keyMenuOpen = true }) {
                        Text(selectedKey, style = LogHoundDesign.Text.Button.copy(color = colors.onSurface))
                        Text(" ▾", style = LogHoundDesign.Text.Status.copy(color = colors.secondary))
                    }
                    DropdownMenu(
                        expanded = keyMenuOpen,
                        onDismissRequest = { keyMenuOpen = false },
                    ) {
                        for (key in FILTER_CLAUSE_KEYS) {
                            DropdownMenuItem(
                                text = { Text(key, style = LogHoundDesign.Text.Tab.copy(color = colors.onSurface)) },
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
                    textStyle = LogHoundDesign.Text.Field.copy(color = colors.onSurface),
                    modifier = Modifier
                        .width(180.dp)
                        .clip(fieldShape)
                        .background(colors.input)
                        .border(1.dp, colors.border, fieldShape)
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag(TestTags.FILTER_BUILDER_VALUE),
                    decorationBox = { inner ->
                        Box {
                            if (value.isEmpty()) {
                                Text(
                                    placeholderFor(selectedKey),
                                    color = colors.secondary,
                                    style = LogHoundDesign.Text.Field.copy(color = colors.onSurface),
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
                    Text("Cancel", style = LogHoundDesign.Text.Button.copy(color = colors.secondary))
                }
                Spacer(Modifier.width(4.dp))
                TextButton(
                    onClick = { onApply(selectedKey, value.trim()) },
                    modifier = Modifier.testTag(TestTags.FILTER_BUILDER_APPLY),
                    enabled = value.isNotBlank(),
                ) {
                    Text("Add", style = LogHoundDesign.Text.Button.copy(color = colors.primary))
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

enum class TagMode { Include, Exclude }

data class TagFilter(val tag: String, val mode: TagMode = TagMode.Include)

private fun List<LogEntry>.applyTagFilters(filters: List<TagFilter>): List<LogEntry> {
    if (filters.isEmpty()) return this
    val includes = filters.filter { it.mode == TagMode.Include }.map { it.tag }
    val excludes = filters.filter { it.mode == TagMode.Exclude }.map { it.tag }
    return filter { entry ->
        (includes.isEmpty() || entry.tag in includes) &&
        (excludes.isEmpty() || entry.tag !in excludes)
    }
}
