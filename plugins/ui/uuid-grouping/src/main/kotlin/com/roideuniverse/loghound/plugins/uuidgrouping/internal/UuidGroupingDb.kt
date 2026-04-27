package com.roideuniverse.loghound.plugins.uuidgrouping.internal

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.roideuniverse.loghound.plugins.uuidgrouping.sqldelight.UuidGroupingDb
import java.io.File
import java.util.Properties

internal fun openUuidGroupingDb(databaseFile: File): UuidGroupingDb {
    val isNew = !databaseFile.exists()
    if (isNew) databaseFile.parentFile?.mkdirs()

    val driver = JdbcSqliteDriver(
        url = "jdbc:sqlite:${databaseFile.absolutePath}",
        properties = Properties(),
    )
    if (isNew) UuidGroupingDb.Schema.create(driver)

    driver.execute(null, "PRAGMA journal_mode = WAL;", 0)
    driver.execute(null, "PRAGMA synchronous = NORMAL;", 0)

    return UuidGroupingDb(driver)
}
