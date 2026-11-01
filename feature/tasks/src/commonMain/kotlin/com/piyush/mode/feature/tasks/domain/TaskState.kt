package com.piyush.mode.feature.tasks.domain

import com.piyush.mode.agent.AgentResult
import com.piyush.mode.agent.StepRecord
import kotlinx.coroutines.CompletableDeferred

/** A question from the agent, shown until the user answers or stops the task. */
public class PendingQuestion(
    public val text: String,
    /** True for a yes/no confirmation; false for a free-text answer. */
    public val yesNo: Boolean,
) {
    internal val answer: CompletableDeferred<String?> = CompletableDeferred()
}

/** Everything a screen needs to show the current task. */
public data class TaskState(
    val running: Boolean = false,
    /** One line for the user: connecting, the step, the outcome, or what went wrong. */
    val status: String = "",
    val question: PendingQuestion? = null,
    val steps: List<StepRecord> = emptyList(),
    val lastResult: AgentResult? = null,
    /** The recorded run, under runs/ in the logs. */
    val lastRunId: String? = null,
)
