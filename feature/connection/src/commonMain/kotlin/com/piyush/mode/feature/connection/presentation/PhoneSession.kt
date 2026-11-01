package com.piyush.mode.feature.connection.presentation

import com.piyush.mode.core.common.AppLog
import com.piyush.mode.feature.connection.domain.ConnectionState
import com.piyush.mode.feature.connection.domain.PhoneLink
import com.piyush.mode.ops.PhoneBackend
import com.piyush.mode.ops.ResilientBackend
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * The one connection to the phone, shared by every feature: two sessions
 * would each hold a screen stream. Reconnects by itself when it drops.
 *
 * @param releaseDelay how long to wait after closing before releasing the
 *   link: the session's goodbye (TCP FIN) travels through Mode's VPN, and
 *   stopping the VPN at once can drop it, leaving the phone believing the
 *   session is still open (and its "controlling" icon showing).
 */
public class PhoneSession(
    private val link: PhoneLink,
    private val releaseDelay: Duration = 2.seconds,
) {
    private val mutableState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    public val state: StateFlow<ConnectionState> = mutableState.asStateFlow()

    public val isConnected: Boolean get() = state.value is ConnectionState.Connected

    private val lock = Mutex()
    private var current: PhoneBackend? = null

    /** The session, connecting first if needed. */
    public suspend fun phone(): PhoneBackend = lock.withLock {
        current ?: run {
            val first = connectOnce()
            var pending: PhoneBackend? = first
            ResilientBackend(
                connect = { pending?.also { pending = null } ?: connectOnce() },
                onReconnect = { why -> AppLog.log("phone", "connection dropped ($why); reconnected") },
            ).also { current = it }
        }
    }

    /**
     * Ends the session: the screen stream stops (so iOS removes its "viewing
     * and controlling" indicator) and the link is released. The next use
     * connects again, which takes about a second.
     */
    public suspend fun disconnect() {
        val had = lock.withLock {
            val open = current != null
            close()
            mutableState.value = ConnectionState.Disconnected
            open
        }
        if (had) {
            delay(releaseDelay)
            runCatching { link.release() }
            AppLog.log("phone", "disconnected")
        }
    }

    /**
     * The user's "Stop session": closes the session if there is one and always
     * releases the link (Mode's VPN), even if Mode thinks it is not connected.
     * Unlike iOS's own Stop, it does not unpair Mode.
     */
    public suspend fun stopSession() {
        AppLog.log("phone", "Stop session pressed")
        lock.withLock {
            close()
            mutableState.value = ConnectionState.Stopped
        }
        delay(releaseDelay)
        runCatching { link.release() }
    }

    /** Closes the session right now, without suspending (Mode is being ended). */
    public fun closeNow() {
        close()
        AppLog.log("phone", "closed (Mode is ending)")
    }

    /** Drops the current session and connects again now. */
    public suspend fun reconnect(): PhoneBackend {
        lock.withLock {
            close()
            mutableState.value = ConnectionState.Disconnected
        }
        return phone()
    }

    private fun close() {
        (current as? AutoCloseable)?.let { runCatching { it.close() } }
        current = null
    }

    private suspend fun connectOnce(): PhoneBackend {
        mutableState.value = ConnectionState.Connecting
        AppLog.log("phone", "connecting")
        val started = TimeSource.Monotonic.markNow()
        try {
            val connection = link.connect()
            val ms = started.elapsedNow().inWholeMilliseconds
            mutableState.value = ConnectionState.Connected(connection.route)
            AppLog.log("phone", "connected via ${connection.route} in $ms ms\n${connection.attempts}")
            return connection.backend
        } catch (e: Exception) {
            mutableState.value = ConnectionState.Failed(e.message ?: e.toString())
            AppLog.error("phone", "connect failed after ${started.elapsedNow().inWholeMilliseconds} ms", e)
            throw e
        }
    }
}
