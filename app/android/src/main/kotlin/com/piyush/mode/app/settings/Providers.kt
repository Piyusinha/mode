package com.piyush.mode.app.settings

import android.content.ClipboardManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.onboarding.OnboardingPresenter
import com.piyush.mode.app.screens.settings.ChatGptPhase
import com.piyush.mode.app.screens.settings.ConnectChatGptPresenter
import com.piyush.mode.app.screens.settings.ProvidersPresenter
import com.piyush.mode.app.screens.settings.ProvidersUiState
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.app.system.ChatGptSignIn
import com.piyush.mode.app.system.SystemPages
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeButtonRole
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeChoiceTag
import com.piyush.mode.design.ModeFootnote
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeLeading
import com.piyush.mode.design.ModeLogo
import com.piyush.mode.design.ModeProviderCard
import com.piyush.mode.design.ModeProviderRow
import com.piyush.mode.design.ModeRadio
import com.piyush.mode.design.ModeSheet
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextField
import com.piyush.mode.design.ModeTextStyle
import kotlinx.coroutines.flow.MutableStateFlow

/** One provider as the list shows it. */
data class ProviderItem(val id: String, val kind: ProviderWords.Kind, val title: String, val status: String, val connected: Boolean, val inUse: Boolean)

/** The OpenRouter sign-in page answers on mode://openrouter; MainActivity drops it here for the sheet to finish. */
object OpenRouterCallback {
    val url: MutableStateFlow<String?> = MutableStateFlow(null)
}

private enum class Link { None, ChatGpt, OpenRouter, Claude }

/**
 * Change AI: every provider with its state; tap one connected to use it, Connect to sign in. [setup]: the
 * first run's version (ChatGPT recommended, the radio first, a pick before connecting).
 */
@Composable
fun ProvidersRoute(graph: ModeGraph, pages: SystemPages, signIn: ChatGptSignIn, onDone: () -> Unit, setup: Boolean = false) {
    val presenter: ProvidersPresenter = rememberPresenter("providersSheet") { graph.screens.providers() }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val words = rememberWords()
    val rows = remember(ui) { items(ui, words) }
    var link by rememberSaveable { mutableStateOf(Link.None) }
    val connect: (ProviderItem) -> Unit = { row ->
        if (setup) presenter.select(row.id)
        link = when (row.kind) {
            ProviderWords.Kind.ChatGpt -> Link.ChatGpt
            ProviderWords.Kind.Claude -> Link.Claude
            ProviderWords.Kind.OpenRouter -> Link.OpenRouter
        }
    }
    // A sheet closes by itself once its provider is connected.
    LaunchedEffect(rows, link) {
        val kind = when (link) {
            Link.ChatGpt -> ProviderWords.Kind.ChatGpt
            Link.Claude -> ProviderWords.Kind.Claude
            Link.OpenRouter -> ProviderWords.Kind.OpenRouter
            Link.None -> null
        }
        if (kind != null && rows.any { it.kind == kind && it.connected }) link = Link.None
    }
    ModeSheet(onDone, tall = true) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(top = ModeSpacing.l, bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
            Row(Modifier.padding(horizontal = ModeSpacing.xs), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs)) {
                    ModeText(stringResource(R.string.providers_title), ModeTextStyle.Title2)
                    ModeText(stringResource(R.string.providers_sheet_message), ModeTextStyle.Subheadline, color = Mode.colors.textSecondary)
                }
                ModeButton(stringResource(R.string.common_done), onDone, role = ModeButtonRole.Pill)
            }
            val c = Mode.colors
            ModeCard(lifted = true, rows = rows.map { row ->
                {
                    ModeProviderRow(
                        row.title, row.status, ProviderWords.tile(row.kind), row.inUse, row.connected, stringResource(R.string.providers_connect),
                        onSelect = { if (row.connected) presenter.select(row.id) else connect(row) }, onConnect = { connect(row) },
                        tag = when {
                            setup && row.kind == ProviderWords.Kind.ChatGpt -> ModeChoiceTag(stringResource(R.string.providers_recommended), c.tintSoft, c.tint)
                            !setup && row.inUse && row.connected -> ModeChoiceTag(stringResource(R.string.providers_in_use), c.tokens.tagGreen, c.tokens.tagGreenText)
                            else -> null
                        },
                        radioLeading = setup,
                    )
                }
            })
            ModeFootnote(stringResource(R.string.providers_switch_note))
            ModeFootnote(stringResource(R.string.providers_keychain_note), icon = ModeIcon.Lock)
        }
    }
    when (link) {
        Link.OpenRouter -> OpenRouterSheet(presenter, ui, pages) { link = Link.None }
        Link.Claude -> ClaudeSheet(presenter, ui, pages) { link = Link.None }
        Link.ChatGpt -> ChatGptSheet(graph, signIn) { link = Link.None }
        Link.None -> Unit
    }
}

