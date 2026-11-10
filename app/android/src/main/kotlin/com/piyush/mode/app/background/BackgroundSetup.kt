package com.piyush.mode.app.background

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.provider.Settings
import com.piyush.mode.adb.PairingWindow
import com.piyush.mode.android.NodeSnapshot
import com.piyush.mode.android.ServiceRegistry
import com.piyush.mode.app.MainActivity
import com.piyush.mode.app.ModeApplication
import com.piyush.mode.app.R
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.feature.connection.data.BackgroundMode
import com.piyush.mode.feature.connection.domain.BackgroundBlock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Where background mode's setup is (boards B01–B06; the blocked pages are D06–D08). */
enum class BackgroundStep {
    Intro, DevOptions, Wireless, Pair, Linking, Failed, Linked,
    /** This phone runs Android 13: no hidden display here. */
    BlockedVersion,
    /** No Wi-Fi: the link needs one (Mode carries on by itself when one connects). */
    BlockedWifi,
    /** A work or school policy turned Developer options off. */
    BlockedPolicy,
    /** Paired, but Mode didn't get the right to turn Wireless debugging back on. */
    NoGrant,
}

/**
 * Background mode's setup: Developer options, Wireless debugging, then pairing. While the pairing window is
 * open, Mode reads its code and port through phone control and pairs by itself; the notification (board B04)
 * takes a typed code when it can't. Then Mode connects, gives itself the right to turn Wireless debugging back on
 * after a restart, and starts the helper once to be sure.
 */
