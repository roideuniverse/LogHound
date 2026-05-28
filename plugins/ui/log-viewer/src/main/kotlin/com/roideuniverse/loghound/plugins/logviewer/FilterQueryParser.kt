package com.roideuniverse.loghound.plugins.logviewer

import com.roideuniverse.loghound.core.LogFilter
import com.roideuniverse.loghound.core.LogPriority

internal object FilterQueryParser {
    fun parse(query: String): LogFilter {
        if (query.isBlank()) return LogFilter()

        var tag: String? = null
        var minPriority: LogPriority? = null
        var pid: Int? = null
        var tid: Int? = null
        var packageName: String? = null
        var deviceId: String? = null
        val freeTextParts = mutableListOf<String>()

        for (token in tokenize(query)) {
            val colon = token.indexOf(':')
            if (colon > 0 && colon < token.length - 1) {
                val key = token.substring(0, colon).lowercase()
                val value = token.substring(colon + 1)
                val matched = when (key) {
                    "tag" -> { tag = value; true }
                    "level" -> parseLevel(value)?.also { minPriority = it } != null
                    "pid" -> value.toIntOrNull()?.also { pid = it } != null
                    "tid" -> value.toIntOrNull()?.also { tid = it } != null
                    "package" -> { packageName = value; true }
                    "device" -> { deviceId = value; true }
                    else -> false
                }
                if (matched) continue
            }
            freeTextParts += token
        }

        val freeText = freeTextParts.joinToString(" ")
        val (textSearch, regexSearch) = if (freeText.startsWith("/")) {
            val pattern = freeText.removePrefix("/").removeSuffix("/")
            null to pattern.takeIf { it.isNotEmpty() }
        } else {
            freeText.takeIf { it.isNotEmpty() } to null
        }

        return LogFilter(
            tag = tag,
            minPriority = minPriority,
            pid = pid,
            tid = tid,
            textSearch = textSearch,
            regexSearch = regexSearch,
            packageName = packageName,
            deviceId = deviceId,
        )
    }

    private fun parseLevel(value: String): LogPriority? {
        if (value.length == 1) return LogPriority.fromLabel(value[0].uppercaseChar())
        return LogPriority.entries.find { it.name.equals(value, ignoreCase = true) }
    }

    private fun tokenize(query: String): List<String> {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (c in query) {
            when {
                c == '"' -> inQuotes = !inQuotes
                c.isWhitespace() && !inQuotes -> {
                    if (current.isNotEmpty()) {
                        tokens += current.toString()
                        current.clear()
                    }
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) tokens += current.toString()
        return tokens
    }
}
