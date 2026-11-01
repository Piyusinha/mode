package com.piyush.mode.feature.connection.domain

/** Where the phone session stands. */
public sealed interface ConnectionState {
    public data object Disconnected : ConnectionState
    public data object Connecting : ConnectionState
    public data class Connected(val route: String) : ConnectionState

    /** The user pressed Stop session. */
    public data object Stopped : ConnectionState
    public data class Failed(val reason: String) : ConnectionState
}

/** A one-line status for the screen. */
public val ConnectionState.label: String
    get() = when (this) {
        ConnectionState.Disconnected, is ConnectionState.Failed -> "Phone: not connected"
        ConnectionState.Connecting -> "Connecting…"
        is ConnectionState.Connected -> "Connected via $route"
        ConnectionState.Stopped -> "Session stopped"
    }
