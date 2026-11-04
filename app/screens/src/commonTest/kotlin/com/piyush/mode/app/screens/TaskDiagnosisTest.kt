package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.debug.FindingKind
import com.piyush.mode.app.screens.debug.TaskDiagnosis
import com.piyush.mode.core.telemetry.SpanRecord
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TaskDiagnosisTest {
    private fun act(step: Int, action: String, reason: String = "") = JsonObject(mapOf(
        "event" to JsonPrimitive("act"), "step" to JsonPrimitive(step.toString()), "action" to JsonPrimitive(action),
        "reason" to JsonPrimitive(reason), "outcome" to JsonPrimitive("ignored"),
    ))

    @Test
    fun thisMorningsRunsAnswerWithoutAnActionThenStuck() {
        // Run 2026-11-04T06-50-37: six replies without an action, then stopped as stuck.
        val steps = (1..6).map { act(it, "(no valid action)") }
        val found = TaskDiagnosis.of("stopped", "Stuck: it made no progress in 6 steps. Last: (no valid action).", steps, emptyList())
        assertEquals(listOf(FindingKind.NoAction, FindingKind.Stuck), found.map { it.kind })
        assertTrue(found.all { it.problem })
        assertTrue("6 times" in found.first().detail)
    }

    @Test
    fun anOrderItCouldntConfirmAndABackAndForthLoop() {
        // Run 2026-11-03T20-06-03 (Zomato): 11 back taps, then step 30.
        val steps = (1..11).map { act(18 + it, "back", "go back to search") }
        val found = TaskDiagnosis.of("gave_up", "I couldn’t verify that your order was placed or paid; one Classic Tres Leches remains in the cart.", steps, emptyList())
        assertEquals(setOf(FindingKind.Unconfirmed, FindingKind.BackAndForth), found.map { it.kind }.toSet())
        assertTrue("11 times" in found.first { it.kind == FindingKind.BackAndForth }.detail)
    }

    @Test
    fun aRunLeftRunningMeansModeWasClosedAndAModelErrorIsNamed() {
        val closed = TaskDiagnosis.of("running", null, emptyList(), emptyList())
        assertEquals(FindingKind.Interrupted, closed.single().kind)
        val span = SpanRecord("t", "s", null, "gen_ai.chat", 0, 25_000, emptyMap(), emptyList(), "LlmFailed")
        val failed = TaskDiagnosis.of("failed", "ChatGPT needs you to sign in again", emptyList(), listOf(span))
        assertEquals("LlmFailed", failed.single { it.kind == FindingKind.AiFailed }.detail)
    }
}
