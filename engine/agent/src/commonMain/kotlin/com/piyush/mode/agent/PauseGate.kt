package com.piyush.mode.agent

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/**
 * Lets the user pause a task between steps: the agent finishes the step it is
 * on, then waits here until resumed (or stopped).
 */
class PauseGate {
    private val paused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = paused.asStateFlow()

    fun pause() {
        paused.value = true
    }

    fun resume() {
        paused.value = false
    }

    /** Returns at once when not paused; otherwise when resumed. */
    suspend fun awaitRunning() {
        paused.first { !it }
    }
}
