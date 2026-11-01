package com.piyush.mode.app.screens.run

import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.common.now
import com.piyush.mode.app.screens.conversation.ReplyPart
import com.piyush.mode.app.screens.conversation.replyParts
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
    public data class Waiting(val taskId: String, val task: String, val question: String, val conversationId: String?) : ActivityUiState

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
        scope.launch { runner.state.collect { mutableState.value = map(it) } }
    }

    /** Stop, from the Dynamic Island. */
    public fun stop() {
        runner.stop()
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
            s.running && s.question != null -> ActivityUiState.Waiting(id, task, s.question!!.text, s.conversationId)
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
        const val ROWS_SHOWN = 2
        const val HEADLINE = 90

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
         * The Lock Screen has room for a line and two rows: the first paragraph or
         * heading, then the first list or agenda cut to two rows.
         */
        fun lockScreenParts(all: List<ReplyPart>): Pair<List<ReplyPart>, Int> {
            val headline = all.firstOrNull { it is ReplyPart.Paragraph || it is ReplyPart.Heading }?.let {
                val text = (it as? ReplyPart.Paragraph)?.text ?: (it as ReplyPart.Heading).text
                ReplyPart.Paragraph(text.replace('\n', ' ').let { t -> if (t.length > HEADLINE) t.take(HEADLINE - 1).trimEnd() + "…" else t })
            }
            val rows: Pair<ReplyPart, Int>? = all.firstNotNullOfOrNull { part ->
                when (part) {
                    is ReplyPart.Agenda -> ReplyPart.Agenda(part.rows.take(ROWS_SHOWN)) to (part.rows.size - ROWS_SHOWN).coerceAtLeast(0)
                    is ReplyPart.Bullets -> ReplyPart.Bullets(part.items.take(ROWS_SHOWN)) to (part.items.size - ROWS_SHOWN).coerceAtLeast(0)
                    is ReplyPart.Numbered -> ReplyPart.Numbered(part.start, part.items.take(ROWS_SHOWN)) to (part.items.size - ROWS_SHOWN).coerceAtLeast(0)
                    else -> null
                }
            }
            return listOfNotNull(headline, rows?.first) to (rows?.second ?: 0)
        }
    }
}
