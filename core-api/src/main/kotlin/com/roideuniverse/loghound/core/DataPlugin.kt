package com.roideuniverse.loghound.core

interface DataPlugin {
    val id: String
    val name: String
    suspend fun run(repository: LogRepository)
}
