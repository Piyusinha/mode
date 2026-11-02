package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.settings.ProvidersPresenter
import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.ProviderModels
import com.piyush.mode.llm.ChatGptAuth
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.OpenRouterAuth
import com.piyush.mode.testing.InMemoryKeyValueStore
import com.piyush.mode.testing.InMemorySecretStore
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ProvidersTest {
    private val http = HttpClient(MockEngine { respond("") })
    private val secrets = InMemorySecretStore()
    private val settings = SettingsRepository(secrets, InMemoryKeyValueStore())
    private val chatGpt = ChatGptAccount(ChatGptAuth(http), secrets, onChange = { settings.accountsChanged() })

    private fun presenter(scope: kotlinx.coroutines.CoroutineScope) = ProvidersPresenter(scope, settings, chatGpt, OpenRouterAuth(http), null, null)

    @Test
    fun severalCanBeConnectedAndThePickedOneIsUsed() = runTest {
        val p = presenter(backgroundScope)
        assertEquals(listOf("chatgpt", "openrouter"), p.state.awaitState { it.rows.isNotEmpty() }.rows.map { it.id }, "ChatGPT first")

        p.saveOpenRouterKey("sk-or-v1-abcdefghijklmnop1234")
        val withKey = p.state.awaitState { s -> s.rows.any { it.id == "openrouter" && it.connected } }
        assertEquals("openrouter", withKey.active)
        assertEquals("1234", withKey.rows.single { it.id == "openrouter" }.keyEnding)

        // Signed in with ChatGPT too (the account keeps its tokens in the Keychain).
        secrets.set(ChatGptAccount.CONNECTED_KEY, "refresh")
        settings.accountsChanged()
        p.select("chatgpt")
        val both = p.state.awaitState { it.active == "chatgpt" }
        assertEquals(2, both.connectedCount)
        assertTrue(both.activeConnected)
        assertEquals(ProviderModels.CHATGPT_MAIN, settings.settings.value.model, "ChatGPT's own model")
    }

    @Test
    fun eachProviderKeepsItsOwnModelsAndDisconnectingHandsOver() = runTest {
        val p = presenter(backgroundScope)
        p.saveOpenRouterKey("sk-or-v1-abcdefghijklmnop1234")
        settings.update { it.copy(model = "qwen/qwen3.7-flash") }
        secrets.set(ChatGptAccount.CONNECTED_KEY, "refresh")
        settings.accountsChanged()
        p.select("chatgpt")
        p.select("openrouter")
        assertEquals("qwen/qwen3.7-flash", settings.settings.value.model, "OpenRouter's choice came back")

        p.select("chatgpt")
        p.disconnect("chatgpt")
        val after = p.state.awaitState { s -> s.rows.any { it.id == "chatgpt" && !it.connected } }
        assertEquals("openrouter", after.active, "the connected one took over")
    }

    @Test
    fun chatGptModelsFromThePlanOrTheDefaults() {
        assertEquals(listOf(ProviderModels.CHATGPT_MAIN, ProviderModels.CHATGPT_STRONG), ProviderModels.chatGpt(emptyList()).map { it.id })
        val plan = ProviderModels.chatGpt(listOf("gpt-6.1-luna", "gpt-6.1-terra", "claude-x", "openai/gpt"))
        assertEquals(listOf("gpt-6.1-terra", "gpt-6.1-luna"), plan.map { it.id }, "the default first; foreign ids left out")
        assertEquals("GPT-6.1 Terra", ProviderModels.displayName("gpt-6.1-terra"))
        assertTrue(ProviderModels.accepts(ModelProvider.ChatGpt, "gpt-6.1-sol"))
        assertFalse(ProviderModels.accepts(ModelProvider.ChatGpt, "anthropic/claude-haiku-5.5"))
    }

    @Test fun staleOpenRouterLoginCannotReplaceAPastedKeyOrReconnectAfterDisconnect() = runTest {
        for (disconnect in listOf(false, true)) {
            val entered = kotlinx.coroutines.CompletableDeferred<Unit>()
            val release = kotlinx.coroutines.CompletableDeferred<Unit>()
            val client = HttpClient(MockEngine {
                entered.complete(Unit)
                release.await()
                respond("""{"key":"stale-oauth-key"}""")
            })
            val p = ProvidersPresenter(backgroundScope, settings, chatGpt, OpenRouterAuth(client), null, null)
            settings.apiKey = "original-key"
            p.startOpenRouter("mode://openrouter")
            p.finishOpenRouter("mode://openrouter?code=old-code")
            runCurrent()
            entered.await()
            if (disconnect) p.disconnect("openrouter") else p.saveOpenRouterKey("replacement-key")
            release.complete(Unit)
            runCurrent()
            assertEquals(if (disconnect) null else "replacement-key", settings.apiKey)
            p.close()
            client.close()
        }
    }

    @Test fun cancelledOpenRouterCallbackCannotBeReplayed() = runTest {
        val client = HttpClient(MockEngine { error("cancelled attempt must not exchange a code") })
        val p = ProvidersPresenter(backgroundScope, settings, chatGpt, OpenRouterAuth(client), null, null)
        settings.apiKey = "original-key"
        p.startOpenRouter("mode://openrouter")
        p.finishOpenRouter(null)
        p.finishOpenRouter("mode://openrouter?code=late-code")
        runCurrent()
        assertEquals("original-key", settings.apiKey)
        p.close()
        client.close()
    }

    @Test fun disconnectOutsideTheProviderListInvalidatesItsPendingCallback() = runTest {
        val client = HttpClient(MockEngine { error("disconnected login must not be exchanged") })
        val p = ProvidersPresenter(backgroundScope, settings, chatGpt, OpenRouterAuth(client), null, null)
        p.startOpenRouter("mode://openrouter")
        settings.apiKey = null // The provider detail screen also disconnects through this setter.
        p.finishOpenRouter("mode://openrouter?code=late-code")
        runCurrent()
        assertEquals(null, settings.apiKey)
        assertFalse(p.state.awaitState { !it.openRouterSigningIn }.openRouterSigningIn)
        p.close()
        client.close()
    }
}
