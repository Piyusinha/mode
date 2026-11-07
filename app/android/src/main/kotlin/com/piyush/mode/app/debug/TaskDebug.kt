package com.piyush.mode.app.debug

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.common.RelativeDay
import com.piyush.mode.app.screens.debug.FindingKind
import com.piyush.mode.app.screens.debug.TaskDebugPresenter
import com.piyush.mode.app.screens.debug.TaskDebugUiState
import com.piyush.mode.app.shell.Route
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeAnswerBubble
import com.piyush.mode.design.ModeAskBar
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeChatButton
import com.piyush.mode.design.ModeChevron
import com.piyush.mode.design.ModeDebugHeader
import com.piyush.mode.design.ModeDebugRow
import com.piyush.mode.design.ModeDebugTone
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeInlineText
import com.piyush.mode.design.ModePageBar
import com.piyush.mode.design.ModeSectionHeader
import com.piyush.mode.design.ModeSize
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeSuggestionChip
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.ModeUserBubble
import com.piyush.mode.design.MoMood
import com.piyush.mode.design.modeShimmer

data class DebugItem(val title: String, val detail: String, val icon: ModeIcon, val tone: ModeDebugTone)
data class DebugTurnView(val question: String, val answer: String?, val failed: Boolean) {
    /** The answer's paragraphs (blank lines between). */
    val paragraphs: List<String> get() = answer.orEmpty().split("\n\n").map { it.trim() }.filter { it.isNotEmpty() }
}

data class DebugState(
    val subtitle: String = "",
    val found: List<DebugItem> = emptyList(),
    val repaired: List<DebugItem> = emptyList(),
    val canRepair: Boolean = false,
    val repairing: Boolean = false,
    val turns: List<DebugTurnView> = emptyList(),
    val provider: String = "",
    val suggestions: List<String> = emptyList(),
) {
    val asking: Boolean get() = turns.lastOrNull()?.answer == null && turns.isNotEmpty()
    val showRepair: Boolean get() = canRepair && repaired.isEmpty()
}

sealed interface DebugEvent {
    data object Back : DebugEvent
    data object Repair : DebugEvent
    data object Retry : DebugEvent
    data class Ask(val question: String) : DebugEvent
    data object OpenAsk : DebugEvent
    data object OpenHealth : DebugEvent
}

/** What went wrong: what Mode found, Repair, and Ask about it or Try again. */
@Composable
fun TaskDebugScreen(state: DebugState, onEvent: (DebugEvent) -> Unit) {
    Column(Modifier.fillMaxSize()) {
        ModePageBar(stringResource(R.string.common_back), { onEvent(DebugEvent.Back) })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        ) {
            ModeDebugHeader(stringResource(R.string.debug_title), state.subtitle, MoMood.Stuck)
            ModeSectionHeader(stringResource(R.string.debug_found))
            val found = state.found.ifEmpty {
                listOf(DebugItem(stringResource(R.string.debug_nothing_found), stringResource(R.string.debug_nothing_found_detail), ModeIcon.Info, ModeDebugTone.Neutral))
            }
            ModeCard(rows = found.map { item -> { ModeDebugRow(item.title, item.detail, item.icon, item.tone) } })
            if (state.showRepair) {
                ModeChatButton(
                    stringResource(if (state.repairing) R.string.health_repairing else R.string.debug_repair), { if (!state.repairing) onEvent(DebugEvent.Repair) },
                    Modifier.fillMaxWidth().padding(top = ModeSpacing.xs), icon = ModeIcon.Wrench,
                )
            }
            if (state.repaired.isNotEmpty()) {
                ModeSectionHeader(stringResource(R.string.debug_repaired))
                ModeCard(rows = state.repaired.map { item -> { ModeDebugRow(item.title, item.detail, item.icon, item.tone) } })
            }
            Row(
                Modifier.defaultMinSize(minHeight = ModeSize.touch).clickable(indication = null, interactionSource = null) { onEvent(DebugEvent.OpenHealth) }.padding(horizontal = ModeSpacing.s),
                horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeText(stringResource(R.string.health_row), ModeTextStyle.FootnoteEmphasis, color = Mode.colors.textSecondary)
                ModeChevron()
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.s), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.chatInline)) {
            ModeChatButton(stringResource(R.string.debug_ask), { onEvent(DebugEvent.OpenAsk) }, Modifier.weight(1f))
            ModeChatButton(stringResource(R.string.conversation_try_again), { onEvent(DebugEvent.Retry) }, Modifier.weight(1f), prominent = true)
        }
    }
}

