package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.settings.ProviderDetailPresenter
import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.feature.settings.data.ClaudeAccount
import com.piyush.mode.llm.ChatGptAuth
import com.piyush.mode.llm.ClaudeAccountApi
import com.piyush.mode.llm.ClaudeOAuth
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.OpenRouterAccountApi
import com.piyush.mode.testing.InMemorySecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNull

class ProviderAccountSwitchTest {
    @Test fun oldClaudeProfileRequestCannotRepopulateAReplacementAccountsCache() = runTest {
        val f = Fixture(this)
        val store = f.settings.claudeCredentials
        store.save(ClaudeOAuth.Credentials("old-access", "old-refresh", Long.MAX_VALUE))
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val http = HttpClient(MockEngine { request ->
            if (request.url.encodedPath.endsWith("profile")) {
                entered.complete(Unit)
                release.await()
                respond("""{"account":{"email":"old@example.com"}}""")
            } else respond("""{"five_hour":{"utilization":50}}""")
        })
        val p = ProviderDetailPresenter(backgroundScope, ModelProvider.ClaudeOAuth.id, f.settings,
            ChatGptAccount(ChatGptAuth(http), InMemorySecretStore()), ClaudeAccount(store, ClaudeOAuth(http)),
            ClaudeAccountApi(http), OpenRouterAccountApi(http), f.runs, f.catalog, f.clock)
        testScheduler.runCurrent()
        entered.await()
        store.save(ClaudeOAuth.Credentials("new-access", "new-refresh", Long.MAX_VALUE), newSignIn = true)
        release.complete(Unit)
        testScheduler.runCurrent()
        assertNull(store.profile)
        assertNull(store.usage)
        assertNull(store.usageAt)
        p.close()
        http.close()
    }
}
