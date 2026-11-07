package com.piyush.mode.android

/**
 * Keeps Mode's own overlay out of the agent's way on the user's screen: the app sets these while an overlay shows.
 * Before a screenshot it hides (and waits for a frame), after it shows again; before a gesture it lets touches pass
 * through, after it takes them again. Unset (no overlay), each is a no-op. The hidden display never needs them.
 */
object OverlayGuard {
    @Volatile var hide: (suspend () -> Unit)? = null
    @Volatile var show: (() -> Unit)? = null
    @Volatile var passThrough: (suspend (Boolean) -> Unit)? = null

    internal suspend fun beforeShot() {
        runCatching { hide?.invoke() }
    }

    internal fun afterShot() {
        runCatching { show?.invoke() }
    }

    internal suspend fun beforeGesture() {
        runCatching { passThrough?.invoke(true) }
    }

    internal suspend fun afterGesture() {
        runCatching { passThrough?.invoke(false) }
    }
}
