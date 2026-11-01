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

public data class FinishedRun(val badge: TaskBadge, val summary: String, val steps: Int, val seconds: Long, val costUsd: Double) {
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
                        FinishedRun(badge, summary, r.steps, r.millis / 1000, r.costUsd)
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
    taskId: String,
    private val runs: RunRepository,
    tasks: TaskRepository,
) : Presenter<RunScreensUiState>(parent, RunScreensUiState()) {
    private val runId = tasks.get(taskId)?.runId

    init {
        mutableState.value = RunScreensUiState(tasks.get(taskId)?.text.orEmpty(), runId?.let { runs.shots(it) }.orEmpty())
    }

    /** The JPEG of one screen. */
    public fun image(shot: RunShot): ByteArray? = runId?.let { runs.image(it, shot.image) }
}

public data class RunScreensUiState(val task: String = "", val shots: List<RunShot> = emptyList())
