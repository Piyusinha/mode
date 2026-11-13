package com.piyush.mode.app.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.settings.ProvidersPresenter
import com.piyush.mode.app.screens.settings.ProvidersUiState
import com.piyush.mode.app.screens.settings.SettingsPresenter
import com.piyush.mode.app.screens.settings.SettingsUiState
import com.piyush.mode.app.shell.Route
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeActionSheet
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeButtonRole
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeChevron
import com.piyush.mode.design.ModeFootnote
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeLeading
import com.piyush.mode.design.ModeLogo
import com.piyush.mode.design.ModeMoNote
import com.piyush.mode.design.ModeLogoValueRow
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModePageTitle
import com.piyush.mode.design.ModeRow
import com.piyush.mode.design.ModeRowLeading
import com.piyush.mode.design.ModeSectionHeader
import com.piyush.mode.design.ModeSheetAction
import com.piyush.mode.design.ModeSize
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeStatusLine
import com.piyush.mode.design.ModeStepperRow
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeToggleRow
import com.piyush.mode.design.ModeValueRow
import com.piyush.mode.design.MoMood

/** The AI in use, as Settings' first AI row shows it. */
data class AiInUse(val title: String, val kind: ProviderWords.Kind, val summary: String, val connected: Boolean, val planNote: String)

data class SettingsState(
    val modelName: String = "",
    /** How hard it thinks ("Medium"), under the model's name. */
    val modelEffort: String? = null,
    val stuckName: String = "",
    val stuckEffort: String? = null,
    val showsCost: Boolean = true,
    val maxCost: Double = 0.05,
    val maxCostLabel: String = "",
    val dailyCap: Double = 0.0,
    val dailyCapLabel: String = "",
    val fullAccess: Boolean = false,
    val voiceValue: String = "",
    val speakerLogo: ModeLogo = ModeLogo.Android,
    val listenerLabel: String = "",
    val listenerLogo: ModeLogo = ModeLogo.Android,
    val speakProblem: String? = null,
    val listenProblem: String? = null,
    val talkBack: Boolean = true,
    val voiceNote: String = "",
    /** Developer: debug builds only, hidden in the release APK. */
    val showsDeveloper: Boolean = false,
)

/** This Mode's version line ("Mode 0.1.9"), and a newer Mode when one is out: its note and the row to its release page. */
data class UpdateState(val version: String? = null, val available: String? = null, val whatsNew: String? = null)

sealed interface SettingsEvent {
    data object Back : SettingsEvent
    data class Name(val name: String) : SettingsEvent
    data object SaveName : SettingsEvent
    data class Open(val route: Route) : SettingsEvent
    data class OpenPicker(val stuck: Boolean) : SettingsEvent
    data object OpenProvider : SettingsEvent
    data object ShowProviders : SettingsEvent
    data class TalkBack(val on: Boolean) : SettingsEvent
    data class MaxCost(val usd: Double) : SettingsEvent
    data class DailyCap(val usd: Double) : SettingsEvent
    data object AskReset : SettingsEvent
    data object OpenWhatsNew : SettingsEvent
}

