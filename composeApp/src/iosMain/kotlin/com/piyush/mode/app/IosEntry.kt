package com.piyush.mode.app

import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.storage.FileStore
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.window.ComposeUIViewController
import com.piyush.mode.coredevice.PairingFile
import com.piyush.mode.coredevice.PhoneConnector
import com.piyush.mode.coredevice.currentScreen
import com.piyush.mode.ops.PhoneUnavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import com.piyush.mode.coredevice.LoopbackVpn
import com.piyush.mode.core.common.systemLog
import com.piyush.mode.core.network.darwinHttpClient
import com.piyush.mode.core.platform.IosAppInfo
import com.piyush.mode.core.platform.IosAppLifecycle
import com.piyush.mode.core.platform.IosBackgroundWork
import com.piyush.mode.core.platform.IosNotifier
import com.piyush.mode.core.storage.DocumentsFileStore
import com.piyush.mode.core.storage.KeychainSecretStore
import com.piyush.mode.core.storage.UserDefaultsStore
import platform.UIKit.UIViewController

/** The root view controller Swift hosts in its window. */
fun MainViewController(): UIViewController {
    AppLog.install { line ->
        IosServices.logs.append("events.log", line + "\n")
        systemLog(line)
    }
    AppLog.log("app", "Mode started (${if (LoopbackVpn.builtIn) "paid build, own VPN" else "free build, LocalDevVPN"})")
    return ComposeUIViewController { App(IosPhoneLink, IosServices) }
}

private object IosServices : AppServices {
    private val secrets = KeychainSecretStore()
    private val prefs = UserDefaultsStore()
    private val notifier = IosNotifier()

    override fun secret(name: String): String? = secrets.get(name)
    override fun setSecret(name: String, value: String?) = secrets.set(name, value)
    override fun pref(name: String): String? = prefs.getString(name)
    override fun setPref(name: String, value: String) = prefs.putString(name, value)
    override fun notify(title: String, body: String) = notifier.post(title, body)
    override fun requestNotifications() = notifier.requestPermission()
    override val http by lazy { darwinHttpClient() }
    override val logs: FileStore = DocumentsFileStore("logs")
}

private object IosPhoneLink : PhoneLink {
    private val connector = PhoneConnector()

    override val ownBundleId: String = IosAppInfo().bundleId

    override suspend fun connect(): PhoneConnection {
        val pairing = PairingFile.load() ?: throw PhoneUnavailable(
            "No pairing file yet. Run tools/make-pairing.sh on the Mac with the phone on USB, " +
                "or drop ${PairingFile.NAME} into Mode's folder in Finder or the Files app.",
        )
        val screen = withContext(Dispatchers.Main) { currentScreen() }
        val phone = connector.connect(pairing, screen)
        return PhoneConnection(
            backend = phone.backend,
            route = "${phone.route.label} (${phone.route.host})",
            attempts = phone.attempts.joinToString("\n") { (route, error) ->
                "  ${route.label} (${route.host}): ${error ?: "connected"}"
            } + if (phone.mountedDiskImage) "\n  mounted the developer disk image (the phone had restarted)" else "",
        )
    }

    private val background = IosBackgroundWork()
    private val lifecycle = IosAppLifecycle()

    override suspend fun <T> inBackground(work: suspend () -> T): T = background.run(work)

    override fun decodeImage(bytes: ByteArray): ImageBitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()

    override suspend fun release() {
        LoopbackVpn.stop()
        AppLog.log("vpn", "Mode's VPN stopped")
    }

    override fun onAppTerminate(action: () -> Unit) = lifecycle.onTerminate(action)

    override fun onAppBackground(action: () -> Unit) = lifecycle.onBackground(action)
}
