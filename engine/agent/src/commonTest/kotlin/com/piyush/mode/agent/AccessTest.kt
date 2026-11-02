package com.piyush.mode.agent

import com.piyush.mode.ops.Op
import com.piyush.mode.testing.FakePhone
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AccessTest {
    private class Gate(val onYourTurn: suspend () -> Boolean = { true }) : UserGate {
        val confirmed = mutableListOf<String>()
        val turns = mutableListOf<String>()
        override suspend fun confirm(question: String) = true.also { confirmed += question }
        override suspend fun ask(question: String): String? = "yes"
        override suspend fun yourTurn(what: String): Boolean { turns += what; return onYourTurn() }
    }

    private val base = AgentConfig(settleMs = 0, maxSteps = 6, waitForStill = false)
    private val pay = listOf("Pay ₹348" to (201.0 to 800.0))
    private fun tapPay() = call("tap", "x" to 250, "y" to 915, "reason" to "paying")
    private fun done() = call("done", "evidence" to "on screen", "summary" to "paid")

    @Test
    fun withoutFullAccessAPaymentIsAsked() = runTest {
        val gate = Gate()
        Agent(FakePhone(listOf(pay, listOf("Paid" to (100.0 to 100.0)))), ScriptedModel(tapPay(), done()), gate, base).run("pay")
        assertEquals(1, gate.confirmed.size)
    }

    @Test
    fun fullAccessPaysWithoutAsking() = runTest {
        val gate = Gate()
        val phone = FakePhone(listOf(pay, listOf("Paid" to (100.0 to 100.0))))
        Agent(phone, ScriptedModel(tapPay(), done()), gate, base.copy(fullAccess = true)).run("pay")
        assertTrue(gate.confirmed.isEmpty())
        assertIs<Op.Tap>(phone.actions.single())
    }

    @Test
    fun fullAccessStillAsksAboveTheAmountSet() = runTest {
        val under = Gate()
        Agent(FakePhone(listOf(pay, listOf("Paid" to (100.0 to 100.0)))), ScriptedModel(tapPay(), done()), under,
            base.copy(fullAccess = true, askAboveAmount = 500.0)).run("pay")
        assertTrue(under.confirmed.isEmpty(), "₹348 is under ₹500")
        val over = Gate()
        Agent(FakePhone(listOf(pay, listOf("Paid" to (100.0 to 100.0)))), ScriptedModel(tapPay(), done()), over,
            base.copy(fullAccess = true, askAboveAmount = 300.0)).run("pay")
        assertEquals(1, over.confirmed.size, "₹348 is over ₹300")
    }

    @Test
    fun theRulesSayWhatWasAllowed() {
        val full = Agent.accessRules(AgentConfig(fullAccess = true, readCodes = true))
        assertTrue("FULL ACCESS" in full && "get it yourself" in full && "wait_for_user" in full)
        val ask = Agent.accessRules(AgentConfig(fullAccess = false, readCodes = false))
        assertFalse("FULL ACCESS" in ask)
        assertTrue("ask the user for it" in ask)
    }

    @Test
    fun aHiddenScreenWaitsForTheUserAndCarriesOnWhenThePageChanges() = runTest {
        val pinPad = listOf("Enter UPI PIN" to (201.0 to 300.0), "1 2 3" to (201.0 to 600.0))
        val phone = FakePhone(listOf(pinPad))
        // The user enters the PIN: the page moves on; they never tap "I've entered it".
        val gate = Gate { phone.screens = listOf(listOf("Payment successful" to (201.0 to 300.0), "₹348 paid" to (201.0 to 360.0))); awaitCancellation() }
        val model = ScriptedModel(call("wait_for_user", "what" to "Enter your UPI PIN in PhonePe", "reason" to "PIN"), done())
        val result = Agent(phone, model, gate, base.copy(fullAccess = true)).run("pay")
        assertIs<AgentOutcome.Done>(result.outcome)
        assertEquals(listOf("Enter your UPI PIN in PhonePe"), gate.turns)
        assertTrue(phone.actions.isEmpty(), "Mode typed nothing on the PIN pad")
        assertTrue("the user did it" in model.lastUserText, model.lastUserText)
    }

    @Test
    fun aCodeOnScreenIsNeverKept() = runTest {
        val sms = listOf("Your OTP is 482913 for login" to (201.0 to 300.0))
        val steps = mutableListOf<StepRecord>()
        val looked = mutableListOf<Int>()
        val trace = object : AgentTrace {
            override fun looked(step: Int, jpeg: ByteArray, imageWidth: Int, imageHeight: Int, texts: List<Triple<String, Int, Int>>, millis: Long) {
                looked += jpeg.size
                assertTrue(texts.none { "482913" in it.first }, "screen text is masked")
            }
            override fun acted(step: Int, action: String, op: String?, outcome: String, millis: Long) = assertFalse("482913" in action)
        }
        val model = ScriptedModel(
            call("type_text", "text" to "482913", "x" to 250, "y" to 500, "reason" to "typing the code 482913"),
            call("done", "evidence" to "logged in", "summary" to "Logged in with 482913"),
        )
        val result = Agent(FakePhone(listOf(sms, listOf("Welcome" to (100.0 to 100.0)))), model, Gate(), base, onStep = { steps += it }, trace = trace).run("log in")
        assertEquals(0, looked.first(), "the screen showing the code is not kept")
        assertTrue(steps.none { "482913" in it.reason || "482913" in it.action })
        val summary = (result.outcome as AgentOutcome.Done).summary
        assertFalse("482913" in summary, summary)
    }

    @Test
    fun codesAreFoundOnlyWhereTheLineSaysWhatTheyAre() {
        assertEquals(listOf("482913"), OneTimeCodes.codesIn("482913 is your verification code"))
        assertEquals(listOf("4821"), OneTimeCodes.codesIn("From Messages 4821"))
        assertTrue(OneTimeCodes.codesIn("Total ₹1348").isEmpty())
        assertTrue(OneTimeCodes.codesIn("Order 560103 placed").isEmpty())
    }
}
