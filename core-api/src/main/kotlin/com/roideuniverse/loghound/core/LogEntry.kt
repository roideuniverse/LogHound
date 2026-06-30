package com.roideuniverse.loghound.core

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val pid: Int,
    val tid: Int,
    val priority: LogPriority,
    val tag: String,
    val message: String,
    val packageName: String? = null,
    /**
     * Stable identity of the source that produced this entry. For adb-backed sources this is the
     * device serial; for file ingest it's a synthetic `file:<name>` id. Always present —
     * single-device callsites synthesise a singleton DeviceId rather than leaving this null.
     */
    val deviceId: DeviceId = DeviceId(DEFAULT_DEVICE_ID),
) {
    companion object {
        /**
         * Placeholder DeviceId used by callers that haven't been migrated to carry a real device
         * serial (tests, file ingest before the path is known). Will go away once every callsite
         * supplies a real value.
         */
        const val DEFAULT_DEVICE_ID = "unknown"
    }
}
