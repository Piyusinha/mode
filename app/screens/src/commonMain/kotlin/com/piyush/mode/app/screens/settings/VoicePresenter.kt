package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.VoiceKind
import com.piyush.mode.feature.settings.voice.DeviceVoice
import com.piyush.mode.feature.settings.voice.Voices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** One speech or hearing row. Words for [block] come from the UI. */
public data class VoiceRow(
    val id: String,
    val ready: Boolean,
    val first: Boolean,
    val keyEnding: String? = null,
    val block: String? = null,
)

public data class VoiceUiState(
    val rows: List<VoiceRow> = emptyList(),
    val hearing: Boolean = false,
)

/**
 * The speech or hearing list: several can be ready, the order is the fallback,
 * and a key is pasted the same way as OpenRouter.
 */
public class VoicePresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val voices: Voices,
    private val hearing: Boolean,
) : Presenter<VoiceUiState>(parent, VoiceUiState(hearing = hearing)) {

    init {
        settings.settings.onEach { publish() }.launchIn(scope)
    }

    public fun saveKey(id: String, key: String) {
        val kind = VoiceKind.fromId(id) ?: return
        settings.setVoiceKey(kind, key)
    }

    public fun disconnect(id: String) {
        val kind = VoiceKind.fromId(id) ?: return
        settings.setVoiceKey(kind, null)
    }

    /** Moves [id] one step earlier. Apple can move too; unknown ids are ignored. */
    public fun moveUp(id: String) {
        settings.update { current ->
            val order = if (hearing) current.hearingOrder else current.speechOrder
            val index = order.indexOf(id)
            if (index <= 0) return@update current
            val next = order.toMutableList().apply {
                val item = removeAt(index)
                add(index - 1, item)
            }
            if (hearing) current.copy(hearingOrder = next) else current.copy(speechOrder = next)
        }
    }

    /** Asks Apple to install a missing speech model. The phone does the work. */
    public fun installAppleAsset() {
        DeviceVoice.hearingState()
    }

    private fun publish() {
        val order = if (hearing) settings.settings.value.hearingOrder else settings.settings.value.speechOrder
        val rows = order.mapNotNull { id ->
            val kind = VoiceKind.fromId(id) ?: return@mapNotNull null
            if (hearing && kind !in VoiceKind.HEARING) return@mapNotNull null
            if (!hearing && kind !in VoiceKind.SPEECH) return@mapNotNull null
            val block = voices.block(kind, hearing)
            VoiceRow(
                id = id,
                ready = block == null,
                first = false,
                keyEnding = settings.voiceKeyEnding(kind),
                block = block?.name,
            )
        }
        val first = rows.indexOfFirst { it.ready }
        mutableState.value = VoiceUiState(
            rows = rows.mapIndexed { index, row -> row.copy(first = index == first) },
            hearing = hearing,
        )
    }
}
