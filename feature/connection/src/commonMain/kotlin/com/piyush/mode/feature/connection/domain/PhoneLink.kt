package com.piyush.mode.feature.connection.domain

import com.piyush.mode.ops.PhoneBackend

/** How this platform reaches the phone it controls. */
public interface PhoneLink {
    /** Opens a session; throws [com.piyush.mode.ops.PhoneUnavailable] saying what to fix. */
    public suspend fun connect(): PhoneConnection

    /** Releases what connecting set up (on iOS, Mode's VPN) once the session is closed. */
    public suspend fun release()
}

public class PhoneConnection(
    public val backend: PhoneBackend,
    /** How it connected, such as "Mode's VPN (10.7.0.1)". */
    public val route: String,
    /** Every route tried, one per line, with why the failed ones failed. */
    public val attempts: String,
)
