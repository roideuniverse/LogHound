package com.roideuniverse.loghound.core

import kotlinx.coroutines.flow.Flow

interface LogInputStream {
    fun lines(): Flow<String>
    fun close()
}
