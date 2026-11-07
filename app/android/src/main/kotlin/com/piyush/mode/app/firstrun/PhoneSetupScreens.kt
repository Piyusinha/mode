package com.piyush.mode.app.firstrun

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.piyush.mode.app.R
import com.piyush.mode.app.screens.firstrun.PhoneSetupGuide
import com.piyush.mode.app.screens.firstrun.PhoneSetupKind
import com.piyush.mode.app.screens.firstrun.PhoneSetupRow
import com.piyush.mode.app.screens.firstrun.PhoneSetupUiState
import com.piyush.mode.core.platform.PermissionStatus
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeButtonRole
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeFill
import com.piyush.mode.design.ModeGuideNote
import com.piyush.mode.design.ModeGuideSteps
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeNoteKind
import com.piyush.mode.design.ModePermissionRow
import com.piyush.mode.design.ModeSetupHeader
import com.piyush.mode.design.ModeSetupTitle
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeStatusAction
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeWaitingLine
import com.piyush.mode.design.MoMood

/** What the user does on "Let Mode use your phone" and its guides. */
sealed interface PhoneSetupEvent {
    data class Allow(val row: PhoneSetupRow) : PhoneSetupEvent
    /** The guide's main button: off to the Android page it explains. */
    data class Open(val guide: PhoneSetupGuide) : PhoneSetupEvent
    /** The guide's second button ("I've done this", "Skip for now"). */
    data class Alternative(val guide: PhoneSetupGuide) : PhoneSetupEvent
    data object CloseGuide : PhoneSetupEvent
    data object Continue : PhoneSetupEvent
    data object Back : PhoneSetupEvent
}

/** The hub, or the guide open over it. [dots] is null outside the first run (Settings › Phone control). */
@Composable
fun PhoneSetupScreen(state: PhoneSetupUiState, dots: StepDots?, onEvent: (PhoneSetupEvent) -> Unit) {
    val guide = state.guide
    if (guide != null) GuideScreen(guide, onEvent) else HubScreen(state, dots, onEvent)
}

@Composable
private fun HubScreen(state: PhoneSetupUiState, dots: StepDots?, onEvent: (PhoneSetupEvent) -> Unit) {
    val c = Mode.colors
    Column(Modifier.fillMaxSize()) {
        ModeSetupHeader(
            backLabel = stringResource(R.string.phone_back),
            onBack = { onEvent(PhoneSetupEvent.Back) },
            step = dots?.let { Triple(it.current, it.total, stringResource(R.string.first_run_step, it.current, it.total)) },
        )
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.l).padding(top = ModeSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.xl + ModeSpacing.xs),
        ) {
            ModeSetupTitle(
                stringResource(R.string.phone_title), stringResource(R.string.phone_body),
                if (state.ready) MoMood.Done else MoMood.YourTurn, Modifier.padding(horizontal = ModeSpacing.s),
            )
            ModeCard(lifted = true, rows = state.rows.map { row -> { PhoneRow(row, onEvent) } })
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = ModeSpacing.l).padding(top = ModeSpacing.m, bottom = ModeSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs),
        ) {
            if (!state.ready) {
                ModeText(stringResource(R.string.phone_continue_hint), ModeTextStyle.Footnote, Modifier.fillMaxWidth(), color = c.textSecondary, align = TextAlign.Center)
            }
            ModeButton(stringResource(R.string.phone_continue), { onEvent(PhoneSetupEvent.Continue) }, fullWidth = true, enabled = state.ready)
        }
    }
}

@Composable
private fun PhoneRow(row: PhoneSetupRow, onEvent: (PhoneSetupEvent) -> Unit) {
    val words = ROWS.getValue(row.kind)
    val allowed = row.status == PermissionStatus.Allowed
    val status = when {
        allowed -> ModeStatusAction.Done(stringResource(words.done))
        row.later -> ModeStatusAction.Note(stringResource(R.string.phone_later))
        row.kind == PhoneSetupKind.PhoneControl -> ModeStatusAction.Prominent(stringResource(words.action))
        else -> ModeStatusAction.Quiet(stringResource(words.action))
    }
    ModePermissionRow(
        stringResource(words.title), stringResource(words.why), words.icon, status,
        { onEvent(PhoneSetupEvent.Allow(row)) }, highlighted = row.kind == PhoneSetupKind.PhoneControl,
    )
}

