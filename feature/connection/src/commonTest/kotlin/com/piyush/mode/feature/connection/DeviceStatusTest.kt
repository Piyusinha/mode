package com.piyush.mode.feature.connection

import com.piyush.mode.core.platform.ManualStep
import com.piyush.mode.core.platform.PhoneQuirks
import com.piyush.mode.feature.connection.domain.BackgroundBlock
import com.piyush.mode.feature.connection.domain.CheckState
import com.piyush.mode.feature.connection.domain.DeviceStatus
import com.piyush.mode.feature.connection.domain.PhoneCheckId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeviceStatusTest {
    @Test
    fun onlyADownloadedApkIsLockedNeverAnAdbOrStoreInstall() {
        assertTrue(DeviceStatus.locked(installer = "com.android.chrome", switchOn = false), "a browser download")
        assertFalse(DeviceStatus.locked(installer = null, switchOn = false), "adb leaves no installer")
        assertFalse(DeviceStatus.locked(installer = "com.android.shell", switchOn = false), "adb as the shell")
        assertFalse(DeviceStatus.locked(installer = "com.android.vending", switchOn = false), "the Play Store")
        assertFalse(DeviceStatus.locked(installer = "com.android.chrome", switchOn = true), "once on, Android allowed it")
    }

    @Test
    fun eachMakerGetsItsOwnManualRowsWithWhatTheUserConfirmed() {
        val samsung = DeviceStatus.manual(PhoneQuirks.of("samsung"), confirmed = emptySet())
        assertEquals(listOf(PhoneCheckId.Sleeping), samsung.map { it.id })
        assertEquals(CheckState.Unverifiable(confirmed = false), samsung.single().state)

        val xiaomi = DeviceStatus.manual(PhoneQuirks.of("Xiaomi", "Redmi"), confirmed = setOf(ManualStep.Autostart.name))
        assertEquals(listOf(PhoneCheckId.Autostart, PhoneCheckId.Popups), xiaomi.map { it.id })
        assertEquals(listOf(true, false), xiaomi.map { (it.state as CheckState.Unverifiable).confirmed })

        assertTrue(DeviceStatus.manual(PhoneQuirks.of("Google"), emptySet()).isEmpty(), "a Pixel has nothing to confirm")
    }

    @Test
    fun manualIdsRoundTrip() {
        ManualStep.entries.forEach { assertEquals(it, DeviceStatus.step(DeviceStatus.id(it))) }
        assertNull(DeviceStatus.step(PhoneCheckId.Engine))
    }

    @Test
    fun theShortcutIsSetWhenAndroidSaysSoOrTheUserDoes() {
        assertEquals(CheckState.Unverifiable(confirmed = true), DeviceStatus.shortcut(set = true, confirmed = emptySet()).state)
        assertEquals(CheckState.Unverifiable(confirmed = true), DeviceStatus.shortcut(set = false, confirmed = setOf(ManualStep.Shortcut.name)).state)
        assertEquals(CheckState.Unverifiable(confirmed = false), DeviceStatus.shortcut(set = false, confirmed = emptySet()).state)
        assertEquals(PhoneCheckId.Shortcut, DeviceStatus.shortcut(set = false, confirmed = emptySet()).id)
    }

    @Test
    fun backgroundModeIsBlockedByTheMostPermanentReasonFirst() {
        assertEquals(BackgroundBlock.AndroidTooOld, DeviceStatus.backgroundBlock(sdkInt = 33, wifi = true, debuggingAllowed = true))
        assertEquals(BackgroundBlock.AndroidTooOld, DeviceStatus.backgroundBlock(sdkInt = 33, wifi = false, debuggingAllowed = false))
        assertEquals(BackgroundBlock.DebuggingBlocked, DeviceStatus.backgroundBlock(sdkInt = 35, wifi = false, debuggingAllowed = false))
        assertEquals(BackgroundBlock.NoWifi, DeviceStatus.backgroundBlock(sdkInt = 34, wifi = false, debuggingAllowed = true))
        assertNull(DeviceStatus.backgroundBlock(sdkInt = 34, wifi = true, debuggingAllowed = true))
    }

    @Test
    fun theLinkWaitsForWifiBeforeItCountsAsLost() {
        assertEquals(CheckState.Failed(DeviceStatus.NO_WIFI, null), DeviceStatus.backgroundLink(wifi = false, lastConnectFailed = true))
        assertEquals(CheckState.Failed(DeviceStatus.LINK_LOST, null), DeviceStatus.backgroundLink(wifi = true, lastConnectFailed = true))
        assertEquals(CheckState.Passed(null), DeviceStatus.backgroundLink(wifi = true, lastConnectFailed = false))
    }
}
