package com.piyush.mode.coredevice

import com.piyush.mode.screen.InstalledApp
import com.piyush.mode.screen.ScreenInfo

/**
 * One live CoreDevice connection to the phone: what `native/modecore` can do.
 *
 * Calls block until the phone has the input, and throw
 * [com.piyush.mode.ops.OpFailed] when it refuses. Coordinates are the HID
 * touchscreen's own space, 0..65535 across the screen, top-left origin.
 * Implementations are not thread-safe; [CoreDeviceBackend] serialises calls.
 */
interface DeviceSession : AutoCloseable {
    /** Finger down at (x1, y1), held [holdMs], moved to (x2, y2) over [travelMs], lifted. */
    fun touch(x1: Int, y1: Int, x2: Int, y2: Int, holdMs: Long, travelMs: Long)

    fun button(button: HardwareButton)

    /** A named key from [Keys.NAMED]. */
    fun key(name: String)

    /** Types text the hardware keyboard can produce ([Keys.typeable]). */
    fun typeAscii(text: String)

    /** Puts [text] on the clipboard and pastes it into the focused field. */
    fun pasteText(text: String)

    /** The screen as a full-resolution PNG. */
    fun screenshotPng(): ByteArray

    /** [fresh]: close a running copy first, so the app starts on its home page. */
    fun launch(bundleId: String, fresh: Boolean = false)

    fun listApps(): List<InstalledApp>

    /** Stops the screen stream and keeps this connection. */
    fun pauseStream() {}

    /** Starts the screen stream again on this connection. */
    fun resumeStream() {}
}

enum class HardwareButton { Home, Lock, VolumeUp, VolumeDown }

/** A point on screen in the touchscreen's 0..65535 space, clamped to the screen. */
fun ScreenInfo.toTouch(x: Double, y: Double): Pair<Int, Int> = axis(x, widthPt) to axis(y, heightPt)

private fun axis(v: Double, extent: Double): Int =
    (v / extent * TOUCH_MAX).let { if (it.isNaN()) 0.0 else it }.coerceIn(0.0, TOUCH_MAX).let { kotlin.math.round(it).toInt() }

/** The HID touchscreen's range. */
const val TOUCH_MAX = 65535.0
