package com.roideuniverse.loghound.plugins

import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.plugindsl.plugin
import com.roideuniverse.loghound.plugindsl.stateOf

fun helloDslPlugin() = plugin {
    id = "hello-dsl"
    name = "Hello DSL"

    val recent = stateOf<List<LogEntry>>(emptyList())

    data { repo ->
        repo.ingested.collect { batch ->
            recent.value = (recent.value + batch).takeLast(50)
        }
    }

    ui {
        column {
            section {
                text("Hello DSL — last ${recent.value.size} log lines")
            }
            divider()
            list(items = recent.value, key = { it.id }) { entry ->
                text("${entry.tag}: ${entry.message.take(120)}")
            }
        }
    }
}
