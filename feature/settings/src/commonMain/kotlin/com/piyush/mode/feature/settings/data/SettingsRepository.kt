package com.piyush.mode.feature.settings.data

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.feature.settings.domain.ProviderModels
import com.piyush.mode.core.storage.KeyValueStore
import com.piyush.mode.core.storage.SecretStore
import com.piyush.mode.feature.settings.domain.ModeSettings
import com.piyush.mode.feature.settings.domain.RiskConfirmation
import com.piyush.mode.feature.settings.domain.SpeechSpeed
import com.piyush.mode.feature.settings.domain.VoiceChoice
import com.piyush.mode.feature.settings.domain.VoiceKind
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
    public val claudeCredentials: ClaudeCredentialStore = ClaudeCredentialStore(secrets) { refreshAccounts() }
    private val state = MutableStateFlow(read())
    public val settings: StateFlow<ModeSettings> = state.asStateFlow()

    /** Changes even on disconnecting an empty key, invalidating pending OpenRouter logins. */
    public var openRouterKeyRevision: Long = 0
        private set

    public var apiKey: String?
        get() = secrets.get(API_KEY)
        set(value) {
            openRouterKeyRevision++
            secrets.set(API_KEY, value?.trim())
            state.value = read()
        }

    public val hasApiKey: Boolean get() = !apiKey.isNullOrBlank()
    public val hasModelAccess: Boolean get() = settings.value.hasModelAccess

    /** Account changes refresh readiness; credentials stay in their own store. */
    private fun refreshAccounts() { state.value = read() }

    public fun hasAccess(provider: ModelProvider): Boolean = when (provider) {
        ModelProvider.OpenRouter -> hasApiKey
        ModelProvider.ClaudeOAuth -> claudeCredentials.connected.value
        ModelProvider.ChatGpt -> hasChatGpt()
    }

    /** A sign-in or sign-out happened outside this repository (ChatGptAccount): readiness changes. */
    public fun accountsChanged() { refreshAccounts() }

    /** After Reset Mode cleared the stores: every setting back to its default, setup not done. */
    public fun reload() {
        openRouterKeyRevision++
        state.value = read()
    }

    private fun hasChatGpt(): Boolean = !secrets.get(ChatGptAccount.CONNECTED_KEY).isNullOrBlank()

    /** [provider]'s phone-control and brain models: the live ones if it is in use, else the ones it keeps. */
    public fun modelsFor(provider: ModelProvider): Pair<String, String?> {
        val s = state.value
        if (s.provider == provider) return s.model to s.strongModel
        val prefix = "provider_${provider.id}_"
        val strong = prefs.getString(prefix + "strong")
        return (prefs.getString(prefix + "main") ?: ProviderModels.main(provider)) to
            (if (strong == null) ProviderModels.strong(provider) else strong.ifBlank { null })
    }

    /** Each provider retains its own model selections, including an explicitly disabled strong model. */
    public fun selectProvider(provider: ModelProvider) {
        val old = state.value
        if (old.provider == provider) return
        saveModels(old)
        val prefix = "provider_${provider.id}_"
        val strong = prefs.getString(prefix + "strong")
        update { it.copy(
            provider = provider,
            model = prefs.getString(prefix + "main") ?: ProviderModels.main(provider),
            strongModel = if (strong == null) ProviderModels.strong(provider) else strong.ifBlank { null },
            reflectionModel = prefs.getString(prefix + "reflection")?.ifBlank { null },
        ) }
    }

    private fun saveModels(settings: ModeSettings) {
        val prefix = "provider_${settings.provider.id}_"
        prefs.putString(prefix + "main", settings.model)
        prefs.putString(prefix + "strong", settings.strongModel.orEmpty())
        prefs.putString(prefix + "reflection", settings.reflectionModel.orEmpty())
    }

    /** Applies [change], brings every value into range, and saves it. */
    public fun update(change: (ModeSettings) -> ModeSettings) {
        val next = change(state.value).validated()
        prefs.putString(PROVIDER, next.provider.id)
        saveModels(next)
        prefs.putString(MODEL, next.model)
        // Empty means "never step up", as the first version stored it.
        prefs.putString(STRONG_MODEL, next.strongModel.orEmpty())
        prefs.putString(REFLECTION_MODEL, next.reflectionModel.orEmpty())
        prefs.putString(MAX_STEPS, next.maxSteps.toString())
        prefs.putString(MAX_COST, next.maxCostPerTaskUsd.toString())
        prefs.putString(DAILY_CAP, next.dailyCapUsd?.toString().orEmpty())
        prefs.putString(LEARN, next.learnFromTasks.toString())
        prefs.putString(ASK_BEFORE_RISKY, next.askBeforeRisky.name)
        prefs.putString(FULL_ACCESS, next.fullAccess.toString())
        // One per line: a word may hold spaces ("just do it").
        prefs.putString(TRIGGER_WORDS, next.triggerWords.joinToString("\n"))
        prefs.putString(READ_CODES, next.readCodes.toString())
        prefs.putString(ASK_ABOVE, next.askAboveAmount?.toString().orEmpty())
        prefs.putString(USER_NAME, next.userName.orEmpty())
        prefs.putString(ONBOARDING_DONE, next.onboardingDone.toString())
        prefs.putString(SPEECH_ORDER, next.speechOrder.joinToString(","))
        prefs.putString(HEARING_ORDER, next.hearingOrder.joinToString(","))
        prefs.putString(TALK_BACK, next.talkBack.toString())
        prefs.putString(SPEAK_RESULT, next.speakResult.toString())
        prefs.putString(SPEECH_SPEED, next.speechSpeed.id)
        prefs.putString(LANGUAGE, next.language)
        prefs.putString(REPLY_IN_SPOKEN, next.replyInSpokenLanguage.toString())
        prefs.putString(SUGGEST_ROUTINES, next.suggestRoutines.toString())
        prefs.putString(BARGE_IN, next.bargeIn.toString())
        prefs.putString(VOICE_UNLOCK, next.voiceUnlock.toString())
        prefs.putString(TIPS_STARTED, next.tipsStartedAtMs?.toString().orEmpty())
        prefs.putString(TIPS_HIDDEN, next.tipsHidden.toString())
        prefs.putString(VOICES, kotlinx.serialization.json.Json.encodeToString(next.voices))
        state.value = read()
    }

    /** The pasted key for Speechify, Fish, ElevenLabs, Soniox, or Deepgram. Null removes it. */
    public fun voiceKey(kind: VoiceKind): String? = secrets.get(voiceSecret(kind))?.takeIf { it.isNotBlank() }

    public fun setVoiceKey(kind: VoiceKind, key: String?) {
        if (!kind.needsKey) return
        secrets.set(voiceSecret(kind), key?.trim()?.ifBlank { null })
        state.value = read()
    }

    /** Last four characters, for the row. Null when there is no key. */
    public fun voiceKeyEnding(kind: VoiceKind): String? = voiceKey(kind)?.takeIf { it.length > 8 }?.takeLast(4)

    /** The saved key as its first and last 2 characters around dots ("sk•••••9f"), for showing it safely. */
    public fun voiceKeyMasked(kind: VoiceKind): String? = voiceKey(kind)?.let(::maskKey)

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
            fullAccess = prefs.getString(FULL_ACCESS)?.toBooleanStrictOrNull() ?: defaults.fullAccess,
            triggerWords = prefs.getString(TRIGGER_WORDS)?.split('\n')?.filter { it.isNotBlank() } ?: defaults.triggerWords,
            readCodes = prefs.getString(READ_CODES)?.toBooleanStrictOrNull() ?: defaults.readCodes,
            askAboveAmount = prefs.getString(ASK_ABOVE)?.toDoubleOrNull(),
            userName = prefs.getString(USER_NAME)?.ifBlank { null },
            onboardingDone = prefs.getString(ONBOARDING_DONE)?.toBooleanStrictOrNull() ?: defaults.onboardingDone,
            speechOrder = prefs.getString(SPEECH_ORDER)?.split(',')?.filter { it.isNotBlank() } ?: defaults.speechOrder,
            hearingOrder = prefs.getString(HEARING_ORDER)?.split(',')?.filter { it.isNotBlank() } ?: defaults.hearingOrder,
            talkBack = prefs.getString(TALK_BACK)?.toBooleanStrictOrNull() ?: defaults.talkBack,
            speakResult = prefs.getString(SPEAK_RESULT)?.toBooleanStrictOrNull() ?: defaults.speakResult,
            speechSpeed = SpeechSpeed.fromId(prefs.getString(SPEECH_SPEED)),
            language = com.piyush.mode.feature.settings.domain.SpeechLanguage.fromId(prefs.getString(LANGUAGE)).id,
            replyInSpokenLanguage = prefs.getString(REPLY_IN_SPOKEN)?.toBooleanStrictOrNull() ?: defaults.replyInSpokenLanguage,
            suggestRoutines = prefs.getString(SUGGEST_ROUTINES)?.toBooleanStrictOrNull() ?: defaults.suggestRoutines,
            bargeIn = prefs.getString(BARGE_IN)?.toBooleanStrictOrNull() ?: defaults.bargeIn,
            voiceUnlock = prefs.getString(VOICE_UNLOCK)?.toBooleanStrictOrNull() ?: defaults.voiceUnlock,
            tipsStartedAtMs = prefs.getString(TIPS_STARTED)?.toLongOrNull(),
            tipsHidden = prefs.getString(TIPS_HIDDEN)?.toBooleanStrictOrNull() ?: defaults.tipsHidden,
            voices = prefs.getString(VOICES)?.let {
                runCatching { kotlinx.serialization.json.Json.decodeFromString<Map<String, VoiceChoice>>(it) }.getOrNull()
            } ?: defaults.voices,
            hasApiKey = !secrets.get(API_KEY).isNullOrBlank(),
            provider = ModelProvider.fromId(prefs.getString(PROVIDER)),
            hasClaudeAccount = claudeCredentials.connected.value,
            hasChatGptAccount = hasChatGpt(),
        ).validated()
    }

    internal companion object {
        // The first four names are kept from the first version, so existing installs keep their settings.
        const val API_KEY = "openrouter_api_key"
        const val PROVIDER = "model_provider"
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
        const val FULL_ACCESS = "full_access"
        const val TRIGGER_WORDS = "full_access_words"
        const val READ_CODES = "read_one_time_codes"
        const val ASK_ABOVE = "full_access_ask_above"
        const val USER_NAME = "user_name"
        const val ONBOARDING_DONE = "onboarding_done"
        const val SPEECH_ORDER = "speech_order"
        const val HEARING_ORDER = "hearing_order"
        const val TALK_BACK = "voice_talk_back"
        const val SPEAK_RESULT = "voice_speak_result"
        const val SPEECH_SPEED = "voice_speech_speed"
        const val LANGUAGE = "voice_language"
        const val REPLY_IN_SPOKEN = "voice_reply_in_spoken"
        const val SUGGEST_ROUTINES = "suggest_routines"
        const val BARGE_IN = "voice_barge_in"
        const val VOICE_UNLOCK = "voice_unlock_set_up"
        const val TIPS_STARTED = "home_tips_started_at"
        const val TIPS_HIDDEN = "home_tips_hidden"
        const val VOICES = "voice_choices"

        fun voiceSecret(kind: VoiceKind): String = "voice_key_${kind.id}"
        const val API_KEY_NAME = API_KEY
        fun voiceSecretName(kind: VoiceKind): String = voiceSecret(kind)
    }
}

/** [key] as its first and last 2 characters around dots ("sk•••••9f"); a short key shows only dots. */
public fun maskKey(key: String): String = key.trim().let { if (it.length < 8) "•••••" else it.take(2) + "•••••" + it.takeLast(2) }
