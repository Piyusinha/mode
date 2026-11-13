package com.piyush.mode.app.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.common.RelativeDay
import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.tasks.FinishedTask
import com.piyush.mode.app.screens.tasks.TaskFilter
import com.piyush.mode.app.screens.tasks.TasksPresenter
import com.piyush.mode.app.screens.tasks.TasksUiState
import com.piyush.mode.app.shell.Route
import com.piyush.mode.app.shell.rememberPresenter
import com.piyush.mode.design.ModeActionSheet
import com.piyush.mode.design.ModeCard
import com.piyush.mode.design.ModeEmptyState
import com.piyush.mode.design.ModeIcon
import com.piyush.mode.design.ModeOpacity
import com.piyush.mode.design.ModePillSegments
import com.piyush.mode.design.ModeRow
import com.piyush.mode.design.ModeRowLeading
import com.piyush.mode.design.ModeSectionHeader
import com.piyush.mode.design.ModeSheetAction
import com.piyush.mode.design.ModeSize
import com.piyush.mode.design.ModeSpacing
import com.piyush.mode.design.ModeText
import com.piyush.mode.design.ModeTextStyle
import com.piyush.mode.design.MoMood

/** A one-off or a repeat: its time, and whether it's on. */
data class ScheduleRow(val id: String, val title: String, val subtitle: String, val icon: ModeIcon, val enabled: Boolean)
data class DoneRow(val id: String, val title: String, val subtitle: String, val leading: ModeRowLeading, val conversationId: String?)

data class TasksState(
    val scheduled: List<ScheduleRow> = emptyList(),
    val repeats: List<ScheduleRow> = emptyList(),
    val doneToday: List<DoneRow> = emptyList(),
    val doneYesterday: List<DoneRow> = emptyList(),
) {
    val scheduledIsEmpty: Boolean get() = scheduled.isEmpty() && repeats.isEmpty()
    val doneIsEmpty: Boolean get() = doneToday.isEmpty() && doneYesterday.isEmpty()
}

sealed interface TasksEvent {
    data class Filter(val filter: TaskFilter) : TasksEvent
    data class SetRepeat(val id: String, val on: Boolean) : TasksEvent
    data class DeleteRepeat(val id: String) : TasksEvent
    data class Open(val row: DoneRow) : TasksEvent
}

/** Tasks, as on its board: Scheduled (one-offs and repeats) and Done (today and yesterday). */
@Composable
fun TasksScreen(state: TasksState, filter: TaskFilter, onEvent: (TasksEvent) -> Unit) {
    var managing by remember { mutableStateOf<ScheduleRow?>(null) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = ModeSpacing.screenEdge).padding(top = ModeSpacing.l, bottom = ModeSize.tabBarClearance),
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.s),
    ) {
        ModeText(
            stringResource(R.string.tab_tasks), ModeTextStyle.LargeTitle,
            Modifier.padding(start = ModeSpacing.xs, bottom = ModeSpacing.s - ModeSpacing.xxs).semantics { heading() },
        )
        ModePillSegments(
            listOf(TaskFilter.Scheduled to stringResource(R.string.tasks_scheduled), TaskFilter.Done to stringResource(R.string.tasks_done)),
            filter, { onEvent(TasksEvent.Filter(it)) }, Modifier.padding(bottom = ModeSpacing.xs),
        )
        when (filter) {
            TaskFilter.Scheduled -> {
                if (state.scheduledIsEmpty) {
                    ModeEmptyState(ModeIcon.Clock, stringResource(R.string.tasks_nothing_scheduled), stringResource(R.string.tasks_nothing_scheduled_message))
                }
                if (state.scheduled.isNotEmpty()) {
                    ModeSectionHeader(stringResource(R.string.tasks_up_next))
                    ScheduleCard(state.scheduled) { managing = it }
                }
                if (state.repeats.isNotEmpty()) {
                    ModeSectionHeader(stringResource(R.string.tasks_repeats))
                    ScheduleCard(state.repeats) { managing = it }
                }
            }
            TaskFilter.Done -> {
                if (state.doneIsEmpty) {
                    ModeEmptyState(ModeIcon.Done, stringResource(R.string.tasks_nothing_done), stringResource(R.string.tasks_nothing_done_message))
                }
                if (state.doneToday.isNotEmpty()) {
                    ModeSectionHeader(stringResource(R.string.tasks_today))
                    DoneCard(state.doneToday, onEvent)
                }
                if (state.doneYesterday.isNotEmpty()) {
                    ModeSectionHeader(stringResource(R.string.tasks_yesterday))
                    DoneCard(state.doneYesterday, onEvent)
                }
            }
        }
    }
    managing?.let { row ->
        ModeActionSheet(
            row.title,
            listOf(
                ModeSheetAction(stringResource(if (row.enabled) R.string.tasks_pause else R.string.tasks_resume)) { onEvent(TasksEvent.SetRepeat(row.id, !row.enabled)) },
                ModeSheetAction(stringResource(R.string.common_delete), destructive = true) { onEvent(TasksEvent.DeleteRepeat(row.id)) },
            ),
            stringResource(R.string.common_cancel),
        ) { managing = null }
    }
}

