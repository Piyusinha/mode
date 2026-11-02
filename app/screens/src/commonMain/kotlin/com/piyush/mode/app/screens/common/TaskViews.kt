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
    /** What the agent is doing, in its own plain words ("Adding it to Dinner"); "Working on it" before the first step. */
    val status: String,
    /** The agent is waiting for the user's answer. */
    val question: Question?,
    val paused: Boolean = false,
    /** When it started, epoch ms, for the elapsed time. */
    val startedAtMs: Long? = null,
)

/** The agent's question: a yes/no, a choice of [options] (the [recommended] one marked), or a free answer. */
public data class Question(
    val text: String,
    val yesNo: Boolean,
    val options: List<String> = emptyList(),
    val recommended: String? = null,
    /** Also offers "Yes, and don't ask again" (turns on full access). */
    val allowAlways: Boolean = false,
    /** Something only the user can do on the phone: "I've entered it" instead of an answer. */
    val yourTurn: Boolean = false,
)

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
        status = now(),
        question = question?.let { Question(it.text, it.yesNo, it.options, it.recommended, it.allowAlways, it.yourTurn) },
        paused = paused,
        startedAtMs = startedAtMs,
    )
}

/** What the user sees the agent doing: its latest reason, or the runner's own line before the first step. */
internal fun TaskState.now(): String = steps.lastOrNull { it.reason.isNotBlank() && !it.action.startsWith("done") }?.reason ?: status
