package com.piyush.mode.app.graph

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
    ModeGraph(
        PlatformServices(
            secrets = KeychainSecretStore(),
            prefs = UserDefaultsStore(),
            files = files,
            notifier = IosNotifier(),
            background = IosBackgroundWork(),
            lifecycle = IosAppLifecycle(),
            appInfo = IosAppInfo(),
            http = darwinHttpClient(),
            phoneLink = CoreDevicePhoneLink(),
            database = IosDatabaseFactory(),
        ),
    )
}