@Composable
private fun ScheduleCard(rows: List<ScheduleRow>, onManage: (ScheduleRow) -> Unit) {
    ModeCard(rows = rows.map { row ->
        {
            ModeRow(
                row.title, Modifier.defaultMinSize(minHeight = ModeSize.tasksRow).alpha(if (row.enabled) 1f else ModeOpacity.muted * 2),
                subtitle = row.subtitle, leading = ModeRowLeading.Icon(row.icon), onClick = { onManage(row) },
            )
        }
    })
}

@Composable
private fun DoneCard(rows: List<DoneRow>, onEvent: (TasksEvent) -> Unit) {
    ModeCard(rows = rows.map { row ->
        {
            ModeRow(
                row.title, Modifier.defaultMinSize(minHeight = ModeSize.tasksRow), subtitle = row.subtitle, leading = row.leading,
                onClick = { onEvent(TasksEvent.Open(row)) },
            )
        }
    })
}

/** Connects Tasks to its presenter. */
@Composable
fun TasksRoute(graph: ModeGraph, navigate: (Route) -> Unit) {
    val presenter: TasksPresenter = rememberPresenter("tasks") { graph.screens.tasks() }
    val ui by presenter.state.collectAsStateWithLifecycle()
    val words = rememberWords()
    var filter by rememberSaveable { mutableStateOf(TaskFilter.Scheduled) }
    val state = remember(ui) { map(ui, words) }
    TasksScreen(state, filter) { event ->
        when (event) {
            is TasksEvent.Filter -> {
                filter = event.filter
                presenter.setFilter(event.filter)
            }
            is TasksEvent.SetRepeat -> presenter.setRepeatEnabled(event.id, event.on)
            is TasksEvent.DeleteRepeat -> presenter.deleteRepeat(event.id)
            is TasksEvent.Open -> event.row.conversationId?.let { navigate(Route.Conversation(it)) }
        }
    }
}

private fun map(s: TasksUiState, w: Words): TasksState {
    fun paused(text: String, enabled: Boolean) = if (enabled) text else "$text · ${w.get(R.string.tasks_paused)}"
    return TasksState(
        scheduled = s.repeats.filter { it.isoDays.isEmpty() && it.nextRunAtMs != null }.map { r ->
            ScheduleRow(r.scheduleId, r.text, paused(r.nextRunAtMs?.let(w::upcoming).orEmpty(), r.enabled), ModeIcon.Clock, r.enabled)
        },
        repeats = s.repeats.filter { it.isoDays.isNotEmpty() }.map { r ->
            ScheduleRow(r.scheduleId, r.text, paused("${w.days(r.isoDays)} ${w.time(r.hour, r.minute)}", r.enabled), ModeIcon.RepeatTask, r.enabled)
        },
        doneToday = s.done.filter { it.day == RelativeDay.Today }.map { doneRow(it, w) },
        doneYesterday = s.done.filter { it.day == RelativeDay.Yesterday }.map { doneRow(it, w) },
    )
}

/** "Texted Priya · 17:42 · took 42 s", or "Pay the bill · Couldn't sign in · 10:25 · stopped at 31 s". */
private fun doneRow(d: FinishedTask, w: Words): DoneRow {
    val how = d.seconds?.let { w.duration(it) }?.let { if (d.badge == TaskBadge.Done) w.get(R.string.tasks_took, it) else w.get(R.string.tasks_stopped_at, it) }
    return DoneRow(
        d.taskId,
        listOfNotNull(d.text, d.why).joinToString(" · "),
        listOfNotNull(w.timeOfDay(d.endedAtMs), how).joinToString(" · "),
        ModeRowLeading.Mo(
            when (d.badge) {
                TaskBadge.Done -> MoMood.Done
                TaskBadge.Failed, TaskBadge.GaveUp -> MoMood.Stuck
                TaskBadge.Stopped, TaskBadge.NotStarted -> MoMood.Sleep
            },
        ),
        d.conversationId,
    )
}
