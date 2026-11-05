package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.tasks.TasksPresenter
import com.piyush.mode.feature.schedules.domain.ScheduleRule
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import com.piyush.mode.app.screens.common.RelativeDay
import kotlin.test.assertNotNull
import com.piyush.mode.feature.tasks.domain.TaskStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TasksPresenterTest {
    private fun TestScope.tasks(f: Fixture) = TasksPresenter(backgroundScope, f.runner, f.queue, f.scheduler, f.tasks, f.clock)

    @Test
    fun showsTheRunningTaskItsQuestionAndWhatWaits() = runTest {
        val f = Fixture(this, ScriptedModel(call("ask_user", "question" to "Which Asha?"), call("done", "evidence" to "x", "summary" to "ok")))
        val p = tasks(f)
        f.queue.submit("Message Asha", TaskSource.App)
        testScheduler.advanceUntilIdle()
        f.queue.submit("Book a cab", TaskSource.Watch)
        testScheduler.advanceUntilIdle()

        val s = p.state.awaitState { it.running?.question != null && it.upNext.isNotEmpty() }
        assertEquals("Message Asha", s.running!!.text)
        assertEquals("Which Asha?", s.running!!.question!!.text)
        assertEquals(listOf("Book a cab"), s.upNext.map { it.text })
        assertEquals(TaskSource.Watch, s.upNext.single().source)

        p.cancel(s.upNext.single().taskId)
        p.answer("the first")
        testScheduler.advanceUntilIdle()
        val after = p.state.awaitState { it.running == null && it.done.size == 2 }
        assertEquals(null, after.running)
        assertEquals(setOf(TaskBadge.Done, TaskBadge.Stopped), after.done.map { it.badge }.toSet())
    }

    @Test
    fun repeatsCanBeSwitchedOffAndDeleted() = runTest {
        val f = Fixture(this)
        val p = tasks(f)
        f.scheduler.add("Read me today's calendar", ScheduleRule(8, 30, ScheduleRule.WEEKDAYS))
        val r = p.state.awaitState { it.repeats.isNotEmpty() }.repeats.single()
        assertEquals(setOf(1, 2, 3, 4, 5), r.isoDays)
        assertEquals(nov(2, 8, 30), r.nextRunAtMs)

        p.setRepeatEnabled(r.scheduleId, false)
        assertFalse(p.state.awaitState { !it.repeats.single().enabled }.repeats.single().enabled)
        p.deleteRepeat(r.scheduleId)
        assertTrue(p.state.awaitState { it.isEmpty }.isEmpty)
    }

    @Test
    fun doneShowsOnlyTodayAndYesterdayWithTimesAndWhyItStopped() = runTest {
        val f = Fixture(this, now = nov(3, 9))
        fun finished(text: String, status: TaskStatus, summary: String, startAt: Long, endAt: Long) {
            f.now = startAt
            val t = f.tasks.create(text, TaskSource.App, null)
            f.tasks.markRunning(t.id, "run-$text")
            f.now = endAt
            f.tasks.finish(t.id, status, summary, 3, 0.001)
        }
        finished("Turn off Wi-Fi", TaskStatus.Done, "Wi-Fi is off.", nov(1, 9), nov(1, 9) + 20_000)
        finished("Text Priya", TaskStatus.Done, "Sent.", nov(2, 20), nov(2, 20) + 100_000)
        finished("Pay electricity bill", TaskStatus.GaveUp, "Couldn't sign in. The bank app asked for a password.", nov(3, 10), nov(3, 10) + 31_000)
        finished("Read your calendar", TaskStatus.Done, "You have 3 events.", nov(3, 8), nov(3, 8) + 42_000)
        f.now = nov(3, 18)
        val done = tasks(f).state.awaitState { it.done.size == 3 }.done
        assertEquals(listOf("Pay electricity bill", "Read your calendar", "Text Priya"), done.map { it.text }, "the 1st is two days old: not shown")
        assertEquals(listOf(RelativeDay.Today, RelativeDay.Today, RelativeDay.Yesterday), done.map { it.day })
        val bill = done.first()
        assertEquals(31L, bill.seconds)
        assertEquals("Couldn't sign in", bill.why)
        assertEquals(null, done[1].why, "done tasks have no reason")
        assertEquals(100L, done[2].seconds)
    }

    @Test
    fun aSuggestedTaskRunsLikeAnyTask() = runTest {
        val f = Fixture(this)
        val p = tasks(f)
        assertEquals(3, p.state.value.tryThese.size)
        val conversation = p.tryTask("Turn on Do Not Disturb")
        assertNotNull(conversation)
        assertEquals("Turn on Do Not Disturb", f.tasks.latest(5).single().text)
    }
}
