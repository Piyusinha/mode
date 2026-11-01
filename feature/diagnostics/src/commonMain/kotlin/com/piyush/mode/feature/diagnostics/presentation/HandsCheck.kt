package com.piyush.mode.feature.diagnostics.presentation

import com.piyush.mode.core.common.ActivityTracker
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.AppInfo
import com.piyush.mode.core.platform.BackgroundWork
import com.piyush.mode.feature.connection.domain.label
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.diagnostics.domain.HandsCheckState
import com.piyush.mode.feature.diagnostics.domain.ScreenText
import com.piyush.mode.feature.diagnostics.domain.Shot
import com.piyush.mode.ops.Op
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.TimeSource

/**
 * Proves the hands work from the phone itself: connect, look, open an app,
 * tap some text, type. Everything it does comes from what the user enters;
 * there is no task logic here.
 */
public class HandsCheck(
    private val session: PhoneSession,
    private val background: BackgroundWork,
    private val appInfo: AppInfo,
    private val activity: ActivityTracker,
) {
    private val mutableState = MutableStateFlow(HandsCheckState())
    public val state: StateFlow<HandsCheckState> = mutableState.asStateFlow()

    private val clock = TimeSource.Monotonic
    private var opStarted = clock.markNow()

    public suspend fun connect(): Unit = run("connect") {
        val started = clock.markNow()
        session.reconnect()
        val ms = started.elapsedNow().inWholeMilliseconds
        mutableState.update { it.copy(status = "${session.state.value.label} in $ms ms (closed again after the check)") }
        step("connected in $ms ms")
    }

    /** Looks at the screen as it is now (Mode itself). */
    public suspend fun lookHere(): Unit = run("look") { addShot(look("Mode, now")) }

    public suspend fun home(): Unit = run("home") { send(Op.Home) }

    /**
     * Opens [appName], looks, optionally taps the first text containing
     * [tapText] and types [typeText], looks again, then comes back to Mode.
     */
    public suspend fun probe(appName: String, tapText: String, typeText: String): Unit = run("probe '$appName'") {
        background.run {
            try {
                val bundleId = send(Op.LaunchApp(appName)).jsonPrimitive.content
                step("opened $bundleId")
                delay(2500)
                val first = look(appName)
                addShot(first)
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
                if (tapText.isNotBlank() || typeText.isNotEmpty()) addShot(look("$appName, after"))
            } finally {
                send(Op.LaunchApp(appInfo.bundleId))
            }
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun look(label: String): Shot {
        val started = clock.markNow()
        val capture = send(Op.ScreenCapture(maxSize = 1000)).jsonObject
        val captured = started.elapsedNow().inWholeMilliseconds
        val jpeg = Base64.decode(capture.getValue("jpeg_b64").jsonPrimitive.content)
        val texts = (send(Op.ScreenText()) as JsonArray).map {
            val box = it.jsonObject
            ScreenText(
                box.getValue("text").jsonPrimitive.content,
                box.getValue("x").jsonPrimitive.double,
                box.getValue("y").jsonPrimitive.double,
            )
        }
        val total = started.elapsedNow().inWholeMilliseconds
        step("looked at $label: ${texts.size} pieces of text (picture $captured ms, text ${total - captured} ms, total $total ms)")
        return Shot(label, jpeg, texts)
    }

    private suspend fun send(op: Op) = session.phone().send(op)

    private fun addShot(shot: Shot) {
        mutableState.update { it.copy(shots = listOf(shot) + it.shots) }
    }

    private fun line(text: String) {
        mutableState.update { it.copy(log = (it.log + text).takeLast(MAX_LOG)) }
    }

    private fun step(what: String) {
        line("  ${opStarted.elapsedNow().inWholeMilliseconds} ms  $what")
        AppLog.log("hands", what)
    }

    private suspend fun run(what: String, block: suspend () -> Unit) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true) }
        opStarted = clock.markNow()
        line("▶ $what")
        try {
            activity.track { block() }
            step("done")
        } catch (e: Exception) {
            line("  ✗ ${e.message ?: e.toString()}")
            AppLog.error("hands", what, e)
        } finally {
            // Let go, so iOS drops its "viewing and controlling" icon.
            withContext(NonCancellable) { session.disconnect() }
            mutableState.update { it.copy(busy = false) }
        }
    }

    private companion object {
        const val MAX_LOG = 200
    }
}
