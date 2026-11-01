@file:OptIn(ExperimentalForeignApi::class)

package com.piyush.mode.coredevice

import com.piyush.mode.ops.PhoneUnavailable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSBundle
import platform.Foundation.NSFileManager
import platform.UIKit.UIScreen

/**
 * One way Mode can reach this phone's own `remotepairingd`.
 *
 * The phone drops pair-verify on any connection that starts on the phone
 * itself (127.0.0.1 and its own Wi-Fi and USB addresses all failed, 2026-10-31),
 * so the only route is a loopback VPN that rewrites packets to come from a
 * peer, [Vpn], which the phone treats as a paired computer.
 */
data class Route(val host: String, val label: String) {
    companion object {
        /** Mode's packet tunnel on paid builds, the LocalDevVPN app on free ones. */
        val Vpn = Route(LoopbackVpn.PEER, "loopback VPN")
    }
}

/** A connected phone and how it was reached. */
class ConnectedPhone(
    val backend: CoreDeviceBackend,
    val route: Route,
    /** Every route tried, in order, with why the failed ones failed. */
    val attempts: List<Pair<Route, String?>>,
    /** True when connecting had to mount the developer disk image (the phone had restarted). */
    val mountedDiskImage: Boolean,
)

/**
 * Opens a CoreDevice session to this phone, trying each [Route] in turn.
 *
 * @param hostname the host name the pairing file was made with (`tools/make-pairing.sh` uses "Mode").
 * @param port where `remotepairingd` listens; usually 49152.
 */
class PhoneConnector(
    private val hostname: String = "Mode",
    private val port: Int = DEFAULT_PORT,
) {
    suspend fun connect(
        pairingFile: ByteArray,
        screen: ScreenInfo,
        routes: List<Route> = listOf(Route.Vpn),
    ): ConnectedPhone {
        val attempts = mutableListOf<Pair<Route, String?>>()
        for (route in routes) {
            if (route == Route.Vpn) {
                try {
                    LoopbackVpn.ensureUp()
                } catch (e: Exception) {
                    attempts += route to (e.message ?: e.toString())
                    continue
                }
            }
            val session = try {
                withContext(Dispatchers.IO) {
                    ModecoreSession.open(route.host, port, hostname, pairingFile, bundledDiskImage())
                }
            } catch (e: Exception) {
                attempts += route to (e.message ?: e.toString())
                continue
            }
            attempts += route to null
            val reader = VisionTextReader()
            withContext(Dispatchers.IO) { reader.warmUp() }
            val backend = CoreDeviceBackend(session, screen, reader, UIKitImageTools(), Dispatchers.IO)
            return ConnectedPhone(backend, route, attempts, session.mountedDiskImage)
        }
        // The VPN reached the phone but it refused the pairing: Mode was unpaired.
        if (attempts.any { (_, error) -> error?.contains("pair-verify") == true }) {
            throw PhoneUnavailable(
                "This iPhone no longer trusts Mode: it was unpaired (for example by Stop Session in the " +
                    "Dynamic Island, or by removing it from paired devices). Pair again once from a computer: " +
                    "tools/make-pairing.sh with the phone on USB.",
            )
        }
        throw PhoneUnavailable(
            buildString {
                append("Could not reach this iPhone's developer services.\n")
                attempts.forEach { (route, error) -> append("- ${route.label} (${route.host}:$port): $error\n") }
                if (!LoopbackVpn.builtIn) {
                    append("This build has no VPN of its own: turn on the LocalDevVPN app first.\n")
                }
                append(
                    "Check that Developer Mode is on, the pairing file is current, and the developer disk " +
                        "image is mounted. On mobile data with Wi-Fi off, the phone refuses the VPN route: " +
                        "turn Mobile Data off for a moment, connect, then turn it back on.",
                )
            },
        )
    }

    companion object {
        const val DEFAULT_PORT = 49152
    }
}

/**
 * Apple's developer disk image bundled into Mode by `tools/bundle-ddi.sh`, used
 * to remount it after the phone restarts; null if the build had none.
 */
fun bundledDiskImage(): String? {
    val dir = (NSBundle.mainBundle.resourcePath ?: return null) + "/DDI"
    return dir.takeIf { NSFileManager.defaultManager.fileExistsAtPath("$it/BuildManifest.plist") }
}

/** This screen in points. UIKit wants this read on the main thread. */
fun currentScreen(): ScreenInfo =
    UIScreen.mainScreen.bounds.useContents { ScreenInfo(size.width, size.height) }
