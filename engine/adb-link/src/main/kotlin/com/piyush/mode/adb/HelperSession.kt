package com.piyush.mode.adb

import io.github.muntashirakon.adb.AdbStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.Closeable
import java.io.InputStreamReader

/**
 * The helper running as the shell user (engine:adb-helper), one command line at a time over a kept-open
 * `exec:` stream. Closing it ends the helper, which removes the hidden display.
 */
public class HelperSession private constructor(private val stream: AdbStream) : Closeable {
    private val input = BufferedReader(InputStreamReader(stream.openInputStream()))
    private val output = stream.openOutputStream()
    private val lock = Mutex()

    public val open: Boolean get() = !stream.isClosed

    /** Sends one command and waits for its one-line answer ([HelperAnswer] parses it). */
    public suspend fun send(command: String): String = lock.withLock {
        withContext(Dispatchers.IO) {
            output.write("$command\n".toByteArray())
            output.flush()
            input.readLine() ?: error("the helper stopped")
        }
    }

    override fun close() {
        runCatching { stream.close() }
    }

    public companion object {
        /** Starts the helper from Mode's APK at [apkPath] over [link]; waits for its "ready". */
        public suspend fun start(link: AdbLink, apkPath: String): HelperSession {
            val stream = link.open(HelperCommand.of(apkPath))
            val session = HelperSession(stream)
            val first = withContext(Dispatchers.IO) { session.input.readLine() }
            if (first == null || !first.startsWith(HelperAnswer.READY)) {
                session.close()
                error("the helper didn't start: ${first ?: "no answer"}")
            }
            return session
        }
    }
}
