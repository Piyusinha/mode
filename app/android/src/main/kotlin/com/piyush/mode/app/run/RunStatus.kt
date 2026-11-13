package com.piyush.mode.app.run

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.piyush.mode.app.R
import com.piyush.mode.app.common.Words
import com.piyush.mode.app.common.rememberWords
import com.piyush.mode.app.graph.ModeGraph
import com.piyush.mode.app.screens.common.RunningTask
import com.piyush.mode.app.screens.run.ActivityUiState
import com.piyush.mode.app.screens.run.LiveActivityPresenter
import com.piyush.mode.app.screens.run.LiveRunPresenter
import com.piyush.mode.app.screens.run.PlanStepState
import com.piyush.mode.app.shell.rememberPresenter

/** The running task as the bar above the tabs and Home's dark card show it. */
data class Running(
    val title: String,
    /** "Step 9 · Checking the cart", or Paused. */
    val status: String,
    val paused: Boolean,
    /** Plan items done, of how many; null without a plan. */
    val progress: Pair<Int, Int>?,
)

/** A question the running task is waiting on. */
data class PendingQuestion(
    val id: String,
    val text: String,
    val yesNo: Boolean,
    val options: List<String>,
    val recommended: String?,
    val allowAlways: Boolean,
    val yourTurn: Boolean,
    val spoken: Boolean,
)

data class RunStatusState(val running: Running? = null, val talking: Boolean = false, val question: PendingQuestion? = null)

/** The running task everywhere in the app, and what the user can do to it. */
class RunStatus(val state: RunStatusState, private val presenter: LiveRunPresenter) {
    fun stop() = presenter.stop()
    fun togglePause() = if (state.running?.paused == true) presenter.resume() else presenter.pause()
    fun answer(text: String) = presenter.answer(text)
    fun stopFromQuestion() = presenter.answer(null)

    companion object {
        /** "Step 9 · Checking the cart"; just the status before the first step. */
        fun status(r: RunningTask, words: Words): String = when {
            r.paused -> words.get(R.string.run_paused)
            r.steps > 0 -> words.get(R.string.run_step_status, r.steps, r.status)
            else -> r.status
        }

        fun map(r: RunningTask?, activity: ActivityUiState, talking: Boolean, words: Words): RunStatusState {
            if (r == null) return RunStatusState()
            val plan = (activity as? ActivityUiState.Working)?.plan.orEmpty()
            return RunStatusState(
                running = Running(
                    r.text, status(r, words), r.paused,
                    if (plan.isEmpty()) null else plan.count { it.state == PlanStepState.Done } to plan.size,
                ),
                talking = talking,
                question = r.question?.let {
                    PendingQuestion(r.taskId + it.text, it.text, it.yesNo, it.options, it.recommended, it.allowAlways, it.yourTurn, it.spoken)
                },
            )
        }
    }
}

/** The running task, kept for as long as the tabs show. */
@Composable
fun rememberRunStatus(graph: ModeGraph): RunStatus {
    val presenter: LiveRunPresenter = rememberPresenter("liveRun") { graph.screens.liveRun() }
    val activityPresenter: LiveActivityPresenter = rememberPresenter("liveActivity") { graph.screens.liveActivity() }
    val live by presenter.state.collectAsStateWithLifecycle()
    val activity by activityPresenter.state.collectAsStateWithLifecycle()
    val words = rememberWords()
    val state = remember(live.running, activity) { RunStatus.map(live.running, activity, live.running?.talking == true, words) }
    return RunStatus(state, presenter)
}
