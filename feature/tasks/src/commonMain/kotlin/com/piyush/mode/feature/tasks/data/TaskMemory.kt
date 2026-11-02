package com.piyush.mode.feature.tasks.data

import com.piyush.mode.llm.ModelProvider

import com.piyush.mode.agent.AgentMemory
import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.agent.AgentResult
import com.piyush.mode.agent.TaskContext
import com.piyush.mode.feature.memory.data.MemoryForAgent
import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.data.MemoryRetrieval
import com.piyush.mode.feature.memory.data.Reflector
import com.piyush.mode.feature.tasks.domain.TaskStatus

/**
 * Memory around one task: what the agent is given before it starts (about
 * you, skills, tips, recent tasks), and what is learned once it has finished.
 */
public class TaskMemory(
    private val memory: MemoryRepository,
    private val retrieval: MemoryRetrieval,
    private val reflector: Reflector,
    private val tasks: TaskRepository,
    private val startOfToday: () -> Long,
) {
    public class Prepared(
        public val context: TaskContext,
        /** For recall and app tips mid-task; null for private tasks. */
        public val agentMemory: AgentMemory?,
        /** The items shown, to record how the task went with them. */
        public val usedIds: List<String>,
    )

    /** [base] plus what memory knows; a private task gets nothing from memory. */
    public fun prepare(task: String, base: TaskContext, useMemory: Boolean, currentTaskId: String): Prepared {
        if (!useMemory) return Prepared(base, null, emptyList())
        val found = retrieval.forTask(task)
        val context = base.copy(
            aboutYou = found.aboutYou,
            skills = found.skills,
            appNotes = found.tips,
            knownRoute = found.knownRoute,
            recentTasks = recentTasks(currentTaskId),
        )
        return Prepared(context, MemoryForAgent(retrieval), found.usedIds)
    }

    /** Records how the task went with the items it was shown (ranking, and later expiry). */
    public fun recordUse(result: AgentResult, usedIds: List<String>) {
        if (usedIds.isNotEmpty()) memory.recordUse(usedIds, success = outcomeOf(result) == Reflector.Outcome.Done)
    }

    /** Learns from a finished task; its cost is added to the task. */
    public suspend fun learn(taskId: String, task: String, result: AgentResult, model: String,
        provider: ModelProvider = ModelProvider.OpenRouter): Reflector.Reflection {
        val outcome = outcomeOf(result)
        val summary = when (val o = result.outcome) {
            is AgentOutcome.Done -> o.summary
            is AgentOutcome.Stopped -> o.why
            is AgentOutcome.Failed -> o.why
        }
        val reflection = reflector.reflect(
            Reflector.Input(
                taskId = taskId, task = task, outcome = outcome, summary = summary,
                transcript = result.transcript, notes = result.notes, answers = result.answers,
                appsOpened = result.appsOpened, finalScreen = result.finalScreen,
            ),
            model,
            provider,
        )
        if (provider.reportsCurrencyCost) tasks.addCost(taskId, reflection.costUsd)
        return reflection
    }

    private fun outcomeOf(result: AgentResult) = when (val o = result.outcome) {
        is AgentOutcome.Done -> if (o.success) Reflector.Outcome.Done else Reflector.Outcome.GaveUp
        is AgentOutcome.Stopped -> Reflector.Outcome.Stopped
        is AgentOutcome.Failed -> Reflector.Outcome.Failed
    }

    public fun undo(itemId: String) {
        memory.undo(itemId)
    }

    /** "today: turn off dark mode → Dark mode is off." for the last few finished tasks, newest first. */
    private fun recentTasks(currentTaskId: String): List<String> {
        val today = startOfToday()
        return tasks.latest(RECENT + 1)
            .filter { it.id != currentTaskId && it.status.finished && it.status != TaskStatus.NotStarted }
            .take(RECENT.toInt())
            .map { t ->
                val day = when {
                    t.createdAt >= today -> "today"
                    t.createdAt >= today - DAY -> "yesterday"
                    else -> "earlier"
                }
                val result = when (t.status) {
                    TaskStatus.Done -> t.summary?.take(100)
                    else -> "could not finish"
                }
                "$day: ${t.text.take(100)}" + (result?.let { " → $it" } ?: "")
            }
    }

    private companion object {
        const val RECENT = 5L
        const val DAY = 86_400_000L
    }
}
