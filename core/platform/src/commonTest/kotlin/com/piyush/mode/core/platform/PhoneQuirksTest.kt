package com.piyush.mode.core.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhoneQuirksTest {
    @Test
    fun subBrandsShareTheirMakersPages() {
        assertEquals("Xiaomi", PhoneQuirks.of("Xiaomi", "Redmi")?.name)
        assertEquals("Xiaomi", PhoneQuirks.of("Xiaomi", "POCO")?.name)
        assertEquals("OPPO", PhoneQuirks.of("realme", "realme")?.name)
        assertEquals("vivo", PhoneQuirks.of("vivo", "iQOO")?.name)
        assertEquals("Huawei", PhoneQuirks.of("HONOR")?.name)
    }

    @Test
    fun brandsThatStopPhoneControlHaveAnAutostartPage() {
        listOf("Xiaomi", "OPPO", "OnePlus", "vivo", "Huawei").forEach { maker ->
            assertTrue(PhoneQuirks.of(maker)!!.autostart.isNotEmpty(), maker)
        }
        assertTrue(PhoneQuirks.of("samsung")!!.battery.isNotEmpty())
    }

    @Test
    fun samsungAsksToBeKeptOutOfSleepAndXiaomiToAutostartAndShowPopups() {
        assertEquals(listOf(ManualStep.Sleeping), PhoneQuirks.of("samsung")!!.manualSteps)
        assertEquals(listOf(ManualStep.Autostart, ManualStep.Popups), PhoneQuirks.of("Xiaomi", "POCO")!!.manualSteps)
        val sleeping = PhoneQuirks.of("samsung")!!.sleeping.first()
        assertEquals("com.samsung.android.sm.ACTION_OPEN_CHECKABLE_LISTACTIVITY", sleeping.action)
        assertEquals(2, sleeping.intExtras["activity_type"])
        assertEquals("extra_pkgname", PhoneQuirks.of("Xiaomi")!!.popups.first().appPackageExtra)
        assertTrue(PhoneQuirks.of("OnePlus")!!.manualSteps == listOf(ManualStep.Autostart))
    }

    @Test
    fun aPlainAndroidPhoneUsesAndroidsOwnSetting() {
        assertNull(PhoneQuirks.of("Google", "google"))
        assertNull(PhoneQuirks.of("motorola"))
    }
}
