package com.piyush.mode.feature.memory

import com.piyush.mode.feature.memory.data.Acknowledger
import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.domain.MemoryDraft
import com.piyush.mode.feature.memory.domain.MemoryKind
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

class AcknowledgerTest {
    private class Model(val line: String, val waitMs: Long = 0) : LlmClient {
        var asked: String = ""
        override suspend fun chat(model: String, messages: List<ChatMessage>, tools: kotlinx.serialization.json.JsonArray): ChatResult {
            asked = messages.last().text
            delay(waitMs)
            return ChatResult(ToolCall("say", buildJsonObject { put("line", JsonPrimitive(line)) }), "", Usage(200, 20, 0.0))
        }
    }

    private val memory = MemoryRepository(testDatabase()).also {
        it.save(MemoryDraft(MemoryKind.Fact, "starbucks", "Usual order", "Their usual Starbucks order is an oat latte", "said so"))
    }
    private val fast = { "fast-model" to ModelProvider.OpenRouter }

    @Test
    fun theLineIsTheAIsOwnWordsWithWhatModeKnowsAboutThem() = runTest {
        val model = Model("On it, Alex. Your usual oat latte, coming up.")
        val line = Acknowledger(model, memory, fast).line("Order my usual from Starbucks", "Alex Sinha", hour = 9)

        assertEquals("On it, Alex. Your usual oat latte, coming up.", line)
        assertTrue("THEIR NAME: Alex" in model.asked)
        assertTrue("TIME OF DAY: morning" in model.asked)
        assertTrue("oat latte" in model.asked)
    }

    @Test
    fun aPrivateTaskUsesNothingModeRemembers() = runTest {
        val model = Model("Sure, ordering it now.")
        Acknowledger(model, memory, fast).line("Order my usual from Starbucks", "Alex", hour = 9, private = true)
        assertFalse("oat latte" in model.asked)
    }

    @Test
    fun aSlowAnswerNeverHoldsTheTaskUp() = runTest {
        val slow = Model("Too late.", waitMs = Acknowledger.BUDGET_MS + 500)
        assertNull(Acknowledger(slow, memory, fast).line("Turn on dark mode", "Alex", hour = 20))
    }

    @Test
    fun withoutAModelOrWithAnUnusableLineModeUsesItsOwn() = runTest {
        assertNull(Acknowledger(Model("x"), memory) { null }.line("Turn on dark mode", "Alex", hour = 20))
        val rambling = Model((1..40).joinToString(" ") { "word" })
        assertNull(Acknowledger(rambling, memory, fast).line("Turn on dark mode", "Alex", hour = 20))
        assertEquals("On it, Alex.", Acknowledger(Model("\"On it, Alex.\" 🚀"), memory, fast).line("Turn on dark mode", "Alex", hour = 20))
    }
}
