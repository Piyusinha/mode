package com.piyush.mode.feature.tasks

import com.piyush.mode.feature.tasks.presentation.ChatTitler
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatTitlerTest {
    private fun titler(answer: String, seen: MutableList<String> = mutableListOf()) = ChatTitler(
        LlmClient { _, messages, _ ->
            seen += messages.last().text
            ChatResult(ToolCall("name_chat", buildJsonObject { put("title", JsonPrimitive(answer)) }), "", Usage(10, 4, 0.0))
        },
    ) { "fast@low" to ModelProvider.ChatGpt }

    @Test
    fun theTitleCoversEverythingAskedInTheChat() = runTest {
        val seen = mutableListOf<String>()
        val title = titler("Dark Mode, Sonali and DoorDash.", seen).title(listOf("Can you turn off dark mode", "Reply to Sonali", "Check my DoorDash meeting"))
        assertEquals("Dark Mode, Sonali and DoorDash", title)
        assertTrue("Reply to Sonali" in seen.single() && "DoorDash" in seen.single(), "every request is sent")
    }

    @Test
    fun aRamblingAnswerIsNotUsed() = runTest {
        assertNull(titler("This chat is about turning off dark mode and also replying to Sonali on WhatsApp").title(listOf("x")))
    }

    @Test
    fun noModelNoTitle() = runTest {
        assertNull(ChatTitler(LlmClient { _, _, _ -> error("no") }) { null }.title(listOf("Turn off Wi-Fi")))
    }
}
