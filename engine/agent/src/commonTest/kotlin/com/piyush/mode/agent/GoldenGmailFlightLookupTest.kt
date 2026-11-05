package com.piyush.mode.agent

import com.piyush.mode.ops.Op
import com.piyush.mode.testing.FakePhone
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Golden trace from the phone, 5 Nov 2026 17:09 (Gmail, "find my flight back to Bangalore"): the flight, date
 * and PNR were noted at step 6, then twenty more steps followed, in silence. The harness's part in that:
 * reading steps taken for changes, twelve deletes for a 52-character search, scrolling counted as repeating,
 * and a plan that was moving called stuck.
 */
class GoldenGmailFlightLookupTest {
    private class Gate : UserGate {
        override suspend fun confirm(question: String) = true
        override suspend fun ask(question: String): String? = "yes"
    }
    private val config = AgentConfig(settleMs = 0, maxSteps = 30)

    @Test
    fun readingStepsThatMentionABookingAreNotChanges() {
        // The model's own words in that run.
        for (reason in listOf(
            "Searching flight booking emails", "Finding Bangalore flight bookings", "Finding the booking confirmation",
            "Finding the booking itinerary", "Opening the flight booking confirmation", "Checking for booking changes or cancellations",
            "Returning to your flight itinerary", "Reviewing your order and delivery details",
        )) {
            assertFalse(Changes.isChange(reason, "17:11"), reason)
            assertEquals(Changes.Kind.Other, Changes.kindOf(reason, null), reason)
        }
        // A change still begins with the change.
        for (reason in listOf("Booking the ride", "Placing the scheduled order", "Adding it to Dinner", "Sending the message", "Paying with the wallet", "Confirming the booking")) {
            assertTrue(Changes.isChange(reason, null), reason)
        }
    }

    @Test
    fun aLookupIsNotToldItsChangeWasNeverConfirmed() = runTest {
        val results = listOf("Search in mail" to (129.0 to 104.0), "17:11" to (42.0 to 96.0), "E-Ticket for Your Flight Booking ID: NF2ANWG" to (200.0 to 300.0))
        val phone = FakePhone(List(6) { i -> results + ("page $i" to (100.0 to 600.0)) })
        val steps = mutableListOf<StepRecord>()
        val model = ScriptedModel(
            call("tap", "x" to 52, "y" to 110, "reason" to "Finding the booking itinerary", "expect" to "Gmail search results reappear"),
            call("done", "success" to true, "summary" to "Your flight is IX2817", "evidence" to "E-Ticket for Your Flight Booking ID: NF2ANWG"),
        )
        val r = Agent(phone, model, Gate(), config, onStep = { steps += it }).run("find my flight back to Bangalore")
        assertEquals(2, r.steps, "done is accepted at once: ${steps.map { it.outcome }}")
        assertFalse("changed something" in steps[0].outcome, steps[0].outcome)
    }

    @Test
    fun readingALongEmailByScrollingIsNotGoingInCircles() = runTest {
        val phone = FakePhone(List(12) { i -> listOf("E-Ticket" to (100.0 to 100.0), "Line ${i * 3}" to (100.0 to 300.0), "Line ${i * 3 + 1}" to (100.0 to 400.0), "Line ${i * 3 + 2}" to (100.0 to 500.0)) })
        val scroll = call("scroll", "direction" to "down", "reason" to "Reading the ticket", "item" to 2)
        val model = ScriptedModel(scroll, scroll, scroll, scroll, scroll, call("done", "success" to true, "summary" to "x", "evidence" to "E-Ticket"))
        Agent(phone, model, Gate(), config).run("find my flight")
        assertFalse("ROUTE CHANGE" in model.lastUserText || "TRY ANOTHER WAY" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun aPlanThatMovesFromItemToItemIsNotRewritten() = runTest {
        val phone = FakePhone(List(20) { i -> listOf("Page $i" to (100.0 to 300.0), "Next $i" to (200.0 to 500.0)) })
        fun tap(i: Int, item: Int) = call("tap", "x" to 250, "y" to 570, "reason" to "Opening page $i", "item" to item)
        // Fourteen steps over three plan items: none takes twelve.
        val taps = (1..14).map { tap(it, if (it <= 5) 1 else if (it <= 10) 2 else 3) }
        val model = ScriptedModel(call("plan", "steps" to "1. Search Gmail\n2. Read the confirmation\n3. Report"), *taps.toTypedArray(), call("done", "success" to true, "summary" to "x", "evidence" to "Page"))
        Agent(phone, model, Gate(), config).run("find my flight")
        assertFalse("REPLAN" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun clearDeletesWhatTheFieldShowsAndRetypesWhenOldTextIsLeft() = runTest {
        // Gmail's search holding the first query (cut off on screen), then the new words typed after what 12 deletes left.
        val field = pt("{Bengaluru Bangalore BLR} {flig...")
        val stale = pt("ngalore BLR} {flight itinerH9W74N")
        val clean = pt("H9W74N")
        val phone = FakePhone(listOf(field) + List(80) { stale } + List(200) { clean })
        val steps = mutableListOf<StepRecord>()
        val model = ScriptedModel(
            call("type_text", "text" to "H9W74N", "x" to 129 * 500 / 402, "y" to 104 * 1000 / 874, "clear" to true, "reason" to "Searching the booking reference"),
            call("done", "success" to true, "summary" to "x", "evidence" to "H9W74N"),
        )
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run("find my flight")
        val deletes = phone.actions.count { it is Op.PressKey && it.combo == "delete" }
        assertTrue(deletes >= 52, "enough deletes for a 52-character search, got $deletes")
        assertEquals(2, phone.actions.count { it is Op.TypeText }, "typed again after the old text was cleared")
        assertTrue("the old text was cleared and it was typed again" in steps[0].outcome, steps[0].outcome)
    }

    private fun pt(text: String) = listOf(text to (129.0 to 104.0), "Cancel" to (360.0 to 104.0))

    @Test
    fun withTheAnswerInItsNotesTheAgentIsAskedWhetherItIsDone() = runTest {
        val phone = FakePhone(List(14) { i -> listOf("Mail $i" to (100.0 to 300.0)) })
        fun tap(i: Int, note: String) = call("tap", "x" to 100, "y" to 300, "reason" to "Reading mail $i", "note" to note)
        val notes = listOf("Flight IX2817", "Patna to Bengaluru, 21 November 2026", "PNR H9W74N", "12:40 to 15:30", "Terminal 2", "Economy", "One bag", "Nonstop")
        val model = ScriptedModel(*notes.mapIndexed { i, n -> tap(i, n) }.toTypedArray(), call("done", "success" to true, "summary" to "x", "evidence" to "Mail"))
        Agent(phone, model, Gate(), config).run("find my flight")
        assertTrue("ANSWER CHECK" in model.lastUserText, model.lastUserText)
    }
}
