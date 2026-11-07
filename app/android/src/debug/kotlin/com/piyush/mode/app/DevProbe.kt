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
 *     adb shell am broadcast -n app.heymode.mode/com.piyush.mode.app.DevProbe --es op look
 *     adb shell am broadcast -n app.heymode.mode/com.piyush.mode.app.DevProbe --es op tap --ef x 180 --ef y 400
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
                    // Asks the launcher to add Mode's widget to the home screen.
                    "widget" -> {
                        val manager = context.getSystemService(android.appwidget.AppWidgetManager::class.java)
                        val asked = manager.requestPinAppWidget(android.content.ComponentName(context, com.piyush.mode.app.widget.ModeWidget::class.java), null, null)
                        "pin asked: $asked"
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
                        graph.settings.selectProvider(com.piyush.mode.llm.ModelProvider.OpenRouter)
                        "key set"
                    }
                    // Looks at (and with x/y taps) another display, as background mode does: --ei display 2
                    "on" -> {
                        val service = com.piyush.mode.android.ServiceRegistry.service.value ?: error("phone control is off")
                        val d = intent.getIntExtra("display", 0)
                        val backend = com.piyush.mode.android.AccessibilityBackend(service, com.piyush.mode.android.AndroidImageTools(service::bitmapFor), displayId = d)
                        if (intent.hasExtra("x")) backend.send(Op.Tap(intent.getFloatExtra("x", 0f).toDouble(), intent.getFloatExtra("y", 0f).toDouble()))
                        intent.getStringExtra("launch")?.let { backend.send(Op.LaunchApp(it)); kotlinx.coroutines.delay(2000) }
                        val shot = backend.send(Op.ScreenCapture(maxSize = 1000)).jsonObject
                        val text = backend.send(Op.ScreenText()) as JsonArray
                        "display=$d bounds=${shot["bounds"]} private=${shot["private"]} jpeg=${shot["jpeg_b64"]?.toString()?.length} texts=${text.size}: " +
                            text.take(12).joinToString(" | ") { it.jsonObject["text"].toString() + "@" + it.jsonObject["x"] + "," + it.jsonObject["y"] }
                    }
                    // Background mode's link: --es code 123456 pairs (Wireless debugging's pairing window open),
                    // then connects and runs `id` as the shell user.
                    "adb" -> {
                        val link = adbLink ?: com.piyush.mode.adb.AdbLink(context).also { adbLink = it }
                        val code = intent.getStringExtra("code")
                        val paired = if (code != null) {
                            val port = intent.getIntExtra("port", 0)
                            "paired=${link.pair(code, port)} on $port ${link.lastError ?: ""}; "
                        } else ""
                        val connected = link.connect()
                        paired + "connected=$connected port=${link.port} " + (if (connected) link.run("id").trim() else "")
                    }
                    // Starts the helper over the link, makes a hidden display and opens Settings there.
                    "helper" -> {
                        val link = adbLink ?: com.piyush.mode.adb.AdbLink(context).also { adbLink = it }
                        check(link.connect()) { "not connected" }
                        helper?.close()
                        val session = com.piyush.mode.adb.HelperSession.start(link, context.applicationInfo.sourceDir).also { helper = it }
                        val m = context.resources.displayMetrics
                        val display = session.send(com.piyush.mode.adb.HelperCommand.display(m.widthPixels, m.heightPixels, m.densityDpi))
                        val id = com.piyush.mode.adb.HelperAnswer.displayId(display) ?: error(display)
                        "display=$id " + session.send(com.piyush.mode.adb.HelperCommand.start(id, intent.getStringExtra("component") ?: "com.android.settings/.Settings"))
                    }
                    "tls" -> runCatching {
                        Class.forName("org.conscrypt.OpenSSLProvider").getDeclaredConstructor().newInstance().toString()
                    }.fold({ "bundled conscrypt ok: $it" }, { e -> "bundled conscrypt failed: ${e::class.java.name}: ${e.message} / ${e.cause?.let { c -> c::class.java.name + ": " + c.message }}" })
                    // Turns background mode on or off (after pairing with op=adb), then the next look shows the route.
                    "background" -> {
                        val mode = com.piyush.mode.app.graph.background ?: error("no background mode in this build")
                        if (intent.getBooleanExtra("forget", false)) mode.turnOff()
                        else {
                            mode.enabled = intent.getBooleanExtra("on", true)
                            if (!mode.enabled) mode.stop()
                        }
                        "background=${mode.enabled}"
                    }
                    "route" -> graph.session.phone().let { "state=${graph.session.state.value}" }
                    "helperstop" -> {
                        helper?.close()
                        helper = null
                        "stopped"
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
        var adbLink: com.piyush.mode.adb.AdbLink? = null
        var helper: com.piyush.mode.adb.HelperSession? = null
    }
}
