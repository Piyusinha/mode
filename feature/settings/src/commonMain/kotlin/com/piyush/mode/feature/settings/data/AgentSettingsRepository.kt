package com.piyush.mode.feature.settings.data

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.core.storage.KeyValueStore
import com.piyush.mode.core.storage.SecretStore
import com.piyush.mode.feature.settings.domain.AgentPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The user's agent settings: the OpenRouter key in the [SecretStore] (the
 * Keychain), everything else in plain [KeyValueStore] settings.
 */
public class AgentSettingsRepository(
    private val secrets: SecretStore,
    private val prefs: KeyValueStore,
) {
    private val mutablePreferences = MutableStateFlow(read())
    public val preferences: StateFlow<AgentPreferences> = mutablePreferences.asStateFlow()

    public var apiKey: String?
        get() = secrets.get(API_KEY)
        set(value) {
            secrets.set(API_KEY, value?.trim())
            refresh()
        }

    public val hasApiKey: Boolean get() = !apiKey.isNullOrBlank()

    public var model: String
        get() = prefs.getString(MODEL) ?: AgentConfig.DEFAULT_MODEL
        set(value) = put(MODEL, value.trim())

    public var strongModel: String
        get() = prefs.getString(STRONG_MODEL) ?: AgentConfig.DEFAULT_STRONG_MODEL
        set(value) = put(STRONG_MODEL, value.trim())

    public var maxSteps: String
        get() = prefs.getString(MAX_STEPS) ?: DEFAULT_MAX_STEPS.toString()
        set(value) = put(MAX_STEPS, value.trim())

    /** What the agent runs with: blanks fall back to defaults, steps are kept between 1 and 200. */
    public fun config(): AgentConfig = AgentConfig(
        model = model.ifBlank { AgentConfig.DEFAULT_MODEL },
        strongModel = strongModel.ifBlank { null },
        maxSteps = maxSteps.toIntOrNull()?.coerceIn(1, 200) ?: DEFAULT_MAX_STEPS,
    )

    private fun put(name: String, value: String) {
        prefs.putString(name, value)
        refresh()
    }

    private fun refresh() {
        mutablePreferences.value = read()
    }

    private fun read() = AgentPreferences(
        model = prefs.getString(MODEL) ?: AgentConfig.DEFAULT_MODEL,
        strongModel = prefs.getString(STRONG_MODEL) ?: AgentConfig.DEFAULT_STRONG_MODEL,
        maxSteps = prefs.getString(MAX_STEPS) ?: DEFAULT_MAX_STEPS.toString(),
        hasApiKey = !secrets.get(API_KEY).isNullOrBlank(),
    )

    private companion object {
        // Stored names are kept from the first version, so existing installs keep their settings.
        const val API_KEY = "openrouter_api_key"
        const val MODEL = "agent_model"
        const val STRONG_MODEL = "agent_strong_model"
        const val MAX_STEPS = "agent_max_steps"
        const val DEFAULT_MAX_STEPS = 30
    }
}
