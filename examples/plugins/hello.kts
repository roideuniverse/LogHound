// Smallest possible LogHound DSL plugin. Drop into ~/.loghound/plugins/
// and relaunch the app — a "Hello" tab appears in the sidebar showing
// the last 50 ingested log lines.
//
// Use this as a starting template. Everything else (filtering, sub-tabs,
// sort, querying history, theme overrides) is in plugins.spec.md and
// in examples/plugins/uuid-grouping.kts.

plugin {
    id = "hello"
    name = "Hello"

    val recent = stateOf<List<LogEntry>>(emptyList())

    data { repo ->
        repo.ingested.collect { batch -> recent.value = (recent.value + batch).takeLast(50) }
    }

    ui {
        column {
            section { text("Hello, LogHound — last ${recent.value.size} log lines") }
            divider()
            list(items = recent.value, key = { it.id }) { entry ->
                text("${entry.tag}: ${entry.message.take(120)}")
            }
        }
    }
}
