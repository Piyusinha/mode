package com.piyush.mode.app.screens.common

import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.domain.TaskState
import com.piyush.mode.feature.tasks.domain.TaskStatus

/** The task running now, as every screen shows it (the bottom bar, Tasks, Home). */
public data class RunningTask(
    val taskId: String,
    val text: String,
    val conversationId: String?,
    /** Steps taken so far. */
    val steps: Int,
    /** What the agent is doing, from the runner: "Step 4: thinking". */
    val status: String,
    /** The agent is waiting for the user's answer. */
    val question: Question?,
    val paused: Boolean = false,
    /** When it started, epoch ms, for the elapsed time. */
    val startedAtMs: Long? = null,
)

public data class Question(val text: String, val yesNo: Boolean)

/** How a finished task ended, for its badge. */
public enum class TaskBadge { Done, GaveUp, Stopped, Failed, NotStarted }

internal fun TaskStatus.badge(): TaskBadge? = when (this) {
    TaskStatus.Done -> TaskBadge.Done
    TaskStatus.GaveUp -> TaskBadge.GaveUp
    TaskStatus.Stopped -> TaskBadge.Stopped
    TaskStatus.Failed -> TaskBadge.Failed
    TaskStatus.NotStarted -> TaskBadge.NotStarted
    TaskStatus.Queued, TaskStatus.Running, TaskStatus.WaitingForUser -> null
}

internal fun TaskState.running(tasks: TaskRepository): RunningTask? {
    if (!running) return null
    val id = taskId ?: return null
    return RunningTask(
        taskId = id,
        text = tasks.get(id)?.text.orEmpty(),
        conversationId = conversationId,
        steps = steps.size,
        status = status,
        question = question?.let { Question(it.text, it.yesNo) },
        paused = paused,
        startedAtMs = startedAtMs,
    )
}
