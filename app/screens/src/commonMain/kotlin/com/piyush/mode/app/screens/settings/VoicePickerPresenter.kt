package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.VoiceChoice
import com.piyush.mode.feature.settings.domain.VoiceKind
import com.piyush.mode.feature.settings.voice.VoiceList
import com.piyush.mode.feature.settings.voice.VoiceListError
import com.piyush.mode.feature.settings.voice.VoiceOption
import com.piyush.mode.feature.settings.voice.Voices
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

public data class VoicePickerUiState(
    /** Apple, ElevenLabs or Fish Audio ([VoiceKind.id]). */
    val provider: String = VoiceKind.Apple.id,
    val query: String = "",
    /** Groups to filter by (languages or kinds of voice), most voices first; null filter: all. */
    val groups: List<String> = emptyList(),
    val group: String? = null,
    val voices: List<VoiceOption> = emptyList(),
    /** The voice that would be used ([selected]) and the one whose sample is playing. */
    val selected: String? = null,
    val playing: String? = null,
    val loading: Boolean = true,
    /** No key yet for this provider, or its list could not be loaded. */
    val error: VoiceListError? = null,
    /** The provider has a key (ElevenLabs, Fish Audio) or needs none (Apple). */
    val connected: Boolean = true,
)

/**
 * Choose a voice: Apple's voices on this iPhone, ElevenLabs' library, or Fish Audio's public voices. Each plays
 * a sample; choosing one keeps it for that provider and makes that provider speak first.
 */
public class VoicePickerPresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val voices: Voices,
    start: VoiceKind = VoiceKind.Apple,
) : Presenter<VoicePickerUiState>(parent, VoicePickerUiState(provider = start.id)) {
    private val provider = MutableStateFlow(start)
    private val query = MutableStateFlow("")
    private val group = MutableStateFlow<String?>(null)
    private val lists = MutableStateFlow<Map<VoiceKind, VoiceList>>(emptyMap())
    private val picked = MutableStateFlow<Map<VoiceKind, String>>(emptyMap())
    private val playing = MutableStateFlow<String?>(null)
    private var sample: Job? = null

    init {
        load(start)
        scope.launch {
            combine(provider, combine(query, group) { q, g -> q to g }, lists, picked, playing) { kind, (q, g), all, chosen, p ->
                build(kind, q, g, all[kind], chosen[kind], p)
            }.collect { mutableState.value = it }
        }
    }

    public fun provider(id: String) {
        val kind = VoiceKind.fromId(id)?.takeIf { it in VoiceKind.SPEECH } ?: return
        stopSample()
        provider.value = kind
        group.value = null
        load(kind)
    }

    public fun search(text: String) { query.value = text }

    public fun filter(groupName: String?) { group.value = groupName }

    /** Reloads the list (after a key was added). */
    public fun reload() { lists.value = lists.value - provider.value; load(provider.value) }

    /** Marks [id] as the one to use; [use] then keeps it. */
    public fun select(id: String) { picked.value = picked.value + (provider.value to id) }

    /** Plays [id]'s sample; again stops it. */
    public fun play(id: String) {
        if (playing.value == id) { stopSample(); return }
        stopSample()
        val kind = provider.value
        val option = lists.value[kind]?.options?.firstOrNull { it.id == id } ?: return
        playing.value = id
        sample = scope.launch {
            try { voices.preview(kind, option) } finally { if (playing.value == id) playing.value = null }
        }
    }

    public fun stopSample() {
        sample?.cancel()
        voices.stop()
        playing.value = null
    }

    /** Keeps the selected voice for this provider and puts the provider first, so Mode speaks with it. */
    public fun use() {
        val kind = provider.value
        val id = state.value.selected ?: return
        val option = lists.value[kind]?.options?.firstOrNull { it.id == id } ?: return
        stopSample()
        settings.update { s ->
            s.copy(
                voices = s.voices + (kind.id to VoiceChoice(option.id, option.name)),
                speechOrder = listOf(kind.id) + s.speechOrder.filter { it != kind.id },
            )
        }
    }

    private fun load(kind: VoiceKind) {
        if (kind in lists.value) return
        scope.launch { lists.value = lists.value + (kind to voices.options(kind)) }
    }

    private fun build(kind: VoiceKind, q: String, g: String?, list: VoiceList?, chosen: String?, p: String?): VoicePickerUiState {
        val all = Voices.withOurPick(kind, list?.options.orEmpty())
        val words = q.trim().lowercase().split(' ').filter { it.isNotEmpty() }
        val shown = all.filter { v ->
            (g == null || v.group == g) && words.all { w -> v.name.lowercase().contains(w) || v.traits.lowercase().contains(w) }
        }
        val saved = settings.settings.value.voices[kind.id]?.id
        return VoicePickerUiState(
            provider = kind.id, query = q,
            groups = all.mapNotNull { it.group }.groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }.take(MAX_GROUPS).map { it.key },
            group = g, voices = shown,
            selected = chosen ?: saved?.takeIf { id -> all.any { it.id == id } } ?: all.firstOrNull()?.id,
            playing = p, loading = list == null, error = list?.error,
            connected = !kind.needsKey || settings.voiceKey(kind) != null,
        )
    }

    private companion object {
        const val MAX_GROUPS = 5
    }
}
