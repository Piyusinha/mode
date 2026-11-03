package com.piyush.mode.app.graph

import com.piyush.mode.core.telemetry.LocalTelemetry
import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.core.telemetry.TelemetryResource
import com.piyush.mode.core.telemetry.Event
import kotlin.uuid.Uuid
import platform.Foundation.NSBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.common.systemLog
import com.piyush.mode.core.database.IosDatabaseFactory
import com.piyush.mode.core.network.darwinHttpClient
import com.piyush.mode.core.platform.IosAppInfo
import com.piyush.mode.core.platform.IosAppLifecycle
import com.piyush.mode.core.platform.IosBackgroundWork
import com.piyush.mode.core.platform.IosNotifier
import com.piyush.mode.core.storage.DocumentsFileStore
import com.piyush.mode.core.storage.KeychainSecretStore
import com.piyush.mode.core.storage.UserDefaultsStore
import com.piyush.mode.coredevice.LoopbackVpn
import com.piyush.mode.feature.connection.data.CoreDevicePhoneLink

/** The app's one graph on iOS, built on first use. */
val iosModeGraph: ModeGraph by lazy {
    val files = DocumentsFileStore("logs")
    AppLog.install { line ->
        files.append("events.log", line + "\n")
        systemLog(line)
    }
    AppLog.log("app", "Mode started (${if (LoopbackVpn.builtIn) "paid build, own VPN" else "free build, LocalDevVPN"})")
    val prefs = UserDefaultsStore()
    val bundle = NSBundle.mainBundle
    val installationId = prefs.getString("telemetry.installation_id") ?: Uuid.random().toString().also {
        prefs.putString("telemetry.installation_id", it)
    }
    val telemetry = runCatching {
        LocalTelemetry(files, TelemetryResource(
            installationId = installationId,
            appId = bundle.bundleIdentifier ?: "mode",
            version = bundle.objectForInfoDictionaryKey("CFBundleShortVersionString")?.toString() ?: "unknown",
            build = bundle.objectForInfoDictionaryKey("CFBundleVersion")?.toString() ?: "unknown",
            platform = "ios",
        ))
    }.getOrElse { Telemetry.None }
    telemetry.event(Event.AppStarted)
    val lifecycle = IosAppLifecycle()
    val exportScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    lifecycle.onBackground {
        telemetry.event(Event.Background)
        if (telemetry is LocalTelemetry) exportScope.launch { telemetry.flush() }
    }
    lifecycle.onForeground { telemetry.event(Event.Foreground) }
    ModeGraph(
        PlatformServices(
            secrets = KeychainSecretStore(),
            prefs = prefs,
            files = files,
            notifier = IosNotifier(),
            background = IosBackgroundWork(),
            lifecycle = lifecycle,
            appInfo = IosAppInfo(),
            http = darwinHttpClient(),
            phoneLink = CoreDevicePhoneLink(telemetry),
            telemetry = telemetry,
            database = IosDatabaseFactory(),
            permissions = com.piyush.mode.core.platform.IosPermissions(),
            phoneChecks = com.piyush.mode.feature.connection.data.CoreDevicePhoneChecks(),
        ),
    )
}