/** Settings, as on its board: You, Voice, AI, How Mode acts, Limits, Health, Reset. */
@Composable
fun SettingsScreen(state: SettingsState, ai: AiInUse, name: String, resetting: Boolean, update: UpdateState = UpdateState(), onEvent: (SettingsEvent) -> Unit) {
    val c = Mode.colors
    Column(Modifier.fillMaxSize()) {
        ModePageBar(stringResource(R.string.common_back), { onEvent(SettingsEvent.Back) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        ) {
            ModePageTitle(stringResource(R.string.common_settings))
            update.available?.let { available ->
                ModeMoNote(available, stringResource(R.string.updates_how_to))
                update.whatsNew?.let { row -> ModeCard(rows = listOf({ ModeRow(row, onClick = { onEvent(SettingsEvent.OpenWhatsNew) }) })) }
            }
            ModeSectionHeader(stringResource(R.string.settings_you))
            ModeCard(rows = listOf(
                {
                    ModeRow(stringResource(R.string.settings_name)) {
                        Box(Modifier.widthIn(max = ModeSize.suggestionWidth), Alignment.CenterEnd) {
                            if (name.isEmpty()) ModeText(stringResource(R.string.settings_add_name), ModeTextStyle.Callout, color = c.textSecondary)
                            BasicTextField(
                                name, { onEvent(SettingsEvent.Name(it)) }, textStyle = ModeTextStyle.Callout.style(c.textSecondary).copy(textAlign = TextAlign.End),
                                singleLine = true, cursorBrush = SolidColor(c.tint), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { onEvent(SettingsEvent.SaveName) }),
                            )
                        }
                    }
                },
                { ModeRow(stringResource(R.string.settings_what_mode_knows), leading = ModeRowLeading.MoBadge(MoMood.Idle, ModeIcon.Brain), onClick = { onEvent(SettingsEvent.Open(Route.Memory)) }) },
            ))
            ModeSectionHeader(stringResource(R.string.voice_section))
            ModeCard(rows = listOf(
                { ModeLogoValueRow(stringResource(R.string.voice_modes_voice), state.voiceValue, state.speakerLogo, { onEvent(SettingsEvent.Open(Route.Voice)) }, problem = state.speakProblem) },
                { ModeLogoValueRow(stringResource(R.string.voice_listening_row), state.listenerLabel, state.listenerLogo, { onEvent(SettingsEvent.Open(Route.Listening)) }, problem = state.listenProblem) },
                { ModeToggleRow(stringResource(R.string.voice_talk_back), state.talkBack, { onEvent(SettingsEvent.TalkBack(it)) }, subtitle = stringResource(R.string.voice_talk_back_detail)) },
            ))
            ModeFootnote(state.voiceNote, Modifier.padding(top = ModeSpacing.xxs))
            ModeSectionHeader(stringResource(R.string.settings_ai))
            ModeCard(rows = listOf(
                { AiInUseRow(ai, onEvent) },
                { ModeValueRow(stringResource(R.string.settings_main_model), state.modelName, onClick = { onEvent(SettingsEvent.OpenPicker(false)) }, detail = state.modelEffort) },
                { ModeValueRow(stringResource(R.string.settings_when_stuck_short), state.stuckName, onClick = { onEvent(SettingsEvent.OpenPicker(true)) }, detail = state.stuckEffort) },
            ))
            ModeSectionHeader(stringResource(R.string.access_section))
            ModeCard(rows = listOf(
                {
                    ModeRow(stringResource(R.string.access_how_mode_acts), leading = ModeRowLeading.Mo(MoMood.Idle), onClick = { onEvent(SettingsEvent.Open(Route.Access)) }) {
                        ModeText(stringResource(if (state.fullAccess) R.string.access_full else R.string.access_ask_first_short), ModeTextStyle.Callout, color = c.textSecondary)
                    }
                },
                // Android: the switches phone control needs, as in setup.
                { ModeRow(stringResource(R.string.settings_permissions), leading = ModeRowLeading.Icon(ModeIcon.Tap), onClick = { onEvent(SettingsEvent.Open(Route.PhoneSetup)) }) },
            ))
            ModeSectionHeader(stringResource(R.string.settings_limits))
            if (state.showsCost) {
                ModeCard(rows = listOf(
                    {
                        ModeStepperRow(
                            stringResource(R.string.settings_max_cost), state.maxCostLabel,
                            { onEvent(SettingsEvent.MaxCost((state.maxCost - COST_STEP).coerceAtLeast(COST_MIN))) },
                            { onEvent(SettingsEvent.MaxCost((state.maxCost + COST_STEP).coerceAtMost(COST_MAX))) },
                            stringResource(R.string.settings_less), stringResource(R.string.settings_more),
                        )
                    },
                    {
                        ModeStepperRow(
                            stringResource(R.string.settings_daily_cap), state.dailyCapLabel,
                            { onEvent(SettingsEvent.DailyCap((state.dailyCap - CAP_STEP).coerceAtLeast(0.0))) },
                            { onEvent(SettingsEvent.DailyCap((state.dailyCap + CAP_STEP).coerceAtMost(CAP_MAX))) },
                            stringResource(R.string.settings_less), stringResource(R.string.settings_more),
                        )
                    },
                ))
            } else {
                ModeCard(rows = listOf({ ModeText(ai.planNote, ModeTextStyle.Footnote, Modifier.padding(vertical = ModeSpacing.m), color = c.textSecondary) }))
            }
            ModeCard(Modifier.padding(top = ModeSpacing.xl), rows = listOfNotNull<@Composable () -> Unit>(
                { ModeRow(stringResource(R.string.health_row), leading = ModeRowLeading.Icon(ModeIcon.Wrench), onClick = { onEvent(SettingsEvent.Open(Route.Health)) }) },
                { ModeRow(stringResource(R.string.backup_row), leading = ModeRowLeading.Icon(ModeIcon.Share), onClick = { onEvent(SettingsEvent.Open(Route.Backup)) }) },
                if (state.showsDeveloper) { { ModeRow(stringResource(R.string.developer_title), onClick = { onEvent(SettingsEvent.Open(Route.Developer)) }) } } else null,
            ))
            ModeButton(
                stringResource(if (resetting) R.string.settings_resetting else R.string.settings_reset_mode), { onEvent(SettingsEvent.AskReset) },
                Modifier.fillMaxWidth().padding(top = ModeSpacing.m), role = ModeButtonRole.Destructive, enabled = !resetting,
            )
            update.version?.let { ModeText(it, ModeTextStyle.Footnote, Modifier.fillMaxWidth().padding(top = ModeSpacing.s), color = c.textSecondary, align = TextAlign.Center) }
        }
    }
}

