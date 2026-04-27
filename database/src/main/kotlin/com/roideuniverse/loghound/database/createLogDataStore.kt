package com.roideuniverse.loghound.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.roideuniverse.loghound.database.internal.SqlDelightLogDataStore
import com.roideuniverse.loghound.database.sqldelight.LogHoundDb
import java.io.File
import java.util.Properties

fun createLogDataStore(databaseFile: File): LogDataStore {
    val isNew = !databaseFile.exists()
    if (isNew) databaseFile.parentFile?.mkdirs()

    val driver = JdbcSqliteDriver(
        url = "jdbc:sqlite:${databaseFile.absolutePath}",
        properties = Properties(),
    )
    if (isNew) LogHoundDb.Schema.create(driver)

    driver.execute(null, "PRAGMA journal_mode = WAL;", 0)
    driver.execute(null, "PRAGMA synchronous = NORMAL;", 0)

    return SqlDelightLogDataStore(LogHoundDb(driver))
}
