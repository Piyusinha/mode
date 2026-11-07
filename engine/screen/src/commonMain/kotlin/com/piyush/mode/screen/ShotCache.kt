package com.piyush.mode.screen

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

/**
 * The screenshot a `screen.capture` just took, reused once by the `screen.text`
 * that follows it, so one look (picture + text) costs one shot. Anything that
 * acts on the phone drops it, so callers can watch the screen change.
 */
class ShotCache(
    private val clock: TimeSource = TimeSource.Monotonic,
    private val reuse: Duration = DEFAULT_REUSE,
) {
    private var last: Pair<TimeMark, ByteArray>? = null

    fun put(shot: ByteArray) {
        last = clock.markNow() to shot
    }

    /** The cached shot if it is still fresh, else null; either way it is used up. */
    fun take(): ByteArray? {
        val shot = last ?: return null
        last = null
        return shot.second.takeIf { shot.first.elapsedNow() < reuse }
    }

    fun invalidate() {
        last = null
    }

    companion object {
        /** How long a screenshot stays good for when nothing has been done. */
        val DEFAULT_REUSE: Duration = 1500.milliseconds
    }
}
