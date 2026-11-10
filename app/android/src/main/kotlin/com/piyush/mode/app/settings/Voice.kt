package com.piyush.mode.app.settings

import android.content.ClipboardManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.settings.VoicePresenter
import com.piyush.mode.app.screens.settings.VoiceRow
import com.piyush.mode.app.screens.settings.VoiceUiState
import com.piyush.mode.app.shell.Route
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.app.system.SystemPages
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeButtonRole
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeChoiceTag
import com.piyush.mode.design.ModeFootnote
import com.piyush.mode.design.ModeGuideNote
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeLogo
import com.piyush.mode.design.ModeMo
import com.piyush.mode.design.ModeNoteKind
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModePageTitle
import com.piyush.mode.design.ModePillSegments
import com.piyush.mode.design.ModeProviderRow
import com.piyush.mode.design.ModeRow
import com.piyush.mode.design.ModeRowLeading
import com.piyush.mode.design.ModeSectionHeader
import com.piyush.mode.design.ModeSheet
import com.piyush.mode.design.ModeSize
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeSpinner
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextField
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeToggleRow
import com.piyush.mode.design.ModeValueRow
import com.piyush.mode.design.ModeWideButton
import com.piyush.mode.design.ModeWideStyle
import com.piyush.mode.design.MoMood

/** A voice or listening service: its name, logo, plan line, where to get a key. */
enum class VoiceService(val id: String, val logo: ModeLogo, val site: String?, val keyPage: String?, val hearing: Boolean) {
    Device("apple", ModeLogo.Android, null, null, false),
    Speechify("speechify", ModeLogo.Speechify, "speechify.ai", "https://platform.speechify.ai/api-keys", false),
    Fish("fish", ModeLogo.FishAudio, "fish.audio", "https://fish.audio/app/api-keys/", false),
    ElevenLabs("elevenlabs", ModeLogo.ElevenLabs, "elevenlabs.io", "https://elevenlabs.io/app/settings/api-keys", false),
    Soniox("soniox", ModeLogo.Soniox, "soniox.com", "https://console.soniox.com", true),
    Deepgram("deepgram", ModeLogo.Deepgram, "deepgram.com", "https://console.deepgram.com", true);

    fun plan(hearing: Boolean, w: Words): String = w.get(when (this) {
        Device -> if (hearing) R.string.voice_apple_hearing else R.string.voice_apple_speech
        Speechify -> R.string.voice_speechify_free
        Fish -> R.string.voice_fish_free
        ElevenLabs -> R.string.voice_eleven_free
        Soniox -> R.string.voice_soniox_free
        Deepgram -> R.string.voice_deepgram_free
    })

    fun blurb(w: Words): String = w.get(when (this) {
        Device -> R.string.voice_blurb_apple
        Speechify, Fish, ElevenLabs -> R.string.voice_blurb_speech
        Soniox -> R.string.voice_blurb_soniox
        Deepgram -> R.string.voice_blurb_deepgram
    })

    companion object {
        fun of(id: String?): VoiceService? = entries.firstOrNull { it.id == id }
    }
}

/** A service as the lists show it. */
data class VoiceRowView(val service: VoiceService, val status: String, val chosen: Boolean, val connected: Boolean, val masked: String?, val problem: String?, val needsMic: Boolean)

