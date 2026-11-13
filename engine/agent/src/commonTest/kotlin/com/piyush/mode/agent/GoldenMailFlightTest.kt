package com.piyush.mode.agent

import com.piyush.mode.ops.Op
import com.piyush.mode.ops.wireName
import com.piyush.mode.testing.FakePhone
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Golden traces from the phone, 3 Nov 2026 19:44 and 19:48 (Mail, "tomorrow's IndiGo flight"): open_app said
 * "ok" while Mode's own chat stayed in front (four times, eleven steps lost), and clear did not empty Mail's
 * search, so "4 November" was typed after "boarding pass".
 */
class GoldenMailFlightTest {
    private class Gate : UserGate {
        override suspend fun confirm(question: String) = true
        override suspend fun ask(question: String): String? = "yes"
    }

    private val task = "Hey, can you do one thing for me? Tomorrow I have flight on fourth of November in Indigo. Can you see the status?"
    private val inbox = listOf("Inbox Gmail" to (140.0 to 85.0), "Select" to (328.0 to 98.0), "MorningStar" to (152.0 to 158.0), "00:55 >" to (411.0 to 158.0))
    // Mode's chat as OCR read it at step 8: the task's own words, line by line.
    private val modeChat = listOf(
        "Hey, can you do one thing for" to (212.0 to 154.0), "me? Tomorrow I have flight on..." to (231.0 to 185.0),
        "Today 01:18" to (58.0 to 215.0), "Q Spoken" to (225.0 to 263.0),
    )
    private val config = AgentConfig(settleMs = 0, maxSteps = 8)

    @Test
    fun openAppWithModeStillInFrontGoesHomeAndOpensItAgain() = runTest {
        val phone = FakePhone(listOf(modeChat, modeChat, inbox, inbox, inbox))
        val steps = mutableListOf<StepRecord>()
        val model = ScriptedModel(call("open_app", "name" to "Mail", "reason" to "Opening Mail"), call("done", "success" to false, "summary" to "stop", "evidence" to "x"))
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run(task)
        assertEquals(listOf("apps.launch", "nav.home", "apps.launch"), phone.actions.map { it.wireName }, "Mode was in front: home, then Mail again")
        assertTrue("Mode was in front; opened Mail again" in steps[0].outcome, steps[0].outcome)
    }

    @Test
    fun openAppThatWorksIsLeftAlone() = runTest {
        val phone = FakePhone(listOf(modeChat, inbox, inbox))
        val steps = mutableListOf<StepRecord>()
        val model = ScriptedModel(call("open_app", "name" to "Mail", "reason" to "Opening Mail"), call("done", "success" to false, "summary" to "stop", "evidence" to "x"))
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run(task)
        assertEquals(listOf("apps.launch"), phone.actions.map { it.wireName })
        assertTrue(steps[0].outcome.startsWith("ok") && "in front" !in steps[0].outcome, steps[0].outcome)
    }

    @Test
    fun whenModeStaysInFrontTheOutcomeSaysSo() = runTest {
        val phone = FakePhone(List(6) { modeChat })
        val steps = mutableListOf<StepRecord>()
        val model = ScriptedModel(call("open_app", "name" to "Mail", "reason" to "Opening Mail"), call("done", "success" to false, "summary" to "stop", "evidence" to "x"))
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run(task)
        assertTrue("Mode is still in front: Mail did not open" in steps[0].outcome, steps[0].outcome)
    }

    @Test
    fun clearTapsTheFieldsCrossAndStaleTextIsCalledOut() = runTest {
        // Mail's search field holds "boarding pass" with its × on the right; typing with clear should hit the × first.
        val search = listOf("boarding pass" to (150.0 to 100.0), "×" to (370.0 to 100.0), "Cancel" to (430.0 to 100.0))
        val stale = listOf("4 November passNov" to (150.0 to 100.0), "×" to (370.0 to 100.0))
        val phone = FakePhone(listOf(search, search, search, stale, stale, stale))
        val steps = mutableListOf<StepRecord>()
        val type = call("type_text", "text" to "4 November", "x" to 150 * 500 / 402, "y" to 100 * 1000 / 874, "clear" to true, "reason" to "Searching for the flight")
        val model = ScriptedModel(type, call("done", "success" to false, "summary" to "stop", "evidence" to "x"))
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run(task)
        val taps = phone.actions.filterIsInstance<Op.Tap>()
        assertTrue(taps.any { kotlin.math.abs(it.x - 370.0) < 2 && kotlin.math.abs(it.y - 100.0) < 2 }, "the × was tapped: ${phone.actions}")
        assertTrue("the old text was not cleared" in steps[0].outcome, steps[0].outcome)
    }

    @Test
    fun theTypedCheckReadsTheFieldNotAMessageThatSaysTheSame() = runTest {
        // WhatsApp's self-chat: a sent "testing Android mod" sits above the compose field that now holds "testing".
        // Seen on the phone (13 Nov): the check took the message for the field and sent the agent in circles.
        val chat = listOf("testing Android mod" to (200.0 to 500.0), "testing" to (150.0 to 800.0), "Send" to (380.0 to 800.0))
        val phone = FakePhone(listOf(chat, chat, chat, chat)).apply { fields = setOf("testing") }
        val steps = mutableListOf<StepRecord>()
        val type = call("type_text", "text" to "testing", "x" to 150 * 500 / 402, "y" to 800 * 1000 / 874, "clear" to true, "reason" to "Writing your message")
        val model = ScriptedModel(type, call("done", "success" to false, "summary" to "stop", "evidence" to "x"))
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run(task)
        assertTrue("the field shows \"testing\"" in steps[0].outcome, steps[0].outcome)
        assertTrue("not cleared" !in steps[0].outcome, steps[0].outcome)
    }

    @Test
    fun withoutAFieldMarkTheTypedCheckTakesTheMatchNearestTheField() = runTest {
        // An iPhone's OCR marks no fields: the match nearest where the typing was aimed is the field.
        val chat = listOf("testing Android mod" to (200.0 to 500.0), "testing" to (150.0 to 800.0))
        val phone = FakePhone(listOf(chat, chat, chat, chat))
        val steps = mutableListOf<StepRecord>()
        val type = call("type_text", "text" to "testing", "x" to 150 * 500 / 402, "y" to 800 * 1000 / 874, "clear" to true, "reason" to "Writing your message")
        val model = ScriptedModel(type, call("done", "success" to false, "summary" to "stop", "evidence" to "x"))
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run(task)
        assertTrue("the field shows \"testing\"" in steps[0].outcome, steps[0].outcome)
    }
}
