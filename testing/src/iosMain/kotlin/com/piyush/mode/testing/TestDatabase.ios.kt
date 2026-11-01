package com.piyush.mode.testing

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration
import com.piyush.mode.core.database.ModeDatabase
import kotlin.random.Random

actual fun testDatabase(): ModeDatabase {
    val schema = ModeDatabase.Schema
    return ModeDatabase(
        NativeSqliteDriver(
            DatabaseConfiguration(
                name = "test-${Random.nextLong()}",
                version = schema.version.toInt(),
                create = { connection -> wrapConnection(connection) { schema.create(it) } },
                inMemory = true,
                extendedConfig = DatabaseConfiguration.Extended(foreignKeyConstraints = true),
            ),
        ),
    )
}
