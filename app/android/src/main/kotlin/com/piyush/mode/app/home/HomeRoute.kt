package com.piyush.mode.app.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.run.RunStatus
import com.piyush.mode.app.screens.common.RelativeDay
import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.home.HomePresenter
import com.piyush.mode.app.screens.home.HomeUiState
import com.piyush.mode.app.screens.home.RecentConversation
import com.piyush.mode.app.screens.home.SetupTip
import com.piyush.mode.app.screens.home.Suggestion
import com.piyush.mode.app.shell.Route
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeRowLeading
import com.piyush.mode.design.ModeTip
import com.piyush.mode.design.ModeTipTone
import com.piyush.mode.design.MoMood
import kotlinx.coroutines.delay

/** Connects Home to its presenter: maps its state, and turns taps into intents and navigation. */
@Composable
fun HomeRoute(graph: ModeGraph, run: RunStatus, navigate: (Route) -> Unit, openLive: () -> Unit) {
    val presenter: HomePresenter = rememberPresenter("home") { graph.screens.home() }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val words = rememberWords()
    val state = remember(ui) { map(ui, words) }
    var showingUndo by rememberSaveable { mutableStateOf(false) }
    var undoShown by remember { mutableIntStateOf(0) }
    // After a swipe the carousel waits longer before moving on by itself.
    var swiped by remember { mutableIntStateOf(0) }

    LaunchedEffect(undoShown) {
        if (undoShown == 0) return@LaunchedEffect
        delay(UNDO_MS)
        showingUndo = false
    }
    LaunchedEffect(ui.tips?.showing, swiped) {
        val tips = ui.tips ?: return@LaunchedEffect
        if (tips.tips.size < 2) return@LaunchedEffect
        delay(if (swiped > 0) AFTER_SWIPE_MS else AUTO_SCROLL_MS)
        val i = tips.tips.indexOf(tips.showing)
        presenter.showTip(tips.tips[(i + 1) % tips.tips.size])
    }

    HomeScreen(state, run.state.running, showingUndo) { event ->
        when (event) {
            is HomeEvent.Suggestion -> presenter.ask(event.text)?.let { navigate(Route.Conversation(it)) }
            is HomeEvent.OpenConversation -> navigate(Route.Conversation(event.id))
            HomeEvent.AddKey -> navigate(Route.Settings)
            HomeEvent.PauseToggle -> run.togglePause()
            HomeEvent.Stop -> run.stop()
            HomeEvent.OpenRun -> openLive()
            is HomeEvent.TipAction -> when (ui.tips?.tips?.getOrNull(event.index)) {
                SetupTip.Voice -> navigate(Route.Voice)
                SetupTip.Listening -> navigate(Route.Listening)
                SetupTip.Access -> navigate(Route.Access)
                SetupTip.StartWithoutOpening -> navigate(Route.UnlockVoice)
                SetupTip.Name -> navigate(Route.Settings)
                null -> Unit
            }
            is HomeEvent.PickTip -> ui.tips?.tips?.getOrNull(event.index)?.let {
                presenter.showTip(it)
                swiped++
            }
            HomeEvent.CloseTips -> {
                presenter.hideTips()
                showingUndo = true
                undoShown++
            }
            HomeEvent.UndoCloseTips -> {
                showingUndo = false
                presenter.undoHideTips()
            }
        }
    }
}

private fun map(s: HomeUiState, words: Words): HomeState = HomeState(
    greeting = words.greeting(s.partOfDay, s.name),
    suggestions = s.suggestions.map { suggestion ->
        when (val reason = suggestion.reason) {
            is Suggestion.Reason.Often -> HomeSuggestion(suggestion.text, words.get(R.string.home_suggestion_often, reason.times), ModeIcon.RepeatTask)
            is Suggestion.Reason.Learned -> HomeSuggestion(
                suggestion.text,
                reason.app?.let { words.get(R.string.home_suggestion_learned_in, it) } ?: words.get(R.string.home_suggestion_learned),
                ModeIcon.Skill,
            )
        }
    },
    recent = s.recent.map { RecentRow(it.conversationId, it.title, `when`(it, words), leading(it.badge)) },
    needsKey = s.needsKey,
    tips = s.tips?.let { t -> HomeTips(t.tips.map { tip(it, words) }, t.tips.indexOf(t.showing).coerceAtLeast(0), t.closable) },
    tryThese = s.tryThese,
)

/** "Today 8:13 · 62 s", "Yesterday · 37 s". */
private fun `when`(c: RecentConversation, words: Words): String {
    val day = words.day(c.day) + if (c.day == RelativeDay.Today) " " + words.timeOfDay(c.atMs) else ""
    return listOfNotNull(day, c.durationSeconds?.let { words.seconds(it.toDouble()) }).joinToString(" · ")
}

private fun leading(badge: TaskBadge?): ModeRowLeading = when (badge) {
    TaskBadge.Done -> ModeRowLeading.Mo(MoMood.Done)
    TaskBadge.Failed, TaskBadge.GaveUp -> ModeRowLeading.Mo(MoMood.Stuck)
    TaskBadge.Stopped, TaskBadge.NotStarted -> ModeRowLeading.Mo(MoMood.Sleep)
    null -> ModeRowLeading.Mo(MoMood.Working)
}

/** Each tip's words, Mo's face, its badge and its colour (as on the board). */
private fun tip(tip: SetupTip, w: Words): ModeTip = when (tip) {
    SetupTip.Voice -> ModeTip(w.get(R.string.tips_voice_title), w.get(R.string.tips_voice_message), w.get(R.string.tips_voice_action), MoMood.Listening, ModeIcon.Speaker, ModeTipTone.Coral)
    SetupTip.Listening -> ModeTip(w.get(R.string.tips_listening_title), w.get(R.string.tips_listening_message), w.get(R.string.tips_listening_action), MoMood.Idle, ModeIcon.Microphone, ModeTipTone.Blue)
    SetupTip.Access -> ModeTip(w.get(R.string.tips_access_title), w.get(R.string.tips_access_message), w.get(R.string.tips_access_action), MoMood.Done, ModeIcon.Shield, ModeTipTone.Green)
    SetupTip.StartWithoutOpening -> ModeTip(w.get(R.string.tips_start_title), w.get(R.string.tips_start_message), w.get(R.string.tips_start_action), MoMood.Working, ModeIcon.Power, ModeTipTone.Purple)
    SetupTip.Name -> ModeTip(w.get(R.string.tips_name_title), w.get(R.string.tips_name_message), w.get(R.string.tips_name_action), MoMood.YourTurn, ModeIcon.Profile, ModeTipTone.Orange)
}

/** How long Undo stays after closing the tips, and how often the carousel moves on. */
private const val UNDO_MS = 5_000L
private const val AUTO_SCROLL_MS = 6_000L
private const val AFTER_SWIPE_MS = 12_000L
