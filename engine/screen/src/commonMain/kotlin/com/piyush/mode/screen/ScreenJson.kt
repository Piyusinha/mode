package com.piyush.mode.screen

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.round

/**
 * The answers every phone engine gives, in the one shape the agent reads
 * (`engine:agent` `look()` and `screenBoxes()`), whichever phone produced them.
 */
@OptIn(ExperimentalEncodingApi::class)
object ScreenJson {
    /** `screen.bounds`: the whole screen in points, with the window id the old tree path had. */
    fun bounds(screen: ScreenInfo): JsonObject = buildJsonObject {
        put("x", 0)
        put("y", 0)
        put("w", screen.widthPt)
        put("h", screen.heightPt)
        put("id", 0)
    }

    /** `screen.capture` without a size limit: the full PNG. */
    fun capturePng(png: ByteArray, screen: ScreenInfo): JsonObject = buildJsonObject {
        put("png_b64", Base64.encode(png))
        put("bounds", bounds(screen))
    }

    /** `screen.capture` with a size limit: a JPEG for the model, with its pixel size. */
    fun captureJpeg(small: ShrunkImage, screen: ScreenInfo): JsonObject = buildJsonObject {
        put("jpeg_b64", Base64.encode(small.jpeg))
        put("bounds", bounds(screen))
        put("img_px", buildJsonObject {
            put("w", small.widthPx)
            put("h", small.heightPx)
        })
    }

    /**
     * `screen.capture` for a screen the phone will not show (a bank, a PIN pad):
     * no picture, so the agent hands the step to the user.
     */
    fun capturePrivate(screen: ScreenInfo): JsonObject = buildJsonObject {
        put("private", true)
        put("bounds", bounds(screen))
    }

    /**
     * One `screen.text` box: [x], [y] the centre in points, ready to tap; [source]
     * "ocr" or "tree"; [window] "app", "ime" or "system" when the phone knows.
     */
    fun textBox(
        text: String,
        confidence: Double,
        source: String,
        x: Double,
        y: Double,
        w: Double,
        h: Double,
        window: String? = null,
    ): JsonObject = buildJsonObject {
        put("text", text)
        put("confidence", confidence)
        put("source", source)
        put("x", round(x))
        put("y", round(y))
        put("w", round(w))
        put("h", round(h))
        if (window != null) put("window", window)
    }

    /** An OCR result, from image fractions to screen points. */
    fun textBox(text: RecognizedText, screen: ScreenInfo): JsonElement = textBox(
        text = text.text,
        confidence = text.confidence,
        source = "ocr",
        x = (text.left + text.width / 2) * screen.widthPt,
        y = (text.top + text.height / 2) * screen.heightPt,
        w = text.width * screen.widthPt,
        h = text.height * screen.heightPt,
    )
}
