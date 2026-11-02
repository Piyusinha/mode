package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.conversation.ReplyPart
import com.piyush.mode.app.screens.run.ActivityUiState
import com.piyush.mode.app.screens.run.LiveActivityPresenter
import com.piyush.mode.app.screens.run.PlanStep
import com.piyush.mode.app.screens.run.PlanStepState
import com.piyush.mode.core.platform.Notifier
import com.piyush.mode.core.platform.ResultSurfaces
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LiveActivityTest {
    @AfterTest
    fun reset() {
        ResultSurfaces.onLockScreen = false
    }

    /** Runs the test clock until [test] holds (the fake phone answers instantly; waits take test time). */
    private fun TestScope.until(p: LiveActivityPresenter, test: (ActivityUiState) -> Boolean): ActivityUiState {
        var ticks = 0
        while (!test(p.state.value) && ticks++ < 10_000) {
            testScheduler.advanceTimeBy(10)
            testScheduler.runCurrent()
        }
        return p.state.value
    }

    @Test
    fun workingShowsThePlanAroundTheCurrentItemThenWaitingThenTheAnswer() = runTest {
        val answer = "You have **3 things** today.\n\n- 14:00 – Biozyme\n- 21:00 – Shelcal\n- 23:00 – Softovac"
        val f = Fixture(this, ScriptedModel(
            call("plan", "steps" to "1. Open Settings\n2. Turn off dark mode\n3. Open YouTube"),
            call("tap", "x" to 100, "y" to 100, "reason" to "Dark Mode switch", "item" to 2),
            call("wait", "seconds" to 1, "reason" to "settling", "item" to 2),
            call("ask_user", "question" to "Which one?"),
            call("done", "evidence" to "x", "summary" to answer),
            call("done", "evidence" to "x", "summary" to answer),
        ))
        val p = LiveActivityPresenter(backgroundScope, f.runner, f.tasks)
        assertEquals(ActivityUiState.Hidden, p.state.value)
        f.queue.submit("Turn off dark mode and open YouTube", TaskSource.Siri)

        val working = assertIs<ActivityUiState.Working>(until(p) { it is ActivityUiState.Working && it.now == "Dark Mode switch" })
        assertEquals("Turn off dark mode and open YouTube", working.task)
        assertEquals(
            listOf(PlanStep("Open Settings", PlanStepState.Done), PlanStep("Turn off dark mode", PlanStepState.Now), PlanStep("Open YouTube", PlanStepState.Next)),
            working.plan,
        )

        val waiting = assertIs<ActivityUiState.Waiting>(until(p) { it is ActivityUiState.Waiting })
        assertEquals("Which one?", waiting.question)

        f.runner.answer("first")
        val done = assertIs<ActivityUiState.Finished>(until(p) { it is ActivityUiState.Finished })
        assertEquals(TaskBadge.Done, done.badge)
        // One line fits the Live Activity (160 pt with the header and buttons); the rows wait in the chat.
        assertEquals(listOf(ReplyPart.Paragraph("You have **3 things** today.")), done.parts)
        assertEquals(3, done.more, "the agenda's rows are in the chat")
    }

    @Test
    fun withoutAPlanThereAreNoSegments() = runTest {
        val f = Fixture(this, ScriptedModel(
            call("wait", "seconds" to 1, "reason" to "loading"),
            call("wait", "seconds" to 1, "reason" to "loading"),
            call("done", "evidence" to "x", "summary" to "ok"),
            call("done", "evidence" to "x", "summary" to "ok"),
        ))
        val p = LiveActivityPresenter(backgroundScope, f.runner, f.tasks)
        f.queue.submit("Wait", TaskSource.App)
        val working = assertIs<ActivityUiState.Working>(until(p) { it is ActivityUiState.Working && it.step >= 1 })
        assertEquals(emptyList(), working.plan)
    }

    @Test
    fun stoppingEndsTheActivityAtOnce() = runTest {
        val f = Fixture(this, ScriptedModel(
            call("wait", "seconds" to 5, "reason" to "loading"),
            call("done", "evidence" to "x", "summary" to "ok"),
        ))
        val p = LiveActivityPresenter(backgroundScope, f.runner, f.tasks)
        f.queue.submit("Call 123", TaskSource.App)
        val working = assertIs<ActivityUiState.Working>(until(p) { it is ActivityUiState.Working })
        p.stop()
        assertEquals(ActivityUiState.Ended(working.taskId), until(p) { it is ActivityUiState.Ended })
    }

    @Test
    fun theAnswerIsNotNotifiedWhileTheLockScreenShowsIt() {
        val posted = mutableListOf<String>()
        val notifier = object : Notifier {
            override fun requestPermission() {}
            override fun post(title: String, body: String) { posted += body }
        }
        notifier.postResult("Mode", "first")
        ResultSurfaces.onLockScreen = true
        notifier.postResult("Mode", "second")
        notifier.post("Mode needs you", "a question")
        assertEquals(listOf("first", "a question"), posted)
    }
}
