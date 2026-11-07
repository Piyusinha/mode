package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.SpeechSpeed
import com.piyush.mode.feature.settings.domain.VoiceKind
import com.piyush.mode.feature.settings.voice.DeviceVoice
import com.piyush.mode.feature.settings.voice.VoiceOption
import com.piyush.mode.feature.settings.voice.Voices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** One speech or hearing provider. Words for [block] come from the UI. */
public data class VoiceRow(
    val id: String,
    val ready: Boolean,
    /** The one the user chose: Mode speaks or listens with it, and only it. */
    val first: Boolean,
    val keyEnding: String? = null,
    val block: String? = null,
    /** The saved key, shown as "sk•••••9f"; null: not connected. */
    val masked: String? = null,
    /** Why it last failed ([com.piyush.mode.feature.settings.voice.VoiceTrouble] name), while it is the chosen one. */
    val problem: String? = null,
)

/** Checking a pasted key with its provider before saving it. */
public data class KeyCheckUi(
    val id: String,
    /** "checking", "failed" or "saved". */
    val state: String,
    /** Failed: the [com.piyush.mode.feature.settings.voice.VoiceTrouble] name, or "Unreachable". */
    val trouble: String? = null,
)

public data class VoiceUiState(
    val rows: List<VoiceRow> = emptyList(),
    val hearing: Boolean = false,
    /** Speech: the provider speaking now and the voice picked there (null: its default voice). */
    val speaker: String? = null,
    val voiceName: String? = null,
    val voiceId: String? = null,
    val speed: String = SpeechSpeed.Normal.id,
    val speakResult: Boolean = true,
    /** Listening: the language picked (its English name), for the Language row. */
    val languageName: String = "",
    /** The sample is playing. */
    val playing: Boolean = false,
    /** A key being checked, or the result of the last check. */
    val keyCheck: KeyCheckUi? = null,
)

/**
 * Mode's voice or Listening: the providers in try order (several can be ready; the order is the fallback),
 * keys pasted as for OpenRouter, and for speech the voice in use, its sample, the speed and the result line.
 */
