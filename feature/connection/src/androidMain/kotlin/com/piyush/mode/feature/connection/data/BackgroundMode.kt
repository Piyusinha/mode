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
        set(value) = prefs.putString(ENABLED, value.toString())

    private var lastPort: Int?
        get() = prefs.getString(PORT)?.toIntOrNull()
        set(value) = prefs.putString(PORT, value?.toString().orEmpty())

    /** The hidden display for a task: linked, helper running, display made. Throws with why when it can't. */
    public suspend fun display(): Int = lock.withLock {
        val ready = helper?.takeIf { it.open }
        display?.let { if (ready != null) return it }
        if (!link.connected) {
            wirelessDebuggingOn()
            check(link.connect(lastPort)) { "Wireless debugging is off or not reachable" }
            lastPort = link.port
        }
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

    /** Ends the helper (its display goes with it). The link stays for next time. */
    public suspend fun stop(): Unit = lock.withLock {
        helper?.close()
        helper = null
        display = null
    }

    /** Turns background mode off and forgets the pairing on Mode's side. */
    public suspend fun turnOff() {
        stop()
        link.forget()
        enabled = false
        lastPort = null
    }

    /**
     * Wireless debugging turns itself off on a restart. Setup granted Mode the right to write secure settings, so
     * it switches it back on (Android then only needs the Wi-Fi it was allowed on).
     */
    private fun wirelessDebuggingOn() {
        runCatching {
            if (Settings.Global.getInt(app.contentResolver, ADB_WIFI, 0) == 0) {
                Settings.Global.putInt(app.contentResolver, ADB_WIFI, 1)
                AppLog.log("background", "turned Wireless debugging back on")
            }
        }
    }

    public companion object {
        private const val ENABLED = "background.enabled"
        private const val PORT = "background.port"
        private const val ADB_WIFI = "adb_wifi_enabled"
    }
}
