package com.piyush.mode.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.piyush.mode.ops.Op
import com.piyush.mode.ops.PhoneBackend
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.TimeSource

/** One look at the screen: the picture and the text read off it. */
class Shot(val label: String, val image: ImageBitmap, val texts: List<ScreenText>)

class ScreenText(val text: String, val x: Double, val y: Double)

/**
 * Proves the hands work from the phone itself: connect, look, open an app,
 * tap some text, type. Everything it does comes from what the user enters;
 * there is no task logic here. The agent replaces this.
 */
class HandsCheck(private val link: PhoneLink) {
    var status by mutableStateOf("Phone: not connected")
        private set
    var busy by mutableStateOf(false)
        private set
    val log = mutableStateListOf<String>()
    val shots = mutableStateListOf<Shot>()

    private var phone: PhoneBackend? = null
    private val clock = TimeSource.Monotonic

    val connected: Boolean get() = phone != null

    suspend fun connect() = run("connect") {
        val started = clock.markNow()
        val connection = link.connect()
        phone = connection.backend
        status = "Connected via ${connection.route} in ${started.elapsedNow().inWholeMilliseconds} ms"
        log += connection.attempts
    }

    /** Looks at the screen as it is now (Mode itself). */
    suspend fun lookHere() = run("look") { shots.add(0, look("Mode, now")) }

    suspend fun home() = run("home") { send(Op.Home) }

    /**
     * Opens [appName], looks, optionally taps the first text containing
     * [tapText] and types [typeText], looks again, then comes back to Mode.
     */
    suspend fun probe(appName: String, tapText: String, typeText: String) = run("probe '$appName'") {
        link.inBackground {
            try {
                val bundleId = send(Op.LaunchApp(appName)).jsonPrimitive.content
                step("opened $bundleId")
                delay(2500)
                val first = look(appName)
                shots.add(0, first)
                if (tapText.isNotBlank()) {
                    val target = first.texts.firstOrNull { it.text.contains(tapText, ignoreCase = true) }
                    if (target == null) {
                        step("no text containing '$tapText' on screen")
                    } else {
                        send(Op.Tap(target.x, target.y))
                        step("tapped '${target.text}' at (${target.x.toInt()}, ${target.y.toInt()})")
                        delay(1500)
                    }
                }
                if (typeText.isNotEmpty()) {
                    send(Op.TypeText(typeText))
                    step("typed '$typeText'")
                    delay(1500)
                }
                if (tapText.isNotBlank() || typeText.isNotEmpty()) shots.add(0, look("$appName, after"))
            } finally {
                send(Op.LaunchApp(link.ownBundleId))
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun look(label: String): Shot {
        val capture = send(Op.ScreenCapture(maxSize = 1000)).jsonObject
        val jpeg = Base64.decode(capture.getValue("jpeg_b64").jsonPrimitive.content)
        val texts = (send(Op.ScreenText()) as JsonArray).map {
            val box = it.jsonObject
            ScreenText(
                box.getValue("text").jsonPrimitive.content,
                box.getValue("x").jsonPrimitive.double,
                box.getValue("y").jsonPrimitive.double,
            )
        }
        step("looked at $label: ${texts.size} pieces of text")
        return Shot(label, link.decodeImage(jpeg), texts)
    }

    private suspend fun send(op: Op) = (phone ?: error("connect first")).send(op)

    private var opStarted = clock.markNow()

    private fun step(what: String) {
        log += "  ${opStarted.elapsedNow().inWholeMilliseconds} ms  $what"
    }

    private suspend fun run(what: String, block: suspend () -> Unit) {
        if (busy) return
        busy = true
        opStarted = clock.markNow()
        log += "▶ $what"
        try {
            block()
            step("done")
        } catch (e: Exception) {
            log += "  ✗ ${e.message ?: e.toString()}"
            if (what == "connect") status = "Phone: not connected"
        } finally {
            busy = false
        }
    }
}
