package com.piyush.mode.feature.connection

import com.piyush.mode.core.common.ActivityTracker
import com.piyush.mode.core.platform.AppLifecycle
import com.piyush.mode.feature.connection.domain.ConnectionState
import com.piyush.mode.feature.connection.domain.PhoneConnection
import com.piyush.mode.feature.connection.domain.PhoneLink
import com.piyush.mode.feature.connection.presentation.IdleDisconnectPolicy
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.ops.PhoneUnavailable
import com.piyush.mode.testing.FakePhone
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.time.Duration

class FakeLink(private val fail: String? = null) : PhoneLink {
    var connects = 0
    var releases = 0
    override suspend fun connect(): PhoneConnection {
        connects++
        fail?.let { throw PhoneUnavailable(it) }
        return PhoneConnection(FakePhone(listOf(emptyList())), "test route", "  test route: connected")
    }
    override suspend fun release() {
        releases++
    }
}

private class FakeLifecycle : AppLifecycle {
    var background: () -> Unit = {}
    var terminate: () -> Unit = {}
    override fun onBackground(action: () -> Unit) { background = action }
    override fun onTerminate(action: () -> Unit) { terminate = action }
}

class PhoneSessionTest {
    @Test
    fun connectsOnceAndReusesTheSession() = runTest {
        val link = FakeLink()
        val session = PhoneSession(link, Duration.ZERO)
        session.phone()
        session.phone()
        assertEquals(1, link.connects)
        assertEquals(ConnectionState.Connected("test route"), session.state.value)
    }

    @Test
    fun disconnectClosesAndReleasesTheVpn() = runTest {
        val link = FakeLink()
        val session = PhoneSession(link, Duration.ZERO)
        session.phone()
        session.disconnect()
        assertEquals(ConnectionState.Disconnected, session.state.value)
        assertEquals(1, link.releases)
        session.phone()
        assertEquals(2, link.connects, "the next use connects again")
    }

    @Test
    fun disconnectWithoutASessionLeavesTheVpnAlone() = runTest {
        val link = FakeLink()
        PhoneSession(link, Duration.ZERO).disconnect()
        assertEquals(0, link.releases)
    }

    @Test
    fun doneWithThePhoneReleasesTheVpnEvenWithoutASession() = runTest {
        val link = FakeLink()
        val session = PhoneSession(link, Duration.ZERO)
        session.disconnectAndRelease()
        assertEquals(1, link.releases, "checks and failed connects bring the VPN up without a session")
        session.phone()
        session.disconnectAndRelease()
        assertEquals(2, link.releases)
        assertEquals(ConnectionState.Disconnected, session.state.value)
    }

    @Test
    fun stopSessionAlwaysReleasesTheVpn() = runTest {
        val link = FakeLink()
        val session = PhoneSession(link, Duration.ZERO)
        session.stopSession()
        assertEquals(1, link.releases)
        assertEquals(ConnectionState.Stopped, session.state.value)
    }

    @Test
    fun aFailedConnectSaysWhy() = runTest {
        val session = PhoneSession(FakeLink(fail = "No pairing file yet"), Duration.ZERO)
        assertFailsWith<PhoneUnavailable> { session.phone() }
        val state = assertIs<ConnectionState.Failed>(session.state.value)
        assertEquals("No pairing file yet", state.reason)
    }

    @Test
    fun leavingModeWhileIdleDisconnectsButNotWhileBusy() = runTest {
        val link = FakeLink()
        val session = PhoneSession(link, Duration.ZERO)
        val activity = ActivityTracker()
        val lifecycle = FakeLifecycle()
        IdleDisconnectPolicy(session, activity, this).install(lifecycle)

        session.phone()
        val release = CompletableDeferred<Unit>()
        val work = launch { activity.track { release.await() } }
        testScheduler.runCurrent()
        lifecycle.background()
        testScheduler.runCurrent()
        assertEquals(0, link.releases, "busy: stays connected")

        release.complete(Unit)
        work.join()
        lifecycle.background()
        testScheduler.advanceUntilIdle()
        assertEquals(1, link.releases, "idle: disconnected")
    }
}

class PhoneSessionReuseTest {
    @Test
    fun aNewSessionDuringTheReleaseDelayKeepsTheVpn() = runTest {
        // Setup's check closes; the first job connects a second later, before the check's 2 s release.
        val link = FakeLink()
        val session = PhoneSession(link, kotlin.time.Duration.parse("2s"))
        session.phone()
        val closing = launch { session.disconnectAndRelease() }
        kotlinx.coroutines.delay(1000)
        session.phone()
        closing.join()
        assertEquals(0, link.releases, "the late release must not stop the VPN under the new session")
        assertEquals(ConnectionState.Connected("test route"), session.state.value)
        // Closing the new one still releases it.
        session.disconnect()
        assertEquals(1, link.releases)
    }

    @Test
    fun withNoNewSessionTheReleaseHappens() = runTest {
        val link = FakeLink()
        val session = PhoneSession(link, kotlin.time.Duration.parse("2s"))
        session.phone()
        session.disconnect()
        assertEquals(1, link.releases)
        session.disconnectAndRelease() // nothing open: the VPN is turned off anyway
        assertEquals(2, link.releases)
    }
}
