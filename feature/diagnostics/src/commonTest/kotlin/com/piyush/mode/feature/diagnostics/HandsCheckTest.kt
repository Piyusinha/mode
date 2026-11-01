package com.piyush.mode.feature.diagnostics

import com.piyush.mode.core.common.ActivityTracker
import com.piyush.mode.core.platform.AppInfo
import com.piyush.mode.core.platform.BackgroundWork
import com.piyush.mode.feature.connection.domain.PhoneConnection
import com.piyush.mode.feature.connection.domain.PhoneLink
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.diagnostics.presentation.HandsCheck
import com.piyush.mode.ops.Op
import com.piyush.mode.testing.FakePhone
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration

class HandsCheckTest {
    private val phone = FakePhone(List(5) { listOf("Wi-Fi" to (60.0 to 300.0)) }, supported = setOf(
        "screen.capture", "screen.text", "input.tap", "input.text", "nav.home", "apps.launch",
    ))
    private var releases = 0
    private val session = PhoneSession(object : PhoneLink {
        override suspend fun connect() = PhoneConnection(phone, "test", "")
        override suspend fun release() { releases++ }
    }, Duration.ZERO)
    private val check = HandsCheck(
        session,
        object : BackgroundWork { override suspend fun <T> run(work: suspend () -> T): T = work() },
        object : AppInfo { override val bundleId = "com.piyush.mode" },
        ActivityTracker(),
    )

    @Test
    fun probeOpensTheAppTapsTheTextComesBackAndLetsGo() = runTest {
        check.probe("Settings", "wi-fi", "")
        val tap = phone.actions.filterIsInstance<Op.Tap>().single()
        assertEquals(60.0, tap.x)
        assertEquals("com.piyush.mode", (phone.actions.last() as Op.LaunchApp).name, "came back to Mode")
        assertEquals(2, check.state.value.shots.size, "before and after")
        assertFalse(check.state.value.busy)
        assertEquals(1, releases)
    }

    @Test
    fun aFailureIsLoggedNotThrown() = runTest {
        check.probe("Nope", "", "")
        assertTrue(check.state.value.log.isNotEmpty())
        assertFalse(check.state.value.busy)
    }
}
