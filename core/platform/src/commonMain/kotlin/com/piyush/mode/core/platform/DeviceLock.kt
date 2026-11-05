package com.piyush.mode.core.platform

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether the iPhone is locked, as the system tells Mode while it runs. Mode can't drive other apps
 * on a locked iPhone, so a task that arrives then waits and starts the moment it unlocks.
 */
public interface DeviceLock {
    /** True while the screen is locked (the passcode or Face ID is needed to get past it). */
    public val locked: StateFlow<Boolean>

    public companion object {
        /** A platform with no lock to watch: never locked. */
        public val None: DeviceLock = object : DeviceLock {
            override val locked: StateFlow<Boolean> = MutableStateFlow(false).asStateFlow()
        }
    }
}

/** A lock that tests (and previews) set by hand. */
public class ManualDeviceLock(locked: Boolean = false) : DeviceLock {
    private val state = MutableStateFlow(locked)
    override val locked: StateFlow<Boolean> = state.asStateFlow()

    public fun lock() { state.value = true }
    public fun unlock() { state.value = false }
}
