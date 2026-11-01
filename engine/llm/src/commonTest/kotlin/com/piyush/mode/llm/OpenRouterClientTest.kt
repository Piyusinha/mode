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

    private suspend fun requestFor(model: String, messages: List<ChatMessage>): kotlinx.serialization.json.JsonObject {
        var body = ""
        OpenRouterClient(
            HttpClient(MockEngine { request ->
                body = (request.body as TextContent).text
                respond(ok, HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
            }),
            apiKey = { "k" },
        ).chat(model, messages, JsonArray(emptyList()))
        return Json.parseToJsonElement(body).jsonObject
    }

    private val sectioned = listOf(
        ChatMessage("system", listOf(ChatMessage.Part.Text("rules", cacheAfter = true))),
        ChatMessage(
            "user",
            listOf(
                ChatMessage.Part.Text("task and plan", cacheAfter = true),
                ChatMessage.Part.Text("step 3 and the screen"),
                ChatMessage.Part.Image(byteArrayOf(1, 2, 3)),
            ),
        ),
    )

    @Test
    fun anthropicModelsGetCacheMarkersAtTheEndOfEachStablePart() = runTest {
        val messages = requestFor("anthropic/claude-haiku-5.5", sectioned).getValue("messages") as JsonArray
        val system = messages[0].jsonObject.getValue("content") as JsonArray
        assertEquals("ephemeral", system[0].jsonObject.getValue("cache_control").jsonObject.getValue("type").jsonPrimitive.content)
        val user = messages[1].jsonObject.getValue("content") as JsonArray
        assertEquals(3, user.size)
        assertTrue("cache_control" in user[0].jsonObject, "stable part marked")
        assertFalse("cache_control" in user[1].jsonObject, "per-step part not marked")
        assertEquals("image_url", user[2].jsonObject.getValue("type").jsonPrimitive.content)
    }

    @Test
    fun otherModelsGetTheSameLayoutWithoutMarkers() = runTest {
        val messages = requestFor("qwen/qwen3.7-flash", sectioned).getValue("messages") as JsonArray
        assertEquals("rules", messages[0].jsonObject.getValue("content").jsonPrimitive.content, "plain string when there is nothing to mark")
        val user = messages[1].jsonObject.getValue("content") as JsonArray
        assertTrue(user.none { "cache_control" in it.jsonObject })
        assertEquals("task and plan", user[0].jsonObject.getValue("text").jsonPrimitive.content)
    }

    @Test
    fun aPlainMessageIsSentAsBefore() = runTest {
        val message = ChatMessage("user", "hi", byteArrayOf(9))
        assertEquals("hi", message.text)
        assertEquals(1, message.image!!.size)
        val sent = (requestFor("anthropic/claude-haiku-5.5", listOf(ChatMessage("user", "hi"))).getValue("messages") as JsonArray)[0]
        assertEquals("hi", sent.jsonObject.getValue("content").jsonPrimitive.content)
    }

    @Test
    fun cachedTokensAreRead() {
        val result = OpenRouterClient.parse(
            """{"choices":[{"message":{"content":"x"}}],"usage":{"prompt_tokens":3500,"completion_tokens":60,"cost":0.0009,""" +
                """"prompt_tokens_details":{"cached_tokens":2600,"cache_write_tokens":0}}}""",
            "m",
        )
        assertEquals(2600, result.usage.cachedTokens)
        assertEquals(0, result.usage.cacheWriteTokens)
        assertEquals(0, OpenRouterClient.parse(ok, "m").usage.cachedTokens)
    }
}