/** Connect OpenRouter: sign in on its page (it answers on mode://openrouter), or paste a key. */
@Composable
private fun OpenRouterSheet(presenter: ProvidersPresenter, ui: ProvidersUiState, pages: SystemPages, onDone: () -> Unit) {
    val c = Mode.colors
    val context = LocalContext.current
    var key by rememberSaveable { mutableStateOf("") }
    var useOnce by rememberSaveable { mutableStateOf(true) }
    var waiting by rememberSaveable { mutableStateOf(false) }
    val callback by OpenRouterCallback.url.collectAsStateWithLifecycle()
    LaunchedEffect(callback) {
        val url = callback ?: return@LaunchedEffect
        OpenRouterCallback.url.value = null
        waiting = false
        presenter.finishOpenRouter(url)
    }
    // Back in Mode without an answer: the page was closed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (waiting && OpenRouterCallback.url.value == null) {
            waiting = false
            presenter.finishOpenRouter(null)
        }
    }
    ModeSheet(onDone, tall = true) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(top = ModeSpacing.l, bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
            SheetHeader(ProviderWords.Kind.OpenRouter, stringResource(R.string.providers_connect_open_router), stringResource(R.string.providers_open_router_message))
            ModeButton(stringResource(R.string.providers_sign_in_open_router), {
                waiting = true
                pages.web(presenter.startOpenRouter(OnboardingPresenter.SIGN_IN_CALLBACK, useOnce))
            }, icon = ModeIcon.Link, fullWidth = true, enabled = !ui.openRouterSigningIn)
            ModeText(stringResource(R.string.setup_key_connect_note), ModeTextStyle.Footnote, Modifier.fillMaxWidth(), color = c.textSecondary, align = TextAlign.Center)
            ui.openRouterError?.let { ModeText(it, ModeTextStyle.Footnote, color = c.danger) }
            ModeText(stringResource(R.string.setup_key_or), ModeTextStyle.Subheadline, Modifier.fillMaxWidth(), color = c.textSecondary, align = TextAlign.Center)
            ModeTextField(stringResource(R.string.setup_key_label), stringResource(R.string.setup_key_prompt), key, { key = it }, secure = true)
            ModeButton(stringResource(R.string.setup_key_paste), {
                context.getSystemService(ClipboardManager::class.java).primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.let { key = it }
            }, role = ModeButtonRole.Pill)
            if (key.isNotEmpty()) ModeButton(stringResource(R.string.settings_save_key), {
                presenter.saveOpenRouterKey(key, useOnce)
                key = ""
            }, Modifier.fillMaxWidth(), role = ModeButtonRole.Secondary)
            Row(
                Modifier.defaultMinSize(minHeight = com.piyush.mode.design.ModeSize.touch).clickable(indication = null, interactionSource = null) { useOnce = !useOnce },
                horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m - ModeSpacing.xxs), verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeRadio(useOnce)
                ModeText(stringResource(R.string.providers_use_once_connected), ModeTextStyle.Subheadline)
            }
            ModeFootnote(stringResource(R.string.setup_key_note), icon = ModeIcon.Lock)
        }
    }
}

