package com.piyush.mode.app.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.settings.LanguagePresenter
import com.piyush.mode.app.screens.settings.VoicePickerPresenter
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeChoiceRow
import com.piyush.mode.design.ModeChoiceTag
import com.piyush.mode.design.ModeExampleCard
import com.piyush.mode.design.ModeFilterChip
import com.piyush.mode.design.ModeFootnote
import com.piyush.mode.design.ModeInlineText
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModePageTitle
import com.piyush.mode.design.ModePillSegments
import com.piyush.mode.design.ModeRadius
import com.piyush.mode.design.ModeRowEmphasis
import com.piyush.mode.design.ModeRowLeading
import com.piyush.mode.design.ModeSearchField
import com.piyush.mode.design.ModeSectionHeader
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeSpinner
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeVoiceChoiceRow
import com.piyush.mode.app.screens.settings.LanguageReach
import com.piyush.mode.feature.settings.voice.VoiceListError
import com.piyush.mode.feature.settings.voice.Voices

/** The language the user speaks to Mode, and the language Mode replies in. */
@Composable
fun LanguageRoute(graph: ModeGraph, close: () -> Unit) {
    val presenter: LanguagePresenter = rememberPresenter("language") { graph.screens.language() }
    val s by presenter.state.collectAsStateWithLifecycle()
    val w = rememberWords()
    Column(Modifier.fillMaxSize()) {
        ModePageBar(stringResource(R.string.common_back), close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            ModePageTitle(stringResource(R.string.language_title), subtitle = stringResource(R.string.language_subtitle))
            ModeSectionHeader(stringResource(R.string.language_i_speak))
            ModeCard(rows = s.rows.map { r ->
                {
                    val reach = when (r.reach) {
                        LanguageReach.Auto -> w.get(R.string.language_auto)
                        LanguageReach.OnDevice -> w.get(R.string.language_on_iphone)
                        LanguageReach.Download -> w.get(R.string.language_download)
                        LanguageReach.Cloud -> w.get(R.string.language_cloud, r.listener.orEmpty())
                        else -> w.get(R.string.language_needs_cloud)
                    }
                    ModeChoiceRow(r.name, if (r.native.isEmpty()) reach else "${r.native} · $reach", ModeRowLeading.None, r.selected, { presenter.choose(r.id) }, emphasis = ModeRowEmphasis.Standard)
                }
            })
            ModeSectionHeader(stringResource(R.string.language_replies_in))
            ModeCard(rows = listOf(
                { ModeChoiceRow(stringResource(R.string.language_spoken), stringResource(R.string.language_spoken_detail), ModeRowLeading.None, s.replyInSpoken, { presenter.setReplyInSpoken(true) }, emphasis = ModeRowEmphasis.Standard) },
                { ModeChoiceRow(stringResource(R.string.language_english), stringResource(R.string.language_english_detail), ModeRowLeading.None, !s.replyInSpoken, { presenter.setReplyInSpoken(false) }, emphasis = ModeRowEmphasis.Standard) },
            ))
            ModeFootnote(stringResource(R.string.language_works_in_english), Modifier.padding(top = ModeSpacing.xxs))
            ModeExampleCard(listOf(
                stringResource(R.string.language_example_you_say) to stringResource(R.string.language_example_said),
                stringResource(R.string.language_example_works_on) to stringResource(R.string.language_example_task),
                stringResource(R.string.language_example_says) to stringResource(R.string.language_example_reply),
            ))
        }
    }
}

/** Choose a voice: the phone's own, ElevenLabs' library, or Fish Audio's; each plays a sample. */
@Composable
fun VoicePickerRoute(graph: ModeGraph, provider: String, close: () -> Unit) {
    val presenter: VoicePickerPresenter = rememberPresenter("picker:$provider") { graph.screens.voicePicker(provider) }
    val s by presenter.state.collectAsStateWithLifecycle()
    val w = rememberWords()
    val c = Mode.colors
    var query by rememberSaveable { mutableStateOf("") }
    DisposableEffect(Unit) { onDispose { presenter.stopSample() } }
    val service = VoiceService.of(s.provider) ?: VoiceService.Device
    val selectedName = s.voices.firstOrNull { it.id == s.selected }?.name
    Column(Modifier.fillMaxSize()) {
        ModePageBar(stringResource(R.string.common_back), close)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
            ModePageTitle(stringResource(R.string.voice_choose_voice))
            ModePillSegments(
                listOf(VoiceService.Speechify, VoiceService.Device, VoiceService.ElevenLabs, VoiceService.Fish).map { it.id to w.voiceProvider(it.id) },
                service.id, { presenter.provider(it) }, compact = true,
            )
            ModeInlineText(
                stringResource(when (service) {
                    VoiceService.Speechify -> R.string.voice_speechify_banner
                    VoiceService.ElevenLabs -> R.string.voice_eleven_banner
                    VoiceService.Fish -> R.string.voice_fish_banner
                    else -> R.string.voice_apple_banner
                }),
                ModeTextStyle.Footnote, Modifier.background(c.card, RoundedCornerShape(ModeRadius.bubble)).padding(ModeSpacing.m + ModeSpacing.xxs), color = c.textSecondary,
            )
            when {
                s.error == VoiceListError.NoKey -> {
                    ModeText(w.get(R.string.voice_needs_key, w.voiceProvider(service.id)), ModeTextStyle.Callout)
                    ModeButton(stringResource(R.string.voice_add_key), close, fullWidth = true)
                }
                s.error == VoiceListError.Failed -> {
                    ModeText(stringResource(R.string.voice_could_not_load), ModeTextStyle.Callout)
                    ModeButton(stringResource(R.string.voice_try_again), presenter::reload, role = com.piyush.mode.design.ModeButtonRole.Secondary)
                }
                s.loading -> ModeSpinner()
                else -> {
                    ModeSearchField(stringResource(R.string.voice_search_voices), query, { query = it; presenter.search(it) })
                    if (s.groups.isNotEmpty()) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                            ModeFilterChip(stringResource(R.string.voice_all), s.group == null, { presenter.filter(null) })
                            s.groups.forEach { g -> ModeFilterChip(g, s.group == g, { presenter.filter(g) }) }
                        }
                    }
                    if (s.voices.isEmpty()) {
                        ModeText(stringResource(R.string.voice_no_match), ModeTextStyle.Callout, color = c.textSecondary)
                    } else {
                        ModeCard(lifted = true, rows = s.voices.map { v ->
                            {
                                val tag = when (v.tag) {
                                    Voices.POPULAR -> ModeChoiceTag(stringResource(R.string.voice_popular), c.tokens.tipOrange, c.tokens.tipOrangeText)
                                    Voices.OUR_PICK -> ModeChoiceTag(stringResource(R.string.voice_our_pick), c.tintSoft, c.tint)
                                    null -> null
                                    else -> ModeChoiceTag(v.tag!!, c.tokens.tagGreen, c.tokens.tagGreenText)
                                }
                                ModeVoiceChoiceRow(v.name, v.traits, tag, v.id == s.selected, s.playing == v.id, w.get(R.string.voice_play, v.name), { presenter.select(v.id) }, { presenter.play(v.id) })
                            }
                        })
                        ModeFootnote(stringResource(R.string.voice_picker_note))
                    }
                }
            }
        }
        selectedName?.let { name ->
            ModeButton(w.get(R.string.voice_use, name), { presenter.use(); close() }, Modifier.padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.s), fullWidth = true)
        }
    }
}
