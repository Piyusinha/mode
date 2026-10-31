@file:OptIn(ExperimentalForeignApi::class)

package com.piyush.mode.coredevice

import com.piyush.mode.ops.OpFailed
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.Vision.VNImageRequestHandler
import platform.Vision.VNRecognizeTextRequest
import platform.Vision.VNRecognizedText
import platform.Vision.VNRecognizedTextObservation
import platform.Vision.VNRequestTextRecognitionLevelAccurate

/**
 * On-device OCR with Apple's Vision framework. Nothing leaves the phone.
 * Runs synchronously, so call it off the main thread.
 */
class VisionTextReader(
    /** BCP-47 tags to prefer, in order; Vision detects the language when empty. */
    private val languages: List<String> = emptyList(),
) : TextReader {

    override fun read(png: ByteArray): List<RecognizedText> {
        val request = VNRecognizeTextRequest()
        request.recognitionLevel = VNRequestTextRecognitionLevelAccurate
        request.usesLanguageCorrection = true
        if (languages.isEmpty()) {
            request.automaticallyDetectsLanguage = true
        } else {
            request.recognitionLanguages = languages
        }

        val handler = VNImageRequestHandler(data = png.toNSData(), options = emptyMap<Any?, Any?>())
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
}
