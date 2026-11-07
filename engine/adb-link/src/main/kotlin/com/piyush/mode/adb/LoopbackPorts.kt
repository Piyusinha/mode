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
public object LoopbackPorts {
    /** Linux's default ephemeral port range, where adbd's TLS port lands. */
    public val EPHEMERAL: IntRange = 32768..60999

    public fun open(range: IntRange, threads: Int = THREADS, timeoutMs: Int = CONNECT_MS): List<Int> {
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val chunks = range.chunked(maxOf(1, range.count() / threads))
            val tasks = chunks.map { ports -> Callable { ports.filter { listening(it, timeoutMs) } } }
            return pool.invokeAll(tasks).flatMap { it.get() }.sorted()
        } finally {
            pool.shutdownNow()
            pool.awaitTermination(1, TimeUnit.SECONDS)
        }
    }

    private fun listening(port: Int, timeoutMs: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress(LOCALHOST, port), timeoutMs) }
        true
    }.getOrDefault(false)

    private const val LOCALHOST = "127.0.0.1"
    /** Closed loopback ports refuse at once; many at a time keeps a full pass to a second or two on a phone. */
    private const val THREADS = 256
    /** Some phones drop (not refuse) closed loopback ports, so each try waits this long at most. */
    private const val CONNECT_MS = 25
}
