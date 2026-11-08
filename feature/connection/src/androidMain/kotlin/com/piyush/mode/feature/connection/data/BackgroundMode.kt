package com.piyush.mode.feature.connection.data

import android.content.Context
import android.provider.Settings
import com.piyush.mode.adb.AdbLink
import com.piyush.mode.adb.HelperAnswer
import com.piyush.mode.adb.HelperCommand
import com.piyush.mode.adb.HelperSession
import com.piyush.mode.android.GlobalAction
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.storage.KeyValueStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Background mode (boards B01–B06, R01–R04): Mode links to its own phone's Wireless debugging, starts the helper
 * as the shell user and works on a hidden display, so the user keeps their phone. Set up once with a pairing
 * code; after that Mode connects by itself, turning Wireless debugging back on after a restart when it may.
 */
public class BackgroundMode(context: Context, private val prefs: KeyValueStore) {
    private val app = context.applicationContext
    public val link: AdbLink = AdbLink(app)
    private val lock = Mutex()
    private var helper: HelperSession? = null
    private var display: Int? = null

    /** The user set it up and hasn't turned it off. */
    public var enabled: Boolean
        get() = prefs.getString(ENABLED) == "true" && link.hasKey
        set(value) {
            prefs.putString(ENABLED, value.toString())
            on.value = enabled
        }

    private val on = kotlinx.coroutines.flow.MutableStateFlow(prefs.getString(ENABLED) == "true" && link.hasKey)

    /** [enabled], as it changes. */
    public val enabledFlow: kotlinx.coroutines.flow.StateFlow<Boolean> = on

    private var lastPort: Int?
        get() = prefs.getString(PORT)?.toIntOrNull()
        set(value) = prefs.putString(PORT, value?.toString().orEmpty())

    /** The hidden display for a task: linked, helper running, display made. Throws with why when it can't. */
    public suspend fun display(): Int = lock.withLock {
        val ready = helper?.takeIf { it.open }
        display?.let { if (ready != null) return it }
        if (!link.connected) {
            // Just switched on, Wireless debugging needs a moment before it listens.
            val tries = if (wirelessDebuggingOn()) STARTING_TRIES else 1
            var connected = false
            for (i in 1..tries) {
                if (i > 1 || tries > 1) kotlinx.coroutines.delay(STARTING_WAIT_MS)
                connected = link.connect(lastPort)
                if (connected) break
            }
            check(connected) { "Wireless debugging is off or not reachable (${link.lastError ?: "no port answered"})" }
        }
        // The port, for next time (also when setup connected): finding it again means searching for it.
        link.port?.let { if (it != lastPort) lastPort = it }
        helper?.close()
        val session = HelperSession.start(link, app.applicationInfo.sourceDir)
        helper = session
        val m = app.resources.displayMetrics
        val answer = session.send(HelperCommand.display(m.widthPixels, m.heightPixels, m.densityDpi))
        val id = HelperAnswer.displayId(answer) ?: error("the helper made no display: $answer")
        display = id
        AppLog.log("background", "hidden display $id ready")
        id
    }

    /** Back or Home pressed on the hidden display. */
    public suspend fun press(displayId: Int, action: GlobalAction): Boolean {
        val code = when (action) {
            GlobalAction.Back -> HelperCommand.KEY_BACK
            GlobalAction.Home -> HelperCommand.KEY_HOME
            else -> return false
        }
        val session = helper ?: return false
        return HelperAnswer.ok(session.send(HelperCommand.key(displayId, code)))
    }

    /** The app Mode was using, moved to the user's screen (their turn), so it can go back after. */
    private var withUser: Int? = null

    /** Moves the app on the hidden display to the user's screen: a step only they can do, or they take over. */
    public suspend fun bringToUser(): Boolean {
        val id = display ?: return false
        val session = helper ?: return false
        val task = HelperAnswer.broughtTask(session.send(HelperCommand.bring(id))) ?: return false
        withUser = task
        AppLog.log("background", "brought the app to the user's screen")
        return true
    }

    /** Sends that app back to the hidden display, and Mode carries on there. */
    public suspend fun giveBack(): Boolean {
        val task = withUser ?: return false
        val id = display ?: return false
        val session = helper ?: return false
        withUser = null
        val ok = HelperAnswer.ok(session.send(HelperCommand.send(task, id)))
        AppLog.log("background", if (ok) "the app went back to the hidden screen" else "the app stayed on the user's screen")
        return ok
    }

    /**
     * The user opened, on their own screen, the app Mode was using: Android moved its task there (an app has one
     * window). Remembers that task so [giveBack] returns it to the hidden display.
     */
    public suspend fun userOpenedIt(): Boolean {
        if (withUser != null) return true
        val session = helper ?: return false
        val task = HelperAnswer.topTask(session.send(HelperCommand.top(USER_DISPLAY))) ?: return false
        withUser = task
        AppLog.log("background", "the user opened the app Mode was using")
        return true
    }

    /** The app Mode was using is on the user's screen (their turn, they took over, or they opened it). */
    public val withTheUser: Boolean get() = withUser != null

    /** The hidden display, while there is one. */
    public val hiddenDisplay: Int? get() = display

    /** A task is on the hidden display right now. */
    public val working: Boolean get() = display != null && helper?.open == true

    /** Ends the helper (its display goes with it). The link stays for next time. */
    public suspend fun stop(): Unit = lock.withLock {
        withUser = null
        helper?.close()
        helper = null
        display = null
    }

    /** Turns background mode off and forgets the pairing on Mode's side. */
    public suspend fun turnOff() {
        stop()
        link.forget()
        enabled = false
        on.value = false
        lastPort = null
    }

    /**
     * Wireless debugging turns itself off on a restart. Setup granted Mode the right to write secure settings, so
     * it switches it back on (Android then only needs the Wi-Fi it was allowed on).
     */
    /** True when it was off and Mode switched it on. */
    private fun wirelessDebuggingOn(): Boolean = runCatching {
        if (Settings.Global.getInt(app.contentResolver, ADB_WIFI, 0) != 0) return@runCatching false
        Settings.Global.putInt(app.contentResolver, ADB_WIFI, 1)
        AppLog.log("background", "turned Wireless debugging back on")
        true
    }.getOrDefault(false)

    public companion object {
        private const val ENABLED = "background.enabled"
        private const val USER_DISPLAY = 0
        private const val PORT = "background.port"
        private const val ADB_WIFI = "adb_wifi_enabled"
        private const val STARTING_TRIES = 3
        private const val STARTING_WAIT_MS = 1_500L
    }
}
