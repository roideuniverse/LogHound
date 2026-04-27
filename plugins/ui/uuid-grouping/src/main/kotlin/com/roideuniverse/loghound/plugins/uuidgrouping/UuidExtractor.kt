package com.roideuniverse.loghound.plugins.uuidgrouping

internal object UuidExtractor {

    private val UUID_REGEX = Regex(
        """[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}""",
    )

    /**
     * Returns lowercase UUIDs found in the message. Quick `'-' in message` pre-filter
     * skips the regex on lines that obviously can't contain a UUID — the common case
     * for most logcat lines.
     */
    fun findAll(message: String): List<String> {
        if ('-' !in message) return emptyList()
        val matches = UUID_REGEX.findAll(message).toList()
        if (matches.isEmpty()) return emptyList()
        return matches.map { it.value.lowercase() }
    }
}