private class RowWords(@StringRes val title: Int, @StringRes val why: Int, val icon: ModeIcon, @StringRes val action: Int, @StringRes val done: Int)

private val ROWS = mapOf(
    PhoneSetupKind.PhoneControl to RowWords(R.string.phone_control, R.string.phone_control_why, ModeIcon.Tap, R.string.phone_turn_on, R.string.phone_on),
    PhoneSetupKind.Notifications to RowWords(R.string.phone_notifications, R.string.phone_notifications_why, ModeIcon.Bell, R.string.phone_allow, R.string.phone_allowed),
    PhoneSetupKind.Assistant to RowWords(R.string.phone_assistant, R.string.phone_assistant_why, ModeIcon.Power, R.string.phone_set, R.string.phone_on),
    PhoneSetupKind.Battery to RowWords(R.string.phone_battery, R.string.phone_battery_why, ModeIcon.Battery, R.string.phone_allow, R.string.phone_allowed),
    PhoneSetupKind.Microphone to RowWords(R.string.phone_microphone, R.string.phone_microphone_why, ModeIcon.Microphone, R.string.phone_allow, R.string.phone_allowed),
)

/** One guide page: what to do in Android's Settings, step by step, and the button that goes there. */
@Composable
private fun GuideScreen(guide: PhoneSetupGuide, onEvent: (PhoneSetupEvent) -> Unit) {
    val g = GUIDES.getValue(guide)
    Column(Modifier.fillMaxSize()) {
        ModeSetupHeader(backLabel = stringResource(R.string.phone_back), onBack = { onEvent(PhoneSetupEvent.CloseGuide) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.l).padding(top = ModeSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.l),
        ) {
            ModeSetupTitle(stringResource(g.title), stringResource(g.body), g.mood, Modifier.padding(horizontal = ModeSpacing.s))
            ModeGuideSteps(g.steps.map { stringResource(it) })
            ModeGuideNote(stringResource(g.note), g.noteKind)
            if (g.waiting != null) ModeWaitingLine(stringResource(g.waiting))
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = ModeSpacing.l).padding(top = ModeSpacing.m, bottom = ModeSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs),
        ) {
            ModeButton(stringResource(g.open), { onEvent(PhoneSetupEvent.Open(guide)) }, fullWidth = true)
            if (g.alternative != null) {
                ModeButton(stringResource(g.alternative), { onEvent(PhoneSetupEvent.Alternative(guide)) }, Modifier.fillMaxWidth(), role = ModeButtonRole.Link)
            }
        }
    }
}

private class GuideWords(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val mood: MoMood,
    val steps: List<Int>,
    @StringRes val note: Int,
    val noteKind: ModeNoteKind,
    @StringRes val open: Int,
    @StringRes val alternative: Int? = null,
    @StringRes val waiting: Int? = null,
)

private val GUIDES = mapOf(
    PhoneSetupGuide.RestrictedSettings to GuideWords(
        R.string.guide_restricted_title, R.string.guide_restricted_body, MoMood.YourTurn,
        listOf(R.string.guide_restricted_1, R.string.guide_restricted_2, R.string.guide_restricted_3),
        R.string.guide_restricted_note, ModeNoteKind.Warning, R.string.guide_restricted_open, alternative = R.string.guide_restricted_done,
    ),
    PhoneSetupGuide.PhoneControl to GuideWords(
        R.string.guide_access_title, R.string.guide_access_body, MoMood.YourTurn,
        listOf(R.string.guide_access_1, R.string.guide_access_2, R.string.guide_access_3),
        R.string.guide_access_note, ModeNoteKind.Info, R.string.guide_access_open, waiting = R.string.guide_access_waiting,
    ),
    PhoneSetupGuide.Assistant to GuideWords(
        R.string.guide_assistant_title, R.string.guide_assistant_body, MoMood.Listening,
        listOf(R.string.guide_assistant_1, R.string.guide_assistant_2, R.string.guide_assistant_3),
        R.string.guide_assistant_note, ModeNoteKind.Info, R.string.guide_assistant_open, alternative = R.string.guide_skip,
    ),
    PhoneSetupGuide.Battery to GuideWords(
        R.string.guide_battery_title, R.string.guide_battery_body, MoMood.Sleep,
        listOf(R.string.guide_battery_1, R.string.guide_battery_2),
        R.string.guide_battery_note, ModeNoteKind.Info, R.string.guide_battery_open, alternative = R.string.guide_skip,
    ),
)
