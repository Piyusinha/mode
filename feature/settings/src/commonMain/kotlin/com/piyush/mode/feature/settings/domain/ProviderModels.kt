package com.piyush.mode.feature.settings.domain

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.llm.ClaudeOAuthClient
import com.piyush.mode.llm.ModelProvider

/** Provider-specific defaults/catalogue metadata live here, not in UI or the task runner. */
public object ProviderModels {
    public fun main(provider: ModelProvider): String = when (provider) {
        ModelProvider.OpenRouter -> AgentConfig.DEFAULT_MODEL
        ModelProvider.ClaudeOAuth -> ClaudeOAuthClient.MAIN_MODEL
    }

    public fun strong(provider: ModelProvider): String = when (provider) {
        ModelProvider.OpenRouter -> AgentConfig.DEFAULT_STRONG_MODEL
        ModelProvider.ClaudeOAuth -> ClaudeOAuthClient.STRONG_MODEL
    }

    public fun accepts(provider: ModelProvider, model: String): Boolean = when (provider) {
        ModelProvider.OpenRouter -> '/' in model && !model.startsWith("claude-oauth/")
        ModelProvider.ClaudeOAuth -> model.startsWith("claude-") && '/' !in model
    }

    /** Currency cost is unavailable for OAuth; UI must use provider billing metadata, not these placeholders. */
    public val claude: List<ModelOption> = listOf(
        ModelOption(ClaudeOAuthClient.MAIN_MODEL, "Claude Haiku 5.5", 0.0, ModelTag.Recommended),
        ModelOption(ClaudeOAuthClient.STRONG_MODEL, "Claude Sonnet 5.5", 0.0, ModelTag.Strongest),
    )
}
