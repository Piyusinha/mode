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
import com.piyush.mode.feature.connection.data.UnavailablePhoneChecks
import com.piyush.mode.feature.connection.data.UnavailablePhoneLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import kotlin.uuid.Uuid

/**
 * The app's one graph on Android, built once by the Application. Logs go to
 * `files/logs/events.log` and logcat; backups are written to the cache folder
 * before the system picker copies them where the user chose.
 */
fun androidModeGraph(app: Application, notificationIcon: Int, openApp: () -> Intent): ModeGraph {
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
    val link = if (declaresPhoneControl(app)) AccessibilityPhoneLink(telemetry) else null
    return ModeGraph(
        PlatformServices(
            secrets = KeystoreSecretStore(app),
            prefs = prefs,
            files = files,
            notifier = AndroidNotifier(app, notificationIcon, openApp),
            background = AndroidBackgroundWork(app),
            deviceLock = AndroidDeviceLock(app),
            lifecycle = lifecycle,
            appInfo = AndroidAppInfo(app),
            http = okHttpClient(),
            phoneLink = link ?: UnavailablePhoneLink(PLAY_BUILD),
            telemetry = telemetry,
            database = AndroidDatabaseFactory(app),
            permissions = AndroidPermissions(app, assistantHeld = { holdsAssistantRole(app) }),
            phoneChecks = link?.let { AccessibilityPhoneChecks(app, it) } ?: UnavailablePhoneChecks(PLAY_BUILD),
            appVersion = version,
            backupFiles = AndroidBackupFiles(app.cacheDir),
            profile = com.piyush.mode.agent.PhoneProfile.Android,
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

/** Whether Mode is the phone's digital assistant (hold power to talk to it). */
private fun holdsAssistantRole(app: Application): Boolean =
    app.getSystemService(android.app.role.RoleManager::class.java)?.isRoleHeld(android.app.role.RoleManager.ROLE_ASSISTANT) == true

