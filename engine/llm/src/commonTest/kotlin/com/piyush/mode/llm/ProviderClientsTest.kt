package com.piyush.mode.llm

import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ProviderClientsTest {
    @Test fun explicitProviderSelectionDoesNotDependOnTheModelName() = runTest {
        val called = mutableListOf<String>()
        fun client(name: String) = LlmClient { _, _, _ -> called += name; ChatResult(null, name, Usage(0, 0, 0.0)) }
        val clients = ProviderClients(mapOf(ModelProvider.OpenRouter to client("router"), ModelProvider.ClaudeOAuth to client("claude")))
        val bound = clients.forProvider(ModelProvider.ClaudeOAuth)
        assertEquals("claude", bound.chat("some-future-native-model-id", emptyList(), JsonArray(emptyList())).text)
        assertEquals(listOf("claude"), called)
    }

    @Test fun missingProviderOrFailedOAuthDoesNotFallBackToOpenRouter() = runTest {
        var routerCalled = false
        val router = LlmClient { _, _, _ -> routerCalled = true; error("should not call") }
        assertFailsWith<LlmFailed> { ProviderClients(mapOf(ModelProvider.OpenRouter to router)).forProvider(ModelProvider.ClaudeOAuth) }
        val oauth = LlmClient { _, _, _ -> throw LlmFailed("sign in again") }
        val clients = ProviderClients(mapOf(ModelProvider.OpenRouter to router, ModelProvider.ClaudeOAuth to oauth))
        assertFailsWith<LlmFailed> { clients.forProvider(ModelProvider.ClaudeOAuth).chat("claude-model", emptyList(), JsonArray(emptyList())) }
        assertFalse(routerCalled)
    }
}
