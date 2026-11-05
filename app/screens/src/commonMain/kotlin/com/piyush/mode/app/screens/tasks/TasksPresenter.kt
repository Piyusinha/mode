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

/** Which part of Tasks is showing. */
public enum class TaskFilter { Now, Scheduled, Done }

public data class TasksUiState(
    val filter: TaskFilter = TaskFilter.Now,
    val running: RunningTask? = null,
    val upNext: List<QueuedTask> = emptyList(),
    val repeats: List<RepeatingTask> = emptyList(),
    /** Finished today and yesterday, newest first. */
    val done: List<FinishedTask> = emptyList(),
    /** Easy first tasks, for an empty Now. */
    val tryThese: List<String> = TRY_THESE,
) {
    public companion object {
        /** Small, read-only or easy to undo, and work on any iPhone. */
        public val TRY_THESE: List<String> = listOf(
            "Turn on Do Not Disturb",
            "Tell me about everything on my calendar today",
            "Catch me up on today's unread WhatsApp messages",
        )
    }

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
    /** When it ended (epoch ms). */
    val endedAtMs: Long,
    /** How long it ran, in seconds; null when it never started. */
    val seconds: Long?,
    /** Why it didn't finish, in a few words ("Couldn't sign in"); null when done or too long to show. */
    val why: String?,
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
            combine(runner.state, queue.queue, scheduler.all, tasks.recent(MAX_DONE), filter) { run, waiting, schedules, recent, f ->
                TasksUiState(
                    filter = f,
                    running = run.running(tasks),
                    upNext = waiting.map { QueuedTask(it.id, it.text, it.source, clock.relativeDay(it.createdAt), it.createdAt) },
                    repeats = schedules.map {
                        RepeatingTask(it.id, it.text, it.rule.hour, it.rule.minute, it.rule.days.map { d -> d.isoDayNumber }.toSet(), it.enabled, it.nextRunAt)
                    },
                    done = recent.mapNotNull { t ->
                        val badge = t.status.badge() ?: return@mapNotNull null
                        val ended = t.endedAt ?: t.createdAt
                        val day = clock.relativeDay(ended)
                        if (day != RelativeDay.Today && day != RelativeDay.Yesterday) return@mapNotNull null
                        FinishedTask(
                            t.id, t.text, t.conversationId, badge, day, t.steps, t.source,
                            endedAtMs = ended,
                            seconds = t.startedAt?.let { s -> t.endedAt?.let { e -> ((e - s) / 1000).coerceAtLeast(0) } },
                            why = if (badge == TaskBadge.Done) null else shortWhy(t.summary),
                        )
                    },
                )
            }.collect { mutableState.value = it }
        }
    }

    public fun setFilter(f: TaskFilter) {
        filter.value = f
    }

    /** One of the suggested tasks: it runs like any task; returns its chat, to open. */
    public fun tryTask(text: String): String? = queue.submit(text, TaskSource.App, null, false)?.conversationId

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

    private val filter = kotlinx.coroutines.flow.MutableStateFlow(TaskFilter.Now)

    private companion object {
        const val MAX_DONE = 60L
        const val WHY_MAX = 32

        /** The reason's first short sentence, when it fits on the row ("Couldn't sign in"). */
        fun shortWhy(summary: String?): String? {
            val first = summary?.trim()?.split(Regex("(?<=[.!?])\\s|\\n"))?.firstOrNull()?.trim()?.trimEnd('.', '!')
            return first?.takeIf { it.isNotEmpty() && it.length <= WHY_MAX }
        }
    }
}
