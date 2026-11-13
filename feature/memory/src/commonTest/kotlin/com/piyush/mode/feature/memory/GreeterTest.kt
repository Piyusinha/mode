package com.piyush.mode.feature.memory

import com.piyush.mode.feature.memory.data.Acknowledger
import com.piyush.mode.feature.memory.data.DayContext
import com.piyush.mode.feature.memory.data.Greeter
import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.ChatResult
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.llm.Usage
import com.piyush.mode.testing.testDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GreeterTest {
    private class Model(val line: String, val waitMs: Long = 0) : LlmClient {
        var asked: String = ""
        var model: String = ""
        var calls = 0
        override suspend fun chat(model: String, messages: List<ChatMessage>, tools: kotlinx.serialization.json.JsonArray): ChatResult {
            calls++
            asked = messages.last().text
            this.model = model
            delay(waitMs)
            return ChatResult(ToolCall("say", buildJsonObject { put("line", JsonPrimitive(line)) }), "", Usage(200, 20, 0.0))
        }
    }

    private val memory = MemoryRepository(testDatabase())
    private val fast = { "fast-model@low" to ModelProvider.OpenRouter }
    private val evening = DayContext(
        now = "Thursday 6 November, 18:20",
        events = listOf("Thu 6 Nov 19:00–21:00 · Dinner with Priya · Toit"),
        repeats = listOf("Read today's calendar at 20:00"),
        lastTask = "Turn on Do Not Disturb · done",
    )

    @Test
    fun theFirstWordsAreTheAIsWithWhatsComingUpInTheirDay() = runTest {
        val model = Model("Evening, Alex. Dinner with Priya's at 7. Want me to text her you're on your way?")
        val line = Greeter(model, memory, fast).opening("Alex Sinha", hour = 18, day = evening)?.line

        assertEquals("Evening, Alex. Dinner with Priya's at 7. Want me to text her you're on your way?", line)
        assertEquals("fast-model@low", model.model, "the quick model, at its lowest effort")
        assertTrue("THEIR NAME: Alex" in model.asked)
        assertTrue("NOW: Thursday 6 November, 18:20 (evening)" in model.asked)
        assertTrue("Dinner with Priya" in model.asked)
        assertTrue("Read today's calendar at 20:00" in model.asked)
        assertTrue("Turn on Do Not Disturb · done" in model.asked)
    }

    @Test
    fun withNothingComingUpItJustAsksWhatTheyNeed() = runTest {
        val model = Model("Hey Alex, what can I do for you?")
        Greeter(model, memory, fast).opening("Alex", hour = 10, day = DayContext(now = "Friday 7 November, 10:00"))
        assertTrue("NOTHING IS COMING UP" in model.asked)
        assertFalse("CALENDAR" in model.asked)
    }

    @Test
    fun aSlowOrUnusableAnswerFallsBackToModesOwnGreeting() = runTest {
        assertNull(Greeter(Model("Too late.", waitMs = Greeter.BUDGET_MS + 500), memory, fast).opening("Alex", 18, evening))
        assertNull(Greeter(Model("x"), memory, model = { null }).opening("Alex", 18, evening), "no AI connected")
        assertNull(Greeter(Model((1..40).joinToString(" ") { "word" }), memory, fast).opening("Alex", 18, evening), "too long to say")
    }

    @Test
    fun theReplyToATaskSeesTheirCalendar() = runTest {
        val model = Model("Booking a cab for your 7 pm dinner.")
        Acknowledger(model, memory, fast).line("Book a cab", "Alex", hour = 18, day = evening.events)
        assertTrue("THEIR CALENDAR FROM NOW" in model.asked && "Dinner with Priya" in model.asked)
        Acknowledger(model, memory, fast).line("Book a cab", "Alex", hour = 18, private = true, day = evening.events)
        assertFalse("Dinner with Priya" in model.asked, "a private task reads nothing personal")
    }

    @Test
    fun anOfferInTheFirstWordsComesWithItsTask() = runTest {
        val model = object : LlmClient {
            override suspend fun chat(model: String, messages: List<ChatMessage>, tools: kotlinx.serialization.json.JsonArray): ChatResult =
                ChatResult(ToolCall("say", buildJsonObject {
                    put("line", JsonPrimitive("Your Softovac reminder is at 11:30 tonight. Want me to set an alarm?"))
                    put("offer_task", JsonPrimitive("Set an alarm for 11:30 tonight for Softovac"))
                    put("yes_label", JsonPrimitive("Yes, set it"))
                }), "", Usage(200, 20, 0.0))
        }
        val opening = Greeter(model, memory, fast).opening("Alex", hour = 22, day = evening)
        assertEquals("Set an alarm for 11:30 tonight for Softovac", opening?.offer?.task)
        assertEquals("Yes, set it", opening?.offer?.yes)
        assertEquals("Not now", opening?.offer?.no)
    }

    @Test
    fun aPreparedOpeningGreetsAtOnceAndIsUsedUp() = runTest {
        val model = Model("Dinner with Priya's at 7. Want me to text her you're on your way?")
        var clock = 1_000L
        val greeter = Greeter(model, memory, fast).also { it.now = { clock } }
        greeter.prepare("Alex", 18, evening)
        assertEquals(1, model.calls)
        assertEquals(model.line, greeter.opening("Alex", 18, evening)?.line)
        assertEquals(1, model.calls, "the prepared one is used, the AI isn't asked")
        greeter.opening("Alex", 18, evening)
        assertEquals(2, model.calls, "used up: the next one is written now")

        greeter.prepare("Alex", 18, evening)
        greeter.opening("Alex", 19, evening)
        assertEquals(4, model.calls, "another hour: not the prepared one")
        greeter.prepare("Alex", 18, evening)
        clock += Greeter.PREPARED_FOR_MS + 1
        greeter.opening("Alex", 18, evening)
        assertEquals(6, model.calls, "too old: written afresh")
    }
}
