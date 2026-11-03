package com.piyush.mode.app.screens.run

import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.common.RunShot
import com.piyush.mode.app.screens.common.RunningTask
import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.common.now
import com.piyush.mode.app.screens.common.running
import com.piyush.mode.app.screens.common.shots
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

public data class LiveRunUiState(
    val running: RunningTask? = null,
    /** The app Mode is using, as it last saw it (JPEG). */
    val screen: ByteArray? = null,
    /** What it is doing now, in its own words. */
    val now: String? = null,
    val steps: List<LiveStep> = emptyList(),
    /** How the last task ended, once it has. */
    val finished: FinishedRun? = null,
)

public data class LiveStep(
    val index: Int,
    /** Why, in the agent's words: "Open Settings to change Dark Mode". */
    val reason: String,
    /** What it did: "tap \"Light\"". */
    val action: String,
    val worked: Boolean,
    val seconds: Double,
)

public data class FinishedRun(val badge: TaskBadge, val summary: String, val steps: Int, val seconds: Long, val costUsd: Double, val costReported: Boolean = true) {
    /** The reply as blocks, drawn like the chat's. */
    val parts: List<com.piyush.mode.app.screens.conversation.ReplyPart> get() = com.piyush.mode.app.screens.conversation.replyParts(summary)
}

/** The task running now, step by step, with Stop and the agent's questions. */
public class LiveRunPresenter(
    parent: CoroutineScope,
    private val runner: TaskRunner,
    private val tasks: TaskRepository,
) : Presenter<LiveRunUiState>(parent, LiveRunUiState()) {

    init {
        scope.launch {
            runner.state.collect { s ->
                mutableState.value = LiveRunUiState(
                    running = s.running(tasks),
                    screen = s.latestScreen.takeIf { s.running },
                    now = s.now(),
                    steps = s.steps.map { LiveStep(it.index, it.reason, it.action, !it.outcome.startsWith("failed") && !it.outcome.startsWith("not "), it.millis / 1000.0) },
                    finished = s.lastResult?.takeIf { !s.running }?.let { r ->
                        val (badge, summary) = when (val o = r.outcome) {
                            is AgentOutcome.Done -> (if (o.success) TaskBadge.Done else TaskBadge.GaveUp) to o.summary
                            is AgentOutcome.Stopped -> TaskBadge.Stopped to o.why
                            is AgentOutcome.Failed -> TaskBadge.Failed to o.why
                        }
                        FinishedRun(badge, summary, r.steps, r.millis / 1000, r.costUsd, r.costReported)
                    },
                )
            }
        }
    }

    public fun stop() {
        runner.stop()
    }

    public fun pause() {
        runner.pause()
    }

    public fun resume() {
        runner.resume()
    }

    /** Answers the agent's question; null stops the task. */
    public fun answer(text: String?) {
        runner.answer(text)
    }
}

/** Every screen the agent saw for one task, to look back through. */
public class RunScreensPresenter(
    parent: CoroutineScope,
    private val taskId: String,
    private val runs: RunRepository,
    private val tasks: TaskRepository,
    private val runner: TaskRunner? = null,
    private val inbox: com.piyush.mode.feature.tasks.domain.TaskInbox? = null,
    private val teacher: com.piyush.mode.feature.memory.data.Teacher? = null,
) : Presenter<RunScreensUiState>(parent, RunScreensUiState()) {
    private val runId = tasks.get(taskId)?.runId

    init {
        mutableState.value = RunScreensUiState(tasks.get(taskId)?.text.orEmpty(), runId?.let { runs.shots(it) }.orEmpty())
    }

    /**
     * The user marked [step] as wrong and said what Mode should have done. Every time: kept as a tip for next time.
     * Just this time: the running task does it that way now, or the task runs again that way.
     */
    public fun fixStep(step: Int, label: String, said: String, everyTime: Boolean) {
        val words = said.trim()
        if (words.isEmpty()) return
        val task = tasks.get(taskId)
        mutableState.value = state.value.copy(fixing = true, fixed = null)
        scope.launch {
            val reply = if (everyTime) {
                teacher?.teach(words, task?.text.orEmpty(), "step $step: $label")?.reply ?: NOT_SAVED
            } else {
                val run = runner?.state?.value
                when {
                    run?.running == true && run.taskId == taskId && runner.steer(words) -> NOW
                    task != null && inbox?.submit("${task.text}. $words", com.piyush.mode.feature.tasks.domain.TaskSource.App, task.conversationId, false) != null -> AGAIN
                    else -> NOT_SAVED
                }
            }
            mutableState.value = state.value.copy(fixing = false, fixed = reply)
        }
    }

    public fun fixedSeen() { mutableState.value = state.value.copy(fixed = null) }

    /** The JPEG of one screen. */
    public fun image(shot: RunShot): ByteArray? = runId?.let { runs.image(it, shot.image) }
}

public data class RunScreensUiState(
    val task: String = "",
    val shots: List<RunShot> = emptyList(),
    /** A wrong step is being taught (the model writes the tip), then what Mode says back. */
    val fixing: Boolean = false,
    val fixed: String? = null,
)

private const val NOW = "Okay, I'll do it that way now."
private const val AGAIN = "Okay, I'm doing it again that way."
private const val NOT_SAVED = "I couldn't keep that one. Try saying it another way."
