package com.roideuniverse.loghound.core

data class LogFilter(
    val tag: String? = null,
    val minPriority: LogPriority? = null,
    val pid: Int? = null,
    val tid: Int? = null,
    val textSearch: String? = null,
    val regexSearch: String? = null,
    val packageName: String? = null,
)
