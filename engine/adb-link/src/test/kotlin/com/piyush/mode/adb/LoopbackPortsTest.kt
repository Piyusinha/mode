package com.piyush.mode.adb

import java.net.InetAddress
import java.net.ServerSocket
import kotlin.test.Test
import kotlin.test.assertEquals

class LoopbackPortsTest {
    @Test
    fun findsAListeningPortAndNothingElseInItsRange() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            val port = server.localPort
            val found = LoopbackPorts.open(port - 40..port + 40, threads = 8, timeoutMs = 300)
            assertEquals(listOf(port), found)
        }
    }
}
