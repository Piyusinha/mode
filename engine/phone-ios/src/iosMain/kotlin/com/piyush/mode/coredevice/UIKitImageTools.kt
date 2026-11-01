@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.piyush.mode.coredevice

import com.piyush.mode.ops.OpFailed
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy
import kotlin.math.roundToInt

/** JPEG shrinking with UIKit. */
class UIKitImageTools(private val quality: Double = 0.7) : ImageTools {

    override fun shrinkToJpeg(png: ByteArray, maxSide: Int): ShrunkImage {
        val resized = DecodedScreens.scaled(DecodedScreens.image(png), maxSide)
        val (w, h) = DecodedScreens.pixelSize(resized)
        val jpeg = UIImageJPEGRepresentation(resized, quality) ?: throw OpFailed("could not encode the screenshot as JPEG")
        return ShrunkImage(jpeg.toByteArray(), w.roundToInt(), h.roundToInt())
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
