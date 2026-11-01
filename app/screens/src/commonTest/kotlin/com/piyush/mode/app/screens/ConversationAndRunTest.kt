package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.conversation.ConversationPresenter
import com.piyush.mode.app.screens.repeat.Frequency
import com.piyush.mode.app.screens.repeat.RepeatPresenter
import com.piyush.mode.app.screens.run.LiveRunPresenter
import com.piyush.mode.app.screens.run.RunScreensPresenter
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.first

class ConversationAndRunTest {
    @Test
    fun aConversationShowsTurnsHowTheTaskEndedAndItsScreens() = runTest {
        val f = Fixture(this, ScriptedModel(
            call("open_app", "name" to "Settings", "reason" to "open"),
            call("done", "evidence" to "x", "summary" to "Dark mode is off."),
            call("done", "evidence" to "x", "summary" to "Back on."),
        ))
        val first = f.queue.submit("Turn off dark mode", TaskSource.App)!!
        testScheduler.advanceUntilIdle()
        val p = ConversationPresenter(backgroundScope, first.conversationId!!, f.conversations, f.runner, f.queue, f.tasks, f.runs, f.clock)
        val s = p.state.awaitState { it.turns.size == 2 }
        assertEquals("Turn off dark mode", s.title)
        assertTrue(s.turns[0].fromUser)
        assertEquals(TaskBadge.Done, s.turns[1].badge)
        // The fake screen never changes, so the first done is sent back for a check: three looks.
        assertEquals(3, s.turns[1].screens)

        p.setPrivate(true)
        assertTrue(p.state.awaitState { it.privateNext }.privateNext)
        assertTrue(p.send("Turn it back on"))
        testScheduler.advanceUntilIdle()
        val after = p.state.awaitState { it.turns.size == 4 }
        assertFalse(after.privateNext, "private applies to one message")
        assertTrue(f.tasks.latest(1).single().private)
        assertFalse(p.send("   "))
    }

    @Test
    fun liveRunShowsStepsTheQuestionAndHowItEnded() = runTest {
        val f = Fixture(this, ScriptedModel(
            call("open_app", "name" to "WhatsApp", "reason" to "Open WhatsApp to message Sonali"),
            call("ask_user", "question" to "Which Sonali?"),
            call("done", "evidence" to "x", "summary" to "Sent."),
        ))
        val p = LiveRunPresenter(backgroundScope, f.runner, f.tasks)
        f.queue.submit("Message Sonali", TaskSource.App)
        testScheduler.advanceUntilIdle()
        val asking = p.state.awaitState { it.running?.question != null }
        assertEquals("Open WhatsApp to message Sonali", asking.steps.first().reason)
        assertTrue(asking.steps.first().worked)

        p.answer("Sonali Yadav")
        testScheduler.advanceUntilIdle()
        val done = p.state.awaitState { it.finished != null }.finished!!
        assertEquals(TaskBadge.Done, done.badge)
        assertEquals("Sent.", done.summary)
    }

    @Test
    fun runScreensListsEveryScreenWithWhatWasDone() = runTest {
        val f = Fixture(this, ScriptedModel(call("open_app", "name" to "Settings", "reason" to "x"), call("done", "evidence" to "x", "summary" to "ok")))
        val task = f.queue.submit("Open settings", TaskSource.App)!!
        testScheduler.advanceUntilIdle()
        val p = RunScreensPresenter(backgroundScope, task.id, f.runs, f.tasks)
        val shots = p.state.value.shots
        assertEquals(listOf(1, 2, 3), shots.map { it.step }, "the fake screen never changes, so done is checked once")
        assertEquals("open Settings", shots.first().action)
        assertNotNull(p.image(shots.first()))
    }

    @Test
    fun theRepeatFormValidatesAndSaves() = runTest {
        val f = Fixture(this)
        val p = RepeatPresenter(backgroundScope, "", f.scheduler, f.clock)
        assertFalse(p.state.value.canSave)
        assertFalse(p.save())

        p.setText("Read me today's calendar")
        p.setTime(9, 15)
        p.setFrequency(Frequency.Weekdays)
        assertEquals(setOf(1, 2, 3, 4, 5), p.state.value.isoDays)
        assertEquals(nov(2, 9, 15), p.state.value.firstRunAtMs, "Sunday → Monday")
        p.toggleDay(6)
        assertEquals(Frequency.Custom, p.state.value.frequency)
        p.toggleDay(6)
        assertEquals(Frequency.Weekdays, p.state.value.frequency)

        assertTrue(p.save())
        assertEquals(1, f.scheduler.all.awaitFirst().size)
    }
}

private suspend fun <T> kotlinx.coroutines.flow.Flow<T>.awaitFirst(): T =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { this@awaitFirst.first() }
