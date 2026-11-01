package com.piyush.mode.core.common

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Counts work that needs the phone session (a task, a hands check, a model
 * comparison), so policies such as "disconnect when Mode is left idle" can
 * ask whether anything is running without knowing every feature.
 */
public class ActivityTracker {
    private val running = MutableStateFlow(0)

    /** How many pieces of work are running now. */
    public val active: StateFlow<Int> = running.asStateFlow()

    public val isBusy: Boolean get() = running.value > 0

    /** Runs [block], counted as busy until it ends (also when it fails or is cancelled). */
    public suspend fun <T> track(block: suspend () -> T): T {
        running.update { it + 1 }
        try {
            return block()
        } finally {
            running.update { it - 1 }
        }
    }
}
