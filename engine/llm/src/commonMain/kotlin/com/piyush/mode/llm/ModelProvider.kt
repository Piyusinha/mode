package com.piyush.mode.llm

import kotlinx.serialization.json.JsonArray

/** Stable provider identity, independent of model IDs or authentication state. */
enum class ModelProvider(val id: String, val reportsCurrencyCost: Boolean) {
    OpenRouter("openrouter", true),
    ClaudeOAuth("claude_oauth", false);

    companion object {
        fun fromId(id: String?): ModelProvider = entries.firstOrNull { it.id == id } ?: OpenRouter
    }
}

/** Missing providers fail rather than silently changing billing. Resolve once per task. */
class ProviderClients(private val clients: Map<ModelProvider, LlmClient>) : LlmClient {
    override fun forProvider(provider: ModelProvider): LlmClient =
        clients[provider] ?: throw LlmFailed("${provider.id} is not configured.")

    override suspend fun chat(model: String, messages: List<ChatMessage>, tools: JsonArray): ChatResult =
        forProvider(ModelProvider.OpenRouter).chat(model, messages, tools)
}
