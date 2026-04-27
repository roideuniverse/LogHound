package com.roideuniverse.loghound.core

enum class LogPriority(val label: Char, val level: Int) {
    Verbose('V', 2),
    Debug('D', 3),
    Info('I', 4),
    Warn('W', 5),
    Error('E', 6),
    Fatal('F', 7),
    Silent('S', 8);

    companion object {
        fun fromLabel(label: Char): LogPriority? = entries.find { it.label == label }
    }
}
