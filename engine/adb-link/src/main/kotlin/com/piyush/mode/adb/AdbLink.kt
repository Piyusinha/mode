package com.piyush.mode.adb

import android.content.Context
import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.PrivateKey
import java.security.cert.Certificate

/**
 * Mode's link to its own phone's Wireless debugging: find the pairing port, pair with the code once, then
 * connect by itself and run commands as the shell user. All calls block on the network, so they run on IO.
 */
public class AdbLink(context: Context) {
    private val app = context.applicationContext
    private val keys = AdbKeys(File(app.filesDir, DIR))
    private val lock = Mutex()
    private var manager: Manager? = null

    /** Mode has a debugging key (it paired before, unless the phone forgot it). */
    public val hasKey: Boolean get() = keys.exists

    public val connected: Boolean get() = manager?.isConnected == true

    /** Pairs with the six-digit [code] shown in that window. False when the code was wrong or the window closed. */
    public suspend fun pair(code: String, port: Int): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching { manager().pair(LOCALHOST, port, code) }
                .onFailure { lastError = "${it::class.java.simpleName}: ${it.message}" }
                .getOrDefault(false)
        }
    }

    /**
     * Connects to Wireless debugging on this phone (it must be on): the port that worked last, else the one
     * found on the phone's own loopback address. Never mDNS: on Android 17 that brings up a picker, which
     * closes Settings' pairing window. True once Mode can run commands.
     */
    public suspend fun connect(lastPort: Int? = null): Boolean = lock.withLock {
        withContext(Dispatchers.IO) {
            val m = manager()
            if (m.isConnected) return@withContext true
            val candidates = listOfNotNull(lastPort) + LoopbackPorts.open(LoopbackPorts.EPHEMERAL).filter { it != lastPort }
            candidates.any { port -> runCatching { m.connect(LOCALHOST, port) }.getOrDefault(false).also { if (it) this@AdbLink.port = port } }
        }
    }

    /** Why the last pairing failed, for the log (never shown to the user). */
    public var lastError: String? = null
        private set

    /** The port the last connection used, for next time. */
    public var port: Int? = null
        private set

    /** Runs [command] as the shell user and returns what it printed. */
    public suspend fun run(command: String): String = withContext(Dispatchers.IO) {
        val stream = lock.withLock { manager().openStream("$EXEC$command") }
        stream.use { s -> s.openInputStream().bufferedReader().readText() }
    }

    /** Opens a long-lived command (the helper): its input and output stay open until [AdbStream.close]. */
    public suspend fun open(command: String): AdbStream = withContext(Dispatchers.IO) {
        lock.withLock { manager().openStream("$EXEC$command") }
    }

    /** Disconnects and forgets Mode's key: the next time needs a new pairing code. */
    public suspend fun forget(): Unit = lock.withLock {
        withContext(Dispatchers.IO) {
            runCatching { manager?.close() }
            manager = null
            keys.forget()
        }
    }

    public suspend fun disconnect(): Unit = lock.withLock {
        withContext(Dispatchers.IO) { runCatching { manager?.disconnect() } }
    }

    private fun manager(): Manager = manager ?: Manager(keys.load()).also { manager = it }

    private class Manager(key: Pair<PrivateKey, Certificate>) : AbsAdbConnectionManager() {
        private val privateKey = key.first
        private val certificate = key.second

        init {
            setApi(Build.VERSION.SDK_INT)
            setHostAddress(LOCALHOST)
        }

        override fun getPrivateKey(): PrivateKey = privateKey
        override fun getCertificate(): Certificate = certificate
        override fun getDeviceName(): String = NAME
    }

    private companion object {
        const val DIR = "adb"
        const val NAME = "Mode"
        const val LOCALHOST = "127.0.0.1"
        /** No PTY, so nothing echoes back and binary-safe output. */
        const val EXEC = "exec:"
    }
}
