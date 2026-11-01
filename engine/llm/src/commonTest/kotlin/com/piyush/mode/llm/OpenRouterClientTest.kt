package com.piyush.mode.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OpenRouterClientTest {
    private val ok = """{"choices":[{"message":{"tool_calls":[{"function":{"name":"home","arguments":"{}"}}]}}],"usage":{"cost":0.001}}"""
    private val forcedRejected = """{"error":{"message":"Provider returned error","metadata":{"raw":"tool_choice: type any is not supported for this model.","provider_name":"Anthropic"}}}"""

    private fun client(handler: (String) -> Pair<HttpStatusCode, String>, sent: MutableList<String>) =
        OpenRouterClient(
            HttpClient(MockEngine { request ->
                val body = (request.body as TextContent).text
                sent += Json.parseToJsonElement(body).jsonObject.getValue("tool_choice").jsonPrimitive.content
                val (status, reply) = handler(body)
                respond(reply, status, headersOf("Content-Type", "application/json"))
            }),
            apiKey = { "k" },
        )

    @Test
    fun sonnet55IsAskedWithAutoBecauseItRejectsForcedToolUse() = runTest {
        val sent = mutableListOf<String>()
        client({ HttpStatusCode.OK to ok }, sent).chat("anthropic/claude-sonnet-5.5", listOf(ChatMessage("user", "hi")), JsonArray(emptyList()))
        assertEquals(listOf("auto"), sent)
        assertFalse(OpenRouterClient.forcedToolChoiceAllowed("anthropic/claude-opus-5.5"))
        assertTrue(OpenRouterClient.forcedToolChoiceAllowed("anthropic/claude-haiku-5.5"))
    }

    @Test
    fun aForcedChoice400IsRetriedWithAutoAndRemembered() = runTest {
        val sent = mutableListOf<String>()
        val c = client({ body ->
            if ("\"required\"" in body) HttpStatusCode.BadRequest to forcedRejected else HttpStatusCode.OK to ok
        }, sent)
        val first = c.chat("some/new-model", listOf(ChatMessage("user", "hi")), JsonArray(emptyList()))
        assertEquals("home", first.toolCall!!.name)
        c.chat("some/new-model", listOf(ChatMessage("user", "hi")), JsonArray(emptyList()))
        assertEquals(listOf("required", "auto", "auto"), sent, "retried once, then auto from the start")
    }

    @Test
    fun errorsCarryTheProvidersOwnReason() = runTest {
        val c = client({ HttpStatusCode.BadRequest to forcedRejected }, mutableListOf())
        val e = runCatching { c.chat("anthropic/claude-sonnet-5.5", listOf(ChatMessage("user", "hi")), JsonArray(emptyList())) }.exceptionOrNull()
        assertTrue(e!!.message!!.contains("tool_choice: type any is not supported"), e.message)
    }
}
