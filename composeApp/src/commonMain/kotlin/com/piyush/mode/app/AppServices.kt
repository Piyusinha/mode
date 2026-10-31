package com.piyush.mode.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.ops.PhoneBackend
import com.piyush.mode.ops.ResilientBackend
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Platform services the shared UI needs besides the phone itself. */
interface AppServices {
    /** Secret storage (the Keychain on iOS). */
    fun secret(name: String): String?
    fun setSecret(name: String, value: String?)

    /** Plain settings. */
    fun pref(name: String): String?
    fun setPref(name: String, value: String)

    /** A notification, shown only while Mode is not on screen. */
    fun notify(title: String, body: String)
    fun requestNotifications()

    val http: HttpClient

    /** Debug logs: runs, steps, screenshots, app events. */
    val logs: LogStore
}

/** The user's agent settings. */
class AgentSettings(private val services: AppServices) {
    var apiKey: String?
        get() = services.secret(KEY)
        set(value) = services.setSecret(KEY, value?.trim())

    var model: String by pref("agent_model", AgentConfig.DEFAULT_MODEL)
    var strongModel: String by pref("agent_strong_model", AgentConfig.DEFAULT_STRONG_MODEL)
    var maxSteps: String by pref("agent_max_steps", "30")

    fun config() = AgentConfig(
        model = model.trim().ifBlank { AgentConfig.DEFAULT_MODEL },
        strongModel = strongModel.trim().ifBlank { null },
        maxSteps = maxSteps.trim().toIntOrNull()?.coerceIn(1, 200) ?: 30,
    )

    private fun pref(name: String, default: String) = object : kotlin.properties.ReadWriteProperty<Any?, String> {
        override fun getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>) = services.pref(name) ?: default
        override fun setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, value: String) = services.setPref(name, value)
    }

    companion object {
        private const val KEY = "openrouter_api_key"

        /** OpenRouter models worth comparing on this phone (prices on 2026-10-31, $/M in·out). */
        val CANDIDATES = listOf(
            "anthropic/claude-haiku-5.5" to "0.10 · 0.50",
            "qwen/qwen3.5-flash-02-23" to "0.065 · 0.26",
            "openai/gpt-5-mini" to "0.25 · 2.00",
            "google/gemini-3.5-flash-lite" to "0.30 · 2.50",
            "anthropic/claude-sonnet-5.5" to "2.00 · 10.00",
        )
    }
}

/**
 * The one connection to the phone, shared by every screen: two sessions
 * would each hold a screen stream. Reconnects by itself when it drops.
 */
class PhoneHolder(private val link: PhoneLink, private val log: (String) -> Unit) {
    var status by mutableStateOf("Phone: not connected")
        private set
    var connected by mutableStateOf(false)
        private set

    private val lock = Mutex()
    private var phone: PhoneBackend? = null

    suspend fun phone(): PhoneBackend = lock.withLock {
        phone ?: run {
            val first = connectOnce()
            var pending: PhoneBackend? = first
            ResilientBackend(
                connect = { pending?.also { pending = null } ?: connectOnce() },
                onReconnect = { why ->
                    AppLog.log("phone", "connection dropped ($why); reconnected")
                    log("↻ connection dropped ($why), reconnected")
                },
            ).also { phone = it }
        }
    }

    /**
     * Ends the session: the screen stream stops (so iOS removes its "viewing
     * and controlling" indicator) and Mode's VPN goes off. The next op
     * connects again, which takes about a second.
     */
    suspend fun disconnect() {
        val had = lock.withLock {
            val current = phone
            (current as? AutoCloseable)?.let { runCatching { it.close() } }
            phone = null
            connected = false
            status = "Phone: not connected"
            current != null
        }
        if (had) {
            // The session's goodbye (TCP FIN) travels through the VPN; stopping
            // the VPN at once can drop it, leaving the phone believing the
            // session is still open (and its icon showing). Give it time.
            kotlinx.coroutines.delay(2000)
            runCatching { link.release() }
            AppLog.log("phone", "disconnected (idle)")
        }
    }

    /**
     * The user's "Stop session": closes the connection if there is one and
     * always turns Mode's VPN off, even if the app thinks it is not connected.
     * Unlike iOS's own Stop Session, it does not unpair Mode.
     */
    suspend fun stopSession() {
        AppLog.log("phone", "Stop session pressed")
        lock.withLock {
            (phone as? AutoCloseable)?.let { runCatching { it.close() } }
            phone = null
            connected = false
            status = "Session stopped"
        }
        kotlinx.coroutines.delay(2000)
        runCatching { link.release() }
    }

    /** Closes the session right now, without suspending (Mode is being ended). */
    fun closeNow() {
        (phone as? AutoCloseable)?.let { runCatching { it.close() } }
        phone = null
        AppLog.log("phone", "closed (Mode is ending)")
    }

    /** Drops the current connection and connects again now. */
    suspend fun reconnect(): PhoneBackend {
        lock.withLock {
            (phone as? AutoCloseable)?.close()
            phone = null
            connected = false
        }
        return phone()
    }

    private suspend fun connectOnce(): PhoneBackend {
        status = "Connecting…"
        AppLog.log("phone", "connecting")
        val started = kotlin.time.TimeSource.Monotonic.markNow()
        try {
            val connection = link.connect()
            val ms = started.elapsedNow().inWholeMilliseconds
            status = "Connected via ${connection.route}"
            connected = true
            AppLog.log("phone", "connected via ${connection.route} in $ms ms\n${connection.attempts}")
            log(connection.attempts)
            return connection.backend
        } catch (e: Exception) {
            status = "Phone: not connected"
            connected = false
            AppLog.error("phone", "connect failed after ${started.elapsedNow().inWholeMilliseconds} ms", e)
            throw e
        }
    }
}