/** Ask about it: questions about the failed task, answered with the user's AI. */
@Composable
fun TaskDebugAskScreen(state: DebugState, onEvent: (DebugEvent) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val list = rememberLazyListState()
    LaunchedEffect(state.turns.size) { if (state.turns.isNotEmpty()) list.animateScrollToItem(state.turns.size) }
    Column(Modifier.fillMaxSize().imePadding()) {
        ModePageBar(stringResource(R.string.common_back), { onEvent(DebugEvent.Back) })
        LazyColumn(
            Modifier.weight(1f), list,
            contentPadding = PaddingValues(start = ModeSpacing.screenEdge, end = ModeSpacing.screenEdge, bottom = ModeSpacing.xxl),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.l),
        ) {
            item {
                Column(Modifier.padding(horizontal = ModeSpacing.s), verticalArrangement = Arrangement.spacedBy(ModeSpacing.l)) {
                    ModeText(stringResource(R.string.debug_ask_title), ModeTextStyle.Title)
                    if (state.turns.isEmpty()) ModeText(stringResource(R.string.debug_ask_intro), ModeTextStyle.Callout, color = Mode.colors.textSecondary)
                }
            }
            itemsIndexed(state.turns) { _, turn ->
                Column(verticalArrangement = Arrangement.spacedBy(ModeSpacing.l)) {
                    ModeUserBubble(turn.question)
                    ModeAnswerBubble(
                        if (turn.answer == null || turn.failed) null else stringResource(R.string.debug_answered_with, state.provider),
                        if (turn.answer == null) MoMood.Working else MoMood.Stuck,
                    ) {
                        if (turn.answer == null) {
                            ModeText(stringResource(R.string.debug_thinking), ModeTextStyle.Callout, Modifier.modeShimmer(), color = Mode.colors.textSecondary)
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                                turn.paragraphs.forEach { ModeInlineText(it, ModeTextStyle.Callout, color = if (turn.failed) Mode.colors.danger else Mode.colors.textPrimary) }
                            }
                        }
                    }
                }
            }
        }
        Column(Modifier.fillMaxWidth().padding(bottom = ModeSpacing.s), verticalArrangement = Arrangement.spacedBy(ModeSpacing.chatInline)) {
            if (state.suggestions.isNotEmpty()) {
                LazyRow(contentPadding = PaddingValues(horizontal = ModeSpacing.screenEdge), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                    items(state.suggestions) { s -> ModeSuggestionChip(s, { onEvent(DebugEvent.Ask(s)) }, enabled = !state.asking) }
                }
            }
            Box(Modifier.padding(horizontal = ModeSpacing.screenEdge)) {
                ModeAskBar(text, { text = it }, stringResource(R.string.debug_ask_prompt), stringResource(R.string.ask_send), !state.asking && text.isNotBlank(), {
                    onEvent(DebugEvent.Ask(text))
                    text = ""
                })
            }
            ModeText(
                stringResource(R.string.debug_what_is_sent, state.provider), ModeTextStyle.Caption,
                Modifier.padding(horizontal = ModeSpacing.screenEdge + ModeSpacing.s), color = Mode.colors.textSecondary,
            )
        }
    }
}

/** What went wrong and Ask about it share one presenter, so questions carry over between them. */
@Composable
fun TaskDebugRoute(graph: ModeGraph, taskId: String, startAsking: Boolean, navigate: (Route) -> Unit, close: () -> Unit) {
    val presenter: TaskDebugPresenter = rememberPresenter("debug:$taskId") { graph.screens.taskDebug(taskId) }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val words = rememberWords()
    val state = remember(ui) { map(ui, words) }
    var asking by rememberSaveable { mutableStateOf(startAsking) }
    BackHandler(enabled = asking && !startAsking) { asking = false }
    val onEvent: (DebugEvent) -> Unit = { event ->
        when (event) {
            DebugEvent.Back -> if (asking && !startAsking) asking = false else close()
            DebugEvent.Repair -> presenter.repair()
            DebugEvent.Retry -> {
                presenter.retry()
                close()
            }
            is DebugEvent.Ask -> presenter.ask(event.question)
            DebugEvent.OpenAsk -> asking = true
            DebugEvent.OpenHealth -> navigate(Route.Health)
        }
    }
    if (asking) TaskDebugAskScreen(state, onEvent) else TaskDebugScreen(state, onEvent)
}

private fun map(s: TaskDebugUiState, w: Words): DebugState {
    val whenLine = s.startedAtMs?.let { ms ->
        if (com.piyush.mode.app.screens.common.ScreenClock().relativeDay(ms) == RelativeDay.Today) w.get(R.string.debug_today, w.timeOfDay(ms))
        else "${w.day(com.piyush.mode.app.screens.common.ScreenClock().relativeDay(ms))} ${w.timeOfDay(ms)}"
    }
    val asked = s.turns.map { it.question }.toSet()
    return DebugState(
        subtitle = w.get(R.string.debug_subtitle, listOfNotNull(s.title, whenLine).filter { it.isNotEmpty() }.joinToString(" · ")),
        found = s.findings.map { f ->
            DebugItem(f.title, f.detail, icon(f.kind), when {
                f.ok -> ModeDebugTone.Ok
                f.problem -> ModeDebugTone.Problem
                else -> ModeDebugTone.Neutral
            })
        },
        repaired = s.repaired.map { DebugItem(it, w.get(R.string.health_repaired), ModeIcon.ArrowRight, ModeDebugTone.Repaired) },
        canRepair = s.canRepair,
        repairing = s.repairing,
        turns = s.turns.map { DebugTurnView(it.question, it.answer, it.failed) },
        provider = s.provider,
        suggestions = listOf(R.string.debug_why_stop, R.string.debug_what_to_change, R.string.debug_will_work).map { w.get(it) }.filter { it !in asked },
    )
}

private fun icon(kind: FindingKind): ModeIcon = when (kind) {
    FindingKind.NoAction, FindingKind.AiFailed, FindingKind.AiOk -> ModeIcon.Sparkle
    FindingKind.Stuck, FindingKind.BackAndForth -> ModeIcon.RunAgain
    FindingKind.OutOfSteps, FindingKind.OutOfTime -> ModeIcon.Clock
    FindingKind.Unconfirmed -> ModeIcon.Wrench
    FindingKind.PhoneUnreachable, FindingKind.PhoneOk -> ModeIcon.ActionButton
    FindingKind.WaitedForYou -> ModeIcon.Profile
    FindingKind.Interrupted, FindingKind.NotStarted -> ModeIcon.Stopped
    else -> ModeIcon.Info
}
