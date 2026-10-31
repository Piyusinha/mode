@file:OptIn(ExperimentalForeignApi::class)

package com.piyush.mode.coredevice

import com.piyush.mode.ops.PhoneUnavailable
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.UIKit.UIScreen

/**
 * How Mode reaches this phone's own `remotepairingd`.
 *
 * The pairing service refuses connections it thinks come from the cellular
 * interface, and the loopback VPN looks like one when Wi-Fi is off. A plain
 * loopback connection is tried first because it does not have that problem.
 */
enum class Route(val host: String, val label: String) {
    /** Straight to 127.0.0.1: no VPN needed, should work on cellular. */
    Loopback("127.0.0.1", "loopback"),

    /**
     * Through a loopback VPN whose peer is 10.7.0.1: Mode's own packet tunnel
     * on paid builds, the LocalDevVPN app on free ones.
     */
    Vpn("10.7.0.1", "loopback VPN"),
}

/** A connected phone and how it was reached. */
class ConnectedPhone(
    val backend: CoreDeviceBackend,
    val route: Route,
    /** Every route tried, in order, with why the failed ones failed. */
    val attempts: List<Pair<Route, String?>>,
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
        routes: List<Route> = Route.entries,
    ): ConnectedPhone = withContext(Dispatchers.IO) {
        val attempts = mutableListOf<Pair<Route, String?>>()
        for (route in routes) {
            val session = try {
                ModecoreSession.open(route.host, port, hostname, pairingFile)
            } catch (e: Exception) {
                attempts += route to (e.message ?: e.toString())
                continue
            }
            attempts += route to null
            val backend = CoreDeviceBackend(session, screen, VisionTextReader(), UIKitImageTools(), Dispatchers.IO)
            return@withContext ConnectedPhone(backend, route, attempts)
        }
        throw PhoneUnavailable(
            buildString {
                append("Could not reach this iPhone's developer services.\n")
                attempts.forEach { (route, error) -> append("- ${route.label} (${route.host}:$port): $error\n") }
                append(
                    "Check that Developer Mode is on, the pairing file is current, and the developer disk " +
                        "image is mounted. For the VPN route, turn on the VPN; on cellular with Wi-Fi off, " +
                        "turn mobile data off while connecting.",
                )
            },
        )
    }

    companion object {
        const val DEFAULT_PORT = 49152
    }
}

/** This screen in points. UIKit wants this read on the main thread. */
fun currentScreen(): ScreenInfo =
    UIScreen.mainScreen.bounds.useContents { ScreenInfo(size.width, size.height) }
