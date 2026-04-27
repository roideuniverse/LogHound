package com.roideuniverse.loghound.plugins.synthetic

import com.roideuniverse.loghound.core.DataPlugin
import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogPriority
import com.roideuniverse.loghound.core.LogRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.coroutines.coroutineContext
import kotlin.random.Random

/**
 * Test/demo data source. Emits realistic-looking logcat entries continuously, including a
 * reusable pool of UUIDs so UUID Grouping has something to discover. Useful for verifying
 * the ingest pipeline end-to-end without a connected Android device.
 */
class SyntheticDataPlugin(
    private val random: Random = Random.Default,
) : DataPlugin {
    override val id: String = "core.data.synthetic"
    override val name: String = "Synthetic Data"

    override suspend fun run(repository: LogRepository) {
        val uuids = List(UUID_POOL_SIZE) { UUID.randomUUID().toString() }
        val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)

        while (coroutineContext.isActive) {
            val now = System.currentTimeMillis()
            val batch = ArrayList<LogEntry>(BATCH_SIZE)
            for (i in 0 until BATCH_SIZE) {
                val tag = TAGS[random.nextInt(TAGS.size)]
                val priority = pickPriority()
                val pid = PIDS[random.nextInt(PIDS.size)]
                val tid = pid + random.nextInt(8)
                val message = buildMessage(uuids)
                batch += LogEntry(
                    id = 0L,
                    timestamp = timeFormat.format(Date(now + i)),
                    pid = pid,
                    tid = tid,
                    priority = priority,
                    tag = tag,
                    message = message,
                    packageName = PACKAGE_FOR_TAG[tag],
                )
            }
            repository.append(batch)
            delay(BATCH_INTERVAL_MS)
        }
    }

    private fun pickPriority(): LogPriority {
        val r = random.nextInt(100)
        return when {
            r < 35 -> LogPriority.Info
            r < 65 -> LogPriority.Debug
            r < 85 -> LogPriority.Verbose
            r < 95 -> LogPriority.Warn
            r < 99 -> LogPriority.Error
            else -> LogPriority.Fatal
        }
    }

    private fun buildMessage(uuids: List<String>): String {
        val template = MESSAGES[random.nextInt(MESSAGES.size)]
        // 60% of messages embed a UUID. Distribution is skewed: lower-index UUIDs appear
        // much more often than higher-index ones — produces a realistic "hot tail" pattern.
        return if (random.nextInt(100) < 60) {
            val skewed = (random.nextDouble().pow2() * uuids.size).toInt().coerceIn(0, uuids.lastIndex)
            template.replace("{uuid}", uuids[skewed])
        } else {
            template.replace("{uuid}", "n/a")
        }
    }

    private fun Double.pow2(): Double = this * this

    private companion object {
        const val UUID_POOL_SIZE = 100
        const val BATCH_SIZE = 20
        const val BATCH_INTERVAL_MS = 200L

        val TAGS = listOf(
            "ActivityManager", "GLThread", "Choreographer", "InputDispatcher",
            "MyApp", "OkHttp", "WorkManager", "Camera2", "AudioFlinger", "Zygote",
        )
        val PIDS = listOf(2031, 4127, 8543, 12044, 15877)
        val PACKAGE_FOR_TAG = mapOf(
            "ActivityManager" to "system_server",
            "GLThread" to "com.example.app",
            "Choreographer" to "com.example.app",
            "MyApp" to "com.example.app",
            "OkHttp" to "com.example.app",
            "WorkManager" to "com.example.app",
            "InputDispatcher" to "system_server",
            "Camera2" to "com.example.camera",
            "AudioFlinger" to "media_server",
            "Zygote" to "system_server",
        )
        val MESSAGES = listOf(
            "Starting operation request_id={uuid}",
            "Received response for {uuid} in 132ms",
            "Cache miss for key={uuid}",
            "User session started session={uuid}",
            "Background task scheduled job={uuid}",
            "Frame skipped, count=3 trace={uuid}",
            "Network call POST /api/v1/sync corr={uuid}",
            "DB write committed txn={uuid} rows=4",
            "Permission granted RECORD_AUDIO trace={uuid}",
            "Activity transition to MainActivity ref={uuid}",
            "Connection failed retry=2 endpoint={uuid}",
            "Token refreshed user={uuid} expires=3600s",
            "Image decoded 1920x1080 asset={uuid}",
            "Service bound name=com.example.SyncService corr={uuid}",
            "Pipeline stage 'compress' completed task={uuid}",
        )
    }
}
