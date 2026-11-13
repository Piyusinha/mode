package com.piyush.mode.android

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Mode's accessibility service while Android has it running. Android starts and
 * stops it (the user's switch in Settings, an update, a force stop), so the rest
 * of Mode watches here rather than holding on to it.
 */
object ServiceRegistry {
    private val state = MutableStateFlow<ModeAccessibilityService?>(null)
    val service: StateFlow<ModeAccessibilityService?> = state.asStateFlow()

    /** When the service last stopped while Mode was running (epoch ms), or null. */
    @Volatile var stoppedAtMs: Long? = null
        private set

    /** Called with the service each time it stops, so the app can tell the user. */
    @Volatile var onStopped: (() -> Unit)? = null

    /**
     * The phone's accessibility shortcut, pointed at Mode (the floating button, a two-finger swipe up, or both volume
     * keys, as the user chose in Settings): the app opens Mode over whatever app is in front. Main thread.
     */
    @Volatile var onShortcut: (() -> Unit)? = null

    /** The phone's accessibility button or gesture is set to Mode right now (the volume-key shortcut can't be read). */
    val shortcutSet: Boolean
        get() = runCatching { state.value?.accessibilityButtonController?.isAccessibilityButtonAvailable == true }.getOrDefault(false)

    internal fun connected(service: ModeAccessibilityService) {
        state.value = service
    }

    internal fun disconnected(service: ModeAccessibilityService) {
        if (state.value !== service) return
        state.value = null
        stoppedAtMs = System.currentTimeMillis()
        onStopped?.invoke()
    }
}
