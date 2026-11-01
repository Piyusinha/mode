package com.piyush.mode.feature.connection.data

import com.piyush.mode.coredevice.LoopbackVpn
import com.piyush.mode.coredevice.ModecoreSession
import com.piyush.mode.coredevice.PairingFile
import com.piyush.mode.coredevice.PhoneConnector
import com.piyush.mode.coredevice.bundledDiskImage
import com.piyush.mode.coredevice.currentScreen
import com.piyush.mode.feature.connection.domain.CheckState
import com.piyush.mode.feature.connection.domain.PhoneCheckId
import com.piyush.mode.feature.connection.domain.PhoneCheckResult
import com.piyush.mode.feature.connection.domain.PhoneChecks
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlin.time.TimeSource

/**
 * Each stage of reaching this iPhone on its own: the pairing file, the VPN,
 * the phone's pairing service, Developer Mode, the disk image, seeing the
 * screen and touching it. Stops at the first that fails, with the fix.
 */
public class CoreDevicePhoneChecks : PhoneChecks {
    override fun hasPairingFile(): Boolean = PairingFile.load() != null

    override suspend fun vpnAllowed(): Boolean = LoopbackVpn.isAllowed()

    override suspend fun run(update: (List<PhoneCheckResult>) -> Unit) {
        val results = PhoneCheckId.entries.associateWith<PhoneCheckId, CheckState> { CheckState.NotChecked }.toMutableMap()
        fun report() = update(results.map { (id, state) -> PhoneCheckResult(id, state) })
        suspend fun <T> step(id: PhoneCheckId, block: suspend () -> T): T {
            results[id] = CheckState.Running
            report()
            val started = TimeSource.Monotonic.markNow()
            val value = block()
            results[id] = CheckState.Passed(started.elapsedNow().inWholeMilliseconds)
            report()
            return value
        }
        fun fail(id: PhoneCheckId, reason: String, fix: String?) {
            results[id] = CheckState.Failed(reason, fix)
            report()
        }
        report()

        val pairing = PairingFile.load()
        if (pairing == null) {
            fail(PhoneCheckId.PairingFile, "No pairing file on this iPhone.", "Pair once from your Mac with Mode Setup.")
            return
        }
        results[PhoneCheckId.PairingFile] = CheckState.Passed(null)

        try {
            step(PhoneCheckId.Vpn) { LoopbackVpn.ensureUp() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            fail(PhoneCheckId.Vpn, e.message ?: "The VPN didn't start.",
                if (LoopbackVpn.builtIn) "Allow Mode's VPN, or turn it on in Settings › VPN." else "Turn on the LocalDevVPN app.")
            return
        }

        val session = try {
            step(PhoneCheckId.Reach) {
                withContext(Dispatchers.IO) {
                    ModecoreSession.open(LoopbackVpn.PEER, PhoneConnector.DEFAULT_PORT, HOSTNAME, pairing, bundledDiskImage())
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val message = e.message ?: e.toString()
            when {
                "pair-verify" in message -> {
                    results[PhoneCheckId.Reach] = CheckState.Passed(null)
                    fail(PhoneCheckId.Paired, "This iPhone no longer trusts Mode: it was unpaired, usually by tapping Stop on the screen-control indicator.",
                        "Pair it again from your Mac.")
                }
                "developer mode" in message.lowercase() -> fail(PhoneCheckId.DeveloperMode, message, "Turn on Settings › Privacy & Security › Developer Mode.")
                "image" in message.lowercase() || "mount" in message.lowercase() -> fail(PhoneCheckId.DiskImage, message, "Connect to the internet once so the disk image can be mounted.")
                else -> fail(PhoneCheckId.Reach, message, "On mobile data only, turn it off for a moment so Mode can connect.")
            }
            return
        }
        try {
            results[PhoneCheckId.Paired] = CheckState.Passed(null)
            results[PhoneCheckId.DeveloperMode] = CheckState.Passed(null)
            results[PhoneCheckId.DiskImage] = CheckState.Passed(null)
            try {
                step(PhoneCheckId.Screen) { withContext(Dispatchers.IO) { check(session.screenshotPng().isNotEmpty()) { "the screenshot was empty" } } }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                fail(PhoneCheckId.Screen, e.message ?: "Couldn't see the screen.", null)
                return
            }
            try {
                // A tap on the title area of Mode's own Diagnose page, which does nothing.
                val screen = withContext(Dispatchers.Main) { currentScreen() }
                val (x, y) = screen.toTouch(screen.widthPt / 2, TAP_Y_PT)
                step(PhoneCheckId.Hands) { withContext(Dispatchers.IO) { session.touch(x, y, x, y, TAP_MS, 0) } }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                fail(PhoneCheckId.Hands, e.message ?: "Couldn't tap.", null)
            }
        } finally {
            withContext(Dispatchers.IO) { session.close() }
        }
    }

    private companion object {
        const val HOSTNAME = "Mode"
        const val TAP_Y_PT = 140.0
        const val TAP_MS = 50L
    }
}
