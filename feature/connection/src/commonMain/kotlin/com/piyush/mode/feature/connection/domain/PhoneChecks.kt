package com.piyush.mode.feature.connection.domain

/**
 * The parts of reaching the phone, checked one by one, in order (the Diagnose screen). Each phone runs
 * its own [PhoneChecks.steps]: the iPhone the pairing file to the disk image; Android whether its
 * settings allow the service, the service itself and the battery. Both end with seeing and touching.
 */
public enum class PhoneCheckId {
    PairingFile, Vpn, Reach, Paired, DeveloperMode, DiskImage, Screen, Hands,
    /** Android 13+: a sideloaded app's accessibility switch is locked until "Allow restricted settings". */
    RestrictedSettings,
    /** Android: Mode's accessibility service is on and running. */
    Engine,
    /** Android: Mode may run in the background (battery unrestricted). */
    Battery,
    /** Samsung: Mode is on the "Never sleeping apps" list (a manual step: Android can't read it). */
    Sleeping,
    /** Xiaomi and others: Mode may start by itself (manual). */
    Autostart,
    /** Xiaomi: Mode may open an app while in the background (manual). */
    Popups,
    /** Android: background mode is on and its link to the phone can be reached (Wi-Fi, Wireless debugging). */
    BackgroundLink,
    /** Android: the phone has a speech recognizer, so Mode can hear. */
    Speech,
    /** Android: the accessibility shortcut opens Mode over any app (partly manual: volume keys can't be read). */
    Shortcut,
}

public sealed interface CheckState {
    public data object NotChecked : CheckState
    public data object Running : CheckState
    /** Worked, in [ms] when timed. */
    public data class Passed(val ms: Long?) : CheckState
    /** Didn't work: why, and what fixes it. */
    public data class Failed(val reason: String, val fix: String?) : CheckState
    /** Mode can't read this setting; the user [confirmed] it (or hasn't yet). */
    public data class Unverifiable(val confirmed: Boolean) : CheckState
}

public data class PhoneCheckResult(val id: PhoneCheckId, val state: CheckState)

/** Runs the checks in order and stops at the first that fails; reports after each. */
public interface PhoneChecks {
    public suspend fun run(update: (List<PhoneCheckResult>) -> Unit)

    /** The checks this phone runs, in order. */
    public val steps: List<PhoneCheckId>

    /** Whether what phone control needs is in place (on iOS, the pairing file made once with a Mac). */
    public fun ready(): Boolean

    /** Whether the user has allowed phone control (on iOS, Mode's VPN). */
    public suspend fun permitted(): Boolean

    /**
     * Where each setting phone control needs stands now, read without touching the phone (no screenshot,
     * no tap): for setup pages that refresh whenever Mode comes back from Settings. Empty where a phone
     * has nothing to set up this way (the iPhone).
     */
    public suspend fun status(): List<PhoneCheckResult> = emptyList()
}
