package com.piyush.mode.agent

import com.piyush.mode.testing.FakePhone
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GiveUpGateTest {
    private class Gate(val answer: String = "Schedule it for 11:00") : UserGate {
        var asked = 0
        override suspend fun confirm(question: String) = true
        override suspend fun ask(question: String): String? = answer.also { asked++ }
        override suspend fun choose(question: String, options: List<String>, recommended: String?) = answer.also { asked++ }
    }

    private val closed = listOf("Tres Leches Creamery" to (100.0 to 300.0), "CLOSED RIGHT NOW" to (100.0 to 340.0), "Schedule" to (300.0 to 340.0))
    private val config = AgentConfig(settleMs = 0, maxSteps = 12)
    private fun giveUp(blockedBy: String, evidence: String = "CLOSED RIGHT NOW", vararg routes: String) =
        call("done", "success" to false, "summary" to "Could not order", "evidence" to evidence, "blocked_by" to blockedBy, "routes_tried" to routes.toList())

    @Test
    fun aClosedShopIsTheUsersCallSoTheAgentMustAskFirst() = runTest {
        // 3 Nov 20:12: "CLOSED RIGHT NOW · Schedule" and a give-up with twenty steps left.
        val phone = FakePhone(List(8) { closed })
        val gate = Gate()
        val steps = mutableListOf<StepRecord>()
        val model = ScriptedModel(
            giveUp("closed"),
            call("ask_user", "question" to "It is closed now. Schedule for 11:00, or another bakery?", "options" to listOf("Schedule it for 11:00", "Another bakery")),
            giveUp("closed", "CLOSED RIGHT NOW", "Zomato: closed until 11", "Swiggy: not listed"),
            giveUp("closed", "CLOSED RIGHT NOW", "Zomato: closed until 11", "Swiggy: not listed"),
        )
        val r = Agent(phone, model, gate, config, onStep = { steps += it }).run("order tres leches on Zomato")
        assertTrue("this is the user's call" in steps[0].outcome && "ask_user with 2–3 concrete options" in steps[0].outcome, steps[0].outcome)
        assertEquals(1, gate.asked)
        assertTrue("before giving up" in steps[2].outcome, "asked, two routes: then the other-way question once: ${steps[2].outcome}")
        val done = assertIs<AgentOutcome.Done>(r.outcome)
        assertFalse(done.success)
    }

    @Test
    fun aLoginOrPaymentIsAHandOverNotAGiveUp() = runTest {
        val phone = FakePhone(List(6) { listOf("Enter UPI PIN" to (100.0 to 300.0)) })
        val steps = mutableListOf<StepRecord>()
        val model = ScriptedModel(giveUp("login_or_payment", "Enter UPI PIN"), giveUp("login_or_payment", "Enter UPI PIN"), giveUp("login_or_payment", "Enter UPI PIN"))
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run("pay the bill")
        assertTrue("Call wait_for_user" in steps[0].outcome, steps[0].outcome)
        assertTrue("before giving up" in steps[1].outcome, "the same reason is not refused twice; the softer question follows: ${steps[1].outcome}")
    }

    @Test
    fun oneRouteIsNotEnoughWithStepsLeftButTwoAre() = runTest {
        val phone = FakePhone(List(8) { listOf("No results for IndiGo" to (100.0 to 300.0)) })
        val steps = mutableListOf<StepRecord>()
        val model = ScriptedModel(
            giveUp("not_possible", "No results for IndiGo"),
            giveUp("not_possible", "No results for IndiGo", "Mail search 'IndiGo': no results", "Messages search 'IndiGo': nothing"),
            giveUp("not_possible", "No results for IndiGo", "Mail search 'IndiGo': no results", "Messages search 'IndiGo': nothing"),
        )
        val r = Agent(phone, model, Gate(), config, onStep = { steps += it }).run("find my flight")
        assertTrue("one route is not enough" in steps[0].outcome, steps[0].outcome)
        assertTrue("before giving up" in steps[1].outcome, "two routes pass the gate; the other-way question comes once: ${steps[1].outcome}")
        assertIs<AgentOutcome.Done>(r.outcome)
    }

    @Test
    fun theEvidenceMustQuoteTheScreen() = runTest {
        val phone = FakePhone(List(8) { listOf("Magnolia Bakery" to (100.0 to 300.0), "Open · 10 AM–10 PM" to (100.0 to 340.0)) })
        val steps = mutableListOf<StepRecord>()
        val model = ScriptedModel(
            giveUp("not_possible", "Sold out everywhere", "Zomato: closed", "Swiggy: not listed"),
            call("done", "success" to false, "summary" to "x", "evidence" to "x"),
        )
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run("order a cake")
        assertTrue("the evidence must quote what the screen shows" in steps[0].outcome, steps[0].outcome)
    }

    @Test
    fun quotingNeedsOneContentWordOnScreen() {
        assertTrue(GiveUpGate.quotes("CLOSED RIGHT NOW", listOf("Tres Leches", "CLOSED RIGHT NOW · Schedule")))
        assertTrue(GiveUpGate.quotes("shows closed", listOf("closed right now")))
        assertFalse(GiveUpGate.quotes("Sold out everywhere", listOf("Magnolia Bakery", "Open 10 AM")))
    }
}
