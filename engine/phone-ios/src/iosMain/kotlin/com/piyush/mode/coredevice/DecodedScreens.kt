@file:OptIn(ExperimentalForeignApi::class)

package com.piyush.mode.coredevice

import com.piyush.mode.ops.OpFailed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Decodes each screenshot once. A look shrinks it for the model and reads
 * text off it; both start from the same multi-megabyte PNG, so the decoded
 * image is kept for the most recent one. Callers are serialised by
 * [CoreDeviceBackend].
 */
internal object DecodedScreens {
    private var lastPng: ByteArray? = null
    private var lastImage: UIImage? = null

    fun image(png: ByteArray): UIImage {
        if (png === lastPng) lastImage?.let { return it }
        val image = UIImage.imageWithData(png.toNSData()) ?: throw OpFailed("the screenshot is not an image")
        lastPng = png
        lastImage = image
        return image
    }

    /** [image] redrawn so its longer side is at most [maxSide] pixels (never enlarged). */
    fun scaled(image: UIImage, maxSide: Int): UIImage {
        val (w, h) = pixelSize(image)
        val factor = min(1.0, maxSide / max(w, h))
        if (factor >= 1.0) return image
        val outW = (w * factor).roundToInt().coerceAtLeast(1).toDouble()
        val outH = (h * factor).roundToInt().coerceAtLeast(1).toDouble()
        val format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        return UIGraphicsImageRenderer(size = CGSizeMake(outW, outH), format = format).imageWithActions { _ ->
            image.drawInRect(CGRectMake(0.0, 0.0, outW, outH))
        }
    }

    fun pixelSize(image: UIImage): Pair<Double, Double> =
        image.size.useContents { width * image.scale to height * image.scale }
}
