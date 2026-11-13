package com.piyush.mode.core.network

import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import java.net.HttpURLConnection
import java.net.ServerSocket
import java.net.URL
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LoopbackCallbackTest {
    private fun freePort() = ServerSocket(0).use { it.localPort }

    private fun get(url: String): Pair<Int, String> {
        val c = URL(url).openConnection() as HttpURLConnection
        return c.responseCode to c.inputStream.bufferedReader().readText()
    }

    @Test
    fun answersTheSignInAndHandsBackItsUrl() = runBlocking {
        val port = freePort()
        val callback = LoopbackCallback(port)
        val waiting = async(kotlinx.coroutines.Dispatchers.IO) { callback.await() }
        val (code, page) = get("http://127.0.0.1:$port/favicon.ico")
        assertEquals(200, code)
        assertTrue("Signed in" in page)
        get("http://127.0.0.1:$port/auth/callback?code=abc&state=xyz")
        assertEquals("http://127.0.0.1:$port/auth/callback?code=abc&state=xyz", waiting.await())
    }

    @Test
    fun closingStopsTheWait() = runBlocking {
        val callback = LoopbackCallback(freePort())
        val waiting = async(kotlinx.coroutines.Dispatchers.IO) { callback.await() }
        Thread.sleep(100)
        callback.close()
        assertNull(waiting.await())
    }
}
