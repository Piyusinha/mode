package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.tasks.TasksPresenter
import com.piyush.mode.feature.schedules.domain.ScheduleRule
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TasksPresenterTest {
    private fun TestScope.tasks(f: Fixture) = TasksPresenter(backgroundScope, f.runner, f.queue, f.scheduler, f.tasks, f.clock)

    @Test
    fun showsTheRunningTaskItsQuestionAndWhatWaits() = runTest {
        val f = Fixture(this, ScriptedModel(call("ask_user", "question" to "Which Sonali?"), call("done", "evidence" to "x", "summary" to "ok")))
        val p = tasks(f)
        f.queue.submit("Message Sonali", TaskSource.App)
        testScheduler.advanceUntilIdle()
        f.queue.submit("Book a cab", TaskSource.Watch)
        testScheduler.advanceUntilIdle()

        val s = p.state.awaitState { it.running?.question != null && it.upNext.isNotEmpty() }
        assertEquals("Message Sonali", s.running!!.text)
        assertEquals("Which Sonali?", s.running!!.question!!.text)
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
}
