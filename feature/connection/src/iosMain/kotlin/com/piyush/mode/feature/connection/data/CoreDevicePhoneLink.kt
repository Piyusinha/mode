package com.piyush.mode.feature.connection.data

import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.core.telemetry.Operation
import com.piyush.mode.core.telemetry.trace
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.coredevice.LoopbackVpn
import com.piyush.mode.coredevice.PairingFile
import com.piyush.mode.coredevice.PhoneConnector
import com.piyush.mode.coredevice.currentScreen
import com.piyush.mode.feature.connection.domain.PhoneConnection
import com.piyush.mode.feature.connection.domain.PhoneLink
import com.piyush.mode.ops.PhoneUnavailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reaches this iPhone's own CoreDevice services through Mode's loopback VPN,
 * using the pairing file made once on a Mac.
 */
public class CoreDevicePhoneLink(private val telemetry: Telemetry = Telemetry.None) : PhoneLink {
    private val connector = PhoneConnector(telemetry = telemetry)

    override suspend fun connect(): PhoneConnection {
        val pairing = telemetry.trace(Operation.PairingLoad) { PairingFile.load() } ?: throw PhoneUnavailable(
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

    override suspend fun release() {
        telemetry.trace(Operation.VpnStop) { LoopbackVpn.stop() }
        AppLog.log("vpn", "Mode's VPN stopped")
    }
}
