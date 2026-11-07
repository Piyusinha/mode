package com.piyush.mode.app.system

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import com.piyush.mode.feature.settings.data.ModelBench
import java.io.ByteArrayOutputStream
import kotlin.random.Random

/**
 * Test screenshots for Measure on plan models: an order screen with a number to read, as big as the
 * screenshots Mode sends. The same picture the iPhone draws; not UI, so its colours and sizes are plain.
 */
object ProbeImages {
    fun make(count: Int = 4): List<ModelBench.Probe> = List(count) {
        val number = Random.nextInt(10, 100)
        ModelBench.Probe(png(number), number.toString())
    }

    private fun png(number: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)
        val bold = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.DEFAULT_BOLD; color = INK }
        val plain = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK }
        val line = Paint().apply { color = LINES }
        canvas.drawText(TITLE, MARGIN, TITLE_Y, bold.apply { textSize = TITLE_SIZE })
        for (row in 0 until ROWS) canvas.drawRect(MARGIN, ROW_TOP + row * ROW_STEP, MARGIN + ROW_WIDTH - row * ROW_SHRINK, ROW_TOP + row * ROW_STEP + ROW_HEIGHT, line)
        canvas.drawText(LABEL, MARGIN, LABEL_Y, plain.apply { textSize = LABEL_SIZE })
        canvas.drawText("$number $UNIT", MARGIN, NUMBER_Y, bold.apply { textSize = NUMBER_SIZE }) // literal-ok: a synthetic test image, not UI
        return ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, QUALITY, it) }.toByteArray()
    }

    private const val WIDTH = 460
    private const val HEIGHT = 1000
    private const val BACKGROUND = 0xFFF5F5F5.toInt()
    private const val INK = 0xFF000000.toInt()
    private const val LINES = 0xFFD9D9D9.toInt()
    private const val MARGIN = 32f
    private const val TITLE = "Order status"
    private const val TITLE_Y = 124f
    private const val TITLE_SIZE = 34f
    private const val ROWS = 6
    private const val ROW_TOP = 180f
    private const val ROW_STEP = 70f
    private const val ROW_WIDTH = 300f
    private const val ROW_SHRINK = 25f
    private const val ROW_HEIGHT = 18f
    private const val LABEL = "Arriving in"
    private const val LABEL_Y = 662f
    private const val LABEL_SIZE = 22f
    private const val UNIT = "min"
    private const val NUMBER_Y = 736f
    private const val NUMBER_SIZE = 56f
    private const val QUALITY = 100
}
