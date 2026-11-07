package com.piyush.mode.adb

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Finds listening ports on this phone's loopback address (127.0.0.1): Wireless debugging listens on a random
 * port the system picks from the ephemeral range. Closed ports refuse at once, so a pass takes a second or two.
 */
internal object LoopbackPorts {
    /** Linux's default ephemeral port range, where adbd's TLS port lands. */
    val EPHEMERAL: IntRange = 32768..60999

    fun open(range: IntRange, threads: Int = THREADS): List<Int> {
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val chunks = range.chunked(maxOf(1, range.count() / threads))
            val tasks = chunks.map { ports -> Callable { ports.filter(::listening) } }
            return pool.invokeAll(tasks).flatMap { it.get() }.sorted()
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    private fun listening(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(LOCALHOST, port), CONNECT_MS) }
        true
    }.getOrDefault(false)

    private const val LOCALHOST = "127.0.0.1"
    private const val THREADS = 64
    private const val CONNECT_MS = 200
}
