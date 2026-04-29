val UUID_REGEX = Regex(
    """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""",
)

plugin {
    id = "uuid-grouping-script"
    name = "UUID Grouping (script)"

    val rows = stateOf<List<Pair<String, Long>>>(emptyList())
    val filter = stateOf("")
    val sortByCount = stateOf(true)
    val tabs = tabController(masterName = "UUIDs")
    val detailEntries = stateOf<Map<String, List<LogEntry>>>(emptyMap())

    data { repo ->
        val counts = mutableMapOf<String, Long>()

        fun publish() {
            rows.value = counts.entries.map { it.key to it.value }
        }

        fun ingest(entries: List<LogEntry>): Boolean {
            var changed = false
            entries.forEach { entry ->
                if ('-' !in entry.message) return@forEach
                UUID_REGEX.findAll(entry.message).forEach { match ->
                    val uuid = match.value.lowercase()
                    counts[uuid] = (counts[uuid] ?: 0L) + 1
                    changed = true
                }
            }
            return changed
        }

        // Backfill: scan the entire DB ASC starting from id 0.
        var cursor: Long = 0L
        while (true) {
            val page = repo.query(afterId = cursor, limit = 1000)
            if (page.entries.isEmpty()) break
            ingest(page.entries)
            cursor = page.entries.last().id
            publish()
            if (!page.hasMore) break
        }

        coroutineScope {
            // Live ingest.
            launch {
                repo.ingested.collect { batch ->
                    if (ingest(batch)) publish()
                }
            }

            // When a detail tab opens, query for log lines containing that UUID.
            launch {
                snapshotFlow { tabs.openTabs.value.map { it.id } }.collect { ids ->
                    ids.forEach { id ->
                        if (id !in detailEntries.value) {
                            launch {
                                val page = repo.query(
                                    filter = LogFilter(textSearch = id),
                                    limit = 500,
                                )
                                detailEntries.value = detailEntries.value + (id to page.entries)
                            }
                        }
                    }
                }
            }
        }
    }

    ui {
        tabs(
            controller = tabs,
            master = {
                section {
                    row {
                        weight(1f) {
                            textField(state = filter, placeholder = "Search UUIDs\u2026")
                        }
                        spacer(width = 8)
                        button(
                            label = if (sortByCount.value) "Sort: count ↓" else "Sort: A → Z",
                            onClick = { sortByCount.value = !sortByCount.value },
                        )
                    }
                    val visible = run {
                        val q = filter.value.lowercase()
                        val filtered = if (q.isEmpty()) rows.value
                                       else rows.value.filter { it.first.contains(q) }
                        if (sortByCount.value) filtered.sortedByDescending { it.second }
                        else filtered.sortedBy { it.first }
                    }
                    text("${visible.size} of ${rows.value.size} UUIDs")
                    spacer(height = 4)
                }
                divider()
                val q = filter.value.lowercase()
                val filtered = if (q.isEmpty()) rows.value
                               else rows.value.filter { it.first.contains(q) }
                val visible = if (sortByCount.value) filtered.sortedByDescending { it.second }
                              else filtered.sortedBy { it.first }
                list(items = visible, key = { it.first }) { (uuid, count) ->
                    clickable(onClick = { tabs.open(id = uuid, name = uuid.take(8) + "…") }) {
                        row {
                            text(count.toString().padStart(7))
                            spacer(width = 12)
                            text(uuid)
                        }
                    }
                }
            },
            detail = { uuid ->
                val entries = detailEntries.value[uuid]
                if (entries == null) {
                    centered { loading() }
                } else if (entries.isEmpty()) {
                    centered {
                        text("No log lines for this UUID")
                    }
                } else {
                    list(items = entries, key = { it.id }) { entry ->
                        val color = when (entry.priority) {
                            LogPriority.Verbose -> Color(0xFF666666)
                            LogPriority.Debug -> Color(0xFF1976D2)
                            LogPriority.Info -> Color(0xFF388E3C)
                            LogPriority.Warn -> Color(0xFFF57C00)
                            LogPriority.Error -> Color(0xFFD32F2F)
                            LogPriority.Fatal -> Color(0xFFD32F2F)
                            LogPriority.Silent -> Color(0xFF999999)
                        }
                        val weight = if (entry.priority == LogPriority.Fatal) FontWeight.Bold
                                     else FontWeight.Normal
                        val style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = color,
                            fontWeight = weight,
                        )
                        text(
                            "${entry.timestamp}  ${entry.pid} ${entry.tid} " +
                                "${entry.priority.name.first()}  ${entry.tag}: ${entry.message}",
                            style = style,
                        )
                    }
                }
            },
        )
    }
}
