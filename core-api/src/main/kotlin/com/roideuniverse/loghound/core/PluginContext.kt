package com.roideuniverse.loghound.core

interface PluginContext {
    suspend fun queryLogs(filter: LogFilter, limit: Int, offset: Int): List<LogEntry>
    suspend fun countLogs(filter: LogFilter): Long
    suspend fun totalLogs(): Long
}
