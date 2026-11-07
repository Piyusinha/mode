package com.piyush.mode.adb

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PairingWindowTest {
    @Test
    fun readsTheCodeAndPortFromThePairingWindow() {
        val seen = PairingWindow.read(listOf("Pair with device", "Wi‑Fi pairing code", "951744", "IP address & Port", "10.0.2.16:34121", "Cancel"))
        assertEquals(PairingWindow.Seen("951744", 34121), seen)
    }

    @Test
    fun anIpv6AddressAndAnyLanguage() {
        val seen = PairingWindow.read(listOf("डिवाइस के साथ जोड़ें", "482913", "[fe80::7ab4:5c0b:de71:cded]:37125"))
        assertEquals(PairingWindow.Seen("482913", 37125), seen)
    }

    @Test
    fun otherScreensGiveNothing() {
        val seen = PairingWindow.read(listOf("Wireless debugging", "Device name", "sdk_gphone", "Version 1234567", "12:30"))
        assertNull(seen.code)
        assertNull(seen.port)
    }

    @Test
    fun aTypedCodeKeepsOnlyItsDigits() {
        assertEquals("482913", PairingWindow.code(" 482 913 "))
        assertEquals("482913", PairingWindow.code("482-913"))
        assertNull(PairingWindow.code("48291"))
    }
}
