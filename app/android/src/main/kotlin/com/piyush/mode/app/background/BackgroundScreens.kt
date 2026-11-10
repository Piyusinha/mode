package com.piyush.mode.app.background

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.system.SystemPages
import com.piyush.mode.design.ModeButton
import com.piyush.mode.design.ModeButtonRole
import com.piyush.mode.design.ModeGuideMarks
import com.piyush.mode.design.ModeGuideNote
import com.piyush.mode.design.ModeGuideSteps
import com.piyush.mode.design.ModeNoteKind
import com.piyush.mode.design.ModeSetupHeader
import com.piyush.mode.design.ModeSetupTitle
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeRequirement
import com.piyush.mode.design.ModeRequirementList
import com.piyush.mode.design.ModeRequirementState
import com.piyush.mode.design.ModeWaitingLine
import com.piyush.mode.design.MoMood
import com.piyush.mode.feature.connection.domain.BackgroundBlock
import kotlinx.coroutines.launch

/** What one setup page says (boards B01–B06): title, steps, a note, the button and the other way out. */
private class Page(
    @StringRes val title: Int,
    @StringRes val body: Int,
    val mood: MoMood,
    val steps: List<Int>,
    @StringRes val note: Int?,
    val noteKind: ModeNoteKind,
    @StringRes val open: Int,
    @StringRes val alternative: Int? = null,
    @StringRes val waiting: Int? = null,
    val checks: Boolean = false,
    /** The requirements list (boards D06–D08): which of Android 14, Wi-Fi and Developer options fails. */
    val blocked: BackgroundBlock? = null,
)

/** The three things background mode needs, in the order the requirement list shows them. */
private val REQUIREMENTS = listOf(R.string.bg_req_version, R.string.bg_req_wifi, R.string.bg_req_developer)

private val PAGES = mapOf(
    BackgroundStep.Intro to Page(
        R.string.bg_intro_title, R.string.bg_intro_body, MoMood.Working, listOf(R.string.bg_intro_1, R.string.bg_intro_2, R.string.bg_intro_3),
        R.string.bg_intro_note, ModeNoteKind.Info, R.string.bg_intro_open, alternative = R.string.bg_intro_later,
    ),
    BackgroundStep.DevOptions to Page(
        R.string.bg_dev_title, R.string.bg_dev_body, MoMood.YourTurn, listOf(R.string.bg_dev_1, R.string.bg_dev_2, R.string.bg_dev_3),
        R.string.bg_dev_note, ModeNoteKind.Warning, R.string.bg_dev_open, alternative = R.string.bg_dev_done,
    ),
    BackgroundStep.Wireless to Page(
        R.string.bg_wireless_title, R.string.bg_wireless_body, MoMood.YourTurn, listOf(R.string.bg_wireless_1, R.string.bg_wireless_2, R.string.bg_wireless_3),
        R.string.bg_wireless_note, ModeNoteKind.Warning, R.string.bg_wireless_open, waiting = R.string.bg_wireless_waiting,
    ),
    BackgroundStep.Pair to Page(
        R.string.bg_pair_title, R.string.bg_pair_body, MoMood.YourTurn, listOf(R.string.bg_pair_1, R.string.bg_pair_2, R.string.bg_pair_3),
        R.string.bg_pair_note, ModeNoteKind.Info, R.string.bg_pair_open, waiting = R.string.bg_pair_waiting,
    ),
    BackgroundStep.Linking to Page(
        R.string.bg_pair_title, R.string.bg_pair_body, MoMood.Working, listOf(R.string.bg_pair_1, R.string.bg_pair_2, R.string.bg_pair_3),
        null, ModeNoteKind.Info, R.string.bg_pair_open, waiting = R.string.bg_linking,
    ),
    BackgroundStep.Failed to Page(
        R.string.bg_failed_title, R.string.bg_failed_body, MoMood.Stuck, listOf(R.string.bg_failed_1, R.string.bg_failed_2, R.string.bg_failed_3),
        R.string.bg_failed_note, ModeNoteKind.Problem, R.string.bg_failed_open, alternative = R.string.bg_failed_later,
    ),
    BackgroundStep.Linked to Page(
        R.string.bg_linked_title, R.string.bg_linked_body, MoMood.Done, listOf(R.string.bg_linked_1, R.string.bg_linked_2, R.string.bg_linked_3),
        null, ModeNoteKind.Info, R.string.bg_linked_open, alternative = R.string.bg_turn_off, checks = true,
    ),
    BackgroundStep.BlockedVersion to Page(
        R.string.bg_version_title, R.string.bg_version_body, MoMood.Idle, REQUIREMENTS,
        R.string.bg_version_note, ModeNoteKind.Info, R.string.bg_blocked_back, blocked = BackgroundBlock.AndroidTooOld,
    ),
    BackgroundStep.BlockedWifi to Page(
        R.string.bg_wifi_title, R.string.bg_wifi_body, MoMood.YourTurn, REQUIREMENTS,
        null, ModeNoteKind.Info, R.string.bg_wifi_open, alternative = R.string.bg_intro_later, waiting = R.string.bg_wifi_waiting, blocked = BackgroundBlock.NoWifi,
    ),
    BackgroundStep.BlockedPolicy to Page(
        R.string.bg_policy_title, R.string.bg_policy_body, MoMood.Stuck, REQUIREMENTS,
        R.string.bg_policy_note, ModeNoteKind.Problem, R.string.bg_blocked_back, blocked = BackgroundBlock.DebuggingBlocked,
    ),
    BackgroundStep.NoGrant to Page(
        R.string.bg_nogrant_title, R.string.bg_nogrant_body, MoMood.Stuck, listOf(R.string.bg_nogrant_1, R.string.bg_nogrant_2, R.string.bg_nogrant_3),
        R.string.bg_nogrant_note, ModeNoteKind.Problem, R.string.bg_failed_open, alternative = R.string.bg_failed_later,
    ),
)


