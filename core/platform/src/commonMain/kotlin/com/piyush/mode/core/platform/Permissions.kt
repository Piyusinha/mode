package com.piyush.mode.core.platform

/**
 * What Mode asks the phone for. Phone control itself (the VPN on iOS, Accessibility on Android) is
 * asked for when Mode first connects; see PhoneChecks. Assistant: Siri's App Shortcuts on iOS (always
 * allowed), the assistant role on Android.
 */
public enum class Permission { Notifications, Microphone, Assistant }

public enum class PermissionStatus {
    /** Not asked yet. */
    NotAsked,
    Allowed,
    Denied,
    /** This build can't ask for it. */
    Unavailable,
}

/** Asks for, and reports, what Mode is allowed to do. */
public interface Permissions {
    public suspend fun status(permission: Permission): PermissionStatus

    /** Shows iOS's prompt when it hasn't been answered yet; returns the result. */
    public suspend fun request(permission: Permission): PermissionStatus
}
