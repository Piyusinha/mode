package com.piyush.mode.coredevice

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

data class InstalledApp(val name: String, val bundleId: String)

/** The screen in points, the space ops are given in. */
data class ScreenInfo(val widthPt: Double, val heightPt: Double) {
    init {
        require(widthPt > 0 && heightPt > 0) { "screen size must be positive" }
    }

    /** A point on screen in the touchscreen's 0..65535 space, clamped to the screen. */
    fun toTouch(x: Double, y: Double): Pair<Int, Int> = axis(x, widthPt) to axis(y, heightPt)

    private fun axis(v: Double, extent: Double): Int =
        (v / extent * MAX).let { if (it.isNaN()) 0.0 else it }.coerceIn(0.0, MAX).roundToIntExact()

    private fun Double.roundToIntExact(): Int = kotlin.math.round(this).toInt()

    companion object {
        const val MAX = 65535.0
    }
}

/** Reads text off a screenshot. */
fun interface TextReader {
    fun read(png: ByteArray): List<RecognizedText>
}

/**
 * One piece of text and where it is, as fractions of the image (0..1), top-left
 * origin, so it maps onto any screen size.
 */
data class RecognizedText(
    val text: String,
    val confidence: Double,
    val left: Double,
    val top: Double,
    val width: Double,
    val height: Double,
)

/** Image work the phone does natively. */
fun interface ImageTools {
    /** Re-encodes a PNG as JPEG with its longer side at most [maxSide] pixels. */
    fun shrinkToJpeg(png: ByteArray, maxSide: Int): ShrunkImage
}

class ShrunkImage(val jpeg: ByteArray, val widthPx: Int, val heightPx: Int)
