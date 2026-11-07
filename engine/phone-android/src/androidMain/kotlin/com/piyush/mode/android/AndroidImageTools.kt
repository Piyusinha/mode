package com.piyush.mode.android

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.piyush.mode.screen.ImageTools
import com.piyush.mode.screen.ShrunkImage
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * JPEG for the model. The service keeps the bitmap of its last screenshot beside
 * the PNG it handed out, so shrinking that screenshot skips decoding it again.
 */
class AndroidImageTools(private val lastBitmap: (ByteArray) -> Bitmap? = { null }) : ImageTools {
    override fun shrinkToJpeg(png: ByteArray, maxSide: Int): ShrunkImage {
        val source = lastBitmap(png) ?: BitmapFactory.decodeByteArray(png, 0, png.size)
            ?: error("could not read the screenshot")
        val scale = maxSide.toFloat() / max(source.width, source.height)
        val small = if (scale < 1f) {
            Bitmap.createScaledBitmap(source, (source.width * scale).roundToInt(), (source.height * scale).roundToInt(), true)
        } else source
        val out = ByteArrayOutputStream()
        small.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        return ShrunkImage(out.toByteArray(), small.width, small.height)
    }

    private companion object {
        /** The same quality the iPhone sends (0.7). */
        const val JPEG_QUALITY = 70
    }
}
