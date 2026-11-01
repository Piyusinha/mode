package com.piyush.mode.feature.settings.data

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.core.storage.KeyValueStore
import com.piyush.mode.core.storage.SecretStore
import com.piyush.mode.feature.settings.domain.ModeSettings
import com.piyush.mode.feature.settings.domain.RiskConfirmation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's settings: the OpenRouter key in the [SecretStore] (the Keychain),
 * everything else in [KeyValueStore] settings. Changes are validated before
 * they are saved, and a task reads them when it starts, never mid-task.
 */
public class SettingsRepository(
    private val secrets: SecretStore,
    private val prefs: KeyValueStore,
) {
    private val state = MutableStateFlow(read())
    public val settings: StateFlow<ModeSettings> = state.asStateFlow()

    public var apiKey: String?
        get() = secrets.get(API_KEY)
        set(value) {
            secrets.set(API_KEY, value?.trim())
            state.value = read()
        }

    public val hasApiKey: Boolean get() = !apiKey.isNullOrBlank()

    /** Applies [change], brings every value into range, and saves it. */
    public fun update(change: (ModeSettings) -> ModeSettings) {
        val next = change(state.value).validated()
        prefs.putString(MODEL, next.model)
        // Empty means "never step up", as the first version stored it.
        prefs.putString(STRONG_MODEL, next.strongModel.orEmpty())
        prefs.putString(REFLECTION_MODEL, next.reflectionModel.orEmpty())
        prefs.putString(MAX_STEPS, next.maxSteps.toString())
        prefs.putString(MAX_COST, next.maxCostPerTaskUsd.toString())
        prefs.putString(DAILY_CAP, next.dailyCapUsd?.toString().orEmpty())
        prefs.putString(LEARN, next.learnFromTasks.toString())
        prefs.putString(ASK_BEFORE_RISKY, next.askBeforeRisky.name)
        prefs.putString(USER_NAME, next.userName.orEmpty())
        prefs.putString(ONBOARDING_DONE, next.onboardingDone.toString())
        state.value = read()
    }

    /** What the agent runs with now. */
    public fun agentConfig(): AgentConfig = state.value.agentConfig()

    /**
     * The stored limit. The old default (0.05) stored before the brain existed was never chosen: it
     * moves to the new default once; after that whatever is stored is the user's choice.
     */
    private fun storedMaxCost(): Double? {
        val stored = prefs.getString(MAX_COST)?.toDoubleOrNull()
        if (prefs.getString(MAX_COST_MOVED) != null) return stored
        prefs.putString(MAX_COST_MOVED, "1")
        if (stored != ModeSettings.OLD_DEFAULT_MAX_COST_USD) return stored
        prefs.putString(MAX_COST, ModeSettings.DEFAULT_MAX_COST_USD.toString())
        return ModeSettings.DEFAULT_MAX_COST_USD
    }

    private fun read(): ModeSettings {
        val defaults = ModeSettings()
        val storedStrong = prefs.getString(STRONG_MODEL)
        return ModeSettings(
            model = prefs.getString(MODEL) ?: defaults.model,
            strongModel = if (storedStrong == null) defaults.strongModel else storedStrong.ifBlank { null },
            reflectionModel = prefs.getString(REFLECTION_MODEL)?.ifBlank { null },
            maxSteps = prefs.getString(MAX_STEPS)?.trim()?.toIntOrNull() ?: defaults.maxSteps,
            maxCostPerTaskUsd = storedMaxCost() ?: defaults.maxCostPerTaskUsd,
            dailyCapUsd = prefs.getString(DAILY_CAP)?.toDoubleOrNull(),
            learnFromTasks = prefs.getString(LEARN)?.toBooleanStrictOrNull() ?: defaults.learnFromTasks,
            askBeforeRisky = prefs.getString(ASK_BEFORE_RISKY)
                ?.let { name -> RiskConfirmation.entries.firstOrNull { it.name == name } } ?: defaults.askBeforeRisky,
            userName = prefs.getString(USER_NAME)?.ifBlank { null },
            onboardingDone = prefs.getString(ONBOARDING_DONE)?.toBooleanStrictOrNull() ?: defaults.onboardingDone,
            hasApiKey = !secrets.get(API_KEY).isNullOrBlank(),
        ).validated()
    }

    private companion object {
        // The first four names are kept from the first version, so existing installs keep their settings.
        const val API_KEY = "openrouter_api_key"
        const val MODEL = "agent_model"
        const val STRONG_MODEL = "agent_strong_model"
        const val MAX_STEPS = "agent_max_steps"
        const val REFLECTION_MODEL = "reflection_model"
        const val MAX_COST = "max_cost_per_task_usd"
        /** Set once the old default limit was moved to the new one, so a limit chosen later is kept. */
        const val MAX_COST_MOVED = "max_cost_moved_to_brain_default"
        const val DAILY_CAP = "daily_cap_usd"
        const val LEARN = "learn_from_tasks"
        const val ASK_BEFORE_RISKY = "ask_before_risky"
        const val USER_NAME = "user_name"
        const val ONBOARDING_DONE = "onboarding_done"
    }
}
