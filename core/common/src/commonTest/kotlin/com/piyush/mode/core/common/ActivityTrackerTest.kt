package com.piyush.mode.core.common

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActivityTrackerTest {
    @Test
    fun busyWhileWorkRunsAndIdleAfter() = runTest {
        val tracker = ActivityTracker()
        val release = CompletableDeferred<Unit>()
        val job = launch { tracker.track { release.await() } }
        testScheduler.runCurrent()
        assertTrue(tracker.isBusy)
        release.complete(Unit)
        job.join()
        assertFalse(tracker.isBusy)
    }

    @Test
    fun failedWorkStillCountsDown() = runTest {
        val tracker = ActivityTracker()
        assertFailsWith<IllegalStateException> { tracker.track { error("boom") } }
        assertEquals(0, tracker.active.value)
    }
}
