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
)
