package com.roideuniverse.loghound.plugins.logcat

import com.roideuniverse.loghound.core.DataPlugin
import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.plugins.logcat.internal.PackageResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

class LogcatDataPlugin : DataPlugin {
    override val id: String = "core.data.logcat"
    override val name: String = "ADB Logcat"

    override suspend fun run(repository: LogRepository) {
        val adb = findAdbExecutable()
        if (adb == null) {
            // adb not found anywhere — idle until cancellation. UI surfacing of this
            // condition is a later round.
            awaitCancellation()
        }
        while (coroutineContext.isActive) {
            val deviceId = findFirstDevice(adb)
            if (deviceId == null) {
                delay(RETRY_DELAY_MS)
                continue
            }
            runCatching { streamFromDevice(adb, deviceId, repository) }
            delay(RETRY_DELAY_MS)
        }
    }

    private suspend fun streamFromDevice(
        adb: String,
        deviceId: String,
        repository: LogRepository,
    ) = coroutineScope {
        val proc = withContext(Dispatchers.IO) {
            ProcessBuilder(adb, "-s", deviceId, "logcat", "-v", "threadtime")
                .redirectErrorStream(true)
                .start()
        }

        // Destroy the subprocess when this coroutine is cancelled. That closes the
        // process's stdout, which unblocks the readLine() call in the loop below.
        val destroyOnCancel = launch {
            try {
                awaitCancellation()
            } finally {
                proc.destroyForcibly()
            }
        }

        // Background PID → package resolver: snapshots `adb shell ps -A` every 30s. The
        // streaming hot path queries it synchronously without IO; PIDs not yet seen
        // by a refresh are ingested with packageName = null and get attributed on the
        // next refresh.
        val resolver = PackageResolver(adb, deviceId)
        val resolverJob = launch { resolver.runRefreshLoop() }

        try {
            withContext(Dispatchers.IO) {
                val reader = proc.inputStream.bufferedReader()
                val batch = ArrayList<LogEntry>(BATCH_SIZE)
                while (true) {
                    val line = reader.readLine() ?: break
                    LogcatThreadtimeParser.parse(line)?.let { entry ->
                        batch.add(entry.copy(packageName = resolver.packageNameFor(entry.pid)))
                    }
                    if (batch.size >= BATCH_SIZE) {
                        repository.append(batch.toList())
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) repository.append(batch.toList())
            }
        } finally {
            resolverJob.cancel()
            destroyOnCancel.cancel()
            proc.destroyForcibly()
        }
    }

    private suspend fun findFirstDevice(adb: String): String? = withContext(Dispatchers.IO) {
        val proc = ProcessBuilder(adb, "devices")
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        output.lineSequence()
            .drop(1)                                // skip "List of devices attached"
            .map { it.trim() }
            .firstOrNull { it.endsWith("\tdevice") }
            ?.substringBefore('\t')
            ?.takeIf { it.isNotBlank() }
    }

    private fun findAdbExecutable(): String? {
        val sdkRoot = System.getenv("ANDROID_HOME") ?: System.getenv("ANDROID_SDK_ROOT")
        if (sdkRoot != null) {
            val adb = File(sdkRoot, "platform-tools/adb")
            if (adb.canExecute()) return adb.absolutePath
        }
        val macDefault = File(
            System.getProperty("user.home"),
            "Library/Android/sdk/platform-tools/adb",
        )
        if (macDefault.canExecute()) return macDefault.absolutePath
        // Last resort: rely on PATH.
        return "adb"
    }

    private companion object {
        const val BATCH_SIZE = 100
        const val RETRY_DELAY_MS = 2_000L
    }
}
