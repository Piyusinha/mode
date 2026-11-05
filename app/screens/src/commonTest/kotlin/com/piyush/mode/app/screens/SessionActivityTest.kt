package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.run.SessionActivityPresenter
import com.piyush.mode.app.screens.run.SessionActivityUiState
import com.piyush.mode.feature.connection.domain.ConnectionState
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.testing.ScriptedModel
import com.piyush.mode.testing.call
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SessionActivityTest {
    private fun TestScope.wait(ms: Long) {
        testScheduler.advanceTimeBy(ms)
        testScheduler.runCurrent()
    }

    @Test
    fun anOpenSessionWithNoTaskShowsAfterItSettlesAndDisconnectClosesItWithoutUnpairing() = runTest {
        val f = Fixture(this)
        val p = SessionActivityPresenter(backgroundScope, f.runner, f.session, f.clock)
        assertEquals(SessionActivityUiState.Hidden, p.state.value)

        f.session.phone()
        wait(1_000)
        assertEquals(SessionActivityUiState.Hidden, p.state.value, "not straight away: queued tasks keep it open between them")
        wait(2_500)
        assertEquals(f.now, assertIs<SessionActivityUiState.Connected>(p.state.value).sinceMs)

        p.disconnect()
        wait(100)
        assertEquals(SessionActivityUiState.Hidden, p.state.value)
        assertEquals(ConnectionState.Stopped, f.session.state.value)

        // Not unpaired: the next use connects again by itself.
        f.session.phone()
        assertIs<ConnectionState.Connected>(f.session.state.value)
    }

    @Test
    fun aRunningTaskNeverShowsItAndTheTaskClosesTheSessionAfter() = runTest {
        val f = Fixture(this, ScriptedModel(
            call("tap", "x" to 100, "y" to 100, "reason" to "Wi-Fi"),
            call("wait", "seconds" to 5, "reason" to "settling"),
            call("done", "evidence" to "x", "summary" to "Done."),
        ))
        val p = SessionActivityPresenter(backgroundScope, f.runner, f.session, f.clock)
        val seen = mutableListOf<SessionActivityUiState>()
        f.queue.submit("Open Wi-Fi", TaskSource.App)
        var ticks = 0
        while ((ticks < 50 || f.runner.state.value.running) && ticks++ < 10_000) {
            wait(100)
            seen += p.state.value
        }
        assertTrue(seen.all { it == SessionActivityUiState.Hidden }, "shown during a task: $seen")
        wait(5_000)
        assertEquals(SessionActivityUiState.Hidden, p.state.value)
        assertTrue(f.session.state.value !is ConnectionState.Connected)
    }
}
