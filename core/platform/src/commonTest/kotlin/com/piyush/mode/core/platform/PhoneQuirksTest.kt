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
    fun aPlainAndroidPhoneUsesAndroidsOwnSetting() {
        assertNull(PhoneQuirks.of("Google", "google"))
        assertNull(PhoneQuirks.of("motorola"))
    }
}
