package com.piyush.mode.core.database

import com.piyush.mode.testing.testDatabase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchemaTest {
    @Test
    fun aConversationKeepsItsTurnsInOrderAndTakesThemWhenDeleted() {
        val db = testDatabase()
        db.conversationQueries.insert("c1", "Turn off dark mode", 1, 1)
        db.turnQueries.insert("t1", "c1", "user", "Turn off dark mode", "k1", 10)
        db.turnQueries.insert("t2", "c1", "mode", "Done.", "k1", 20)
        assertEquals(listOf("user", "mode"), db.turnQueries.forConversation("c1").executeAsList().map { it.role })

        db.conversationQueries.delete("c1")
        assertTrue(db.turnQueries.forConversation("c1").executeAsList().isEmpty(), "turns go with their conversation")
    }

    @Test
    fun aTaskMovesThroughItsStatusesAndOutlivesItsConversation() {
        val db = testDatabase()
        db.conversationQueries.insert("c1", "t", 1, 1)
        db.taskQueries.insert("k1", "c1", "Turn off dark mode", "app", "queued", 1)
        db.taskQueries.start("running", 2, "run-1", "k1")
        db.taskQueries.finish("done", 3, "Light mode on", 7, 0.004, "k1")
        val task = db.taskQueries.byId("k1").executeAsOne()
        assertEquals("done", task.status)
        assertEquals(7L, task.steps)
        assertEquals("run-1", task.run_id)

        db.conversationQueries.delete("c1")
        assertNull(db.taskQueries.byId("k1").executeAsOne().conversation_id, "the task stays, unlinked")
    }

    @Test
    fun runsListNewestFirstAndOldOnesCanBeFound() {
        val db = testDatabase()
        listOf("2026-10-30T10-00-00", "2026-11-01T08-00-00", "2026-10-31T09-00-00").forEach {
            db.runQueries.upsert(it, null, "t", "done", "", null, null, null, null, null, 0)
        }
        assertEquals("2026-11-01T08-00-00", db.runQueries.recent(10).executeAsList().first().id)
        assertEquals(listOf("2026-10-30T10-00-00"), db.runQueries.idsBeyond(2).executeAsList())
    }
}
