package com.piyush.mode.core.database

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import app.cash.sqldelight.driver.native.wrapConnection
import co.touchlab.sqliter.DatabaseConfiguration

/** The database file in the app's private storage, with foreign keys on. */
public class IosDatabaseFactory(private val name: String = "mode.db") : DatabaseFactory {
    override fun create(): ModeDatabase {
        val schema = ModeDatabase.Schema
        val driver = NativeSqliteDriver(
            DatabaseConfiguration(
                name = name,
                version = schema.version.toInt(),
                create = { connection -> wrapConnection(connection) { schema.create(it) } },
                upgrade = { connection, old, new -> wrapConnection(connection) { schema.migrate(it, old.toLong(), new.toLong()) } },
                extendedConfig = DatabaseConfiguration.Extended(foreignKeyConstraints = true),
            ),
        )
        return ModeDatabase(driver)
    }
}
