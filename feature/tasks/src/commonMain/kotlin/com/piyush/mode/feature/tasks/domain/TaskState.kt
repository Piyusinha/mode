package com.piyush.mode.feature.tasks.domain

import com.piyush.mode.feature.memory.domain.MemoryItem
import com.piyush.mode.agent.AgentResult
import com.piyush.mode.agent.StepRecord
import kotlinx.coroutines.CompletableDeferred

/** A question from the agent, shown until the user answers or stops the task. */
public class PendingQuestion(
    public val text: String,
    /** True for a yes/no confirmation; false for a free-text answer. */
    public val yesNo: Boolean,
    /** Choices to tap (a typed answer still works); empty for a free question. */
    public val options: List<String> = emptyList(),
    /** The option the agent would pick. */
    public val recommended: String? = null,
    /** Matches an answer from a notification or the Live Activity to this question. */
    public val id: String = "q" + kotlin.random.Random.nextLong().toULong().toString(36),
    /** A yes/no before paying or sending: it also offers "Yes, and don't ask again" (full access). */
    public val allowAlways: Boolean = false,
    /** Something only the user can do on the phone (a PIN, a password): Mode waits, the app stays in front. */
    public val yourTurn: Boolean = false,
) {
    internal val answer: CompletableDeferred<String?> = CompletableDeferred()

    public companion object {
        /** The answer "Yes, and don't ask again". */
        public const val ALWAYS: String = "always"
        /** The answer "I've entered it". */
        public const val DONE: String = "done"
    }
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
    /** The saved task (see TaskRepository). */
    val taskId: String? = null,
    /** The conversation it belongs to; null for comparison runs. */
    val conversationId: String? = null,
    /** Paused by the user between steps. */
    val paused: Boolean = false,
    /** The last screenshot the agent looked at (JPEG), for the live view. */
    val latestScreen: ByteArray? = null,
    /** The agent's plan for a task with several parts, as it last wrote it. */
    val plan: List<com.piyush.mode.agent.PlanItem> = emptyList(),
    /** When this task started running, epoch ms. */
    val startedAtMs: Long? = null,
    /** Facts about the user this task taught Mode, for "Remembered · Undo". Arrives a moment after the task ends. */
    val remembered: List<MemoryItem> = emptyList(),
    /** A spoken question in a task the user spoke: Mode came forward to ask it out loud and listen. */
    val voice: VoiceTurn? = null,
)

/** Where a spoken question is: Mode saying it, listening for the answer, or saying it heard it before going back. */
public enum class VoiceTurnPhase { Asking, Listening, Answered }

public data class VoiceTurn(
    val phase: VoiceTurnPhase,
    val question: String,
    val options: List<String> = emptyList(),
    /** The task, for context ("Order my usual from Swiggy"). */
    val task: String = "",
    /** The answer as heard so far, then as given (said or tapped). */
    val heard: String = "",
    /** What Mode says back before it goes back to work. */
    val reply: String = "",
)
