package com.roideuniverse.loghound.plugins.logcat

import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogPriority

/**
 * Parses Android `adb logcat -v threadtime` lines into `LogEntry`.
 *
 * Format: `MM-DD HH:MM:SS.mmm PID TID PRIORITY TAG: MESSAGE`
 *
 * Malformed lines return null — callers skip them.
 */
internal object LogcatThreadtimeParser {

    private val LINE =
        Regex(
            """^(\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+)\s+(\d+)\s+([VDIWEFS])\s+([^:]+?):\s?(.*)$"""
        )

    fun parse(line: String): LogEntry? {
        val m = LINE.matchEntire(line) ?: return null
        val (timestamp, pidStr, tidStr, prioChar, tagRaw, message) = m.destructured
        val pid = pidStr.toIntOrNull() ?: return null
        val tid = tidStr.toIntOrNull() ?: return null
        val priority = LogPriority.fromLabel(prioChar.single()) ?: return null
        return LogEntry(
            id = 0L,
            timestamp = timestamp,
            pid = pid,
            tid = tid,
            priority = priority,
            tag = tagRaw.trim(),
            message = message,
        )
    }
}
