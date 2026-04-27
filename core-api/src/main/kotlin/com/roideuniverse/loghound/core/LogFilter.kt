package com.roideuniverse.loghound.core

data class LogFilter(
    val tag: String? = null,
    val minPriority: LogPriority? = null,
    val pid: Int? = null,
    val tid: Int? = null,
    val textSearch: String? = null,
    val regexSearch: String? = null,
    val packageName: String? = null,
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
        return true
    }
}