class BackgroundSetup(context: Context, private val mode: BackgroundMode) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val state = MutableStateFlow(if (mode.enabled) BackgroundStep.Linked else BackgroundStep.Intro)
    val step: StateFlow<BackgroundStep> = state.asStateFlow()
    private var watching: Job? = null
    /** The pairing port last seen on screen, for a code typed in the notification. */
    private var seenPort: Int? = null

    val enabled: Boolean get() = mode.enabled

    /** Background mode can never work on this phone (Android 13): the hub says so instead of "Set up". */
    val neverOnThisPhone: Boolean get() = mode.block() == BackgroundBlock.AndroidTooOld

    private var wifiWatch: ConnectivityManager.NetworkCallback? = null

    private val developerOptions get() = Settings.Global.getInt(app.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    private val wirelessDebugging get() = Settings.Global.getInt(app.contentResolver, ADB_WIFI, 0) == 1

    /** Set it up: what blocks it, else the first step that isn't done yet. */
    fun begin() {
        when (mode.block()) {
            BackgroundBlock.AndroidTooOld -> return blocked(BackgroundStep.BlockedVersion)
            BackgroundBlock.DebuggingBlocked -> return blocked(BackgroundStep.BlockedPolicy)
            BackgroundBlock.NoWifi -> return blocked(BackgroundStep.BlockedWifi)
            null -> stopWifiWatch()
        }
        state.value = when {
            !developerOptions -> BackgroundStep.DevOptions
            !wirelessDebugging -> BackgroundStep.Wireless
            else -> BackgroundStep.Pair
        }
        if (state.value == BackgroundStep.Pair) watchForPairing()
    }

    /** Mode came back to the front: move on past a step that's now done. */
    fun refresh() {
        when (state.value) {
            BackgroundStep.DevOptions -> if (developerOptions) begin()
            BackgroundStep.Wireless -> if (wirelessDebugging) begin()
            // Back on the page still waiting for Wi-Fi: watch for it again (the watch stopped when the page went).
            BackgroundStep.BlockedWifi -> if (mode.block() == null) begin() else watchWifi()
            BackgroundStep.BlockedPolicy -> if (mode.block() == null) begin()
            else -> Unit
        }
    }

    /** The page went (Back, another tab): nothing keeps watching the network for a page nobody sees. */
    fun left() {
        stopWifiWatch()
    }

    private fun blocked(step: BackgroundStep) {
        AppLog.log("background", "setup blocked: $step")
        state.value = step
        if (step == BackgroundStep.BlockedWifi) watchWifi()
    }

    /** While waiting for Wi-Fi, carries on by itself the moment one connects (board D07). */
    private fun watchWifi() {
        if (wifiWatch != null) return
        val manager = app.getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                scope.launch { if (state.value == BackgroundStep.BlockedWifi && mode.wifiConnected()) begin() }
            }
        }
        wifiWatch = callback
        runCatching { manager.registerDefaultNetworkCallback(callback) }
    }

    private fun stopWifiWatch() {
        val callback = wifiWatch ?: return
        wifiWatch = null
        runCatching { app.getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(callback) }
    }

    /** "They're on already" on Developer options. */
    fun skipDevOptions() {
        state.value = if (wirelessDebugging) BackgroundStep.Pair else BackgroundStep.Wireless
        if (state.value == BackgroundStep.Pair) watchForPairing()
    }

    fun tryAgain() {
        state.value = BackgroundStep.Pair
        watchForPairing()
    }

    fun reset() {
        stopWatching()
        stopWifiWatch()
        state.value = if (mode.enabled) BackgroundStep.Linked else BackgroundStep.Intro
    }

    suspend fun turnOff() {
        stopWatching()
        mode.turnOff()
        state.value = BackgroundStep.Intro
    }

    /**
     * Watches the screen for Wireless debugging's pairing window while the user opens it (and asks for the code
     * in a notification, in case it can't be read). Also moves Wireless → Pair when the user switches it on.
     */
    fun watchForPairing() {
        if (watching?.isActive == true) return
        postCodeNotification()
        watching = scope.launch {
            while (isActive && state.value in WATCHED) {
                if (state.value == BackgroundStep.Wireless && wirelessDebugging) state.value = BackgroundStep.Pair
                val seen = PairingWindow.read(screenTexts())
                if (seen.port != null) seenPort = seen.port
                val code = seen.code
                val port = seen.port
                if (state.value == BackgroundStep.Pair && code != null && port != null) {
                    AppLog.log("background", "read the pairing window")
                    // Pairing runs on its own: it ends this watcher, which mustn't cancel it.
                    scope.launch { pair(code, port) }
                    return@launch
                }
                delay(WATCH_MS)
            }
        }
    }

    /** A code typed in the notification. */
    fun codeTyped(typed: String) {
        val code = PairingWindow.code(typed) ?: return fail()
        val port = seenPort ?: PairingWindow.read(screenTexts()).port ?: return fail()
        scope.launch { pair(code, port) }
    }

    private suspend fun pair(code: String, port: Int) {
        stopWatching()
        state.value = BackgroundStep.Linking
        val ok = mode.link.pair(code, port) && mode.link.connect()
        if (!ok) {
            AppLog.log("background", "pairing failed: ${mode.link.lastError ?: "not connected"}")
            return fail()
        }
        // The right to switch Wireless debugging back on after a restart, so Mode reconnects by itself.
        val granted = runCatching { mode.link.run("pm grant ${app.packageName} $WRITE_SECURE_SETTINGS") }
        if (!mode.holdsSecureSettings()) {
            AppLog.log("background", "the grant didn't stick: ${granted.getOrNull()?.trim() ?: granted.exceptionOrNull()?.message}")
            state.value = BackgroundStep.NoGrant
            bringModeBack()
            return
        }
        mode.enabled = true
        val helper = runCatching { mode.display() }
        mode.stop()
        if (helper.isFailure) {
            AppLog.log("background", "the helper didn't start: ${helper.exceptionOrNull()?.message}")
            mode.enabled = false
            return fail()
        }
        AppLog.log("background", "background mode is on")
        state.value = BackgroundStep.Linked
        bringModeBack()
    }

    private fun fail() {
        stopWatching()
        state.value = BackgroundStep.Failed
        bringModeBack()
    }

    private fun stopWatching() {
        watching?.cancel()
        watching = null
        app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
    }

    /** Every text on the user's screen, read by Mode's phone control. */
    private fun screenTexts(): List<String> {
        val service = ServiceRegistry.service.value ?: return emptyList()
        val out = mutableListOf<String>()
        fun walk(n: NodeSnapshot) {
            n.text?.let(out::add)
            n.children.forEach(::walk)
        }
        runCatching { service.windows(0).forEach { w -> w.root?.let(::walk) } }
        return out
    }

    private fun bringModeBack() {
        runCatching { app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
    }

    /** Board B04: "Type the 6-digit code here", answered without leaving the pairing window. */
    private fun postCodeNotification() {
        if (app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, app.getString(R.string.bg_channel), NotificationManager.IMPORTANCE_HIGH))
        val reply = PendingIntent.getBroadcast(
            app, NOTIFICATION_ID, Intent(app, PairingCodeReceiver::class.java),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val action = Notification.Action.Builder(null, app.getString(R.string.bg_code_label), reply)
            .addRemoteInput(RemoteInput.Builder(REPLY_KEY).setLabel(app.getString(R.string.bg_code_label)).build())
            .build()
        manager.notify(
            NOTIFICATION_ID,
            Notification.Builder(app, CHANNEL).setSmallIcon(R.drawable.ic_mode_notification)
                .setContentTitle(app.getString(R.string.bg_code_title)).setContentText(app.getString(R.string.bg_code_body))
                .setStyle(Notification.BigTextStyle().bigText(app.getString(R.string.bg_code_body)))
                .addAction(action).setOnlyAlertOnce(true).setAutoCancel(true).build(),
        )
    }

    companion object {
        private const val ADB_WIFI = "adb_wifi_enabled"
        private const val WRITE_SECURE_SETTINGS = "android.permission.WRITE_SECURE_SETTINGS"
        private const val CHANNEL = "mode.background"
        private const val NOTIFICATION_ID = 41
        const val REPLY_KEY = "mode.pairing_code"
        private const val WATCH_MS = 400L
        private val WATCHED = setOf(BackgroundStep.Wireless, BackgroundStep.Pair)
    }
}

/** The code typed into the pairing notification. */
class PairingCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val typed = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(BackgroundSetup.REPLY_KEY)?.toString() ?: return
        ModeApplication.backgroundSetup(context)?.codeTyped(typed)
    }
}
