package com.piyush.mode.helper

import android.annotation.SuppressLint
import android.content.AttributionSource
import android.content.Context
import android.content.ContextWrapper
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Build
import android.os.Looper
import android.os.Process
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit
import kotlin.system.exitProcess

/**
 * Background mode's helper, started by Mode as the shell user:
 * `CLASSPATH=<Mode's APK> app_process / com.piyush.mode.helper.Helper`.
 *
 * One command per line on standard input, one answer per line on standard output:
 * - `display <width> <height> <dpi>` → `display <id>`: makes the hidden display (one at a time)
 * - `start <displayId> <package/activity>` → `started`: opens an app on it
 * - `key <displayId> <keycode>` → `pressed`: Back (4) or Home (3) on that display, never the user's screen
 * - `bring <displayId>` → `brought <taskId>`: moves the app on top of that display to the user's screen
 * - `send <taskId> <displayId>` → `sent`: moves it back to the hidden display
 * - `release` → `released`: removes the display and what runs on it
 * - `quit` → `bye`
 * Anything that fails answers `error <why>`. It prints `ready <sdk>` once it can take commands, and ends when
 * Mode closes its input (Mode stopped, or the link dropped), taking the display with it.
 */
public object Helper {
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null

    @JvmStatic
    public fun main(args: Array<String>) {
        Looper.prepareMainLooper()
        val context = runCatching { ShellContext.create() }.getOrElse {
            println("error context: ${it.message}")
            exitProcess(1)
        }
        println("ready ${Build.VERSION.SDK_INT}")
        val input = BufferedReader(InputStreamReader(System.`in`))
        while (true) {
            val line = input.readLine() ?: break
            val parts = line.trim().split(' ').filter { it.isNotEmpty() }
            val answer = runCatching { handle(context, parts) }.getOrElse { "error ${it::class.java.simpleName}: ${it.message}" }
            println(answer)
            if (answer == BYE) break
        }
        release()
        exitProcess(0)
    }

    private fun handle(context: Context, parts: List<String>): String = when (parts.firstOrNull()) {
        "display" -> {
            val (w, h, dpi) = parts.drop(1).map { it.toInt() }
            "display ${makeDisplay(context, w, h, dpi)}"
        }
        "start" -> start(parts[1].toInt(), parts[2])
        "key" -> key(parts[1].toInt(), parts[2].toInt())
        "bring" -> bring(parts[1].toInt())
        "send" -> move(parts[1].toInt(), parts[2].toInt()).let { if (it == null) "sent" else "error $it" }
        "release" -> {
            release()
            "released"
        }
        "quit" -> BYE
        null -> "error empty"
        else -> "error unknown ${parts.first()}"
    }

    @SuppressLint("WrongConstant")
    private fun makeDisplay(context: Context, width: Int, height: Int, dpi: Int): Int {
        release()
        // Frames go nowhere: Mode reads the display through its accessibility service.
        val images = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        images.setOnImageAvailableListener({ r -> r.acquireLatestImage()?.close() }, null)
        val manager = DisplayManager::class.java.getDeclaredConstructor(Context::class.java).apply { isAccessible = true }.newInstance(context)
        val virtual = manager.createVirtualDisplay(NAME, width, height, dpi, images.surface, flags())
            ?: error("no display")
        noKeyboard(virtual.display.displayId)
        reader = images
        display = virtual
        return virtual.display.displayId
    }

    /**
     * No keyboard for the hidden display: without this Android shows its keyboard on the user's screen whenever
     * Mode taps a field there. Mode fills fields through phone control, so no keyboard is needed (scrcpy's
     * --display-ime-policy=hide).
     */
    @SuppressLint("PrivateApi")
    private fun noKeyboard(displayId: Int) {
        runCatching {
            val binder = Class.forName("android.os.ServiceManager").getMethod("getService", String::class.java).invoke(null, "window")
            val wm = Class.forName("android.view.IWindowManager\$Stub").getMethod("asInterface", android.os.IBinder::class.java).invoke(null, binder)
            wm.javaClass.getMethod("setDisplayImePolicy", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).invoke(wm, displayId, IME_HIDE)
        }.onFailure { System.err.println("ime policy: ${it.message}") }
    }

