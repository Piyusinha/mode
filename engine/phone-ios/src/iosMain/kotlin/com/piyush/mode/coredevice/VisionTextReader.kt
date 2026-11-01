@file:OptIn(ExperimentalForeignApi::class)

package com.piyush.mode.coredevice

import com.piyush.mode.ops.OpFailed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGSizeMake
import platform.UIKit.UIGraphicsImageRenderer
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/**
 * On-device OCR with Apple's Vision framework. Nothing leaves the phone.
 * Runs synchronously, so call it off the main thread.
 *
 * @param maxSide the screenshot is shrunk to this many pixels on its longer
 *   side first: a phone screen's text stays readable at half resolution and
 *   recognition gets several times faster.
 */
class VisionTextReader(
    /** BCP-47 tags to prefer, in order; Vision detects the language when empty. */
    private val languages: List<String> = emptyList(),
    private val maxSide: Int = 1400,
) : TextReader {

    override fun read(png: ByteArray): List<RecognizedText> {
        val image = DecodedScreens.scaled(DecodedScreens.image(png), maxSide)
        val cgImage = image.CGImage ?: throw OpFailed("the screenshot has no bitmap")
        val request = newRequest()
        val handler = VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any?>())
        if (!handler.performRequests(listOf(request), error = null)) {
            throw OpFailed("could not read text off the screenshot")
        }

        return request.results.orEmpty().mapNotNull { result ->
            val observation = result as? VNRecognizedTextObservation ?: return@mapNotNull null
            val best = observation.topCandidates(1u).firstOrNull() as? VNRecognizedText ?: return@mapNotNull null
            // Vision's boxes are fractions of the image with a bottom-left origin.
            observation.boundingBox.useContents {
                RecognizedText(
                    text = best.string,
                    confidence = best.confidence.toDouble(),
                    left = origin.x,
                    top = 1.0 - origin.y - size.height,
                    width = size.width,
                    height = size.height,
                )
            }
        }
    }

    /**
     * Loads Vision's text models now, so the first real look is not the slow
     * one. Takes a second or two; call it off the main thread after connecting.
     */
    fun warmUp() {
        val blank = UIGraphicsImageRenderer(size = CGSizeMake(64.0, 64.0)).imageWithActions { _ -> }
        val cgImage = blank.CGImage ?: return
        VNImageRequestHandler(cGImage = cgImage, options = emptyMap<Any?, Any?>())
            .performRequests(listOf(newRequest()), error = null)
    }

    private fun newRequest() = VNRecognizeTextRequest().apply {
        recognitionLevel = VNRequestTextRecognitionLevelAccurate
        usesLanguageCorrection = true
        if (languages.isEmpty()) automaticallyDetectsLanguage = true else recognitionLanguages = languages
    }
}
