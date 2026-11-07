package com.piyush.mode.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.piyush.mode.ops.Op
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlin.time.TimeSource

/**
 * Debug builds only: drives the phone engine from adb, before there are screens to do it from.
 *
 *     adb shell am broadcast -n com.piyush.mode/com.piyush.mode.app.DevProbe --es op look
 *     adb shell am broadcast -n com.piyush.mode/com.piyush.mode.app.DevProbe --es op tap --ef x 180 --ef y 400
 *
 * Ops: look, text, tap, back, home, recents, notifications, quick, launch (--es name …), type (--es s …),
 * enter, scroll (--es dir down|up), checks, hold (--el ms …). Results go to logcat under the tag ModeProbe.
 */
class DevProbe : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = ModeApplication.graph(context)
        val pending = goAsync()
        scope.launch {
            val started = TimeSource.Monotonic.markNow()
            val result = runCatching {
                when (val name = intent.getStringExtra("op") ?: "look") {
                    "hold" -> {
                        // The working service and wake locks, held for a few seconds, as a task holds them.
                        com.piyush.mode.core.platform.AndroidBackgroundWork(context).run { kotlinx.coroutines.delay(intent.getLongExtra("ms", 8000)) }
                        "held"
                    }
                    // A question notification, as a task would post it (kind: YesNo, YesNoAlways, Choice, Text, YourTurn).
                    "ask" -> {
                        val kind = com.piyush.mode.core.platform.QuestionKind.valueOf(intent.getStringExtra("kind") ?: "YesNo")
                        val options = intent.getStringExtra("options")?.split("|").orEmpty()
                        graph.notifier.ask("probe", intent.getStringExtra("text") ?: "Pay ₹349 to Swiggy?", kind, options)
                        "asked"
                    }
                    // Walks setup past Connect your AI on a test phone: a key from the probe, or a stand-in.
                    "key" -> {
                        graph.settings.apiKey = intent.getStringExtra("value") ?: "sk-or-probe"
                        "key set"
                    }
                    "checks" -> buildString {
                        graph.phoneChecks.run { rows -> clear(); rows.forEach { append("${it.id}=${it.state} ") } }
                    }
                    else -> {
                        val phone = graph.session.phone()
                        when (name) {
                            "look" -> {
                                val shot = phone.send(Op.ScreenCapture(maxSize = 1000)).jsonObject
                                val text = phone.send(Op.ScreenText()) as JsonArray
                                "bounds=${shot["bounds"]} img=${shot["img_px"]} private=${shot["private"]} jpeg=${shot["jpeg_b64"]?.toString()?.length} texts=${text.size}: " +
                                    text.take(25).joinToString(" | ") { it.jsonObject["text"].toString() + "@" + it.jsonObject["x"] + "," + it.jsonObject["y"] + "/" + it.jsonObject["window"] }
                            }
                            "text" -> (phone.send(Op.ScreenText()) as JsonArray).joinToString("\n")
                            "tap" -> phone.send(Op.Tap(intent.getFloatExtra("x", 0f).toDouble(), intent.getFloatExtra("y", 0f).toDouble())).toString()
                            "back" -> phone.send(Op.Back).toString()
                            "home" -> phone.send(Op.Home).toString()
                            "recents" -> phone.send(Op.Recents).toString()
                            "notifications" -> phone.send(Op.OpenNotifications).toString()
                            "quick" -> phone.send(Op.OpenQuickSettings).toString()
                            "launch" -> phone.send(Op.LaunchApp(intent.getStringExtra("name").orEmpty(), fresh = intent.getBooleanExtra("fresh", false))).toString()
                            "type" -> phone.send(Op.TypeText(intent.getStringExtra("s").orEmpty())).toString()
                            "enter" -> phone.send(Op.PressKey("return")).toString()
                            "scroll" -> phone.send(Op.Scroll(180.0, 400.0, dy = if (intent.getStringExtra("dir") == "up") 300.0 else -300.0)).toString()
                            "current" -> phone.send(Op.CurrentApp).toString()
                            else -> "unknown op $name"
                        }
                    }
                }
            }
            val ms = started.elapsedNow().inWholeMilliseconds
            result.fold({ Log.i(TAG, "ok ${ms}ms $it") }, { Log.w(TAG, "failed ${ms}ms ${it::class.simpleName}: ${it.message}") })
            pending.finish()
        }
    }

    private companion object {
        const val TAG = "ModeProbe"
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    }
}
