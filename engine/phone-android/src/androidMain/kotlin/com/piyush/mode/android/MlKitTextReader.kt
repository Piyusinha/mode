package com.piyush.mode.android

import android.graphics.BitmapFactory
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.screen.RecognizedText
import com.piyush.mode.screen.TextReader
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Reads text from a screenshot on the phone, for screens phone control can't read (games, maps, canvases,
 * some web views). Google's on-device text recognition, Latin script, plus Devanagari when the phone's language
 * is written in it. Lines, with where they are as fractions of the image.
 */
class MlKitTextReader(private val language: () -> Locale = { Locale.getDefault() }) : TextReader {
    private val latin: TextRecognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val devanagari: TextRecognizer by lazy { TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build()) }

    override fun read(png: ByteArray): List<RecognizedText> {
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return emptyList()
        val image = InputImage.fromBitmap(bitmap, 0)
        val readers = listOf(latin) + if (language().language in DEVANAGARI) listOf(devanagari) else emptyList()
        val w = bitmap.width.toDouble()
        val h = bitmap.height.toDouble()
        return readers.flatMap { reader ->
            val text = runCatching { Tasks.await(reader.process(image), TIMEOUT_S, TimeUnit.SECONDS) }
                .onFailure { AppLog.log("phone", "text recognition failed: ${it::class.simpleName}") }
                .getOrNull() ?: return@flatMap emptyList()
            text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                RecognizedText(
                    line.text, (line.confidence.takeIf { it > 0f } ?: DEFAULT_CONFIDENCE).toDouble(),
                    box.left / w, box.top / h, box.width() / w, box.height() / h,
                )
            }
        }.distinctBy { it.text to (it.top * ROUND).toInt() }
    }

    private companion object {
        /** Hindi, Marathi, Nepali, Sanskrit, Konkani, Maithili. */
        val DEVANAGARI = setOf("hi", "mr", "ne", "sa", "kok", "mai")
        const val TIMEOUT_S = 5L
        /** ML Kit gives no confidence for some lines; they still count. */
        const val DEFAULT_CONFIDENCE = 0.8f
        const val ROUND = 50
    }
}
