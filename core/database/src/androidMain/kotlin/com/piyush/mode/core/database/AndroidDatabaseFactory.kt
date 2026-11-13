package com.piyush.mode.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver

/** The database file in the app's private storage, with foreign keys on. */
public class AndroidDatabaseFactory(context: Context, private val name: String = "mode.db") : DatabaseFactory {
    private val app = context.applicationContext

    override fun create(): ModeDatabase {
        val schema = ModeDatabase.Schema
        val driver = AndroidSqliteDriver(
            schema = schema,
            context = app,
            name = name,
            callback = object : AndroidSqliteDriver.Callback(schema) {
                override fun onConfigure(db: SupportSQLiteDatabase) {
                    super.onConfigure(db)
                    db.setForeignKeyConstraintsEnabled(true)
                }
            },
        )
        return ModeDatabase(driver)
    }
}