/** Mode's voice ([hearing] false) or Listening ([hearing] true): who speaks or listens, keys, and how. */
@Composable
fun VoiceRoute(graph: ModeGraph, hearing: Boolean, pages: SystemPages, navigate: (Route) -> Unit, close: () -> Unit) {
    val presenter: VoicePresenter = rememberPresenter("voice:$hearing") { graph.screens.voice(hearing) }
    val s by presenter.state.collectAsStateWithLifecycle()
    val w = rememberWords()
    val rows = rows(s, hearing, w)
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    var linking by rememberSaveable { mutableStateOf<String?>(null) }
    DisposableEffect(Unit) { onDispose { presenter.stopSample() } }
    BackHandler(enabled = opened != null) { opened = null }
    val open = rows.firstOrNull { it.service.id == opened }
    Column(Modifier.fillMaxSize()) {
        ModePageBar(stringResource(R.string.common_back), { if (opened != null) opened = null else close() })
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            if (open != null) {
                ProviderPage(open, hearing, w, presenter, pages, onKey = { linking = open.service.id }, onChangeVoice = { opened = null; navigate(Route.VoicePicker(s.speaker ?: VoiceService.Device.id)) }) { opened = null }
            } else if (hearing) {
                Row(verticalAlignment = Alignment.Top) {
                    ModePageTitle(stringResource(R.string.voice_listening_row), Modifier.weight(1f), subtitle = stringResource(R.string.voice_listening_subtitle))
                    ModeMo(MoMood.Listening, ModeSize.moProvider)
                }
                ModeSectionHeader(stringResource(R.string.voice_who_listens))
                Picker(rows, hearing, presenter, onOpen = { opened = it.service.id }, onKey = { linking = it.service.id })
                ModeFootnote(stringResource(R.string.voice_listening_note))
                ModeSectionHeader(stringResource(R.string.voice_privacy))
                ModeCard(lifted = true, rows = listOf(R.string.voice_privacy_listen, R.string.voice_privacy_where, R.string.voice_privacy_type).map { id ->
                    { ModeRow(stringResource(id), leading = ModeRowLeading.Check) }
                })
                ModeSectionHeader(stringResource(R.string.language_title))
                ModeCard(rows = listOf({ ModeValueRow(stringResource(R.string.language_i_speak), s.languageName, onClick = { navigate(Route.Language) }) }))
            } else {
                ModePageTitle(stringResource(R.string.voice_modes_voice), Modifier.padding(bottom = ModeSpacing.l), subtitle = stringResource(R.string.voice_voice_subtitle))
                val speaker = VoiceService.of(s.speaker) ?: VoiceService.Device
                ModeCard(rows = listOf({
                    Column(Modifier.padding(vertical = ModeSpacing.l), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs)) {
                        ModeRow(
                            s.voiceName ?: stringResource(R.string.voice_default_voice), subtitle = w.voiceProvider(speaker.id),
                            leading = ModeRowLeading.LogoTile(speaker.logo, Mode.colors.textPrimary, Mode.colors.page),
                        )
                        ModeWideButton(stringResource(R.string.voice_change_voice), { navigate(Route.VoicePicker(speaker.id)) }, style = ModeWideStyle.Raised)
                    }
                }))
                ModeSectionHeader(stringResource(R.string.voice_who_speaks))
                Picker(rows, hearing, presenter, onOpen = { opened = it.service.id }, onKey = { linking = it.service.id })
                ModeFootnote(stringResource(R.string.voice_who_speaks_note))
                ModeFootnote(stringResource(R.string.voice_keys_note), icon = ModeIcon.Lock)
                ModeSectionHeader(stringResource(R.string.voice_speaking))
                ModeCard(rows = listOf(
                    {
                        Column(Modifier.padding(vertical = ModeSpacing.m), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                            ModeText(stringResource(R.string.voice_speed), ModeTextStyle.Callout)
                            ModePillSegments(
                                listOf(SLOWER to stringResource(R.string.voice_slower), NORMAL to stringResource(R.string.voice_normal), FASTER to stringResource(R.string.voice_faster)),
                                s.speed, presenter::setSpeed, compact = true,
                            )
                        }
                    },
                    { ModeToggleRow(stringResource(R.string.voice_read_result), s.speakResult, presenter::setSpeakResult, subtitle = stringResource(R.string.voice_read_result_detail)) },
                ))
            }
        }
    }
    linking?.let { id ->
        val service = VoiceService.of(id) ?: return@let
        KeySheet(service, rows.firstOrNull { it.service == service }?.masked, s, w, pages, presenter) {
            presenter.clearKeyCheck()
            linking = null
        }
    }
}

@Composable
private fun Picker(rows: List<VoiceRowView>, hearing: Boolean, presenter: VoicePresenter, onOpen: (VoiceRowView) -> Unit, onKey: (VoiceRowView) -> Unit) {
    val c = Mode.colors
    ModeCard(lifted = hearing, rows = rows.map { row ->
        {
            Column {
                ModeProviderRow(
                    w(row.service), row.status, ModeRowLeading.LogoTile(row.service.logo, c.textPrimary, c.page), row.chosen, row.connected,
                    stringResource(R.string.voice_connect_key), onSelect = { presenter.choose(row.service.id) }, onConnect = { onKey(row) },
                    tag = if (row.chosen) ModeChoiceTag(stringResource(R.string.voice_in_use), c.tokens.tagGreen, c.tokens.tagGreenText) else null,
                    onOpen = if (row.service == VoiceService.Device) null else { { onOpen(row) } },
                )
                if (row.needsMic) {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = ModeSpacing.m),
                        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ModeText(stringResource(R.string.voice_turn_on_apple), ModeTextStyle.Subheadline, Modifier.weight(1f))
                        ModeButton(stringResource(R.string.voice_turn_on), presenter::prepareDevice, role = ModeButtonRole.PillProminent)
                    }
                }
            }
        }
    })
}

