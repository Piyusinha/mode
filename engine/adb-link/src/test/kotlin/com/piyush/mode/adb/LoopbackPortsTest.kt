package com.piyush.mode.adb

import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LoopbackPortsTest {
    @Test
    fun findsAListeningPort() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val port = server.localPort
            // Only our port is asserted: on a shared CI runner other processes (the Gradle and Kotlin daemons)
            // can be listening nearby, so "nothing else in the range" failed there at random (PR #3, 14 Nov).
            val found = LoopbackPorts.open(port - 40..port + 40, threads = 8, timeoutMs = 300)
            assertTrue(port in found, "expected $port in $found")
        }
    }

    @Test
    fun doesNotReportAClosedPort() {
        val port = ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { it.localPort }
        assertEquals(emptyList(), LoopbackPorts.open(port..port, threads = 1, timeoutMs = 300))
    }
}
