package com.piyush.mode.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonObject
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProviderAccountsTest {
    private fun jwt(payload: String) =
        "e30." + Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(payload.encodeToByteArray()) + ".sig"

    @Test
    fun theChatGptAccountComesFromTheIdToken() {
        val a = Jwt.chatGptAccount(jwt("""{"email":"p@example.com","name":"Alex","https://api.openai.com/auth":{"chatgpt_plan_type":"plus"}}"""))!!
        assertEquals(AccountInfo("p@example.com", "Alex", "Plus"), a)
        assertNull(Jwt.chatGptAccount("not a token"))
        assertEquals(AccountInfo("p@example.com", null, null), Jwt.chatGptAccount(jwt("""{"email":"p@example.com"}""")))
    }

    @Test
    fun claudeProfileAndUsageAreReadAndAMissingAnswerIsNothing() = runTest {
        val json = headersOf("Content-Type", "application/json")
        val http = HttpClient(MockEngine { req ->
            assertEquals("oauth-2025-04-20", req.headers["anthropic-beta"])
            when (req.url.encodedPath) {
                "/api/oauth/profile" -> respond("""{"account":{"email":"p@example.com","display_name":"Alex","has_claude_max":true}}""", headers = json)
                "/api/oauth/usage" -> respond("""{"five_hour":{"utilization":34.0,"resets_at":"2026-11-02T15:40:00Z"},"seven_day":{"utilization":12,"resets_at":null}}""", headers = json)
                else -> respond("", HttpStatusCode.NotFound)
            }
        })
        val api = ClaudeAccountApi(http)
        assertEquals(AccountInfo("p@example.com", "Alex", "Max"), api.profile("t"))
        val usage = api.usage("t")
        assertEquals(listOf(UsageWindow.Kind.Session, UsageWindow.Kind.Week), usage.map { it.kind })
        assertEquals(0.34, usage[0].usedFraction, 1e-9)
        assertTrue(usage[0].resetsAtMillis!! > 0)
        assertNull(usage[1].resetsAtMillis)
        val refusing = ClaudeAccountApi(HttpClient(MockEngine { respond("", HttpStatusCode.Forbidden) }))
        assertNull(refusing.profile("t"))
        assertTrue(refusing.usage("t").isEmpty())
    }

    @Test
    fun openRouterCreditsLeft() = runTest {
        val json = headersOf("Content-Type", "application/json")
        val http = HttpClient(MockEngine { req ->
            if (req.url.encodedPath.endsWith("/key")) respond("""{"data":{"label":"Mode","limit":null,"limit_remaining":null}}""", headers = json)
            else respond("""{"data":{"total_credits":10.0,"total_usage":5.79}}""", headers = json)
        })
        val b = OpenRouterAccountApi(http).balance("k")!!
        assertEquals("Mode", b.label)
        assertEquals(4.21, b.creditsLeft!!, 1e-9)
    }

    @Test
    fun theEffortTravelsWithTheModel() {
        val body = ResponsesClient.request("gpt-6.1-terra@low", listOf(ChatMessage("user", "hi")), kotlinx.serialization.json.JsonArray(emptyList()))
        assertEquals("\"gpt-6.1-terra\"", body["model"].toString())
        assertEquals("\"low\"", body["reasoning"]!!.jsonObject["effort"].toString())
        assertNull(ResponsesClient.request("gpt-6.1-terra", emptyList(), kotlinx.serialization.json.JsonArray(emptyList()))["reasoning"])
    }
}
