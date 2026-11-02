package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.settings.ProviderDetailPresenter
import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.feature.settings.data.ClaudeAccount
import com.piyush.mode.llm.ChatGptAuth
import com.piyush.mode.testing.InMemorySecretStore
import com.piyush.mode.llm.ClaudeAccountApi
import com.piyush.mode.llm.ClaudeOAuth
import com.piyush.mode.llm.OpenRouterAccountApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class ClaudeAccountPageTest {
    @Test
    fun theAccountIsKeptAndOnlyTheUsageIsAskedAgain() = runTest {
        val f = Fixture(this)
        f.settings.claudeCredentials.save(ClaudeOAuth.Credentials("access", "refresh", Long.MAX_VALUE))
        val asked = mutableListOf<String>()
        var used = 34.0
        val json = headersOf("Content-Type", "application/json")
        val http = HttpClient(MockEngine { req ->
            asked += req.url.encodedPath
            when (req.url.encodedPath) {
                "/api/oauth/profile" -> respond("""{"account":{"email":"p@example.com","display_name":"Piyush","has_claude_max":true}}""", headers = json)
                "/api/oauth/usage" -> respond("""{"five_hour":{"utilization":$used,"resets_at":null}}""", headers = json)
                else -> respond("", HttpStatusCode.NotFound)
            }
        })
        val account = ClaudeAccount(f.settings.claudeCredentials, ClaudeOAuth(http))
        val chatGpt = ChatGptAccount(ChatGptAuth(HttpClient(MockEngine { respond("") })), InMemorySecretStore())
        fun page() = ProviderDetailPresenter(
            backgroundScope, "claude_oauth", f.settings, chatGpt, account, ClaudeAccountApi(http), OpenRouterAccountApi(http), f.runs, f.catalog, f.clock,
        )

        // First visit: the profile and the usage are asked, and kept.
        val first = page().state.awaitState { it.loaded && it.email != null }
        assertEquals("Max", first.plan)
        assertEquals(listOf("/api/oauth/profile", "/api/oauth/usage"), asked)

        // Next visit: the kept account and last usage show at once; only the usage is asked again.
        asked.clear()
        used = 50.0
        val next = page()
        val fresh = next.state.awaitState { it.limits.firstOrNull()?.usedFraction == 0.5 }
        assertEquals("Piyush", fresh.name)
        assertEquals(listOf("/api/oauth/usage"), asked)

        // Pull to refresh asks for everything.
        asked.clear()
        used = 60.0
        next.refresh()
        next.state.awaitState { it.limits.firstOrNull()?.usedFraction == 0.6 }
        assertEquals(setOf("/api/oauth/profile", "/api/oauth/usage"), asked.toSet())
    }
}
