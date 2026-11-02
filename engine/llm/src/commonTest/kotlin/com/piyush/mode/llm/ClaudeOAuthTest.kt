package com.piyush.mode.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.*

class ClaudeOAuthTest {
    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")
    private val tools = Json.parseToJsonElement("""[{"type":"function","function":{"name":"tap","description":"Tap","parameters":{"type":"object","properties":{"x":{"type":"number"}}}}}]""").jsonArray

    @Test fun loginBindsTheCodeToThisAttemptAndExpires() = runTest {
        var calls = 0
        val http = HttpClient(MockEngine {
            calls++
            respond("""{"access_token":"access","refresh_token":"refresh","expires_in":3600}""", headers = jsonHeaders)
        })
        val auth = ClaudeOAuth(http)
        val start = auth.start(now = 100)
        assertTrue("code_challenge_method=S256" in start.url)
        assertTrue("${ClaudeOAuth.CLIENT_ID}" in start.url)
        assertFailsWith<IllegalArgumentException> { auth.exchange("code#wrong-state", start, now = 101) }
        assertFailsWith<IllegalArgumentException> { auth.exchange("code", start, now = 101) }
        assertFailsWith<IllegalArgumentException> { auth.exchange("code#${start.state}", start, now = 100 + ClaudeOAuth.LOGIN_MAX_AGE_MS + 1) }
        assertEquals(0, calls)
        assertEquals("access", auth.exchange("code#${start.state}", start, now = 101).accessToken)
        assertEquals(1, calls)
        http.close()
    }

    @Test fun refreshPreservesAnUnrotatedRefreshTokenAndRedactsErrors() = runTest {
        var fail = false
        val http = HttpClient(MockEngine {
            if (fail) respond("secret-token-that-must-not-appear", HttpStatusCode.BadRequest)
            else respond("""{"access_token":"new","expires_in":3600}""", headers = jsonHeaders)
        })
        val auth = ClaudeOAuth(http)
        val credentials = ClaudeOAuth.Credentials("old", "keep-refresh", 0)
        assertEquals("keep-refresh", auth.refresh(credentials).refreshToken)
        fail = true
        val error = assertFailsWith<LlmFailed> { auth.refresh(credentials) }
        assertFalse(error.message.orEmpty().contains("secret-token"))
        http.close()
    }

