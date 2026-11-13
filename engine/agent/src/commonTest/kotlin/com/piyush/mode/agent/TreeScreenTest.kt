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

/** Phones that report windows (Android's tree): the keyboard's keys and screens the phone hides. */
class TreeScreenTest {
    private class Gate(private val onAsk: () -> Unit = {}) : UserGate {
        val asked = mutableListOf<String>()
        override suspend fun confirm(question: String) = true
        override suspend fun ask(question: String): String? { asked += question; onAsk(); return "done" }
        override suspend fun choose(question: String, options: List<String>, recommended: String?) = "done"
    }

    private val quick = AgentConfig(settleMs = 0, maxSteps = 10)

    @Test
    fun theKeyboardsKeysComeFromItsWindowNotFromGuessing() = runTest {
        // Three letter keys: too few for the OCR heuristic, but the phone says they are the keyboard's.
        val phone = FakePhone(listOf(listOf("Search" to (100.0 to 200.0), "q" to (20.0 to 760.0), "w" to (60.0 to 760.0), "e" to (100.0 to 760.0))))
        phone.windows = mapOf("q" to "ime", "w" to "ime", "e" to "ime", "Search" to "app")
        val model = ScriptedModel(call("done", "evidence" to "on screen", "summary" to "x"))
        Agent(phone, model, Gate(), quick, profile = PhoneProfile.Android).run("task")
        assertTrue("\"Search\"" in model.lastUserText, model.lastUserText)
        assertFalse("\"q\"" in model.lastUserText, model.lastUserText)
        assertTrue("keyboard" in model.lastUserText.lowercase(), model.lastUserText)
    }

    @Test
    fun withWindowsReportedAndNoKeyboardWindowTheKeyboardIsDown() = runTest {
        // Eight single letters on the page (a word game), all in the app's window: not a keyboard.
        val letters = "abcdefgh".mapIndexed { i, c -> c.toString() to (20.0 + i * 40 to 800.0) }
        val phone = FakePhone(listOf(listOf("Play" to (100.0 to 200.0)) + letters))
        phone.windows = (letters.map { it.first } + "Play").associateWith { "app" }
        val model = ScriptedModel(call("done", "evidence" to "on screen", "summary" to "x"))
        Agent(phone, model, Gate(), quick, profile = PhoneProfile.Android).run("task")
        assertTrue("\"h\"" in model.lastUserText, model.lastUserText)
        assertFalse("keyboard" in model.lastUserText.lowercase(), model.lastUserText)
    }

    @Test
    fun aPrivateScreenIsTheUsersTurnWithoutAskingTheModel() = runTest {
        val phone = FakePhone(listOf(listOf("PIN" to (100.0 to 400.0)), listOf("Paid" to (100.0 to 400.0))))
        phone.privateScreens = setOf(0)
        // The user enters the PIN: the screen is private no more.
        val gate = Gate(onAsk = { phone.privateScreens = emptySet(); phone.screens = listOf(listOf("Paid" to (100.0 to 400.0))) })
        val model = ScriptedModel(call("done", "evidence" to "Paid", "summary" to "paid"))
        val steps = mutableListOf<StepRecord>()
        val result = Agent(phone, model, gate, quick, onStep = { steps += it }, profile = PhoneProfile.Android).run("pay the bill")

        assertIs<AgentOutcome.Done>(result.outcome)
        assertEquals(listOf(Agent.PRIVATE_SCREEN), gate.asked)
        assertEquals("wait_for_user", steps.first().action)
        assertEquals(1, model.models.size, "the model is only asked once the screen shows again")
        assertTrue("\"Paid\"" in model.lastUserText, model.lastUserText)
    }
}
