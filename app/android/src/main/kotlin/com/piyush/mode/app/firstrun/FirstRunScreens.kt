package com.piyush.mode.app.firstrun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import com.piyush.mode.app.R
import com.piyush.mode.app.screens.firstrun.ConnectState
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeButtonRole
import com.piyush.mode.design.ModeFill
import com.piyush.mode.design.ModeJobButton
import com.piyush.mode.design.ModeJobField
import com.piyush.mode.design.ModeLogo
import com.piyush.mode.design.ModeMo
import com.piyush.mode.design.ModeMoreRow
import com.piyush.mode.design.ModeProviderCard
import com.piyush.mode.design.ModeSetupHeader
import com.piyush.mode.design.ModeSize
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.MoMood

/** What the user does on the first run's pages. */
sealed interface FirstRunEvent {
    data object GetStarted : FirstRunEvent
    data object SeeCode : FirstRunEvent
    data object ConnectChatGpt : FirstRunEvent
    data object CancelSignIn : FirstRunEvent
    data object MoreOptions : FirstRunEvent
    data object Next : FirstRunEvent
    data object Back : FirstRunEvent
    data class StartJob(val text: String) : FirstRunEvent
    data object Skip : FirstRunEvent
}

/** Which dot a page is, of how many. */
data class StepDots(val current: Int, val total: Int)

@Composable
private fun header(dots: StepDots, onEvent: (FirstRunEvent) -> Unit) {
    ModeSetupHeader(
        backLabel = stringResource(R.string.common_back),
        onBack = { onEvent(FirstRunEvent.Back) },
        step = Triple(dots.current, dots.total, stringResource(R.string.first_run_step, dots.current, dots.total)),
    )
}

/** Welcome: Mo, what Mode is, and Get started. */
@Composable
fun WelcomeScreen(onEvent: (FirstRunEvent) -> Unit) {
    val c = Mode.colors
    Column(
        Modifier.fillMaxSize().padding(horizontal = ModeSpacing.page).padding(bottom = ModeSpacing.l),
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.l),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ModeFill()
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxl)) {
            ModeMo(MoMood.Idle, ModeSize.moWelcome)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
                ModeText(stringResource(R.string.first_run_welcome_title), ModeTextStyle.Title, Modifier.semantics { heading() }, align = TextAlign.Center)
                ModeText(stringResource(R.string.first_run_welcome_body), ModeTextStyle.Body, align = TextAlign.Center)
                ModeText(stringResource(R.string.first_run_welcome_caveat), ModeTextStyle.Subheadline, color = c.textSecondary, align = TextAlign.Center)
            }
        }
        ModeFill()
        ModeButton(stringResource(R.string.first_run_get_started), { onEvent(FirstRunEvent.GetStarted) }, fullWidth = true)
        ModeButton(stringResource(R.string.first_run_see_code), { onEvent(FirstRunEvent.SeeCode) }, role = ModeButtonRole.Plain)
    }
}

/** Connect your AI: ChatGPT's card in each of its states, More options, and Continue once connected. */
@Composable
fun ConnectScreen(connect: ConnectState, connected: Boolean, dots: StepDots, onEvent: (FirstRunEvent) -> Unit) {
    val c = Mode.colors
    Column(Modifier.fillMaxSize()) {
        header(dots, onEvent)
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = ModeSpacing.xl).padding(top = ModeSpacing.l, bottom = ModeSpacing.l),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.xl + ModeSpacing.xs),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs)) {
                ModeText(stringResource(R.string.first_run_connect_title), ModeTextStyle.TabTitle, Modifier.semantics { heading() })
                ModeText(stringResource(R.string.first_run_connect_body), ModeTextStyle.Callout)
            }
            Column(verticalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs)) {
                ChatGptCard(connect, onEvent)
                ModeMoreRow(stringResource(R.string.first_run_more_options), { onEvent(FirstRunEvent.MoreOptions) })
                ModeText(
                    stringResource(R.string.first_run_keychain), ModeTextStyle.Footnote,
                    Modifier.padding(horizontal = ModeSpacing.labelInset), color = c.textSecondary,
                )
            }
            ModeFill()
            ModeButton(stringResource(R.string.first_run_continue), { onEvent(FirstRunEvent.Next) }, fullWidth = true, enabled = connected)
        }
    }
}