@Composable
private fun w(service: VoiceService): String = rememberWords().voiceProvider(service.id)

/** One connected service: why it failed, its key, edit, remove, use it, and its usage page. */
@Composable
private fun ProviderPage(row: VoiceRowView, hearing: Boolean, w: Words, presenter: VoicePresenter, pages: SystemPages, onKey: () -> Unit, onChangeVoice: () -> Unit, close: () -> Unit) {
    val c = Mode.colors
    val name = w.voiceProvider(row.service.id)
    ModePageTitle(name, subtitle = w.get(when {
        row.problem != null -> if (hearing) R.string.voice_in_use_couldnt_hear else R.string.voice_in_use_couldnt_speak
        row.chosen -> if (hearing) R.string.voice_in_use_hearing else R.string.voice_in_use_speaking
        else -> R.string.voice_connected
    }))
    row.problem?.let { problem ->
        ModeGuideNote(troubleLong(problem, name, hearing, w), ModeNoteKind.Problem)
        if (!hearing && problem == PLAN_LIMIT) ModeWideButton(stringResource(R.string.voice_choose_another_voice), onChangeVoice, style = ModeWideStyle.Ink)
        ModeWideButton(stringResource(R.string.voice_update_key), onKey, style = if (!hearing && problem == PLAN_LIMIT) ModeWideStyle.Raised else ModeWideStyle.Ink)
        ModeWideButton(stringResource(if (hearing) R.string.voice_use_apple_hearing else R.string.voice_use_apple_speaking), {
            presenter.choose(VoiceService.Device.id)
            close()
        }, style = ModeWideStyle.Raised)
    }
    ModeSectionHeader(stringResource(R.string.voice_key_section))
    ModeCard(rows = listOf({ ModeValueRow(stringResource(R.string.voice_key), row.masked ?: DASH) }))
    if (row.problem == null) {
        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs)) {
            ModeWideButton(stringResource(R.string.voice_edit_key), onKey, Modifier.weight(1f), style = ModeWideStyle.Raised)
            ModeWideButton(stringResource(R.string.voice_remove), { presenter.remove(row.service.id); close() }, Modifier.weight(1f), style = ModeWideStyle.Danger)
        }
        if (!row.chosen) ModeWideButton(w.get(if (hearing) R.string.voice_listen_with else R.string.voice_speak_with, name), { presenter.choose(row.service.id); close() }, style = ModeWideStyle.Ink)
        ModeFootnote(w.get(if (hearing) R.string.voice_remove_note_hear else R.string.voice_remove_note_speak, name))
        row.service.site?.let { site ->
            ModeSectionHeader(stringResource(R.string.voice_plan_section))
            ModeWideButton(w.get(R.string.voice_see_usage, site), { pages.web("$HTTPS$site") }, trailingIcon = ModeIcon.External)
        }
    }
    ModeFootnote(stringResource(R.string.voice_keys_note), Modifier.padding(top = ModeSpacing.xxs), icon = ModeIcon.Lock)
}

