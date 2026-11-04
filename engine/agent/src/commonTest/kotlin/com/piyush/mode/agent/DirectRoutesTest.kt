package com.piyush.mode.agent

import com.piyush.mode.ops.DeviceData
import com.piyush.mode.ops.Op
import com.piyush.mode.testing.FakePhone
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DirectRoutesTest {
    private class Gate : UserGate {
        override suspend fun confirm(question: String) = true
        override suspend fun ask(question: String): String? = "yes"
    }

    private class Device : DeviceData {
        val opened = mutableListOf<String>()
        override val canOpenUrl = true
        override val hasCalendar = true
        override val hasContacts = true
        override suspend fun openUrl(url: String) = true.also { opened += url }
        override suspend fun calendar(days: Int) = listOf("Thu 5 Nov 06:40–08:55 · IndiGo 6E223 BLR→DEL · Terminal 1")
        override suspend fun contacts(query: String) = if ("asha" in query.lowercase()) listOf("Asha Rao · +91 98765 43210") else emptyList()
    }

    private val page = listOf("Home" to (100.0 to 300.0))
    private val config = AgentConfig(settleMs = 0, maxSteps = 8)

    @Test
    fun theDirectToolsAreOfferedOnlyWhereThePlatformHasThem() {
        val none = AgentTools.available(setOf("input.tap", "apps.launch")).map { it.name }
        assertFalse("open_url" in none || "read_calendar" in none || "find_contact" in none, none.toString())
        val all = AgentTools.available(setOf("input.tap"), device = Device()).map { it.name }
        assertTrue(all.containsAll(listOf("open_url", "run_shortcut", "read_calendar", "find_contact")), all.toString())
        assertEquals(listOf("open_url", "run_shortcut"), AgentTools.available(setOf("url.open")).map { it.name }.filter { it in AgentTools.DIRECT })
        assertEquals("shortcuts://run-shortcut?name=Cab%20home&input=text&text=now%20please", AgentTools.shortcutUrl("Cab home", "now please"))
    }

    @Test
    fun theCalendarAndAContactAreReadWithoutAScreenAndKeptAsFacts() = runTest {
        val phone = FakePhone(List(8) { page })
        val device = Device()
        val model = ScriptedModel(
            call("read_calendar", "days" to 1, "reason" to "Checking tomorrow's flight"),
            call("find_contact", "name" to "Asha", "reason" to "Her number", "facts" to listOf("flight: 6E223", "terminal: 1")),
            call("open_url", "url" to "https://www.flightradar24.com/data/flights/6e223", "reason" to "Flight status", "expect" to "the flight's status page"),
            call("done", "success" to true, "summary" to "6E223 is on time", "evidence" to "Home"),
        )
        val steps = mutableListOf<StepRecord>()
        Agent(phone, model, Gate(), config, onStep = { steps += it }, device = device).run("is tomorrow's flight on time, and text Asha")
        assertTrue("IndiGo 6E223 BLR→DEL" in steps[0].outcome, steps[0].outcome)
        assertTrue("Asha Rao · +91 98765 43210" in steps[1].outcome, steps[1].outcome)
        assertEquals(listOf("https://www.flightradar24.com/data/flights/6e223"), device.opened)
        assertTrue(steps[2].outcome.startsWith("ok"), steps[2].outcome)
        assertTrue("TASK FACTS" in model.lastUserText && "flight: 6E223" in model.lastUserText && "terminal: 1" in model.lastUserText, model.lastUserText)
        assertTrue(phone.actions.none { it is Op.Tap }, "no screen was driven")
    }

    @Test
    fun aPhoneThatOpensUrlsItselfIsUsedBeforeTheApp() = runTest {
        val phone = FakePhone(List(6) { page }, supported = setOf("screen.capture", "screen.text", "input.tap", "url.open"))
        val model = ScriptedModel(call("open_url", "url" to "zomato://search?q=tres+leches", "reason" to "Search"), call("done", "success" to false, "summary" to "x", "evidence" to "Home"))
        Agent(phone, model, Gate(), config).run("order tres leches")
        assertEquals<List<Op>>(listOf(Op.OpenUrl("zomato://search?q=tres+leches")), phone.actions)
    }

    @Test
    fun aLookupAsksTheProvidersWebSearchAndBowsOutOnAPlanWithoutIt() = runTest {
        val phone = FakePhone(List(8) { page })
        val tap = call("tap", "x" to 1, "y" to 1, "reason" to "x")
        var lookups = 0
        val script = ScriptedModel(
            call("lookup", "question" to "IndiGo 6E223 status today", "reason" to "Flight status"),
            call("lookup", "question" to "Magnolia Bakery Indiranagar hours", "reason" to "Hours"),
            call("done", "success" to true, "summary" to "On time", "evidence" to "Home"),
        )
        val model = com.piyush.mode.llm.LlmClient { m, msgs, tools ->
            if (com.piyush.mode.llm.WebSearchTool.wanted(tools)) {
                lookups++
                if (lookups == 1) com.piyush.mode.llm.ChatResult(null, "6E223 BLR→DEL departs 06:40, on time (flightradar24).", com.piyush.mode.llm.Usage(50, 20, 0.002))
                else throw com.piyush.mode.llm.LlmFailed("web_search is not available", status400 = true)
            } else script.chat(m, msgs, tools)
        }
        val steps = mutableListOf<StepRecord>()
        Agent(phone, model, Gate(), config, onStep = { steps += it }).run("is my flight on time")
        assertTrue(steps[0].outcome.startsWith("found: 6E223 BLR→DEL departs 06:40"), steps[0].outcome)
        assertTrue("lookup is not available on this plan" in steps[1].outcome, steps[1].outcome)
        assertEquals(2, lookups)
        assertTrue(AgentTools.available(setOf("input.tap"), lookup = false).none { it.name == "lookup" })
        assertTrue(AgentTools.directRoutes(setOf("input.tap"), null, lookup = true).contains("lookup"))
    }
}
