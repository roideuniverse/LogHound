package com.roideuniverse.loghound.plugins.logcat

import com.roideuniverse.loghound.core.DataPlugin
import com.roideuniverse.loghound.core.Device
import com.roideuniverse.loghound.core.DeviceId
import com.roideuniverse.loghound.core.LogEntry
import com.roideuniverse.loghound.core.LogRepository
import com.roideuniverse.loghound.plugins.logcat.internal.PackageResolver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * Streams `adb logcat -v threadtime` from every connected device in parallel.
 *
 * The plugin owns a small supervisor coroutine that polls `adb devices`
 * every [POLL_INTERVAL_MS], diffs the result against its in-memory map of
 * `serial -> stream Job`, and reconciles:
 *
 * - serials that newly appeared get a fresh `streamFromDevice` coroutine
 * - serials that disappeared have their stream cancelled (which destroys
 *   the subprocess, unblocking the readLine() loop)
 * - serials that are still present are left alone
 *
 * The detected set is also published to [LogRepository.devices] so the UI
 * status-bar pill / autocomplete / per-device tabs can render without
 * having to shell out themselves.
 *
 * Each per-device stream tags its emitted `LogEntry`s with `DeviceId(serial)`
 * so downstream consumers (filter, UUID Grouping, sessions) can scope by
 * source.
 */
class LogcatDataPlugin : DataPlugin {
    override val id: String = "core.data.logcat"
    override val name: String = "ADB Logcat"

    override suspend fun run(repository: LogRepository) {
        val adb = findAdbExecutable()
        if (adb == null) {
            // adb not found anywhere — publish an empty set so the UI can
            // render a "no devices" empty state, then idle until cancel.
            repository.publishDevices(emptySet())
            awaitCancellation()
        }
        supervisorScope {
            val active = mutableMapOf<String, Job>()
            try {
                while (coroutineContext.isActive) {
                    val detected = listAttachedDevices(adb)
                    repository.publishDevices(detected.map { Device(DeviceId(it)) }.toSet())

                    val gone = active.keys - detected
                    for (serial in gone) {
                        active.remove(serial)?.cancel()
                    }

                    val fresh = detected - active.keys
                    for (serial in fresh) {
                        active[serial] = launch {
                            runCatching { streamFromDevice(adb, serial, repository) }
                            // On stream termination, drop the entry so the
                            // next reconciliation can relaunch if the device
                            // is still attached (logcat exit + reattach).
                            active.remove(serial)
                        }
                    }

                    delay(POLL_INTERVAL_MS)
                }
            } finally {
                active.values.forEach { it.cancel() }
                repository.publishDevices(emptySet())
            }
        }
    }

    private suspend fun streamFromDevice(
        adb: String,
        serial: String,
        repository: LogRepository,
    ) = coroutineScope {
        val deviceId = DeviceId(serial)
        val proc = withContext(Dispatchers.IO) {
            ProcessBuilder(adb, "-s", serial, "logcat", "-v", "threadtime")
                .redirectErrorStream(true)
                .start()
        }

        // Destroy the subprocess when this coroutine is cancelled. That closes
        // stdout, which unblocks the readLine() call in the loop below.
        val destroyOnCancel = launch {
            try {
                awaitCancellation()
            } finally {
                proc.destroyForcibly()
            }
        }

        val resolver = PackageResolver(adb, serial)
        val resolverJob = launch { resolver.runRefreshLoop() }

        try {
            withContext(Dispatchers.IO) {
                val reader = proc.inputStream.bufferedReader()
                val batch = ArrayList<LogEntry>(BATCH_SIZE)
                while (true) {
                    val line = reader.readLine() ?: break
                    LogcatThreadtimeParser.parse(line)?.let { entry ->
                        batch.add(
                            entry.copy(
                                deviceId = deviceId,
                                packageName = resolver.packageNameFor(entry.pid),
                            ),
                        )
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

    private suspend fun listAttachedDevices(adb: String): Set<String> = withContext(Dispatchers.IO) {
        val proc = ProcessBuilder(adb, "devices")
            .redirectErrorStream(true)
            .start()
        val output = proc.inputStream.bufferedReader().readText()
        proc.waitFor()
        output.lineSequence()
            .drop(1) // skip "List of devices attached"
            .map { it.trim() }
            .filter { it.endsWith("\tdevice") }
            .map { it.substringBefore('\t') }
            .filter { it.isNotBlank() }
            .toSet()
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
        return "adb"
    }

    private companion object {
        const val BATCH_SIZE = 100
        const val POLL_INTERVAL_MS = 2_000L
    }
}
