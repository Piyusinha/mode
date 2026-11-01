package com.piyush.mode.app.screens.tasks

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.common.RelativeDay
import com.piyush.mode.app.screens.common.RunningTask
import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.common.badge
import com.piyush.mode.app.screens.common.running
import com.piyush.mode.feature.schedules.presentation.Scheduler
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.presentation.TaskQueue
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.isoDayNumber

public data class TasksUiState(
    val running: RunningTask? = null,
    val upNext: List<QueuedTask> = emptyList(),
    val repeats: List<RepeatingTask> = emptyList(),
    val done: List<FinishedTask> = emptyList(),
) {
    val isEmpty: Boolean get() = running == null && upNext.isEmpty() && repeats.isEmpty() && done.isEmpty()
}

public data class QueuedTask(val taskId: String, val text: String, val source: TaskSource, val day: RelativeDay, val queuedAtMs: Long)

public data class RepeatingTask(
    val scheduleId: String,
    val text: String,
    val hour: Int,
    val minute: Int,
    /** 1 = Monday … 7 = Sunday; empty for a one-off. */
    val isoDays: Set<Int>,
    val enabled: Boolean,
    /** Epoch ms of the next run; null when off or a one-off that has run. */
    val nextRunAtMs: Long?,
)

public data class FinishedTask(
    val taskId: String,
    val text: String,
    val conversationId: String?,
    val badge: TaskBadge,
    val day: RelativeDay,
    val steps: Int?,
    val source: TaskSource,
)

/** Tasks: what runs now, waits, repeats, and has finished. */
public class TasksPresenter(
    parent: CoroutineScope,
    private val runner: TaskRunner,
    private val queue: TaskQueue,
    private val scheduler: Scheduler,
    private val tasks: TaskRepository,
    private val clock: ScreenClock = ScreenClock(),
) : Presenter<TasksUiState>(parent, TasksUiState()) {

    init {
        scope.launch {
            combine(runner.state, queue.queue, scheduler.all, tasks.recent(MAX_DONE)) { run, waiting, schedules, recent ->
                TasksUiState(
                    running = run.running(tasks),
                    upNext = waiting.map { QueuedTask(it.id, it.text, it.source, clock.relativeDay(it.createdAt), it.createdAt) },
                    repeats = schedules.map {
                        RepeatingTask(it.id, it.text, it.rule.hour, it.rule.minute, it.rule.days.map { d -> d.isoDayNumber }.toSet(), it.enabled, it.nextRunAt)
                    },
                    done = recent.mapNotNull { t ->
                        val badge = t.status.badge() ?: return@mapNotNull null
                        FinishedTask(t.id, t.text, t.conversationId, badge, clock.relativeDay(t.endedAt ?: t.createdAt), t.steps, t.source)
                    },
                )
            }.collect { mutableState.value = it }
        }
    }

    public fun cancel(taskId: String) {
        queue.cancel(taskId)
    }

    public fun stopRunning() {
        runner.stop()
    }

    public fun answer(text: String?) {
        runner.answer(text)
    }

    public fun setRepeatEnabled(scheduleId: String, enabled: Boolean) {
        scheduler.setEnabled(scheduleId, enabled)
    }

    public fun deleteRepeat(scheduleId: String) {
        scheduler.delete(scheduleId)
    }

    private companion object {
        const val MAX_DONE = 30L
    }
}
