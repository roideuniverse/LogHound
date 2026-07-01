package com.roideuniverse.loghound.plugins.logcat.internal

import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Maps PID → package name for log lines emitted by the connected device.
 *
 * Lookups from the streaming hot path (`packageNameFor`) are synchronous and never touch IO — they
 * hit an in-memory `ConcurrentHashMap`. Population happens out-of-band via [runRefreshLoop], which
 * runs `adb shell ps -A` on a fixed interval.
 *
 * PIDs not yet seen by a refresh return `null`. They get attributed on the next refresh. There is
 * no retroactive backfill of previously-ingested rows.
 */
internal class PackageResolver(
    private val adbPath: String,
    private val deviceId: String,
    private val refreshIntervalMs: Long = REFRESH_INTERVAL_MS,
) {
    private val cache = ConcurrentHashMap<Int, String>()

    /** Sync, non-suspending lookup. Safe to call from the parser hot path. */
    fun packageNameFor(pid: Int): String? = cache[pid]

    /**
     * Snapshot once immediately, then snapshot again every [refreshIntervalMs] until the coroutine
     * is cancelled. Each snapshot invokes `adb shell ps -A` and merges the parsed results into the
     * cache.
     */
    suspend fun runRefreshLoop() {
        while (coroutineContext.isActive) {
            val snapshot = withContext(Dispatchers.IO) { runPsSnapshot() }
            cache.putAll(snapshot)
            delay(refreshIntervalMs)
        }
    }

    private fun runPsSnapshot(): Map<Int, String> {
        return try {
            val proc =
                ProcessBuilder(adbPath, "-s", deviceId, "shell", "ps", "-A")
                    .redirectErrorStream(true)
                    .start()
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            parsePsOutput(out)
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 30_000L
    }
}

/**
 * Parses the output of `adb shell ps -A` into a PID → package map. The format varies across Android
 * versions, so we use a positional approach rather than a fixed regex: second column is PID, last
 * column is the process name. Sub-process suffixes are stripped (e.g. `com.app.debug:remote` →
 * `com.app.debug`); kernel threads (names starting with `[`) are dropped.
 */
internal fun parsePsOutput(output: String): Map<Int, String> {
    val result = mutableMapOf<Int, String>()
    output.lineSequence().drop(1).forEach { line ->
        val cols = line.trim().split(Regex("\\s+"))
        if (cols.size < 2) return@forEach
        val pid = cols[1].toIntOrNull() ?: return@forEach
        val rawName = cols.last()
        if (rawName.startsWith("[")) return@forEach
        val name = rawName.substringBefore(':').trim()
        if (name.isEmpty()) return@forEach
        result[pid] = name
    }
    return result
}
