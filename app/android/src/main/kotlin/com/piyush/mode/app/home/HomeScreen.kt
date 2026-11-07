package com.piyush.mode.app.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.piyush.mode.app.R
import com.piyush.mode.app.run.Running
import com.piyush.mode.design.Mode
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeEmptyState
import com.piyush.mode.design.ModeHomeHeader
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeMiniPlayer
import com.piyush.mode.design.ModeRow
import com.piyush.mode.design.ModeRowEmphasis
import com.piyush.mode.design.ModeRowLeading
import com.piyush.mode.design.ModeRunningWords
import com.piyush.mode.design.ModeSectionHeader
import com.piyush.mode.design.ModeSize
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeSuggestionCard
import com.piyush.mode.design.ModeTip
import com.piyush.mode.design.ModeTipBanner
import com.piyush.mode.design.ModeTryPill
import com.piyush.mode.design.ModeUndoToast
import androidx.compose.foundation.layout.defaultMinSize

data class HomeSuggestion(val title: String, val subtitle: String, val icon: ModeIcon)
data class RecentRow(val id: String, val title: String, val subtitle: String, val leading: ModeRowLeading)
data class HomeTips(val tips: List<ModeTip>, val index: Int, val closable: Boolean)

/** What Home shows. */
data class HomeState(
    val greeting: String = "",
    val suggestions: List<HomeSuggestion> = emptyList(),
    val recent: List<RecentRow> = emptyList(),
    val needsKey: Boolean = false,
    val tips: HomeTips? = null,
    val tryThese: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = suggestions.isEmpty() && recent.isEmpty()
}

sealed interface HomeEvent {
    data class Suggestion(val text: String) : HomeEvent
    data class OpenConversation(val id: String) : HomeEvent
    data object AddKey : HomeEvent
    data object PauseToggle : HomeEvent
    data object Stop : HomeEvent
    data object OpenRun : HomeEvent
    data class TipAction(val index: Int) : HomeEvent
    data class PickTip(val index: Int) : HomeEvent
    data object CloseTips : HomeEvent
    data object UndoCloseTips : HomeEvent
}

/** Home, as on the board: the greeting, tips, the running task's dark card, things to try, suggestions, recent chats. */
@Composable
fun HomeScreen(state: HomeState, running: Running?, showingUndo: Boolean, onEvent: (HomeEvent) -> Unit) {
    val edge = Modifier.padding(horizontal = ModeSpacing.screenEdge)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = ModeSpacing.l, bottom = ModeSize.tabBarClearance)) {
        ModeHomeHeader(
            state.greeting, stringResource(R.string.home_subtitle),
            Modifier.padding(horizontal = ModeSpacing.xl).padding(bottom = ModeSpacing.xl + ModeSpacing.xs),
        )
        AnimatedVisibility(state.tips != null, enter = fadeIn(), exit = fadeOut()) {
            val tips = state.tips ?: return@AnimatedVisibility
            val dots = tips.tips.indices.map { stringResource(R.string.tips_dot, it + 1, tips.tips.size) }
            ModeTipBanner(
                stringResource(R.string.tips_label), tips.tips, tips.index, tips.closable, stringResource(R.string.tips_close),
                dotLabel = { dots[it] },
                onAction = { onEvent(HomeEvent.TipAction(it)) }, onClose = { onEvent(HomeEvent.CloseTips) }, onPick = { onEvent(HomeEvent.PickTip(it)) },
                modifier = edge.padding(bottom = ModeSpacing.m),
            )
        }
        AnimatedVisibility(state.tips == null && showingUndo, enter = fadeIn(), exit = fadeOut()) {
            ModeUndoToast(
                stringResource(R.string.tips_hidden), stringResource(R.string.tips_hidden_detail), stringResource(R.string.tips_undo),
                { onEvent(HomeEvent.UndoCloseTips) }, edge.padding(bottom = ModeSpacing.m),
            )
        }
        if (running != null) {
            ModeMiniPlayer(
                running.title, running.status, running.paused, runningWords(),
                onPauseToggle = { onEvent(HomeEvent.PauseToggle) }, onStop = { onEvent(HomeEvent.Stop) }, onOpen = { onEvent(HomeEvent.OpenRun) },
                modifier = edge.padding(bottom = ModeSpacing.m), progress = running.progress,
            )
        }
        if (state.needsKey) {
            ModeCard(edge.padding(bottom = ModeSpacing.m), rows = listOf {
                ModeRow(
                    stringResource(R.string.home_needs_key_title), subtitle = stringResource(R.string.home_needs_key_message),
                    leading = ModeRowLeading.Icon(ModeIcon.Key, Mode.colors.warning), emphasis = ModeRowEmphasis.Prominent,
                    onClick = { onEvent(HomeEvent.AddKey) },
                )
            })
        }
        if (state.tryThese.isNotEmpty()) {
            Section(stringResource(R.string.home_try))
            LazyRow(
                Modifier.padding(bottom = ModeSpacing.m),
                contentPadding = PaddingValues(horizontal = ModeSpacing.screenEdge),
                horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
            ) { items(state.tryThese) { job -> ModeTryPill(job, { onEvent(HomeEvent.Suggestion(job)) }) } }
        }
        if (state.isEmpty && running == null) {
            ModeEmptyState(ModeIcon.Ask, stringResource(R.string.home_empty_title), stringResource(R.string.home_empty_message))
        }
        if (state.suggestions.isNotEmpty()) {
            Section(stringResource(R.string.home_suggested))
            // Cards as tall as the tallest: a plain scrolling row (a lazy one can't be measured that way).
            androidx.compose.foundation.layout.Row(
                Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()).height(IntrinsicSize.Max)
                    .padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.m),
                horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
            ) { state.suggestions.forEach { s -> ModeSuggestionCard(s.icon, s.title, s.subtitle, { onEvent(HomeEvent.Suggestion(s.title)) }) } }
        }
        if (state.recent.isNotEmpty()) {
            Section(stringResource(R.string.home_recent))
            ModeCard(edge, rows = state.recent.map { row ->
                {
                    ModeRow(
                        row.title, Modifier.defaultMinSize(minHeight = ModeSize.recentRow), subtitle = row.subtitle, leading = row.leading,
                        onClick = { onEvent(HomeEvent.OpenConversation(row.id)) },
                    )
                }
            })
        }
    }
}

@Composable
private fun Section(title: String) {
    ModeSectionHeader(title, Modifier.padding(horizontal = ModeSpacing.screenEdge).padding(bottom = ModeSpacing.s + ModeSpacing.xxs))
}

/** Pause, Resume and Stop, for the running task's card and bar. */
@Composable
fun runningWords(): ModeRunningWords =
    ModeRunningWords(stringResource(R.string.run_pause), stringResource(R.string.run_resume), stringResource(R.string.common_stop))
