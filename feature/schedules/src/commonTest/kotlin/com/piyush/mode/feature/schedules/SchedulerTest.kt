package com.piyush.mode.feature.schedules

import com.piyush.mode.core.platform.AppLifecycle
import com.piyush.mode.core.platform.Notifier
import com.piyush.mode.feature.schedules.data.ScheduleRepository
import com.piyush.mode.feature.schedules.domain.ScheduleRule
import com.piyush.mode.feature.schedules.presentation.Scheduler
import com.piyush.mode.feature.tasks.domain.Task
import com.piyush.mode.feature.tasks.domain.TaskInbox
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.domain.TaskStatus
import com.piyush.mode.testing.testDatabase
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SchedulerTest {
    private val zone = TimeZone.of("Asia/Kolkata")
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime(2026, 11, d, h, m).toInstant(zone).toEpochMilliseconds()

    private class Inbox : TaskInbox {
        val submitted = mutableListOf<Pair<String, TaskSource>>()
        override fun submit(text: String, source: TaskSource, conversationId: String?, private: Boolean): Task {
            submitted += text to source
            return Task("t${submitted.size}", null, text, source, TaskStatus.Queued, 0, null, null, null, null, null, null)
        }
        override fun cancel(taskId: String) {}
    }

    private class Reminders : Notifier {
        val pending = mutableMapOf<String, Pair<String, Long>>()
        override fun requestPermission() {}
        override fun post(title: String, body: String) {}
        override fun schedule(id: String, title: String, body: String, atEpochMs: Long) { pending[id] = body to atEpochMs }
        override fun cancel(id: String) { pending.remove(id) }
    }

    private class Lifecycle : AppLifecycle {
        val foreground = mutableListOf<() -> Unit>()
        override fun onBackground(action: () -> Unit) {}
        override fun onForeground(action: () -> Unit) { foreground += action }
        override fun onTerminate(action: () -> Unit) {}
    }

    private var clock = at(1, 7, 0)
    private val inbox = Inbox()
    private val reminders = Reminders()
    private val repo = ScheduleRepository(testDatabase())

    private fun scheduler(scope: kotlinx.coroutines.CoroutineScope) =
        Scheduler(repo, inbox, reminders, scope, now = { clock }, zone = { zone }, tickMs = 30_000)

    @Test
    fun aDueScheduleGoesToTheInboxAndIsSetForNextTime() = runTest {
        val s = scheduler(this)
        val added = s.add("Send good morning to Sonali", ScheduleRule(8, 0, ScheduleRule.EVERY_DAY))!!
        assertEquals("Send good morning to Sonali — tap to run it" to at(1, 8), reminders.pending["schedule-${added.id}"])

        clock = at(1, 8, 0)
        s.checkDue()
        assertEquals(listOf("Send good morning to Sonali" to TaskSource.Schedule), inbox.submitted)
        assertEquals(at(2, 8), repo.get(added.id)!!.nextRunAt)
        assertEquals(at(2, 8), reminders.pending["schedule-${added.id}"]!!.second)

        s.checkDue()
        assertEquals(1, inbox.submitted.size, "not twice")
    }

    @Test
    fun aRunMissedByHoursIsSkippedNotRunLate() = runTest {
        val s = scheduler(this)
        val added = s.add("Read my calendar", ScheduleRule(8, 0, ScheduleRule.EVERY_DAY))!!
        clock = at(1, 11, 0)
        s.checkDue()
        assertTrue(inbox.submitted.isEmpty())
        assertEquals(at(2, 8), repo.get(added.id)!!.nextRunAt)
    }

    @Test
    fun aOneOffRunsOnceAndSwitchesItselfOff() = runTest {
        val s = scheduler(this)
        val added = s.add("Call the plumber reminder", ScheduleRule(7, 30, emptySet()))!!
        clock = at(1, 7, 31)
        s.checkDue()
        val after = repo.get(added.id)!!
        assertFalse(after.enabled)
        assertNull(after.nextRunAt)
        assertTrue(reminders.pending.isEmpty())
        assertEquals(1, inbox.submitted.size)
    }

    @Test
    fun switchingOffOrDeletingTakesBackTheNotification() = runTest {
        val s = scheduler(this)
        val a = s.add("A", ScheduleRule(9, 0, ScheduleRule.EVERY_DAY))!!
        val b = s.add("B", ScheduleRule(10, 0, ScheduleRule.EVERY_DAY))!!
        s.setEnabled(a.id, false)
        s.delete(b.id)
        assertTrue(reminders.pending.isEmpty())
        clock = at(1, 9, 0)
        s.checkDue()
        assertTrue(inbox.submitted.isEmpty())
        s.setEnabled(a.id, true)
        assertEquals(at(2, 9), reminders.pending["schedule-${a.id}"]!!.second)
    }

    @Test
    fun comingToTheFrontAndTheTimerBothCheck() = runTest {
        val s = scheduler(this)
        val lifecycle = Lifecycle()
        s.add("A", ScheduleRule(8, 0, ScheduleRule.EVERY_DAY))
        s.start(lifecycle)
        clock = at(1, 8, 0)
        lifecycle.foreground.single()()
        assertEquals(1, inbox.submitted.size)

        s.add("B", ScheduleRule(9, 0, ScheduleRule.EVERY_DAY))
        clock = at(1, 9, 0)
        testScheduler.advanceTimeBy(30_001)
        assertEquals(2, inbox.submitted.size)
        coroutineContext.cancelChildren()
    }

    @Test
    fun resetDeletesEveryScheduleAndItsNotification() = runTest {
        val s = scheduler(this)
        s.add("Send good morning to Sonali", ScheduleRule(8, 0, ScheduleRule.EVERY_DAY))
        s.add("Read my calendar", ScheduleRule(9, 0, ScheduleRule.EVERY_DAY))
        assertEquals(2, reminders.pending.size)

        s.deleteAll()

        assertTrue(repo.all().isEmpty())
        assertTrue(reminders.pending.isEmpty())
    }
}
