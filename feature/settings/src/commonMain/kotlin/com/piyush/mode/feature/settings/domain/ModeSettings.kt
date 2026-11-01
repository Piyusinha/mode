package com.piyush.mode.feature.settings.domain

import com.piyush.mode.agent.AgentConfig

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
) {
    /** The same settings with every value brought into range. */
    public fun validated(): ModeSettings = copy(
        model = model.trim().ifBlank { AgentConfig.DEFAULT_MODEL },
        strongModel = strongModel?.trim()?.ifBlank { null },
        reflectionModel = reflectionModel?.trim()?.ifBlank { null },
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
        maxCostUsd = maxCostPerTaskUsd,
    )

    public companion object {
        public const val DEFAULT_MAX_STEPS: Int = 30
        public const val DEFAULT_MAX_COST_USD: Double = 0.05
        public val STEPS_RANGE: IntRange = 1..200
        public val COST_RANGE: ClosedFloatingPointRange<Double> = 0.005..5.0
        public val DAILY_CAP_RANGE: ClosedFloatingPointRange<Double> = 0.01..100.0
    }
}
