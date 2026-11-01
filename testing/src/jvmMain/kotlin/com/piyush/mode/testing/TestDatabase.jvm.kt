package com.piyush.mode.testing

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.piyush.mode.core.database.ModeDatabase
import java.util.Properties

actual fun testDatabase(): ModeDatabase {
    val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY, Properties().apply { put("foreign_keys", "true") })
    ModeDatabase.Schema.create(driver)
    return ModeDatabase(driver)
}