public class VoicePresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val voices: Voices,
    private val hearing: Boolean,
) : Presenter<VoiceUiState>(parent, VoiceUiState(hearing = hearing)) {
    private val playing = MutableStateFlow(false)
    private val refreshed = MutableStateFlow(0)
    private val checking = MutableStateFlow<KeyCheckUi?>(null)
    private var sample: Job? = null

    init {
        scope.launch { combine(settings.settings, playing, refreshed, checking, voices.problem) { _, p, _, _, _ -> p }.collect { publish(it) } }
    }

    /** Makes [id] the one Mode speaks or listens with (only a ready one can be chosen). */
    public fun choose(id: String) {
        val kind = VoiceKind.fromId(id) ?: return
        if (voices.block(kind, hearing) == com.piyush.mode.feature.settings.domain.VoiceBlock.NoKey) return
        moveTo(id, 0)
    }

    /**
     * Checks [key] with [id]'s provider, then saves it and makes it the one in use. A refused key is not
     * saved; [VoiceUiState.keyCheck] says why.
     */
    public fun checkAndSaveKey(id: String, key: String) {
        val kind = VoiceKind.fromId(id) ?: return
        checking.value = KeyCheckUi(id, "checking")
        scope.launch {
            when (val result = voices.checkKey(kind, key)) {
                com.piyush.mode.feature.settings.voice.KeyCheck.Valid -> {
                    settings.setVoiceKey(kind, key)
                    moveTo(id, 0)
                    checking.value = KeyCheckUi(id, "saved")
                }
                is com.piyush.mode.feature.settings.voice.KeyCheck.Rejected -> checking.value = KeyCheckUi(id, "failed", result.trouble.name)
                com.piyush.mode.feature.settings.voice.KeyCheck.Unreachable -> checking.value = KeyCheckUi(id, "failed", "Unreachable")
            }
        }
    }

    /** The key sheet closed: forget the check. */
    public fun clearKeyCheck() { checking.value = null }

    /** Removes [id]'s key; when it was the one in use, Apple takes over (it needs no key). */
    public fun remove(id: String) {
        val kind = VoiceKind.fromId(id) ?: return
        val wasChosen = voices.chosen(hearing) == kind
        settings.setVoiceKey(kind, null)
        if (wasChosen) moveTo(VoiceKind.Device.id, 0)
    }

    public fun saveKey(id: String, key: String) {
        val kind = VoiceKind.fromId(id) ?: return
        settings.setVoiceKey(kind, key)
    }

    public fun disconnect(id: String) {
        val kind = VoiceKind.fromId(id) ?: return
        settings.setVoiceKey(kind, null)
    }

    /** Moves [id] one step earlier. */
    public fun moveUp(id: String) {
        val index = order().indexOf(id)
        if (index > 0) moveTo(id, index - 1)
    }

    /** Puts [id] at [index] in the try order (a drag on the list). */
    public fun moveTo(id: String, index: Int) {
        settings.update { current ->
            val order = (if (hearing) current.hearingOrder else current.speechOrder).toMutableList()
            if (!order.remove(id)) return@update current
            order.add(index.coerceIn(0, order.size), id)
            if (hearing) current.copy(hearingOrder = order) else current.copy(speechOrder = order)
        }
    }

    public fun setSpeed(id: String) { settings.update { it.copy(speechSpeed = SpeechSpeed.fromId(id)) } }

    public fun setSpeakResult(on: Boolean) { settings.update { it.copy(speakResult = on) } }

    /** Plays the voice in use saying the sample line; again stops it. */
    public fun playSample() {
        if (sample?.isActive == true) { stopSample(); return }
        val (kind, choice) = voices.speaker() ?: return
        sample = scope.launch {
            playing.value = true
            try {
                voices.preview(kind, VoiceOption(choice?.id ?: "", choice?.name ?: ""))
            } finally {
                playing.value = false
            }
        }
    }

    public fun stopSample() {
        voices.stop()
        sample?.cancel()
        playing.value = false
    }

    /**
     * Turns Apple hearing on when the user asks: the microphone permission prompt, then Apple's speech model.
     * The row follows along until it is ready, denied, or a minute has passed.
     */
    public fun prepareDevice() {
        DeviceVoice.prepare()
        scope.launch {
            repeat(PREPARE_CHECKS) {
                delay(1_000)
                refreshed.value++
                if (DeviceVoice.hearingState() in setOf("ready", "denied", "unsupported")) return@launch
            }
        }
    }

    private fun order(): List<String> = if (hearing) settings.settings.value.hearingOrder else settings.settings.value.speechOrder

    private fun publish(isPlaying: Boolean) {
        val s = settings.settings.value
        val rows = order().mapNotNull { id ->
            val kind = VoiceKind.fromId(id) ?: return@mapNotNull null
            if (kind !in (if (hearing) VoiceKind.HEARING else VoiceKind.SPEECH)) return@mapNotNull null
            val block = voices.block(kind, hearing)
            val problem = voices.problem.value?.takeIf { it.kind == kind && it.hearing == hearing }?.trouble?.name
            VoiceRow(id = id, ready = block == null, first = false, keyEnding = settings.voiceKeyEnding(kind), block = block?.name,
                masked = settings.voiceKeyMasked(kind), problem = problem)
        }
        // The chosen one is first in the saved order, ready or not: there is no falling back to the next.
        val first = rows.indexOfFirst { it.id == voices.chosen(hearing).id }
        val speaker = if (hearing) null else voices.speaker()
        mutableState.value = VoiceUiState(
            rows = rows.mapIndexed { index, row -> row.copy(first = index == first) },
            hearing = hearing,
            speaker = speaker?.first?.id,
            voiceName = speaker?.second?.name,
            voiceId = speaker?.second?.id,
            speed = s.speechSpeed.id,
            speakResult = s.speakResult,
            languageName = com.piyush.mode.feature.settings.domain.SpeechLanguage.fromId(s.language).english,
            playing = isPlaying,
            keyCheck = checking.value,
        )
    }

    private companion object {
        const val PREPARE_CHECKS = 60
    }
}
