package com.piyush.mode.core.database

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/** A phone with the first version's database keeps its history when Mode updates. */
class MigrationTest {
    @Test
    fun aVersionOneDatabaseUpgradesWithItsTasksIntact() {
        val file = File.createTempFile("mode-v1", ".db").apply { deleteOnExit() }
        File("src/commonMain/sqldelight/databases/1.db").copyTo(file, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        driver.execute(null, "INSERT INTO task(id, text, source, status, created_at) VALUES ('k1', 'Turn off dark mode', 'app', 'done', 1)", 0)

        ModeDatabase.Schema.migrate(driver, 1, ModeDatabase.Schema.version)
        val db = ModeDatabase(driver)

        assertEquals("Turn off dark mode", db.taskQueries.byId("k1").executeAsOne().text)
        db.memoryQueries.insert("m1", "fact", "sonali", "Sonali", "Sonali: message on WhatsApp", "task k1", "", null, "k1", 2, 2)
        assertEquals(1, db.memoryQueries.all().executeAsList().size)
        db.scheduleQueries.insert("s1", "Send good morning to Sonali", 8, 0, 0b0011111, 1, 100, 1)
        assertEquals(listOf("s1"), db.scheduleQueries.due(100).executeAsList().map { it.id })
    }

    @Test
    fun aVersionTwoDatabaseUpgradesToo() {
        val file = File.createTempFile("mode-v2", ".db").apply { deleteOnExit() }
        File("src/commonMain/sqldelight/databases/2.db").copyTo(file, overwrite = true)
        val driver = JdbcSqliteDriver("jdbc:sqlite:${file.absolutePath}")
        driver.execute(null, "INSERT INTO memory(id, kind, subject, title, text, evidence, created_at, updated_at) VALUES ('m1', 'fact', 's', 'S', 't', 'e', 1, 1)", 0)
        ModeDatabase.Schema.migrate(driver, 2, ModeDatabase.Schema.version)
        val db = ModeDatabase(driver)
        assertEquals("t", db.memoryQueries.byId("m1").executeAsOne().text)
        assertEquals(0, db.scheduleQueries.all().executeAsList().size)
    }
}
