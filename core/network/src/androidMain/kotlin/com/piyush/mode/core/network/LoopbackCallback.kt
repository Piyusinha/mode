package com.piyush.mode.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketException

/**
 * Listens on 127.0.0.1 for the one request a sign-in page sends back when it finishes ("Continue with
 * ChatGPT": OpenAI's sign-in only allows an HTTP loopback redirect). Answers with a short page and
 * hands back the full URL; requests without a code or an error (a favicon) are answered and ignored.
 */
public class LoopbackCallback(private val port: Int) : Closeable {
    private val socket = ServerSocket(port, BACKLOG, InetAddress.getByName(HOST)).apply { reuseAddress = true }

    /** Waits for the sign-in to come back; null when [close] was called first. */
    public suspend fun await(): String? = withContext(Dispatchers.IO) {
        try {
            while (true) {
                socket.accept().use { client ->
                    val request = client.getInputStream().bufferedReader().readLine().orEmpty()
                    // "GET /auth/callback?code=…&state=… HTTP/1.1"
                    val target = request.split(' ').getOrNull(1)
                    val page = "<html><body style=\"font-family:sans-serif;text-align:center;padding-top:30vh\">" +
                        "<h2>Signed in</h2><p>Return to Mode.</p></body></html>"
                    val bytes = page.toByteArray()
                    client.getOutputStream().apply {
                        write("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray())
                        write(bytes)
                        flush()
                    }
                    if (target != null && ("code=" in target || "error=" in target)) return@withContext "http://$HOST:$port$target"
                }
            }
            @Suppress("UNREACHABLE_CODE") null
        } catch (e: SocketException) {
            null
        } finally {
            close()
        }
    }

    override fun close() {
        runCatching { socket.close() }
    }

    private companion object {
        const val HOST = "127.0.0.1"
        const val BACKLOG = 4
    }
}
