package com.piyush.mode.app.graph

import android.app.Application
import android.content.Intent
import android.util.Log
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.database.AndroidDatabaseFactory
import com.piyush.mode.core.network.okHttpClient
import com.piyush.mode.core.platform.AndroidAppInfo
import com.piyush.mode.core.platform.AndroidAppLifecycle
import com.piyush.mode.core.platform.AndroidDeviceLock
import com.piyush.mode.core.platform.AndroidNotifier
import com.piyush.mode.core.platform.AndroidPermissions
import com.piyush.mode.core.platform.AndroidBackgroundWork
import com.piyush.mode.core.storage.JavaFileStore
import com.piyush.mode.core.storage.KeystoreSecretStore
import com.piyush.mode.core.storage.SharedPrefsStore
import com.piyush.mode.core.telemetry.Event
import com.piyush.mode.core.telemetry.LocalTelemetry
import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.core.telemetry.TelemetryResource
import com.piyush.mode.feature.backup.data.AndroidBackupFiles
import com.piyush.mode.feature.connection.data.AccessibilityPhoneChecks
import com.piyush.mode.feature.connection.data.AccessibilityPhoneLink
import com.piyush.mode.feature.connection.data.BackgroundMode
import com.piyush.mode.feature.connection.data.UnavailablePhoneChecks
import com.piyush.mode.feature.connection.data.UnavailablePhoneLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.stateIn
import java.io.File
import kotlin.uuid.Uuid

/**
 * The app's one graph on Android, built once by the Application. Logs go to
 * `files/logs/events.log` and logcat; backups are written to the cache folder
 * before the system picker copies them where the user chose.
 */
fun androidModeGraph(
    app: Application,
    notificationIcon: Int,
    openApp: () -> Intent,
    notifierWords: com.piyush.mode.core.platform.NotifierWords? = null,
    notifierHooks: com.piyush.mode.core.platform.NotifierHooks? = null,
    presence: com.piyush.mode.core.platform.ModePresence = com.piyush.mode.core.platform.ModePresence.None,
): ModeGraph {
    val files = JavaFileStore(File(app.filesDir, "logs"))
    AppLog.install { line ->
        files.append("events.log", line + "\n")
        Log.i("Mode", line)
    }
    AppLog.log("app", "Mode started (Android ${android.os.Build.VERSION.RELEASE}, ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL})")
    val prefs = SharedPrefsStore(app)
    val installationId = prefs.getString("telemetry.installation_id") ?: Uuid.random().toString().also {
        prefs.putString("telemetry.installation_id", it)
    }
    val info = app.packageManager.getPackageInfo(app.packageName, 0)
    val version = info.versionName ?: "unknown"
    val telemetry = runCatching {
        LocalTelemetry(files, TelemetryResource(
            installationId = installationId,
            appId = app.packageName,
            version = version,
            build = info.longVersionCode.toString(),
            platform = "android",
        ))
    }.getOrElse { Telemetry.None }
    telemetry.event(Event.AppStarted)
    val lifecycle = AndroidAppLifecycle()
    val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lifecycle.onBackground {
        telemetry.event(Event.Background)
        if (telemetry is LocalTelemetry) exportScope.launch { telemetry.flush() }
    }
    lifecycle.onForeground { telemetry.event(Event.Foreground) }
    // The full build declares Mode's accessibility service; the Play build has none and cannot drive the phone.
    val link = if (declaresPhoneControl(app)) AccessibilityPhoneLink(telemetry, BackgroundMode(app, prefs).also { background = it }) else null
    return ModeGraph(
        PlatformServices(
            secrets = KeystoreSecretStore(app),
            prefs = prefs,
            files = files,
            notifier = AndroidNotifier(app, notificationIcon, openApp, notifierWords, notifierHooks, presence),
            background = AndroidBackgroundWork(app),
            // In background mode a locked phone needn't hold tasks back: the hidden display is always unlocked.
            deviceLock = background?.let { HiddenDisplayLock(AndroidDeviceLock(app), it, exportScope) } ?: AndroidDeviceLock(app),
            lifecycle = lifecycle,
            appInfo = AndroidAppInfo(app),
            http = okHttpClient(),
            phoneLink = link ?: UnavailablePhoneLink(PLAY_BUILD),
            telemetry = telemetry,
            database = AndroidDatabaseFactory(app),
            permissions = AndroidPermissions(
                app, assistantHeld = { askModeOn(app) }, assistantAvailable = { declaresAskMode(app) }, turnOnAssistant = { setAskMode(app, true) },
            ),
            phoneChecks = link?.let { AccessibilityPhoneChecks(app, it) } ?: UnavailablePhoneChecks(PLAY_BUILD),
            appVersion = version,
            backupFiles = AndroidBackupFiles(app.cacheDir),
            profile = com.piyush.mode.agent.PhoneProfile.Android,
            presence = presence,
        ),
    )
}

private const val PLAY_BUILD = "This version of Mode can't use your phone's apps. Install Mode from heymode.app to let it."

/** Whether this build's manifest has Mode's accessibility service. */
private fun declaresPhoneControl(app: Application): Boolean = runCatching {
    val component = android.content.ComponentName(app, "com.piyush.mode.android.ModeAccessibilityService")
    app.packageManager.getServiceInfo(component, 0)
    true
}.getOrDefault(false)

/** The phone's lock as tasks see it: locked only when background mode can't work on the hidden display. */
private class HiddenDisplayLock(
    real: com.piyush.mode.core.platform.DeviceLock,
    mode: BackgroundMode,
    scope: CoroutineScope,
) : com.piyush.mode.core.platform.DeviceLock {
    override val locked: kotlinx.coroutines.flow.StateFlow<Boolean> =
        kotlinx.coroutines.flow.combine(real.locked, mode.enabledFlow) { locked, background -> locked && !background }
            .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, real.locked.value && !mode.enabled)
}

/** Background mode, in builds that can drive the phone (the app's setup screens use it). */
var background: BackgroundMode? = null
    private set

/**
 * "Ask Mode": a second app entry that opens Mode straight into talking mode, so the user's own assistant starts a
 * voice session ("Hey Google, open Ask Mode") without Mode replacing it. Off until the user turns it on.
 */
private fun askMode(app: Application) = android.content.ComponentName(app, ASK_MODE)

private fun declaresAskMode(app: Application): Boolean =
    runCatching { app.packageManager.getActivityInfo(askMode(app), android.content.pm.PackageManager.MATCH_DISABLED_COMPONENTS); true }.getOrDefault(false)

private fun askModeOn(app: Application): Boolean =
    app.packageManager.getComponentEnabledSetting(askMode(app)) == android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED

/** Shows or hides the "Ask Mode" entry (the launcher and the user's assistant find it by its name). */
fun setAskMode(app: Application, on: Boolean) {
    app.packageManager.setComponentEnabledSetting(
        askMode(app),
        if (on) android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED else android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
        android.content.pm.PackageManager.DONT_KILL_APP,
    )
}

/** The entry's activity alias in the app's manifest. */
const val ASK_MODE: String = "com.piyush.mode.app.AskMode"

/** Whether Mode is the phone's digital assistant (hold power to talk to it). */
private fun holdsAssistantRole(app: Application): Boolean =
    app.getSystemService(android.app.role.RoleManager::class.java)?.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) == true

/** Whether this build has an assistant service Android can offer as the digital assistant. */
private fun declaresAssistant(app: Application): Boolean =
    app.packageManager.queryIntentServices(
        android.content.Intent("android.service.voice.VoiceInteractionService").setPackage(app.packageName), 0,
    ).isNotEmpty()