@Composable
private fun AiInUseRow(ai: AiInUse, onEvent: (SettingsEvent) -> Unit) {
    val c = Mode.colors
    Row(Modifier.fillMaxWidth().padding(vertical = ModeSpacing.m), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f).clickable(indication = null, interactionSource = null) { onEvent(SettingsEvent.OpenProvider) },
            horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeLeading(ProviderWords.tile(ai.kind, inUse = true))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                ModeText(stringResource(R.string.providers_ai_in_use), ModeTextStyle.Footnote, color = c.textSecondary)
                ModeText(ai.title, ModeTextStyle.Headline)
                ModeStatusLine(ai.summary, if (ai.connected) c.success else c.ring)
            }
        }
        ModeButton(stringResource(R.string.providers_change), { onEvent(SettingsEvent.ShowProviders) }, Modifier.defaultMinSize(minHeight = ModeSize.touch), role = ModeButtonRole.Pill)
    }
}

/** Connects Settings to its presenters; reset wipes Mode and starts setup again. */
@Composable
fun SettingsRoute(graph: ModeGraph, navigate: (Route) -> Unit, close: () -> Unit, showProviders: () -> Unit, reset: () -> Unit) {
    val presenter: SettingsPresenter = rememberPresenter("settings") { graph.screens.settings() }
    val providers: ProvidersPresenter = rememberPresenter("providers") { graph.screens.providers() }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val pui by providers.state.collectAsStateWithLifecycle()
    val words = rememberWords()
    val updates: com.piyush.mode.app.screens.settings.AppUpdatePresenter = rememberPresenter("appUpdate") { graph.screens.appUpdate() }
    val uui by updates.state.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val debug = remember(context) { DeveloperFeature.enabled(context) }
    val state = remember(ui, debug) { map(ui, words).copy(showsDeveloper = debug) }
    val update = remember(uui) {
        UpdateState(
            version = uui.current.takeIf { it.isNotEmpty() }?.let { words.get(R.string.updates_version, it) },
            available = uui.newer?.let { words.get(R.string.updates_available, it) },
            whatsNew = uui.newer?.takeIf { uui.notesUrl != null }?.let { words.get(R.string.updates_whats_new, it) },
        )
    }
    val ai = remember(pui) { ProviderWords.inUse(pui, words) }
    var name by rememberSaveable(ui.name.isEmpty()) { mutableStateOf(ui.name) }
    var edited by rememberSaveable { mutableStateOf(false) }
    var confirmingReset by remember { mutableStateOf(false) }
    var resetting by rememberSaveable { mutableStateOf(false) }
    fun saveName() {
        if (edited && name != ui.name) presenter.setName(name)
        edited = false
    }
    DisposableEffect(Unit) { onDispose { saveName() } }
    SettingsScreen(state, ai, name, resetting, update) { event ->
        when (event) {
            SettingsEvent.Back -> close()
            is SettingsEvent.Name -> {
                name = event.name
                edited = true
            }
            SettingsEvent.SaveName -> saveName()
            is SettingsEvent.Open -> if (event.route != Route.Developer || state.showsDeveloper) navigate(event.route)
            SettingsEvent.OpenWhatsNew -> uui.notesUrl?.let { url ->
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))) }
            }
            is SettingsEvent.OpenPicker -> navigate(if (state.showsCost) Route.ModelPicker(event.stuck) else Route.PlanModels(event.stuck))
            SettingsEvent.OpenProvider -> navigate(Route.ProviderDetail(ui.provider))
            SettingsEvent.ShowProviders -> showProviders()
            is SettingsEvent.TalkBack -> presenter.setTalkBack(event.on)
            is SettingsEvent.MaxCost -> presenter.setMaxCostPerTask(event.usd)
            is SettingsEvent.DailyCap -> presenter.setDailyCap(event.usd.takeIf { it > 0 })
            SettingsEvent.AskReset -> confirmingReset = true
        }
    }
    if (confirmingReset) {
        ModeActionSheet(
            stringResource(R.string.settings_reset_confirm),
            listOf(ModeSheetAction(stringResource(R.string.settings_reset_mode), destructive = true) {
                resetting = true
                reset()
            }),
            stringResource(R.string.common_cancel),
        ) { confirmingReset = false }
    }
}

