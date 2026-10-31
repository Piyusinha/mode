package com.piyush.mode.ops

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
) : PhoneBackend {
    private val lock = Mutex()
    private var current: PhoneBackend? = null

    override val name: String get() = current?.name ?: "phone"

    override suspend fun ops(): Set<String> = backend().ops()

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

    private suspend fun backend(): PhoneBackend = lock.withLock { current ?: connect().also { current = it } }

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
