package com.piyush.mode.feature.settings.domain

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.llm.ModelProvider

/** When Mode asks before a tap that can't be undone (send, pay, delete…). */
public enum class RiskConfirmation {
    /** Only for the riskiest taps; the user's preference: as few questions as possible. */
    OnlyVeryRisky,
    Always,
    Never,
}

/** Everything the user can set, typed and already validated. */
public data class ModeSettings(
    val model: String = AgentConfig.DEFAULT_MODEL,
    /** Used when the main model gets stuck; null never steps up. */
    val strongModel: String? = AgentConfig.DEFAULT_STRONG_MODEL,
    /** For reflection and summaries; null uses the main model. */
    val reflectionModel: String? = null,
    val maxSteps: Int = DEFAULT_MAX_STEPS,
    val maxCostPerTaskUsd: Double = DEFAULT_MAX_COST_USD,
    /** Null: no daily cap. */
    val dailyCapUsd: Double? = null,
    val learnFromTasks: Boolean = true,
    val askBeforeRisky: RiskConfirmation = RiskConfirmation.OnlyVeryRisky,
    /** What Mode calls the user; null until setup asks. */
    val userName: String? = null,
    /** First-run setup finished (or skipped); the app opens on Home after that. */
    val onboardingDone: Boolean = false,
    val hasApiKey: Boolean = false,
    val provider: ModelProvider = ModelProvider.OpenRouter,
    /** Derived from the credential store, never persisted as a preference. */
    val hasClaudeAccount: Boolean = false,
    /** Signed in with ChatGPT; derived from the Keychain, never a preference. */
    val hasChatGptAccount: Boolean = false,
) {
    public val hasModelAccess: Boolean get() = when (provider) {
        ModelProvider.OpenRouter -> hasApiKey
        ModelProvider.ClaudeOAuth -> hasClaudeAccount
        ModelProvider.ChatGpt -> hasChatGptAccount
    }
    /** The same settings with every value brought into range. */
    public fun validated(): ModeSettings = copy(
        model = model.trim().takeIf { ProviderModels.accepts(provider, it) } ?: ProviderModels.main(provider),
        strongModel = strongModel?.trim()?.takeIf { it.isNotBlank() && ProviderModels.accepts(provider, it) },
        reflectionModel = reflectionModel?.trim()?.takeIf { it.isNotBlank() && ProviderModels.accepts(provider, it) },
        maxSteps = maxSteps.coerceIn(STEPS_RANGE),
        maxCostPerTaskUsd = maxCostPerTaskUsd.coerceIn(COST_RANGE),
        dailyCapUsd = dailyCapUsd?.coerceIn(DAILY_CAP_RANGE),
        userName = userName?.trim()?.ifBlank { null },
    )

    /** What the agent runs with. */
    public fun agentConfig(): AgentConfig = AgentConfig(
        model = model,
        strongModel = strongModel,
        maxSteps = maxSteps,
        maxCostUsd = maxCostPerTaskUsd.takeIf { provider.reportsCurrencyCost },
        provider = provider,
        // The strong model is also the brain: it briefs the hands before the first tap.
        brainModel = strongModel,
    )

    public companion object {
        public const val DEFAULT_MAX_STEPS: Int = 30
        /** Room for the brain's brief plus the steps (raised from 0.05 with the brain). */
        public const val DEFAULT_MAX_COST_USD: Double = 0.10
        /** The old default: a stored value equal to it was never chosen, so it moves to the new one. */
        public const val OLD_DEFAULT_MAX_COST_USD: Double = 0.05
        public val STEPS_RANGE: IntRange = 1..200
        public val COST_RANGE: ClosedFloatingPointRange<Double> = 0.005..5.0
        public val DAILY_CAP_RANGE: ClosedFloatingPointRange<Double> = 0.01..100.0
    }
}