/** Connect or edit a key: paste it, Mode checks it with the service, then saves it. */
@Composable
private fun KeySheet(service: VoiceService, masked: String?, s: VoiceUiState, w: Words, pages: SystemPages, presenter: VoicePresenter, onDone: () -> Unit) {
    val c = Mode.colors
    val context = LocalContext.current
    var key by rememberSaveable { mutableStateOf("") }
    val name = w.voiceProvider(service.id)
    val check = s.keyCheck?.takeIf { it.id == service.id }
    ModeSheet(onDone, tall = true) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(top = ModeSpacing.l, bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
            ModeText(w.get(if (masked == null) R.string.voice_connect else R.string.voice_edit_key_title, name), ModeTextStyle.Title2)
            ModeText(if (masked != null && check?.state != SAVED) w.get(R.string.voice_in_use_now_key, masked) else service.blurb(w), ModeTextStyle.Subheadline, color = c.textSecondary)
            ModeTextField(w.get(if (masked == null || check?.state == SAVED) R.string.voice_key_field else R.string.voice_new_key_field, name), stringResource(R.string.voice_paste_here), key, { key = it }, secure = true)
            ModeButton(stringResource(R.string.voice_paste), {
                context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.let { key = it }
            }, role = ModeButtonRole.Pill)
            when (check?.state) {
                CHECKING -> Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s), verticalAlignment = Alignment.CenterVertically) {
                    ModeSpinner()
                    ModeText(w.get(R.string.voice_checking_key, name), ModeTextStyle.SubheadlineEmphasis, color = c.tint)
                }
                FAILED -> ModeGuideNote(keyFailed(check.trouble, service, name, w), ModeNoteKind.Problem)
                SAVED -> ModeGuideNote(w.get(if (service.hearing) R.string.voice_now_listens else R.string.voice_now_speaks, name), ModeNoteKind.Info)
                else -> service.keyPage?.let { page ->
                    ModeWideButton(w.get(if (masked == null) R.string.voice_get_free_key else R.string.voice_open_keys, service.site ?: name), { pages.web(page) }, trailingIcon = ModeIcon.External)
                }
            }
            if (check?.state == SAVED) {
                ModeButton(stringResource(R.string.common_done), onDone, fullWidth = true)
            } else {
                ModeButton(
                    stringResource(when (check?.state) {
                        CHECKING -> R.string.voice_checking
                        FAILED -> R.string.voice_check_again
                        else -> R.string.voice_check_and_save
                    }),
                    { presenter.checkAndSaveKey(service.id, key.trim()) }, fullWidth = true, enabled = check?.state != CHECKING && key.isNotBlank(),
                )
            }
            ModeFootnote(stringResource(R.string.voice_keys_note), icon = ModeIcon.Lock)
        }
    }
}

private fun rows(s: VoiceUiState, hearing: Boolean, w: Words): List<VoiceRowView> {
    // A fixed order (Speechify first for who speaks); which one is in use is the radio, not the position.
    val order = if (hearing) listOf(VoiceService.Device, VoiceService.Deepgram, VoiceService.Soniox)
    else listOf(VoiceService.Speechify, VoiceService.Device, VoiceService.ElevenLabs, VoiceService.Fish)
    return s.rows.mapNotNull { r -> VoiceService.of(r.id)?.let { row(it, r, hearing, w) } }.sortedBy { order.indexOf(it.service) }
}

private fun row(service: VoiceService, r: VoiceRow, hearing: Boolean, w: Words): VoiceRowView {
    val status = when {
        r.problem != null && r.first -> w.voiceTrouble(r.problem!!, hearing)
        r.masked != null -> w.get(R.string.voice_connected_key, r.masked!!)
        service == VoiceService.Device -> service.plan(hearing, w)
        else -> w.get(R.string.voice_not_connected, w.get(if (service.hearing) R.string.voice_free_credit_short else R.string.voice_free_plan_short))
    }
    return VoiceRowView(
        service, status, r.first, service == VoiceService.Device || r.masked != null, r.masked, if (r.first) r.problem else null,
        needsMic = service == VoiceService.Device && hearing && r.block == PERMISSION,
    )
}

private fun troubleLong(trouble: String, provider: String, hearing: Boolean, w: Words): String = when (trouble) {
    "KeyRejected" -> w.get(R.string.voice_trouble_key_long, provider)
    PLAN_LIMIT -> w.get(if (hearing) R.string.voice_trouble_plan_hear_long else R.string.voice_trouble_plan_speak_long, provider)
    "NoKey" -> w.get(R.string.voice_trouble_no_key_long, provider)
    "NotReady" -> w.get(R.string.voice_trouble_not_ready_long)
    else -> w.get(if (hearing) R.string.voice_trouble_unreachable_hear_long else R.string.voice_trouble_unreachable_long, provider)
}

private fun keyFailed(trouble: String?, service: VoiceService, provider: String, w: Words): String = when (trouble) {
    "KeyRejected" -> w.get(R.string.voice_key_rejected, provider, service.site ?: provider)
    PLAN_LIMIT -> w.get(R.string.voice_key_plan, provider)
    "NoKey" -> w.get(R.string.voice_key_empty)
    else -> w.get(R.string.voice_key_unreachable, provider)
}

private const val SLOWER = "slower"
private const val NORMAL = "normal"
private const val FASTER = "faster"
private const val PLAN_LIMIT = "PlanLimit"
private const val PERMISSION = "Permission"
private const val CHECKING = "checking"
private const val FAILED = "failed"
private const val SAVED = "saved"
private const val DASH = "—"
private const val HTTPS = "https://"
