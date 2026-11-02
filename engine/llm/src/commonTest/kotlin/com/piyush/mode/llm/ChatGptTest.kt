package com.piyush.mode.llm

import io.ktor.client.engine.mock.respond
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChatGptTest {
    private val auth = ChatGptAuth(io.ktor.client.HttpClient(io.ktor.client.engine.mock.MockEngine { respond("") }))

    @Test
    fun theFirstSignInRegistersModeAndLaterOnesUseTheKeptClientId() {
        val first = auth.start("http://127.0.0.1:1455/auth/callback", clientId = null, hostId = "host-1")
        assertTrue(first.url.startsWith("https://auth.openai.com/api/accounts/authorize?client_id=dynamic_agent_client&agent_name_hint=Mode&ext_agent_host_id=host-1"), first.url)
        assertTrue("scope=openid%20profile%20email%20offline_access%20resource.invoke%20chatgpt.tokens.use.direct" in first.url, first.url)
        assertTrue("code_challenge_method=S256" in first.url && "resource=https%3A%2F%2Fapi.openai.com%2Fv1" in first.url)
        val later = auth.start("http://127.0.0.1:1455/auth/callback", clientId = "oaiapp_123", hostId = "host-1")
        assertTrue("client_id=oaiapp_123" in later.url && "agent_name_hint" !in later.url)
    }

    @Test
    fun theCallbackGivesTheCodeAndTheNewClientIdOnlyForThisAttempt() {
        val start = auth.start("http://127.0.0.1:1455/auth/callback", null, "h")
        val cb = auth.callback("http://127.0.0.1:1455/auth/callback?code=abc%2B1&client_id=oaiapp_9&state=${start.state}", start.state)
        assertEquals("abc+1", cb.code)
        assertEquals("oaiapp_9", cb.clientId)
        assertFailsWith<LlmFailed> { auth.callback("http://x/auth/callback?code=abc&state=other", start.state) }
        val denied = assertFailsWith<LlmFailed> { auth.callback("http://x/auth/callback?error=access_denied&state=${start.state}", start.state) }
        assertTrue("cancelled" in denied.message!!)
    }

    @Test
    fun tokensSayWhetherThePlanMayBeUsed() {
        val t = ChatGptAuth.parseTokens(Json.parseToJsonElement("""{"access_token":"a","refresh_token":"r","expires_in":3600,"scope":"openid offline_access chatgpt.tokens.use.direct"}""").jsonObject)
        assertTrue(t.canUsePlan)
        assertEquals(3600, t.expiresIn)
        assertFalse(ChatGptAuth.parseTokens(Json.parseToJsonElement("""{"access_token":"a","scope":"openid"}""").jsonObject).canUsePlan)
    }

    @Test
    fun theRequestFollowsThePlansRules() {
        val tools = Json.parseToJsonElement("""[{"type":"function","function":{"name":"tap","description":"Tap","parameters":{"type":"object"}}}]""") as JsonArray
        val body = ResponsesClient.request(
            "gpt-6.1-terra",
            listOf(ChatMessage("system", "RULES"), ChatMessage("user", "the screen", byteArrayOf(1, 2, 3))),
            tools,
        )
        assertEquals("false", body["store"].toString())
        assertEquals("true", body["stream"].toString())
        assertEquals("RULES", body["instructions"]!!.jsonPrimitive.content)
        assertFalse("temperature" in body || "max_output_tokens" in body)
        val content = body["input"]!!.jsonArray.single().jsonObject["content"]!!.jsonArray
        assertEquals(listOf("input_text", "input_image"), content.map { it.jsonObject["type"]!!.jsonPrimitive.content })
        assertTrue(content[1].jsonObject["image_url"]!!.jsonPrimitive.content.startsWith("data:image/jpeg;base64,"))
        val tool = body["tools"]!!.jsonArray.single().jsonObject
        assertEquals("tap", tool["name"]!!.jsonPrimitive.content)
        assertEquals("required", body["tool_choice"]!!.jsonPrimitive.content)
    }

    @Test
    fun theStreamGivesTheToolCallAndUsageAtNoCost() {
        val stream = """
            event: response.created
            data: {"type":"response.created","response":{"id":"r1"}}

            event: response.completed
            data: {"type":"response.completed","response":{"output":[{"type":"function_call","name":"tap","arguments":"{\"id\":7,\"reason\":\"Book\"}"}],"usage":{"input_tokens":1200,"output_tokens":30,"input_tokens_details":{"cached_tokens":1000}}}}
        """.trimIndent()
        val r = ResponsesClient.parse(stream)
        assertEquals("tap", r.toolCall!!.name)
        assertEquals("7", r.toolCall!!.arguments["id"].toString())
        assertEquals(1200, r.usage.promptTokens)
        assertEquals(1000, r.usage.cachedTokens)
        assertEquals(0.0, r.usage.costUsd)
        assertNull(ResponsesClient.parse("""data: {"type":"response.completed","response":{"output":[{"type":"message","content":[{"type":"output_text","text":"hi"}]}]}}""").toolCall)
    }

    @Test
    fun thePlansErrorsAreSaidPlainly() {
        assertEquals(ResponsesClient.SHARE_USED_UP, ResponsesClient.failure(429, """{"code":"subscription_sharing_usage_limit_exceeded"}""").message)
        assertEquals(ResponsesClient.NOT_ELIGIBLE, ResponsesClient.failure(403, "").message)
        assertEquals(ResponsesClient.SIGN_IN_AGAIN, ResponsesClient.failure(401, "").message)
        val midStream = assertFailsWith<LlmFailed> {
            ResponsesClient.parse("""data: {"type":"response.failed","response":{"error":{"code":"subscription_sharing_usage_limit_exceeded"}}}""")
        }
        assertEquals(ResponsesClient.SHARE_USED_UP, midStream.message)
    }
}
