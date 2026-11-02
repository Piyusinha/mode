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
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        val plan = ProviderModels.chatGpt(listOf("gpt-6-luna", "gpt-6.1-terra", "claude-x", "openai/gpt"))
        assertEquals(listOf("gpt-6.1-terra", "gpt-6-luna"), plan.map { it.id }, "the default first; foreign ids left out")
        assertEquals("GPT-6.1 Terra", ProviderModels.displayName("gpt-6.1-terra"))
        assertTrue(ProviderModels.accepts(ModelProvider.ChatGpt, "gpt-6.1-sol"))
        assertFalse(ProviderModels.accepts(ModelProvider.ChatGpt, "anthropic/claude-haiku-5.5"))
    }
}
