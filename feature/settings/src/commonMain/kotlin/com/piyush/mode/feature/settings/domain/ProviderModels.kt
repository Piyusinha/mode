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

    public fun accepts(provider: ModelProvider, model: String): Boolean {
        val base = base(model)
        // Only plan models carry an effort ("gpt-6.1-terra@low", "claude-haiku-5-5@medium").
        if (base != model && (!supportsEffort(provider) || effort(model) !in EFFORTS)) return false
        return when (provider) {
            ModelProvider.OpenRouter -> '/' in base && !base.startsWith("claude-oauth/")
            ModelProvider.ClaudeOAuth -> base.startsWith("claude-") && '/' !in base
            ModelProvider.ChatGpt -> '/' !in base && !base.startsWith("claude-") && base.isNotBlank()
        }
    }

    /** Reasoning effort, picked per model and role, for providers that take it. */
    public val EFFORTS: List<String> = listOf("low", "medium", "high")

    public fun supportsEffort(provider: ModelProvider): Boolean = provider == ModelProvider.ChatGpt || provider == ModelProvider.ClaudeOAuth

    /** The model without its effort: "gpt-6.1-terra@low" → "gpt-6.1-terra". */
    public fun base(id: String): String = id.substringBefore('@')

    public fun effort(id: String): String? = id.substringAfter('@', "").ifEmpty { null }

    /**
     * The effort [id] runs at: the one chosen, or, with none, the model's own default where it is known
     * (Claude Haiku 5.5 runs at medium and Sonnet 5.5 at high when no effort is sent).
     */
    public fun effortUsed(id: String): String? = effort(id) ?: when {
        base(id).startsWith("claude-haiku-") -> "medium"
        base(id).startsWith("claude-sonnet-") -> "high"
        else -> null
    }

    public fun withEffort(id: String, effort: String?): String = base(id) + (effort?.let { "@$it" } ?: "")

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