/** Which requirement row each block fails. */
private fun BackgroundBlock.failsRow(index: Int): Boolean = when (this) {
    BackgroundBlock.AndroidTooOld -> index == 0
    BackgroundBlock.NoWifi -> index == 1
    BackgroundBlock.DebuggingBlocked -> index == 2
}

/** What a requirement row says when it fails, by what blocks it, and when it passes, by row. */
private val FAILS_AS = mapOf(
    BackgroundBlock.AndroidTooOld to R.string.bg_req_version_fails,
    BackgroundBlock.NoWifi to R.string.bg_req_wifi_fails,
    BackgroundBlock.DebuggingBlocked to R.string.bg_req_developer_fails,
)
private val PASSES_AS = mapOf(0 to R.string.bg_req_version_ok, 1 to R.string.bg_req_wifi_ok, 2 to R.string.bg_req_developer_ok)

/** What the user tapped on a setup page. */
enum class BackgroundEvent { Open, Alternative, Back }

/** Settings › Permissions › Work in the background: the setup pages, one step at a time. */
@Composable
fun BackgroundRoute(setup: BackgroundSetup, pages: SystemPages, close: () -> Unit) {
    val step by setup.step.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { setup.refresh() }
    androidx.compose.runtime.DisposableEffect(setup) { onDispose { setup.left() } }
    BackHandler { close() }
    BackgroundScreen(step) { event ->
        when (event) {
            BackgroundEvent.Back -> close()
            BackgroundEvent.Open -> when (step) {
                BackgroundStep.Intro -> setup.begin()
                BackgroundStep.DevOptions -> pages.aboutPhone()
                BackgroundStep.Wireless, BackgroundStep.Pair, BackgroundStep.Linking -> {
                    setup.watchForPairing()
                    pages.wirelessDebugging()
                }
                BackgroundStep.Failed, BackgroundStep.NoGrant -> {
                    setup.tryAgain()
                    pages.wirelessDebugging()
                }
                BackgroundStep.Linked -> close()
                BackgroundStep.BlockedWifi -> pages.wifi()
                BackgroundStep.BlockedVersion, BackgroundStep.BlockedPolicy -> {
                    setup.reset()
                    close()
                }
            }
            BackgroundEvent.Alternative -> when (step) {
                BackgroundStep.DevOptions -> setup.skipDevOptions()
                BackgroundStep.Linked -> scope.launch { setup.turnOff() }
                else -> {
                    setup.reset()
                    close()
                }
            }
        }
    }
}

@Composable
fun BackgroundScreen(step: BackgroundStep, onEvent: (BackgroundEvent) -> Unit) {
    val page = PAGES.getValue(step)
    Column(Modifier.fillMaxSize()) {
        ModeSetupHeader(backLabel = stringResource(R.string.phone_back), onBack = { onEvent(BackgroundEvent.Back) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.l).padding(top = ModeSpacing.xl),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.l),
        ) {
            ModeSetupTitle(stringResource(page.title), stringResource(page.body), page.mood, Modifier.padding(horizontal = ModeSpacing.s))
            val blocked = page.blocked
            if (blocked != null) {
                ModeRequirementList(
                    page.steps.mapIndexed { i, res ->
                        val fails = blocked.failsRow(i)
                        ModeRequirement(
                            stringResource(res),
                            stringResource(if (fails) FAILS_AS.getValue(blocked) else PASSES_AS.getValue(i)),
                            when {
                                !fails -> ModeRequirementState.Met
                                blocked == BackgroundBlock.NoWifi -> ModeRequirementState.Waiting
                                else -> ModeRequirementState.Unmet
                            },
                        )
                    },
                )
            } else {
                ModeGuideSteps(page.steps.map { stringResource(it) }, marks = if (page.checks) ModeGuideMarks.Checks else ModeGuideMarks.Numbers)
            }
            page.note?.let { ModeGuideNote(stringResource(it), page.noteKind) }
            page.waiting?.let { ModeWaitingLine(stringResource(it)) }
        }
        Column(
            Modifier.fillMaxWidth().padding(horizontal = ModeSpacing.l).padding(top = ModeSpacing.m, bottom = ModeSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs),
        ) {
            ModeButton(stringResource(page.open), { onEvent(BackgroundEvent.Open) }, fullWidth = true, enabled = step != BackgroundStep.Linking)
            page.alternative?.let {
                ModeButton(stringResource(it), { onEvent(BackgroundEvent.Alternative) }, Modifier.fillMaxWidth(), role = ModeButtonRole.Link)
            }
        }
    }
}
