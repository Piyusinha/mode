package com.piyush.mode.app

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
import platform.Foundation.NSBundle
import platform.UIKit.UIApplication
import platform.UIKit.UIBackgroundTaskInvalid
import platform.UIKit.UIViewController

/** The root view controller Swift hosts in its window. */
fun MainViewController(): UIViewController = ComposeUIViewController { App(IosPhoneLink) }

private object IosPhoneLink : PhoneLink {
    private val connector = PhoneConnector()

    override val ownBundleId: String = NSBundle.mainBundle.bundleIdentifier ?: "com.piyush.mode"

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

    override suspend fun <T> inBackground(work: suspend () -> T): T {
        // Silent mixable audio keeps Mode running for as long as the work
        // takes; the background task is a second, shorter safety net.
        withContext(Dispatchers.Main) { KeepAwake.start() }
        val app = UIApplication.sharedApplication
        var task = UIBackgroundTaskInvalid
        task = app.beginBackgroundTaskWithName("mode.drive") {
            app.endBackgroundTask(task)
            task = UIBackgroundTaskInvalid
        }
        try {
            return work()
        } finally {
            if (task != UIBackgroundTaskInvalid) app.endBackgroundTask(task)
            withContext(Dispatchers.Main) { KeepAwake.stop() }
        }
    }

    override fun decodeImage(bytes: ByteArray): ImageBitmap = Image.makeFromEncoded(bytes).toComposeImageBitmap()
}
