package com.piyush.mode.android

import kotlin.math.hypot
import kotlin.math.roundToLong

/** Finger paths for the ops, from points in dp to pixels. */
object Strokes {
    const val TAP_MS = 50L
    /** A scroll moves at this speed, so the list stops where the finger stops (no fling). */
    const val SCROLL_DP_PER_S = 800.0
    const val MIN_SCROLL_MS = 200L

    fun tap(x: Double, y: Double, density: Float, displayId: Int = 0): Stroke =
        Stroke(listOf(px(x, density) to px(y, density), px(x, density) to px(y, density)), TAP_MS, displayId)

    fun longPress(x: Double, y: Double, seconds: Double, density: Float, displayId: Int = 0): Stroke =
        Stroke(listOf(px(x, density) to px(y, density), px(x, density) to px(y, density)), ms(seconds), displayId)

    fun drag(x1: Double, y1: Double, x2: Double, y2: Double, seconds: Double, density: Float, displayId: Int = 0): Stroke =
        Stroke(listOf(px(x1, density) to px(y1, density), px(x2, density) to px(y2, density)), ms(seconds), displayId)

    /** Content-space deltas, as [com.piyush.mode.ops.Op.Scroll] gives them: the finger moves by them. */
    fun scroll(x: Double, y: Double, dx: Double, dy: Double, density: Float, displayId: Int = 0): Stroke {
        val travel = (hypot(dx, dy) / SCROLL_DP_PER_S * 1000).roundToLong().coerceAtLeast(MIN_SCROLL_MS)
        return Stroke(listOf(px(x, density) to px(y, density), px(x + dx, density) to px(y + dy, density)), travel, displayId)
    }

    private fun px(dp: Double, density: Float): Float = (dp * density).toFloat()
    private fun ms(seconds: Double): Long = (seconds * 1000).roundToLong().coerceAtLeast(1)
}
