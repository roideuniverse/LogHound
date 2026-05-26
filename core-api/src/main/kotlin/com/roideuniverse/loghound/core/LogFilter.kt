package com.roideuniverse.loghound.core

data class LogFilter(
    val tag: String? = null,
    val minPriority: LogPriority? = null,
    val pid: Int? = null,
    val tid: Int? = null,
    val textSearch: String? = null,
    val regexSearch: String? = null,
    val packageName: String? = null,
    /**
     * Substring (case-insensitive) match against the entry's device id or
     * label. Matches the `device:<name-or-prefix>` clause from the filter
     * bar; composes AND with every other clause. Null means "any device."
     */
    val deviceId: String? = null,
) {
    fun matches(entry: LogEntry): Boolean {
        if (tag != null && !entry.tag.contains(tag, ignoreCase = true)) return false
        if (minPriority != null && entry.priority.level < minPriority.level) return false
        if (pid != null && entry.pid != pid) return false
        if (tid != null && entry.tid != tid) return false
        if (packageName != null) {
            val pkg = entry.packageName ?: return false
            if (!pkg.contains(packageName, ignoreCase = true)) return false
        }
        if (textSearch != null && !entry.message.contains(textSearch, ignoreCase = true)) return false
        if (regexSearch != null && !Regex(regexSearch).containsMatchIn(entry.message)) return false
        if (deviceId != null && !entry.deviceId.value.contains(deviceId, ignoreCase = true)) return false
        return true
    }
}
