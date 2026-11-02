package com.piyush.mode.feature.settings.domain

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.llm.ClaudeOAuthClient
import com.piyush.mode.llm.ModelProvider

/** Provider-specific defaults/catalogue metadata live here, not in UI or the task runner. */
public object ProviderModels {
    public fun main(provider: ModelProvider): String = when (provider) {
        ModelProvider.OpenRouter -> AgentConfig.DEFAULT_MODEL
        ModelProvider.ClaudeOAuth -> ClaudeOAuthClient.MAIN_MODEL
        ModelProvider.ChatGpt -> CHATGPT_MAIN
    }

    public fun strong(provider: ModelProvider): String = when (provider) {
        ModelProvider.OpenRouter -> AgentConfig.DEFAULT_STRONG_MODEL
        ModelProvider.ClaudeOAuth -> ClaudeOAuthClient.STRONG_MODEL
        ModelProvider.ChatGpt -> CHATGPT_STRONG
    }

    public fun accepts(provider: ModelProvider, model: String): Boolean = when (provider) {
        ModelProvider.OpenRouter -> '/' in model && !model.startsWith("claude-oauth/")
        ModelProvider.ClaudeOAuth -> model.startsWith("claude-") && '/' !in model
        ModelProvider.ChatGpt -> '/' !in model && !model.startsWith("claude-") && model.isNotBlank()
    }

    /** The defaults until the plan's own list is known (OpenAI names them; the list replaces these). */
    public const val CHATGPT_MAIN: String = "gpt-6.1-terra"
    public const val CHATGPT_STRONG: String = "gpt-6.1-sol"

    /**
     * The models on the user's ChatGPT plan ([known], from the account), or the defaults when the plan
     * has not been asked yet. The defaults are marked; the rest keep the plan's order.
     */
    public fun chatGpt(known: List<String>): List<ModelOption> {
        val ids = known.filter { accepts(ModelProvider.ChatGpt, it) }.ifEmpty { listOf(CHATGPT_MAIN, CHATGPT_STRONG) }
        return ids.map { id ->
            ModelOption(
                id, displayName(id), 0.0,
                when (id) { CHATGPT_MAIN -> ModelTag.Recommended; CHATGPT_STRONG -> ModelTag.Strongest; else -> null },
            )
        }.sortedBy { if (it.tag != null) 0 else 1 }
    }

    /** "gpt-6.1-terra" → "GPT-6.1 Terra". */
    public fun displayName(id: String): String = id.split('-').joinToString(" ") { part ->
        if (part.equals("gpt", true)) "GPT" else part.replaceFirstChar { it.uppercase() }
    }.replace(Regex("""^GPT (\d)"""), "GPT-$1")

    /** Currency cost is unavailable for OAuth; UI must use provider billing metadata, not these placeholders. */
    public val claude: List<ModelOption> = listOf(
        ModelOption(ClaudeOAuthClient.MAIN_MODEL, "Claude Haiku 5.5", 0.0, ModelTag.Recommended),
        ModelOption(ClaudeOAuthClient.STRONG_MODEL, "Claude Sonnet 5.5", 0.0, ModelTag.Strongest),
    )
}