    /** As scrcpy's new display: hidden from the user's screen, its own focus, usable while the phone is locked. */
    private fun flags(): Int {
        var flags = PUBLIC or PRESENTATION or OWN_CONTENT_ONLY or SUPPORTS_TOUCH or ROTATES_WITH_CONTENT or DESTROY_CONTENT_ON_REMOVAL
        flags = flags or TRUSTED or OWN_DISPLAY_GROUP or ALWAYS_UNLOCKED or TOUCH_FEEDBACK_DISABLED
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) flags = flags or OWN_FOCUS or DEVICE_DISPLAY_GROUP
        return flags
    }

    /** Opens [component] on the hidden display, as `am start` would from a computer. */
    private fun start(displayId: Int, component: String): String {
        val process = ProcessBuilder(
            "cmd", "activity", "start-activity", "--display", displayId.toString(), "--windowingMode", FULLSCREEN,
            "-f", NEW_TASK, "-n", component,
        ).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(START_SECONDS, TimeUnit.SECONDS)) return "error start timed out"
        return if (process.exitValue() == 0 && "Error" !in out) "started" else "error ${out.lines().firstOrNull { "Error" in it } ?: out.trim()}"
    }

    /** Moves the top task of [displayId] to the user's screen (a private step only they can do, or take over). */
    private fun bring(displayId: Int): String {
        val list = run("cmd", "activity", "stack", "list") ?: return "error no task list"
        // "RootTask id=87 bounds=[…] displayId=11 …": the first one on that display is its top task.
        val task = list.lineSequence().mapNotNull { ROOT_TASK.find(it) }
            .firstOrNull { it.groupValues[2].toInt() == displayId }?.groupValues?.get(1)?.toInt()
            ?: return "error nothing on display $displayId"
        return move(task, USER_DISPLAY)?.let { "error $it" } ?: "brought $task"
    }

    /** Null when moved, else why not. */
    private fun move(task: Int, displayId: Int): String? {
        val out = run("cmd", "activity", "display", "move-stack", task.toString(), displayId.toString()) ?: return "timed out"
        return if ("Exception" in out || "Error" in out) out.lines().first() else null
    }

    private fun run(vararg command: String): String? {
        val process = ProcessBuilder(*command).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        return if (process.waitFor(START_SECONDS, TimeUnit.SECONDS)) out else null
    }

    /** A key press on one display, as `input -d` does from a computer. */
    private fun key(displayId: Int, code: Int): String {
        val process = ProcessBuilder("input", "-d", displayId.toString(), "keyevent", code.toString()).redirectErrorStream(true).start()
        val out = process.inputStream.bufferedReader().readText()
        if (!process.waitFor(START_SECONDS, TimeUnit.SECONDS)) return "error key timed out"
        return if (process.exitValue() == 0) "pressed" else "error ${out.trim()}"
    }

    private fun release() {
        display?.release()
        display = null
        reader?.close()
        reader = null
    }

    private const val NAME = "Mode"
    /** WindowManager.DISPLAY_IME_POLICY_HIDE. */
    private const val IME_HIDE = 2
    private const val USER_DISPLAY = 0
    private val ROOT_TASK = Regex("""RootTask id=(\d+) .*displayId=(\d+)""")
    private const val BYE = "bye"
    private const val FULLSCREEN = "1"
    private const val NEW_TASK = "0x10000000"
    private const val START_SECONDS = 10L

    // DisplayManager's virtual display flags (some are hidden from apps).
    private const val PUBLIC = 1
    private const val PRESENTATION = 1 shl 1
    private const val OWN_CONTENT_ONLY = 1 shl 3
    private const val SUPPORTS_TOUCH = 1 shl 6
    private const val ROTATES_WITH_CONTENT = 1 shl 7
    private const val DESTROY_CONTENT_ON_REMOVAL = 1 shl 8
    private const val TRUSTED = 1 shl 10
    private const val OWN_DISPLAY_GROUP = 1 shl 11
    private const val ALWAYS_UNLOCKED = 1 shl 12
    private const val TOUCH_FEEDBACK_DISABLED = 1 shl 13
    private const val OWN_FOCUS = 1 shl 14
    private const val DEVICE_DISPLAY_GROUP = 1 shl 15
}

/** A context that is the shell package, so the framework accepts the helper's calls (scrcpy's FakeContext). */
internal class ShellContext private constructor(base: Context) : ContextWrapper(base) {
    override fun getPackageName(): String = SHELL
    override fun getOpPackageName(): String = SHELL
    override fun getAttributionSource(): AttributionSource = AttributionSource.Builder(Process.SHELL_UID).setPackageName(SHELL).build()
    override fun getApplicationContext(): Context = this

    companion object {
        const val SHELL = "com.android.shell"

        /** An ActivityThread for this process and its system context (scrcpy's Workarounds). */
        @SuppressLint("PrivateApi", "DiscouragedPrivateApi")
        fun create(): Context {
            val threadClass = Class.forName("android.app.ActivityThread")
            val thread = threadClass.getDeclaredConstructor().apply { isAccessible = true }.newInstance()
            threadClass.getDeclaredField("sCurrentActivityThread").apply { isAccessible = true }.set(null, thread)
            threadClass.getDeclaredField("mSystemThread").apply { isAccessible = true }.setBoolean(thread, true)
            val system = threadClass.getDeclaredMethod("getSystemContext").invoke(thread) as Context
            return ShellContext(system)
        }
    }
}
