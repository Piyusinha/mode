package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.run.ActivityUiState
import com.piyush.mode.app.screens.run.LiveActivityPresenter
import com.piyush.mode.app.screens.start.UnlockTest
import com.piyush.mode.app.screens.start.UnlockVoicePresenter
import com.piyush.mode.core.platform.ManualDeviceLock
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.presentation.TaskQueue
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UnlockVoiceTest {
    @Test
    fun setUpRemembersItAndStartsTheCheckWaitingForTheLock() = runTest {
        val f = Fixture(this)
        val lock = ManualDeviceLock()
        val p = UnlockVoicePresenter(this, f.settings, lock)
        assertFalse(p.state.value.setUp)
        p.setUp()
        testScheduler.runCurrent()
        assertTrue(f.settings.settings.value.voiceUnlock)
        assertEquals(UnlockTest.WaitingForLock(UnlockVoicePresenter.LOCK_WAIT_S), p.state.value.test)
        p.close()
    }

    @Test
    fun lockingThenUnlockingInTimeShowsItWorksAndHowLongItTook() = runTest {
        val f = Fixture(this)
        val lock = ManualDeviceLock()
        val p = UnlockVoicePresenter(this, f.settings, lock)
        p.startTest()
        testScheduler.advanceTimeBy(5_000)
        lock.lock()
        testScheduler.runCurrent()
        assertIs<UnlockTest.WaitingForUnlock>(p.state.value.test)
        testScheduler.advanceTimeBy(3_000)
        testScheduler.runCurrent()
        assertEquals(UnlockTest.WaitingForUnlock(UnlockVoicePresenter.UNLOCK_WAIT_S - 3), p.state.value.test, "the seconds count down")
        lock.unlock()
        testScheduler.runCurrent()
        assertIs<UnlockTest.Works>(p.state.value.test)
        assertTrue(f.settings.settings.value.voiceUnlock, "a check that passes also turns it on")
        p.close()
    }

    @Test
    fun neverLockingEndsTheCheckWithAClearResult() = runTest {
        val f = Fixture(this)
        val p = UnlockVoicePresenter(this, f.settings, ManualDeviceLock())
        p.startTest()
        testScheduler.advanceTimeBy(UnlockVoicePresenter.LOCK_WAIT_S * 1_000L + 100)
        testScheduler.runCurrent()
        assertEquals(UnlockTest.NeverLocked, p.state.value.test)
        p.close()
    }

    @Test
    fun stayingLockedMeansThePhraseDidNotUnlockIt() = runTest {
        val f = Fixture(this)
        val lock = ManualDeviceLock()
        val p = UnlockVoicePresenter(this, f.settings, lock)
        p.startTest()
        testScheduler.runCurrent()
        lock.lock()
        testScheduler.advanceTimeBy(UnlockVoicePresenter.UNLOCK_WAIT_S * 1_000L + 100)
        testScheduler.runCurrent()
        assertEquals(UnlockTest.StayedLocked, p.state.value.test)
        p.close()
    }

    @Test
    fun aCheckOpenedOnALockedIPhoneWaitsForItToUnlockFirst() = runTest {
        val f = Fixture(this)
        val lock = ManualDeviceLock(locked = true)
        val p = UnlockVoicePresenter(this, f.settings, lock)
        p.startTest()
        testScheduler.runCurrent()
        assertEquals(UnlockTest.Idle, p.state.value.test, "nothing counts while it is still locked from before")
        lock.unlock()
        testScheduler.runCurrent()
        assertIs<UnlockTest.WaitingForLock>(p.state.value.test)
        p.close()
    }

    @Test
    fun turnOffForgetsItAndStopsAnyCheck() = runTest {
        val f = Fixture(this)
        val p = UnlockVoicePresenter(this, f.settings, ManualDeviceLock())
        p.setUp()
        testScheduler.runCurrent()
        p.turnOff()
        testScheduler.runCurrent()
        assertFalse(f.settings.settings.value.voiceUnlock)
        assertEquals(UnlockTest.Idle, p.state.value.test)
        testScheduler.advanceTimeBy(UnlockVoicePresenter.LOCK_WAIT_S * 2_000L)
        testScheduler.runCurrent()
        assertEquals(UnlockTest.Idle, p.state.value.test, "the cancelled check never reports")
        p.close()
    }

    @Test
    fun aTaskWaitingForAnUnlockShowsOnTheLiveActivityWithoutThePhrase() = runTest {
        val f = Fixture(this)
        f.settings.update { it.copy(voiceUnlock = true) }
        val lock = ManualDeviceLock(locked = true)
        val queue = TaskQueue(f.runner, f.tasks, f.conversations, this, lock = lock)
        val activity = LiveActivityPresenter(this, f.runner, f.tasks, queue, f.settings)
        val task = queue.submit("Order my usual oats", TaskSource.Siri)!!
        testScheduler.runCurrent()
        assertEquals(ActivityUiState.WaitingForUnlock(task.id, "Order my usual oats", voiceUnlock = true, conversationId = task.conversationId), activity.state.value)
        assertTrue(task.conversationId != null, "a tap on the activity opens the waiting task's own chat")
        activity.cancelWaiting(task.id)
        testScheduler.runCurrent()
        assertEquals(null, queue.waitingForUnlock.value)
        activity.close()
    }
}
