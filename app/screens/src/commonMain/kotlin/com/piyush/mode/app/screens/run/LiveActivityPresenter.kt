package com.piyush.mode.app.screens.run

import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.common.now
import com.piyush.mode.app.screens.conversation.ReplyPart
import com.piyush.mode.app.screens.conversation.replyParts
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.ResultSurfaces
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.domain.TaskState
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the Live Activity (Dynamic Island, Lock Screen, Watch Smart Stack) shows. */
public sealed interface ActivityUiState {
    /** Nothing has run since Mode opened. */
    public data object Hidden : ActivityUiState

    public data class Working(
        val taskId: String,
        val task: String,
        /** Steps taken so far. */
        val step: Int,
        /** When it started, epoch ms: the clock ticks on its own. */
        val startedAtMs: Long?,
        /** What it is doing now, in the agent's words. */
        val now: String,
        val paused: Boolean,
        /** Up to three plan items around the current one; empty without a plan. */
        val plan: List<PlanStep>,
        val conversationId: String?,
    ) : ActivityUiState

    /** The user stopped it (or it ended without an answer): the activity goes away at once. */
    public data class Ended(val taskId: String) : ActivityUiState

    /** The agent asked something only the user can answer. */
    public data class Waiting(
        val taskId: String,
        val task: String,
        val question: String,
        val conversationId: String?,
        /** Answers come back with this id (from a Live Activity button or a notification). */
        val questionId: String = "",
        val yesNo: Boolean = false,
        val options: List<String> = emptyList(),
    ) : ActivityUiState

    public data class Finished(
        val taskId: String,
        val badge: TaskBadge,
        val seconds: Long,
        /** The start of the answer, small enough for the Lock Screen. */
        val parts: List<ReplyPart>,
        /** Rows of the answer left out ("+2 more"). */
        val more: Int,
        val conversationId: String?,
    ) : ActivityUiState
}

public data class PlanStep(val label: String, val state: PlanStepState)

public enum class PlanStepState { Done, Now, Next }

/** The running task, for the Live Activity. */
public class LiveActivityPresenter(
    parent: CoroutineScope,
    private val runner: TaskRunner,
    private val tasks: TaskRepository,
) : Presenter<ActivityUiState>(parent, ActivityUiState.Hidden) {

    init {
        scope.launch {
            var said = ""
            runner.state.collect { s ->
                val state = map(s)
                // One line per change of phase or step, to see in the logs what the activity was given.
                val line = when (state) {
                    is ActivityUiState.Working -> "working, step ${state.step}"
                    else -> state::class.simpleName.orEmpty().lowercase()
                }
                if (line != said && state != ActivityUiState.Hidden) AppLog.log("activity", "state: $line")
                said = line
                mutableState.value = state
            }
        }
    }

    /** Stop, from the Dynamic Island. */
    public fun stop() {
        runner.stop()
    }

    /** An answer tapped on the Live Activity. */
    public fun answer(questionId: String, text: String) {
        runner.answer(questionId, text)
    }

    /** What happened to the Live Activity (started, updated, ended, refused), for the logs. */
    public fun log(message: String) {
        AppLog.log("activity", message)
    }

    /** The Live Activity is (or no longer is) on screen: then the answer lands there, not in a notification too. */
    public fun showing(on: Boolean) {
        ResultSurfaces.onLockScreen = on
    }

    private fun map(s: TaskState): ActivityUiState {
        val id = s.taskId ?: return ActivityUiState.Hidden
        val task = tasks.get(id)?.text.orEmpty()
        val result = s.lastResult
        return when {
            s.running && s.question != null -> s.question!!.let { q ->
                ActivityUiState.Waiting(id, task, q.text, s.conversationId, q.id, q.yesNo, q.options)
            }
            s.running -> ActivityUiState.Working(
                taskId = id,
                task = task,
                step = s.steps.size,
                startedAtMs = s.startedAtMs,
                now = s.now(),
                paused = s.paused,
                plan = planSteps(s),
                conversationId = s.conversationId,
            )
            // Stopped by the user: from Mode, the island, or by answering a question with Stop.
            result == null || result.outcome is AgentOutcome.Stopped -> ActivityUiState.Ended(id)
            else -> {
                val (badge, reply) = when (val o = result.outcome) {
                    is AgentOutcome.Done -> (if (o.success) TaskBadge.Done else TaskBadge.GaveUp) to o.summary
                    is AgentOutcome.Failed -> TaskBadge.Failed to o.why
                    is AgentOutcome.Stopped -> TaskBadge.Stopped to o.why
                }
                val (parts, more) = lockScreenParts(replyParts(reply))
                ActivityUiState.Finished(id, badge, result.millis / 1000, parts, more, s.conversationId)
            }
        }
    }

    private companion object {
        const val PLAN_SHOWN = 3
        /** About two rows of the Live Activity headline. */
        const val HEADLINE = 80

        /** The previous, current and next plan items; current is what the latest action said it works on. */
        fun planSteps(s: TaskState): List<PlanStep> {
            if (s.plan.isEmpty()) return emptyList()
            val current = (s.steps.lastOrNull { it.planItem != null }?.planItem ?: (s.plan.indexOfFirst { !it.done } + 1))
                .coerceIn(1, s.plan.size) - 1
            val from = (current - 1).coerceIn(0, (s.plan.size - PLAN_SHOWN).coerceAtLeast(0))
            return s.plan.withIndex().drop(from).take(PLAN_SHOWN).map { (i, item) ->
                PlanStep(item.text, when { i == current -> PlanStepState.Now; item.done || i < current -> PlanStepState.Done; else -> PlanStepState.Next })
            }
        }

        /**
         * iOS gives a Live Activity 160 pt: a header, one line of the answer (two rows at most) and the
         * buttons. The line is the first paragraph or heading, or else the first row of a list; the list's
         * other rows are counted for "Open chat · 2 more".
         */
        fun lockScreenParts(all: List<ReplyPart>): Pair<List<ReplyPart>, Int> {
            fun line(t: String) = ReplyPart.Paragraph(t.replace('\n', ' ').let { if (it.length > HEADLINE) it.take(HEADLINE - 1).trimEnd() + "…" else it })
            val text = all.firstNotNullOfOrNull { (it as? ReplyPart.Paragraph)?.text ?: (it as? ReplyPart.Heading)?.text }
            val rows: List<String> = all.firstNotNullOfOrNull { part ->
                when (part) {
                    is ReplyPart.Agenda -> part.rows.map { listOfNotNull(it.start, it.text).joinToString(" ") }
                    is ReplyPart.Bullets -> part.items
                    is ReplyPart.Numbered -> part.items
                    else -> null
                }
            }.orEmpty()
            return when {
                text != null -> listOf(line(text)) to rows.size
                rows.isNotEmpty() -> listOf(line(rows.first())) to rows.size - 1
                else -> emptyList<ReplyPart>() to 0
            }
        }
    }
}