private fun map(s: SettingsUiState, w: Words): SettingsState {
    val cap = s.dailyCapUsd
    val speaker = s.speaker
    val voiceValue = when {
        speaker == null -> w.get(R.string.voice_off)
        speaker == "apple" -> s.voiceName ?: w.get(R.string.voice_default_voice)
        else -> "${w.voiceProvider(speaker)} · ${s.voiceName ?: w.get(R.string.voice_default_voice)}"
    }
    return SettingsState(
        modelName = s.model?.name.orEmpty(),
        modelEffort = s.modelEffort?.let(w::effort),
        stuckName = s.stuckModel?.name ?: w.get(R.string.settings_off),
        stuckEffort = s.stuckModel?.let { s.stuckEffort?.let(w::effort) },
        showsCost = s.showsCost,
        maxCost = s.maxCostPerTaskUsd,
        maxCostLabel = w.money(s.maxCostPerTaskUsd),
        dailyCap = cap ?: 0.0,
        dailyCapLabel = cap?.let(w::money) ?: w.get(R.string.settings_daily_cap_off),
        fullAccess = s.fullAccess,
        voiceValue = voiceValue,
        speakerLogo = ProviderWords.voiceLogo(speaker),
        listenerLabel = when (val l = s.listener) {
            null -> w.get(R.string.voice_off)
            "apple" -> w.get(R.string.voice_on_i_phone)
            else -> w.voiceProvider(l)
        },
        listenerLogo = ProviderWords.voiceLogo(s.listener),
        speakProblem = s.speakProblem?.let { w.voiceTrouble(it, hearing = false) },
        listenProblem = s.listenProblem?.let { w.voiceTrouble(it, hearing = true) },
        talkBack = s.talkBack,
        voiceNote = w.get(if (s.speakProblem != null || s.listenProblem != null) R.string.voice_section_note_failed else R.string.voice_section_note),
    )
}

/** Steps for the cost limits (dollars). */
private const val COST_STEP = 0.01
private const val COST_MIN = 0.01
private const val COST_MAX = 1.0
private const val CAP_STEP = 0.1
private const val CAP_MAX = 10.0

/** Names, logos and tiles for AI providers and voice services. */
object ProviderWords {
    enum class Kind { ChatGpt, Claude, OpenRouter }

    fun kind(id: String): Kind = when (id) {
        "chatgpt" -> Kind.ChatGpt
        "claude_oauth" -> Kind.Claude
        else -> Kind.OpenRouter
    }

    fun title(k: Kind, w: Words): String = when (k) {
        Kind.ChatGpt -> w.get(R.string.providers_chatgpt)
        Kind.Claude -> w.get(R.string.providers_claude)
        Kind.OpenRouter -> w.get(R.string.providers_openrouter)
    }

    fun logo(k: Kind): ModeLogo = when (k) {
        Kind.ChatGpt -> ModeLogo.OpenAI
        Kind.Claude -> ModeLogo.Claude
        Kind.OpenRouter -> ModeLogo.OpenRouter
    }

    /** The tile of a provider in a list; the one in use on Settings is ink for ChatGPT. */
    @Composable
    fun tile(k: Kind, inUse: Boolean = false): ModeRowLeading {
        val c = Mode.colors
        return when {
            k == Kind.ChatGpt && inUse -> ModeRowLeading.LogoTile(logo(k), c.textPrimary, c.page)
            k == Kind.ChatGpt -> ModeRowLeading.LogoTile(logo(k), c.cardRaised, c.textPrimary)
            k == Kind.Claude -> ModeRowLeading.LogoTile(logo(k), c.tokens.tileOrange, c.tokens.tileOrangeText)
            else -> ModeRowLeading.LogoTile(logo(k), c.infoSoft, c.infoText)
        }
    }

    fun voiceLogo(id: String?): ModeLogo = when (id) {
        "speechify" -> ModeLogo.Speechify
        "fish" -> ModeLogo.FishAudio
        "elevenlabs" -> ModeLogo.ElevenLabs
        "soniox" -> ModeLogo.Soniox
        "deepgram" -> ModeLogo.Deepgram
        else -> ModeLogo.Android
    }

    fun inUse(s: ProvidersUiState, w: Words): AiInUse {
        val k = kind(s.active)
        val summary = if (s.activeConnected) {
            if (s.connectedCount == 1) w.get(R.string.providers_connected_count_one) else w.get(R.string.providers_connected_count, s.connectedCount)
        } else w.get(R.string.providers_not_connected_yet)
        return AiInUse(title(k, w), k, summary, s.activeConnected, w.get(R.string.providers_plan_note, title(k, w)))
    }
}
