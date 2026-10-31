package com.piyush.mode.app

import androidx.compose.ui.graphics.ImageBitmap
import com.piyush.mode.ops.PhoneBackend

/** What the shared UI needs from the platform to reach the phone. */
interface PhoneLink {
    /** Opens a session to this phone; throws [com.piyush.mode.ops.PhoneUnavailable] with what to fix. */
    suspend fun connect(): PhoneConnection

    /** This app's bundle id, to bring Mode back to the front after driving another app. */
    val ownBundleId: String

    /**
     * Asks the OS for time to finish [work] if Mode goes to the background
     * (it does whenever it drives another app).
     */
    suspend fun <T> inBackground(work: suspend () -> T): T

    /** Decodes a JPEG or PNG for display. */
    fun decodeImage(bytes: ByteArray): ImageBitmap

    /** Releases what connecting set up (Mode's VPN) once the session is closed. */
    suspend fun release()

    /** Calls [action] whenever Mode goes to the background (the user left it). */
    fun onAppBackground(action: () -> Unit)
}

class PhoneConnection(
    val backend: PhoneBackend,
    /** How it connected, such as "loopback (127.0.0.1)". */
    val route: String,
    /** Every route tried, one per line, with why the failed ones failed. */
    val attempts: String,
)
