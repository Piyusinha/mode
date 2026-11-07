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
 * enter, scroll (--es dir down|up), checks. Results go to logcat under the tag ModeProbe.
 */
class DevProbe : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val graph = ModeApplication.graph(context)
        val pending = goAsync()
        scope.launch {
            val started = TimeSource.Monotonic.markNow()
            val result = runCatching {
                when (val name = intent.getStringExtra("op") ?: "look") {
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