@Composable
private fun ChatGptCard(connect: ConnectState, onEvent: (FirstRunEvent) -> Unit) {
    val name = stringResource(R.string.name_chatgpt)
    when (connect) {
        ConnectState.Choose -> ModeProviderCard(
            ModeLogo.OpenAI, name, stringResource(R.string.first_run_chat_gpt_detail), tag = stringResource(R.string.first_run_recommended),
        ) { ModeButton(stringResource(R.string.first_run_connect), { onEvent(FirstRunEvent.ConnectChatGpt) }, role = ModeButtonRole.Main) }
        ConnectState.Waiting, ConnectState.Checking -> ModeProviderCard(
            ModeLogo.OpenAI,
            stringResource(if (connect == ConnectState.Waiting) R.string.first_run_waiting else R.string.first_run_checking),
            stringResource(R.string.first_run_waiting_detail),
        ) { ModeButton(stringResource(R.string.first_run_cancel), { onEvent(FirstRunEvent.CancelSignIn) }, role = ModeButtonRole.Other) }
        // The AI that is connected and in use: ChatGPT, or one from More options (Claude, OpenRouter).
        is ConnectState.Connected -> {
            val kind = com.piyush.mode.app.settings.ProviderWords.kind(connect.provider)
            ModeProviderCard(
                com.piyush.mode.app.settings.ProviderWords.logo(kind), com.piyush.mode.app.settings.ProviderWords.title(kind, com.piyush.mode.app.common.rememberWords()),
                stringResource(R.string.first_run_connected),
            )
        }
        is ConnectState.Failed -> if (connect.notEligible) {
            ModeProviderCard(
                ModeLogo.OpenAI, stringResource(R.string.first_run_not_eligible), stringResource(R.string.first_run_not_eligible_detail), warning = true,
            ) {
                ModeButton(stringResource(R.string.first_run_use_open_router), { onEvent(FirstRunEvent.MoreOptions) }, role = ModeButtonRole.Main)
                ModeButton(stringResource(R.string.first_run_try_again), { onEvent(FirstRunEvent.ConnectChatGpt) }, role = ModeButtonRole.Other)
            }
        } else {
            ModeProviderCard(ModeLogo.OpenAI, stringResource(R.string.first_run_failed), connect.message) {
                ModeButton(stringResource(R.string.first_run_try_again), { onEvent(FirstRunEvent.ConnectChatGpt) }, role = ModeButtonRole.Main)
            }
        }
    }
}

/** Give Mo its first job: three small jobs, or type your own. */
@Composable
fun FirstJobScreen(jobs: List<String>, starting: Boolean, dots: StepDots, onEvent: (FirstRunEvent) -> Unit) {
    val c = Mode.colors
    var own by rememberSaveable { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        header(dots, onEvent)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.xl).padding(top = ModeSpacing.l, bottom = ModeSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.xl + ModeSpacing.xs),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ModeSpacing.l)) {
                ModeMo(if (starting) MoMood.Working else MoMood.Listening, ModeSize.moFirstJob)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs)) {
                    ModeText(stringResource(R.string.first_run_first_job_title), ModeTextStyle.TabTitle, Modifier.semantics { heading() }, align = TextAlign.Center)
                    ModeText(stringResource(R.string.first_run_first_job_body), ModeTextStyle.Callout, align = TextAlign.Center)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs)) {
                jobs.forEach { job -> ModeJobButton(job, { onEvent(FirstRunEvent.StartJob(job)) }, enabled = !starting) }
                ModeJobField(
                    own, { own = it }, stringResource(R.string.first_run_own_job), stringResource(R.string.first_run_start),
                    { onEvent(FirstRunEvent.StartJob(own)) }, enabled = !starting,
                )
                ModeText(
                    stringResource(if (starting) R.string.first_run_starting else R.string.first_run_permissions_note), ModeTextStyle.Footnote,
                    Modifier.padding(horizontal = ModeSpacing.labelInset), color = c.textSecondary,
                )
            }
            ModeButton(
                stringResource(R.string.first_run_skip), { onEvent(FirstRunEvent.Skip) }, Modifier.align(Alignment.CenterHorizontally),
                role = ModeButtonRole.Plain, enabled = !starting,
            )
        }
    }
}
