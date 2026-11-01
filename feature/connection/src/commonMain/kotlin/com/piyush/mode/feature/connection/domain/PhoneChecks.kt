package com.piyush.mode.feature.connection.domain

/** The parts of reaching this iPhone, checked one by one, in order (the Diagnose screen). */
public enum class PhoneCheckId { PairingFile, Vpn, Reach, Paired, DeveloperMode, DiskImage, Screen, Hands }

public sealed interface CheckState {
    public data object NotChecked : CheckState
    public data object Running : CheckState
    /** Worked, in [ms] when timed. */
    public data class Passed(val ms: Long?) : CheckState
    /** Didn't work: why, and what fixes it. */
    public data class Failed(val reason: String, val fix: String?) : CheckState
}

public data class PhoneCheckResult(val id: PhoneCheckId, val state: CheckState)

/** Runs the checks in order and stops at the first that fails; reports after each. */
public interface PhoneChecks {
    public suspend fun run(update: (List<PhoneCheckResult>) -> Unit)

    /** Whether the pairing file (made once with a Mac) is on this iPhone. */
    public fun hasPairingFile(): Boolean

    /** Whether the user has allowed Mode's VPN. */
    public suspend fun vpnAllowed(): Boolean
}
