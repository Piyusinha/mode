package com.piyush.mode.app.live

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.conversation.block
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.home.runningWords
import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.run.LiveRunPresenter
import com.piyush.mode.app.screens.run.LiveRunUiState
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeBadge
import com.piyush.mode.design.ModeBadgeTone
import com.piyush.mode.design.ModeEmptyState
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeIconButton
import com.piyush.mode.design.ModeRadius
import com.piyush.mode.design.ModeReply
import com.piyush.mode.design.ModeReplyBlock
import com.piyush.mode.design.ModeRunBadge
import com.piyush.mode.design.ModeRunPanel
import com.piyush.mode.design.ModeSegment
import com.piyush.mode.design.ModeSegmentState
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeSpinner
import com.piyush.mode.design.ModeText
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.text.style.TextAlign
import com.piyush.mode.design.ModeSize
import com.piyush.mode.design.MoMood
import com.piyush.mode.design.ModeMo
import com.piyush.mode.design.ModeVoiceButtonStyle
import com.piyush.mode.design.ModeVoiceButton
import com.piyush.mode.design.ModeTalkStatus
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.modeShadow

data class LiveRunning(val badge: String, val sinceMs: Long?, val paused: Boolean, val task: String, val now: String, val segments: List<ModeSegment>)
data class LiveFinished(val badge: Triple<String, ModeIcon, ModeBadgeTone>, val reply: List<ModeReplyBlock>, val line: String)
data class LiveState(
    val running: LiveRunning? = null,
    val screen: ImageBitmap? = null,
    val finished: LiveFinished? = null,
    /** Background mode: the task runs on a hidden screen; [takenOver] while the user has its app. */
    val background: Boolean = false,
    val takenOver: Boolean = false,
)

sealed interface LiveEvent {
    data object Stop : LiveEvent
    data object TogglePause : LiveEvent
    data object Close : LiveEvent
    /** Background mode: the user takes the app (Mode pauses), then gives it back. */
    data object TakeOver : LiveEvent
    data object GiveBack : LiveEvent
}

/** Live run, as on its board: the screen Mode sees right now, and the panel with what it's doing. */
@Composable
fun LiveScreen(state: LiveState, onEvent: (LiveEvent) -> Unit) {
    val c = Mode.colors
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = ModeSpacing.screenEdge), verticalAlignment = Alignment.CenterVertically) {
            ModeIconButton(ModeIcon.Back, stringResource(R.string.common_back), { onEvent(LiveEvent.Close) })
            Box(Modifier.weight(1f), Alignment.Center) {
                when {
                    state.running != null && state.takenOver -> ModeTalkStatus(stringResource(R.string.live_in_control_pill), c.info)
                    state.running != null && state.background -> ModeTalkStatus(stringResource(R.string.live_background_pill), c.brand)
                    else -> state.running?.let { ModeRunBadge(it.badge, it.sinceMs, it.paused) }
                }
            }
            ModeIconButton(ModeIcon.Shrink, stringResource(R.string.run_shrink), { onEvent(LiveEvent.Close) })
        }
        val screen = state.screen
        when {
            state.running != null && state.takenOver -> Column(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = ModeSpacing.xl),
                verticalArrangement = Arrangement.spacedBy(ModeSpacing.l, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ModeMo(MoMood.YourTurn, ModeSize.talkMo)
                ModeText(stringResource(R.string.live_in_control), ModeTextStyle.Title2, align = TextAlign.Center)
                ModeText(stringResource(R.string.live_in_control_body), ModeTextStyle.Subheadline, color = c.textSecondary, align = TextAlign.Center)
                ModeVoiceButton(stringResource(R.string.live_give_back), ModeVoiceButtonStyle.Ink, { onEvent(LiveEvent.GiveBack) }, height = ModeSize.primaryButton)
            }
            screen != null -> {
                val frame = RoundedCornerShape(ModeRadius.liveScreen)
                Box(
                    Modifier.weight(1f).fillMaxWidth().padding(horizontal = ModeSpacing.xl).modeShadow(frame).background(c.card, frame).padding(ModeSpacing.l),
                    Alignment.Center,
                ) {
                    Image(screen, null, Modifier.clip(RoundedCornerShape(ModeRadius.card)), contentScale = ContentScale.Fit)
                    if (state.background) {
                        com.piyush.mode.design.ModeScreenTag(stringResource(R.string.live_hidden_screen), Modifier.align(Alignment.TopStart))
                    }
                }
                if (state.background && state.running != null) {
                    ModeVoiceButton(
                        stringResource(R.string.live_take_over), ModeVoiceButtonStyle.Raised, { onEvent(LiveEvent.TakeOver) },
                        Modifier.padding(horizontal = ModeSpacing.xl),
                    )
                }
            }
            state.running == null && state.finished != null -> {
                val f = state.finished
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(ModeSpacing.screenEdge), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
                    ModeBadge(f.badge.first, f.badge.second, f.badge.third)
                    ModeReply(f.reply)
                    ModeText(f.line, ModeTextStyle.Footnote, color = c.textSecondary)
                }
            }
            state.running == null -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
                ModeEmptyState(ModeIcon.Running, stringResource(R.string.live_idle_title), stringResource(R.string.live_idle_message))
            }
            else -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { ModeSpinner() }
        }
        state.running?.let { r ->
            ModeRunPanel(
                r.task, r.now, r.segments, r.paused, runningWords(), { onEvent(LiveEvent.TogglePause) }, { onEvent(LiveEvent.Stop) },
                Modifier.padding(horizontal = ModeSpacing.s).padding(bottom = ModeSpacing.s),
            )
        }
    }
}

