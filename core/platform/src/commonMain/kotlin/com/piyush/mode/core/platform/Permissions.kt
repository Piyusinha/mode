package com.piyush.mode.core.platform

/** What Mode asks iOS for (the VPN is asked for when Mode first connects; see PhoneChecks). */
public enum class Permission { Notifications, Microphone, Siri }

public enum class PermissionStatus {
    /** Not asked yet. */
    NotAsked,
    Allowed,
    Denied,
    /** This build can't ask for it (Siri needs a paid Apple account). */
    Unavailable,
}

/** Asks for, and reports, what Mode is allowed to do. */
public interface Permissions {
    public suspend fun status(permission: Permission): PermissionStatus

    /** Shows iOS's prompt when it hasn't been answered yet; returns the result. */
    public suspend fun request(permission: Permission): PermissionStatus
}
