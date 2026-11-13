package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.SpeechLanguage
import com.piyush.mode.feature.settings.domain.VoiceKind
import com.piyush.mode.feature.settings.voice.DeviceVoice
import com.piyush.mode.feature.settings.voice.Voices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** How a language can be heard: on this iPhone (Apple), after a one-time download, by the cloud listener, or not yet. */
public enum class LanguageReach { Auto, OnDevice, Download, Cloud, NeedsCloud }

/** [listener]: the cloud listener's name when [reach] is Cloud. */
public data class LanguageRow(
    val id: String, val name: String, val native: String, val reach: LanguageReach, val listener: String?, val selected: Boolean,
)

public data class LanguageUiState(
    val rows: List<LanguageRow> = emptyList(),
    /** Mode answers in the language spoken; false: always English. */
    val replyInSpoken: Boolean = true,
)

/** Settings → Listening → Language: what the user speaks, and what Mode answers in. */
public class LanguagePresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val voices: Voices,
) : Presenter<LanguageUiState>(parent, LanguageUiState()) {
    init {
        scope.launch { settings.settings.collect { mutableState.value = state(it.language, it.replyInSpokenLanguage) } }
    }

    private fun state(chosen: String, replyInSpoken: Boolean): LanguageUiState {
        val listener = voices.listener() ?: VoiceKind.Device
        val apple = DeviceVoice.deviceLanguages().map { it.lowercase() }.toSet()
        val installed = DeviceVoice.deviceInstalled().map { it.lowercase() }.toSet()
        fun reach(l: SpeechLanguage): LanguageReach = when {
            l == SpeechLanguage.Auto -> LanguageReach.Auto
            listener != VoiceKind.Device -> LanguageReach.Cloud
            l.id.lowercase() in installed -> LanguageReach.OnDevice
            l.id.lowercase() in apple || apple.isEmpty() -> LanguageReach.Download
            else -> LanguageReach.NeedsCloud
        }
        val rows = SpeechLanguage.entries.map { l ->
            LanguageRow(l.id, l.english, l.native, reach(l), listener.takeIf { it != VoiceKind.Device }?.let(::nameOf), l.id == chosen)
        }
        // The chosen one first after Auto, then the rest in the list's order.
        return LanguageUiState(rows = rows, replyInSpoken = replyInSpoken)
    }

    private fun nameOf(kind: VoiceKind): String = when (kind) {
        VoiceKind.Soniox -> "Soniox"
        VoiceKind.Deepgram -> "Deepgram"
        else -> kind.id
    }

    /** Pick what the user speaks; Apple's model for it downloads now if needed. */
    public fun choose(id: String) {
        val language = SpeechLanguage.fromId(id)
        settings.update { it.copy(language = language.id) }
        DeviceVoice.listenLanguage = language.takeUnless { it == SpeechLanguage.Auto }?.id
        if (voices.listener() == VoiceKind.Device) DeviceVoice.prepare()
    }

    public fun setReplyInSpoken(on: Boolean) {
        settings.update { it.copy(replyInSpokenLanguage = on) }
    }
}
