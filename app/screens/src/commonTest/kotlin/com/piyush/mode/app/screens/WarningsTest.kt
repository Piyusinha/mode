package com.piyush.mode.app.screens

import com.piyush.mode.app.screens.home.WarningKind
import com.piyush.mode.app.screens.home.WarningTone
import com.piyush.mode.app.screens.home.WarningsPresenter
import com.piyush.mode.feature.connection.domain.CheckState
import com.piyush.mode.feature.connection.domain.DeviceStatus
import com.piyush.mode.feature.connection.domain.PhoneCheckId
import com.piyush.mode.feature.connection.domain.PhoneCheckResult
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WarningsTest {
    private fun ok(id: PhoneCheckId) = PhoneCheckResult(id, CheckState.Passed(null))
    private fun failed(id: PhoneCheckId, reason: String) = PhoneCheckResult(id, CheckState.Failed(reason, null))
    private val fine = listOf(ok(PhoneCheckId.RestrictedSettings), ok(PhoneCheckId.Engine), ok(PhoneCheckId.Battery), ok(PhoneCheckId.Speech))

    @Test
    fun nothingWrongMeansNoBanner() = runTest {
        val f = Fixture(this)
        f.checks.quick = fine
        val p = WarningsPresenter(backgroundScope, f.checks, f.settings, FakeLifecycle(), now = { nov(7, 9) })
        testScheduler.advanceUntilIdle()
        assertNull(p.state.value.current)
    }

    @Test
    fun theMostSeriousWarningShowsFirstAndALockHidesThePlainOffSwitch() = runTest {
        val f = Fixture(this)
        f.checks.quick = listOf(
            failed(PhoneCheckId.RestrictedSettings, "locked"), failed(PhoneCheckId.Engine, DeviceStatus.ENGINE_OFF),
            PhoneCheckResult(PhoneCheckId.BackgroundLink, CheckState.Failed(DeviceStatus.NO_WIFI, null)), failed(PhoneCheckId.Speech, "none"),
        )
        val p = WarningsPresenter(backgroundScope, f.checks, f.settings, FakeLifecycle(), now = { nov(7, 9) })
        val s = p.state.awaitState { it.current != null }
        assertEquals(WarningKind.Locked, s.current?.kind)
        assertEquals(WarningTone.Problem, s.current?.tone)
        assertFalse(s.current!!.closable, "a problem stays until it's fixed")
        val all = p.warnings(f.checks.quick.associate { it.id to it.state }).map { it.kind }
        assertEquals(listOf(WarningKind.Locked, WarningKind.BackgroundWaitingWifi, WarningKind.NoSpeech), all)
    }

    @Test
    fun aStoppedServiceIsAWarningThatNamesTheMakerAndCanBeClosedForThreeDays() = runTest {
        val f = Fixture(this)
        var time = nov(7, 9)
        f.checks.quick = listOf(ok(PhoneCheckId.RestrictedSettings), failed(PhoneCheckId.Engine, DeviceStatus.ENGINE_STOPPED), ok(PhoneCheckId.Speech))
        val p = WarningsPresenter(backgroundScope, f.checks, f.settings, FakeLifecycle(), now = { time }, brand = { "Samsung" })
        val s = p.state.awaitState { it.current != null }
        assertEquals(WarningKind.PhoneControlStopped, s.current?.kind)
        assertEquals("Samsung", s.current?.brand)
        assertTrue(s.current!!.closable)

        p.dismiss()
        val gone = p.state.awaitState { it.current == null }
        assertNull(gone.current)
        assertEquals(time + WarningsPresenter.SNOOZE_MS, f.settings.settings.value.warningSnoozes[WarningKind.PhoneControlStopped.name])

        // Three days on, still broken: it's back.
        time += WarningsPresenter.SNOOZE_MS
        p.refresh()
        assertEquals(WarningKind.PhoneControlStopped, p.state.awaitState { it.current != null }.current?.kind)
    }

    @Test
    fun comingBackToTheFrontReadsAgainAndAFixClearsTheBanner() = runTest {
        val f = Fixture(this)
        val lifecycle = FakeLifecycle()
        f.checks.quick = listOf(ok(PhoneCheckId.RestrictedSettings), failed(PhoneCheckId.Engine, DeviceStatus.ENGINE_OFF))
        val p = WarningsPresenter(backgroundScope, f.checks, f.settings, lifecycle, now = { nov(7, 9) })
        assertEquals(WarningKind.PhoneControlOff, p.state.awaitState { it.current != null }.current?.kind)
        f.checks.quick = fine
        lifecycle.cameBack()
        assertNull(p.state.awaitState { it.current == null }.current)
    }
}
