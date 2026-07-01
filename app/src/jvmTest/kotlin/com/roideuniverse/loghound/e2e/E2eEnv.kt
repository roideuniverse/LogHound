package com.roideuniverse.loghound.e2e

import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogPriority
import com.roideuniverse.loghound.core.impl.LogRepositoryImpl
import com.roideuniverse.loghound.database.LogDataStore
import com.roideuniverse.loghound.database.createLogDataStore
import java.io.File

/**
 * Builds the same object graph the running app builds: real `LogDataStore` over real SQLite (in a
 * temp dir) wrapped by `LogRepositoryImpl`. No fakes, no mocks.
 */
internal class E2eEnv(rootDir: File) {
    val logDbFile: File = File(rootDir, "logs.db")
    val pluginDbDir: File = File(rootDir, "plugins").also { it.mkdirs() }

    val dataStore: LogDataStore = createLogDataStore(logDbFile)
    val repository: LogRepositoryImpl = LogRepositoryImpl(dataStore)
}

internal fun makeEntry(
    timestamp: String = "01-15 12:00:00.000",
    pid: Int = 1234,
    tid: Int = 5678,
    priority: LogPriority = LogPriority.Info,
    tag: String = "T",
    message: String,
    packageName: String? = null,
) =
    LogEntry(
        id = 0L,
        timestamp = timestamp,
        pid = pid,
        tid = tid,
        priority = priority,
        tag = tag,
        message = message,
        packageName = packageName,
    )
