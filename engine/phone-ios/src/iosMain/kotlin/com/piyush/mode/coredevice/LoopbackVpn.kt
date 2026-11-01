@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.piyush.mode.coredevice

import com.piyush.mode.ops.PhoneUnavailable
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import platform.Foundation.NSBundle
import platform.Foundation.NSError
import platform.NetworkExtension.NETunnelProviderManager
import platform.NetworkExtension.NETunnelProviderProtocol
import platform.NetworkExtension.NETunnelProviderSession
import platform.NetworkExtension.NEVPNStatusConnected
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The loopback VPN that makes Mode look like a paired computer to its own phone:
 * packets to [PEER] come back with source and destination swapped.
 *
 * Paid builds ship it as Mode's own packet-tunnel extension (`iosApp/ModeTunnel`)
 * and turn it on here. Free builds cannot sign that extension; the user turns on
 * the LocalDevVPN app instead, which uses the same peer address.
 */
object LoopbackVpn {
    /** The peer address the tunnel reflects; both Mode's tunnel and LocalDevVPN use it. */
    const val PEER = "10.7.0.1"

    /** True in builds made with `tools/gen-project.sh paid` (Info.plist `ModeBuiltInVPN`). */
    val builtIn: Boolean
        get() = when (val v = NSBundle.mainBundle.objectForInfoDictionaryKey("ModeBuiltInVPN")) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> v.equals("YES", ignoreCase = true) || v == "true"
            else -> false
        }

    private val providerBundleId: String
        get() = (NSBundle.mainBundle.bundleIdentifier ?: "com.piyush.mode") + ".tunnel"

    /**
     * Makes sure the loopback VPN is up. On a paid build it adds Mode's VPN
     * configuration the first time (iOS asks the user to allow it) and starts
     * it. On a free build it does nothing; the connection attempt tells.
     */
    suspend fun ensureUp(timeout: Duration = 10.seconds) {
        if (!builtIn) return
        val manager = loadOrCreate()
        // A manager object can go stale (the user toggled the VPN in Settings,
        // or it was just stopped): reload before starting.
        completion { cb -> manager.loadFromPreferencesWithCompletionHandler(cb) }
        if (manager.connection.status == NEVPNStatusConnected) return
        repeat(2) { attempt ->
            val session = manager.connection as? NETunnelProviderSession
                ?: throw PhoneUnavailable("Mode's VPN configuration is not a packet tunnel")
            memScoped {
                val error = alloc<ObjCObjectVar<NSError?>>()
                if (!session.startVPNTunnelAndReturnError(error.ptr)) {
                    throw PhoneUnavailable("could not start Mode's VPN: ${error.value?.localizedDescription}")
                }
            }
            val up = withTimeoutOrNull(timeout / 2) {
                while (manager.connection.status != NEVPNStatusConnected) delay(100.milliseconds)
                true
            } ?: false
            if (up) return
            if (attempt == 0) {
                manager.connection.stopVPNTunnel()
                delay(500.milliseconds)
            }
        }
        throw PhoneUnavailable(
            "Mode's VPN did not connect (status ${manager.connection.status}). Open Settings › VPN, check that " +
                "\"Mode\" is listed and allowed, then try again.",
        )
    }

    /**
     * Turns Mode's VPN off when Mode is not working, so iOS stops showing it.
     * Free builds leave LocalDevVPN alone: the user runs that app.
     */
    suspend fun stop() {
        if (!builtIn) return
        loadAll().filter {
            (it.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier == providerBundleId
        }.forEach { it.connection.stopVPNTunnel() }
    }

    /** Whether the user has allowed Mode's VPN (its configuration exists); free builds use LocalDevVPN instead. */
    suspend fun isAllowed(): Boolean = builtIn && loadAll().any {
        (it.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier == providerBundleId
    }

    private suspend fun loadOrCreate(): NETunnelProviderManager {
        val existing = loadAll().firstOrNull {
            (it.protocolConfiguration as? NETunnelProviderProtocol)?.providerBundleIdentifier == providerBundleId
        }
        val manager = existing ?: NETunnelProviderManager().apply {
            protocolConfiguration = NETunnelProviderProtocol().apply {
                providerBundleIdentifier = providerBundleId
                serverAddress = "This iPhone"
            }
            localizedDescription = "Mode"
        }
        if (existing == null || !manager.enabled) {
            manager.enabled = true
            // The first save shows iOS's "Add VPN Configurations" prompt.
            completion { cb -> manager.saveToPreferencesWithCompletionHandler(cb) }
            // A freshly saved manager must be reloaded before it can start.
            completion { cb -> manager.loadFromPreferencesWithCompletionHandler(cb) }
        }
        return manager
    }

    private suspend fun loadAll(): List<NETunnelProviderManager> = suspendCancellableCoroutine { cont ->
        NETunnelProviderManager.loadAllFromPreferencesWithCompletionHandler { managers, error ->
            if (error != null) {
                cont.resumeWithException(PhoneUnavailable("could not read VPN settings: ${error.localizedDescription}"))
            } else {
                cont.resume(managers.orEmpty().filterIsInstance<NETunnelProviderManager>())
            }
        }
    }

    private suspend fun completion(call: ((NSError?) -> Unit) -> Unit) = suspendCancellableCoroutine { cont ->
        call { error ->
            if (error != null) {
                cont.resumeWithException(PhoneUnavailable("VPN setup failed: ${error.localizedDescription}"))
            } else {
                cont.resume(Unit)
            }
        }
    }
}
