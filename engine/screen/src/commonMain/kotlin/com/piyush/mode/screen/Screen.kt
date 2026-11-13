package com.piyush.mode.screen

/**
 * The screen in points (iOS points, Android dp): the space every op is given
 * in, with a top-left origin.
 */
data class ScreenInfo(val widthPt: Double, val heightPt: Double) {
    init {
        require(widthPt > 0 && heightPt > 0) { "screen size must be positive" }
    }
}

/** An app the phone can open, by the name the user knows and the id the system uses. */
data class InstalledApp(val name: String, val bundleId: String)

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
