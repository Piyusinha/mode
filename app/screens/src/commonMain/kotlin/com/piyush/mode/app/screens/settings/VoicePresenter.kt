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
    /** The one that speaks or listens now (the first ready one). */
    val first: Boolean,
    val keyEnding: String? = null,
    val block: String? = null,
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
    /** The sample is playing. */
    val playing: Boolean = false,
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
    private var sample: Job? = null

    init {
        scope.launch { combine(settings.settings, playing, refreshed) { _, p, _ -> p }.collect { publish(it) } }
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
    public fun prepareApple() {
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
            VoiceRow(id = id, ready = block == null, first = false, keyEnding = settings.voiceKeyEnding(kind), block = block?.name)
        }
        val first = rows.indexOfFirst { it.ready }
        val speaker = if (hearing) null else voices.speaker()
        mutableState.value = VoiceUiState(
            rows = rows.mapIndexed { index, row -> row.copy(first = index == first) },
            hearing = hearing,
            speaker = speaker?.first?.id,
            voiceName = speaker?.second?.name,
            voiceId = speaker?.second?.id,
            speed = s.speechSpeed.id,
            speakResult = s.speakResult,
            playing = isPlaying,
        )
    }

    private companion object {
        const val PREPARE_CHECKS = 60
    }
}