/** Connects the live run to its presenter; the screenshot is decoded only when it changes. */
@Composable
fun LiveRoute(graph: ModeGraph, close: () -> Unit) {
    val presenter: LiveRunPresenter = rememberPresenter("live") { graph.screens.liveRun() }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val words = rememberWords()
    val screen = remember(ui.screen) { ui.screen?.let { BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() } }
    val state = remember(ui, screen) { map(ui, screen, words) }
    // Background mode: whether this task runs on the hidden screen, and whether the user has taken its app.
    val connection by graph.session.state.collectAsStateWithLifecycle()
    val background = (connection as? com.piyush.mode.feature.connection.domain.ConnectionState.Connected)?.route ==
        com.piyush.mode.feature.connection.data.AccessibilityPhoneLink.BACKGROUND_ROUTE
    var takenOver by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    LiveScreen(state.copy(background = background, takenOver = takenOver)) { event ->
        when (event) {
            LiveEvent.Stop -> presenter.stop()
            LiveEvent.TogglePause -> if (state.running?.paused == true) presenter.resume() else presenter.pause()
            LiveEvent.Close -> close()
            LiveEvent.TakeOver -> scope.launch {
                presenter.pause()
                if (com.piyush.mode.app.graph.background?.bringToUser() == true) takenOver = true else presenter.resume()
            }
            LiveEvent.GiveBack -> scope.launch {
                com.piyush.mode.app.graph.background?.giveBack()
                takenOver = false
                presenter.resume()
            }
        }
    }
}

private fun map(s: LiveRunUiState, screen: ImageBitmap?, w: Words): LiveState = LiveState(
    running = s.running?.let { r ->
        val steps = s.steps.map { it.reason }.filter { it.isNotEmpty() }.takeLast(3)
        LiveRunning(
            w.get(R.string.run_live_badge, r.steps), r.startedAtMs, r.paused, r.text,
            if (r.paused) w.get(R.string.run_paused) else s.now ?: r.status,
            steps.mapIndexed { i, step -> ModeSegment(step, if (i == steps.lastIndex) ModeSegmentState.Now else ModeSegmentState.Done) },
        )
    },
    screen = screen,
    finished = s.finished?.let { f ->
        val (text, icon) = w.badge(f.badge)
        val tone = when (f.badge) {
            TaskBadge.Done -> ModeBadgeTone.Success
            TaskBadge.GaveUp -> ModeBadgeTone.Warning
            TaskBadge.Failed -> ModeBadgeTone.Danger
            TaskBadge.Stopped, TaskBadge.NotStarted -> ModeBadgeTone.Neutral
        }
        LiveFinished(Triple(text, icon, tone), f.parts.map { block(it, w) }, w.get(R.string.live_summary, w.seconds(f.seconds.toDouble()), f.steps))
    },
)