/** Connect Claude: open its page, then paste the code it shows. */
@Composable
private fun ClaudeSheet(presenter: ProvidersPresenter, ui: ProvidersUiState, pages: SystemPages, onDone: () -> Unit) {
    val c = Mode.colors
    var code by rememberSaveable { mutableStateOf("") }
    ModeSheet({ presenter.cancelClaude(); onDone() }, tall = true) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(top = ModeSpacing.l, bottom = ModeSpacing.xxl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.l)) {
            SheetHeader(ProviderWords.Kind.Claude, stringResource(R.string.providers_connect_claude), stringResource(R.string.claude_note))
            if (ui.claude.awaitingCode) {
                ModeText(stringResource(R.string.claude_instructions), ModeTextStyle.Footnote, color = c.textSecondary)
                ModeTextField(stringResource(R.string.claude_code), stringResource(R.string.claude_code_prompt), code, { code = it }, secure = true)
                ModeButton(stringResource(if (ui.claude.signingIn) R.string.claude_connecting else R.string.claude_finish), {
                    presenter.finishClaude(code)
                    code = ""
                }, fullWidth = true, enabled = !ui.claude.signingIn && code.isNotBlank())
            } else {
                ModeButton(stringResource(R.string.claude_connect), { presenter.startClaude()?.let(pages::web) }, icon = ModeIcon.Claude, fullWidth = true)
            }
            ui.claude.error?.let { ModeText(it, ModeTextStyle.Footnote, color = c.danger) }
            ModeFootnote(stringResource(R.string.providers_keychain_note), icon = ModeIcon.Lock)
        }
    }
}

/** Connect ChatGPT: the same card as the first run, signing in through the loopback. */
@Composable
private fun ChatGptSheet(graph: ModeGraph, signIn: ChatGptSignIn, onDone: () -> Unit) {
    val presenter: ConnectChatGptPresenter = rememberPresenter("chatgpt") { graph.screens.connectChatGpt() }
    val ui by presenter.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { signIn.resumed() }
    val name = stringResource(R.string.name_chatgpt)
    val start = { signIn.start(presenter.loopbackPort, presenter::start, onAnswer = presenter::finish, onCancelled = presenter::cancelled) }
    ModeSheet({ signIn.stop(); onDone() }) {
        Column(Modifier.padding(horizontal = ModeSpacing.screenEdge).padding(top = ModeSpacing.l, bottom = ModeSpacing.xxl)) {
            when (val phase = ui.phase) {
                ChatGptPhase.Idle -> ModeProviderCard(ModeLogo.OpenAI, name, stringResource(R.string.first_run_chat_gpt_detail)) {
                    ModeButton(stringResource(R.string.first_run_connect), start, role = ModeButtonRole.Main)
                }
                ChatGptPhase.Waiting, ChatGptPhase.Checking -> ModeProviderCard(
                    ModeLogo.OpenAI, stringResource(if (phase == ChatGptPhase.Waiting) R.string.first_run_waiting else R.string.first_run_checking),
                    stringResource(R.string.first_run_waiting_detail),
                ) { ModeButton(stringResource(R.string.first_run_cancel), { signIn.stop(); presenter.cancelled() }, role = ModeButtonRole.Other) }
                is ChatGptPhase.Connected -> ModeProviderCard(ModeLogo.OpenAI, name, phase.check)
                is ChatGptPhase.Failed -> ModeProviderCard(
                    ModeLogo.OpenAI, stringResource(if (phase.notEligible) R.string.first_run_not_eligible else R.string.first_run_failed),
                    if (phase.notEligible) stringResource(R.string.first_run_not_eligible_detail) else phase.message, warning = phase.notEligible,
                ) { ModeButton(stringResource(R.string.first_run_try_again), start, role = ModeButtonRole.Main) }
            }
        }
    }
}

@Composable
private fun SheetHeader(kind: ProviderWords.Kind, title: String, message: String) {
    Row(Modifier.padding(horizontal = ModeSpacing.xs), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        ModeLeading(ProviderWords.tile(kind))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            ModeText(title, ModeTextStyle.Title2)
            ModeText(message, ModeTextStyle.Subheadline, color = Mode.colors.textSecondary)
        }
    }
}

private fun items(s: ProvidersUiState, w: Words): List<ProviderItem> = s.rows.map { r ->
    val k = ProviderWords.kind(r.id)
    val detail = when (k) {
        ProviderWords.Kind.ChatGpt -> w.get(R.string.providers_chatgpt_detail)
        ProviderWords.Kind.Claude -> w.get(R.string.providers_claude_detail)
        ProviderWords.Kind.OpenRouter -> r.keyEnding?.let { w.get(R.string.providers_key_ending, it) } ?: w.get(R.string.providers_openrouter_detail)
    }
    val state = w.get(if (r.connected) R.string.providers_connected else R.string.providers_not_connected)
    ProviderItem(r.id, k, ProviderWords.title(k, w), r.account ?: "$state · $detail", r.connected, r.inUse)
}
