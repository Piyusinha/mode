@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.piyush.mode.coredevice

import com.piyush.mode.ops.OpFailed
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** JPEG shrinking with UIKit. */
class UIKitImageTools(private val quality: Double = 0.7) : ImageTools {

    override fun shrinkToJpeg(png: ByteArray, maxSide: Int): ShrunkImage {
        val image = UIImage.imageWithData(png.toNSData()) ?: throw OpFailed("the screenshot is not an image")
        val (w, h) = image.size.useContents { width * image.scale to height * image.scale }
        val factor = min(1.0, maxSide / max(w, h))
        val outW = (w * factor).roundToInt().coerceAtLeast(1)
        val outH = (h * factor).roundToInt().coerceAtLeast(1)

        val format = UIGraphicsImageRendererFormat()
        format.scale = 1.0
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(outW.toDouble(), outH.toDouble()), format = format)
        val resized = renderer.imageWithActions { _ ->
            image.drawInRect(CGRectMake(0.0, 0.0, outW.toDouble(), outH.toDouble()))
        }
        val jpeg = UIImageJPEGRepresentation(resized, quality) ?: throw OpFailed("could not encode the screenshot as JPEG")
        return ShrunkImage(jpeg.toByteArray(), outW, outH)
    }
}

internal fun ByteArray.toNSData(): NSData =
    if (isEmpty()) NSData() else usePinned { NSData.create(bytes = it.addressOf(0), length = size.convert()) }

internal fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    val out = ByteArray(size)
    if (size > 0) out.usePinned { memcpy(it.addressOf(0), bytes, length) }
    return out
}