    @Test fun screenshotsToolsAndCacheBoundariesSurviveTranslation() {
        val request = ClaudeOAuthClient.request(ClaudeOAuthClient.MAIN_MODEL, listOf(
            ChatMessage("system", listOf(ChatMessage.Part.Text("rules", cacheAfter = true))),
            ChatMessage("user", listOf(ChatMessage.Part.Text("task", true), ChatMessage.Part.Text("screen"), ChatMessage.Part.Image(byteArrayOf(1, 2, 3)))),
        ), tools)
        assertEquals("claude-haiku-5-5", request["model"]!!.jsonPrimitive.content)
        assertEquals(2, request["system"]!!.jsonArray.size)
        val user = request["messages"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        assertNotNull(user[0].jsonObject["cache_control"])
        assertEquals("AQID", user[2].jsonObject["source"]!!.jsonObject["data"]!!.jsonPrimitive.content)
        assertEquals("image/jpeg", user[2].jsonObject["source"]!!.jsonObject["media_type"]!!.jsonPrimitive.content)
        assertEquals("tap", request["tools"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
        assertNotNull(request["tools"]!!.jsonArray.single().jsonObject["input_schema"])
        assertFailsWith<IllegalArgumentException> { ClaudeOAuthClient.request("openai/model", emptyList(), tools) }
        assertNull(request["output_config"], "no effort chosen: Claude's own default")
    }

    @Test fun theChosenEffortIsSentAndRoomIsLeftForThinking() {
        val request = ClaudeOAuthClient.request("claude-sonnet-5-5@high", listOf(ChatMessage("user", "hi")), tools)
        assertEquals("claude-sonnet-5-5", request["model"]!!.jsonPrimitive.content)
        assertEquals("high", request["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content)
        assertTrue(request["max_tokens"]!!.jsonPrimitive.int >= 16_384, "thinking counts against max_tokens")
        val max = ClaudeOAuthClient.request("claude-opus-5-5@max", emptyList(), tools)
        assertEquals("max", max["output_config"]!!.jsonObject["effort"]!!.jsonPrimitive.content, "any effort the model listed is sent")
        assertTrue(max["max_tokens"]!!.jsonPrimitive.int > request["max_tokens"]!!.jsonPrimitive.int, "more room to think at max")
    }

    @Test fun theAccountsModelListKeepsEveryModelAndTheEffortsEachTakes() {
        val root = Json.parseToJsonElement(
            """{"data":[
              {"id":"claude-opus-5-5","display_name":"Claude Opus 5.5","capabilities":{"effort":{"supported":true,"low":{"supported":true},"medium":{"supported":true},"high":{"supported":true},"xhigh":{"supported":true},"max":{"supported":true}}}},
              {"id":"claude-sonnet-5-5","display_name":"Claude Sonnet 5.5","capabilities":{"effort":{"supported":true,"max":{"supported":true},"low":{"supported":true},"high":{"supported":true},"medium":{"supported":false}}}},
              {"id":"claude-3-5-haiku-20241022","display_name":"Claude Haiku 3.5","capabilities":{"effort":{"supported":false}}},
              {"id":"claude-haiku-5-5"}
            ],"has_more":false}""",
        ).jsonObject
        val models = ClaudeOAuthClient.parseModels(root)
        assertEquals(listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-3-5-haiku-20241022", "claude-haiku-5-5"), models.map { it.id }, "nothing trimmed, Anthropic's order")
        assertEquals(listOf("low", "medium", "high", "xhigh", "max"), models[0].efforts)
        assertEquals(listOf("low", "high", "max"), models[1].efforts, "only those marked supported, lowest first")
        assertEquals(emptyList(), models[2].efforts, "takes no effort")
        assertNull(models[3].efforts, "not said: Mode offers the common ones")
        assertEquals("Claude Opus 5.5", models[0].name)
    }

    @Test fun aModelThatNeedsCreditsSaysSoAndOtherFieldsAreKeptForTheLog() {
        assertTrue(ClaudeOAuthClient.needsCredits("""{"error":{"message":"Your credit balance is too low to access the Anthropic API."}}"""))
        assertFalse(ClaudeOAuthClient.needsCredits("""{"error":{"message":"model not found"}}"""))
        val m = ClaudeOAuthClient.parseModels(Json.parseToJsonElement("""{"data":[{"id":"claude-fable-5-1","type":"model","billing":"api_credits"}]}""").jsonObject)
        assertEquals(listOf("billing=api_credits"), m.single().otherFields)
    }

    @Test fun toolArgumentsAndTokenUsageAreReturnedToTheAgent() {
        val result = ClaudeOAuthClient.parse("""{"content":[{"type":"text","text":"Next"},{"type":"tool_use","name":"tap","input":{"x":42}}],"usage":{"input_tokens":10,"output_tokens":3,"cache_read_input_tokens":20,"cache_creation_input_tokens":5}}""")
        assertEquals("tap", result.toolCall!!.name)
        assertEquals(42, result.toolCall!!.arguments["x"]!!.jsonPrimitive.int)
        assertEquals(35, result.usage.promptTokens)
        assertEquals(20, result.usage.cachedTokens)
        assertEquals("Next", result.text)
        assertFailsWith<LlmFailed> { ClaudeOAuthClient.parse("""{"content":[{"type":"tool_use","name":"tap","input":{}},{"type":"tool_use","name":"tap","input":{}}]}""") }
    }

    @Test fun anUnauthorizedRequestRefreshesOnceWithoutPaidFallback() = runTest {
        var requests = 0
        val refreshFlags = mutableListOf<Boolean>()
        val http = HttpClient(MockEngine { request ->
            requests++
            assertEquals(if (requests == 1) "Bearer old" else "Bearer new", request.headers[HttpHeaders.Authorization])
            assertTrue((request.body as TextContent).text.contains("input_schema"))
            if (requests == 1) respond("{}", HttpStatusCode.Unauthorized)
            else respond("""{"content":[{"type":"tool_use","name":"tap","input":{"x":3}}]}""", headers = jsonHeaders)
        })
        val client = ClaudeOAuthClient(http, { force -> refreshFlags += force; if (force) "new" else "old" })
        assertEquals("tap", client.chat(ClaudeOAuthClient.MAIN_MODEL, listOf(ChatMessage("user", "tap")), tools).toolCall!!.name)
        assertEquals(listOf(false, true), refreshFlags)
        assertEquals(2, requests)
        http.close()
    }
}
