package com.piyush.mode.ops

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement

/**
 * A phone connection that heals itself. When an op fails because the
 * connection dropped (iOS paused Mode, the VPN blinked), it reconnects.
 *
 * Looks, Home and opening an app are safe to repeat, so they are retried once.
 * Taps, swipes and typing are not: if the drop came after the phone acted, a
 * retry would act twice (a second tap on "Book" is not harmless). For those it
 * reconnects and reports that the action may not have happened, so the caller
 * looks at the screen before deciding again.
 */
class ResilientBackend(
    private val connect: suspend () -> PhoneBackend,
    private val onReconnect: (String) -> Unit = {},
) : PhoneBackend, AutoCloseable {
    private val lock = Mutex()
    private var current: PhoneBackend? = null
    /** Set while voice work owns the phone. Ops wait on it. It is not a dropped connection. */
    private var held: CompletableDeferred<Unit>? = null

    override val name: String get() = current?.name ?: "phone"

    override suspend fun ops(): Set<String> = backend().ops()

    /**
     * Blocks [send] for the whole of [block]. [block] receives the live connection and may
     * pause and resume its stream. This does not close the connection or open a new one.
     */
    suspend fun <T> hold(block: suspend (PhoneBackend) -> T): T {
        val phone = lock.withLock {
            check(held == null) { "the phone is already held" }
            held = CompletableDeferred()
            backendLocked()
        }
        try {
            return block(phone)
        } finally {
            lock.withLock {
                held?.complete(Unit)
                held = null
            }
        }
    }

    override suspend fun send(op: Op): JsonElement {
        val phone = backend()
        try {
            return phone.send(op)
        } catch (e: Exception) {
            if (!isConnectionLost(e)) throw e
            reconnect(phone, e)
            if (op.isSafeToRepeat()) return backend().send(op)
            throw OpFailed(
                "The connection to the phone dropped and was restored; the ${op.describe()} may not have " +
                    "happened. Look at the screen before trying again.",
                e,
            )
        }
    }

    /** Closes the current connection; the next op connects again. */
    override fun close() {
        (current as? AutoCloseable)?.let { runCatching { it.close() } }
        current = null
    }

    private suspend fun backend(): PhoneBackend {
        while (true) {
            val waiting = lock.withLock { held }
            if (waiting == null) break
            waiting.await()
        }
        return lock.withLock {
            if (held != null) null else backendLocked()
        } ?: backend()
    }

    /** Caller holds [lock]. */
    private suspend fun backendLocked(): PhoneBackend = current ?: connect().also { current = it }

    private suspend fun reconnect(broken: PhoneBackend, cause: Exception) = lock.withLock {
        if (current === broken) {
            (broken as? AutoCloseable)?.let { runCatching { it.close() } }
            current = null
            onReconnect(cause.message ?: cause.toString())
            current = connect()
        }
    }

    companion object {
        private val LOST = listOf(
            "socket io", "broken pipe", "reset by peer", "connection reset", "adapter closed",
            "connection closed", "session is closed", "not connected", "timed out",
        )

        /** True when [e] means the connection is gone rather than the op being wrong. */
        fun isConnectionLost(e: Throwable): Boolean {
            val text = generateSequence(e) { it.cause }.mapNotNull { it.message?.lowercase() }.joinToString(" ")
            return LOST.any { it in text }
        }

        private fun Op.isSafeToRepeat(): Boolean = when (this) {
            is Op.ScreenCapture, is Op.ScreenText, Op.ScreenBounds, Op.SessionState,
            Op.Tree, Op.CurrentApp, Op.Home, is Op.LaunchApp -> true
            else -> false
        }

        private fun Op.describe(): String = when (this) {
            is Op.Tap -> "tap"
            is Op.LongPress -> "long press"
            is Op.Drag -> "drag"
            is Op.Scroll -> "scroll"
            is Op.TypeText -> "typing"
            is Op.PressKey -> "key press"
            is Op.OpenUrl -> "link"
            else -> "action"
        }
    }
}
