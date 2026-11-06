package com.piyush.mode.feature.tasks

import com.piyush.mode.feature.tasks.presentation.Narrator
import com.piyush.mode.llm.ChatResult
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.llm.Usage
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NarratorTest {
    private fun model(line: String, seen: MutableList<String> = mutableListOf()) = LlmClient { _, messages, _ ->
        seen += messages.last().text
        ChatResult(ToolCall("say", buildJsonObject { put("line", JsonPrimitive(line)) }), "", Usage(100, 10, 0.0))
    }
    private val quick = { "gpt-6.1-terra@low" to ModelProvider.ChatGpt }

    @Test
    fun updatesWaitForProgressAndKeepTheirDistance() {
        val p = Narrator.Pacing(firstAfterMs = 8_000, gapMs = 15_000, max = 2)
        assertFalse(p.due(5_000, "1|Find your usual"), "too early")
        assertTrue(p.due(9_000, "1|Find your usual"))
        p.said(9_000, "1|Find your usual")
        assertFalse(p.due(30_000, "1|Find your usual"), "nothing new")
        assertFalse(p.due(15_000, "2|Add it to the cart"), "too soon after the last")
        assertTrue(p.due(25_000, "2|Add it to the cart"))
        p.said(25_000, "2|Add it to the cart")
        assertFalse(p.due(60_000, "3|Pay"), "at most two")
    }

    @Test
    fun theLineIsTheModelsOwnWithWhatIsDoneAndWhatWasAlreadySaid() = runTest {
        val seen = mutableListOf<String>()
        val line = Narrator(model("Found your usual, adding it now.", seen), quick)
            .line("Order my usual from Swiggy", listOf("Open Swiggy"), "Add the usual to the cart", listOf("On it, Piyush."), "Piyush",
                recent = listOf("search for Paneer Tikka Bowl"))
        assertEquals("Found your usual, adding it now.", line)
        assertTrue("DONE SO FAR:\n- Open Swiggy" in seen.single())
        assertTrue("YOU ALREADY SAID:\n- On it, Piyush." in seen.single())
        assertTrue("LAST STEPS:\n- search for Paneer Tikka Bowl" in seen.single())
    }

    @Test
    fun nothingNewMeansModeStaysQuiet() = runTest {
        assertNull(Narrator(model("nothing"), quick).line("Turn on dark mode", emptyList(), "Open Settings", emptyList(), null))
        assertNull(Narrator(model("x"), { null }).line("Turn on dark mode", emptyList(), null, emptyList(), null))
    }

    @Test
    fun updatesCanShareWhatTheAgentFoundNotJustThatItIsStillWorking() = runTest {
        val seen = mutableListOf<String>()
        Narrator(model("Sonali messaged you five times today.", seen), quick)
            .line("Catch me up on WhatsApp and Gmail", emptyList(), "Read unread chats", listOf("Checking your WhatsApp now."), "Piyush",
                found = listOf("WhatsApp shows 113 unread chats.", "Sonali sent five messages today."))
        assertTrue("WHAT YOU FOUND SO FAR (newest last):\n- WhatsApp shows 113 unread chats.\n- Sonali sent five messages today." in seen.single())
    }
}
