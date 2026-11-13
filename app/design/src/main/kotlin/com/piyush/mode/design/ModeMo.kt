package com.piyush.mode.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Dp

/** What Mo's face says Mode is doing. */
public enum class MoMood {
    /** Ready for a task. */
    Idle,
    /** Hearing you. */
    Listening,
    /** Working in your apps. */
    Working,
    /** Needs an answer or your hands. */
    YourTurn,
    /** Finished. */
    Done,
    /** Couldn't finish. */
    Stuck,
    /** Resting: nothing to do. */
    Sleep,
}

/**
 * Mo, Mode's character: a soft orange blob whose face says what Mode is doing. Drawn on the
 * canvas's 120-point grid, exactly as on the iPhone (ModeMo.swift), so it is sharp at any size.
 */
@Composable
public fun ModeMo(mood: MoMood = MoMood.Idle, size: Dp = ModeSize.moRow, modifier: Modifier = Modifier, tint: Color = Mode.colors.brand) {
    val c = Mode.colors
    Canvas(modifier.size(size).clearAndSetSemantics {}) {
        val k = this.size.minDimension / GRID
        scale(k, k, pivot = Offset.Zero) { drawMo(mood, tint, c) }
    }
}

private const val GRID = 120f

private fun DrawScope.drawMo(mood: MoMood, tint: Color, c: ModeColors) {
    val ink = c.moInk
    oval(60f, 113f, 30f, 4f, ink.copy(alpha = 0.12f))
    drawPath(path { move(60f, 12f); cubic(90f, 12f, 106f, 30f, 106f, 60f); cubic(106f, 92f, 88f, 108f, 60f, 108f); cubic(32f, 108f, 14f, 92f, 14f, 60f); cubic(14f, 30f, 30f, 12f, 60f, 12f); close() }, tint)
    rotate(-24f, pivot = Offset(42f, 32f)) { oval(42f, 32f, 12f, 6.5f, Color.White.copy(alpha = 0.38f)) }
    when (mood) {
        MoMood.Idle -> {
            oval(47f, 58f, 5f, 8f, ink); oval(73f, 58f, 5f, 8f, ink)
            line(ink, 4f) { move(52f, 76f); quad(60f, 83f, 68f, 76f) }
        }
        MoMood.Listening -> {
            oval(49f, 52f, 5f, 8f, ink); oval(75f, 52f, 5f, 8f, ink); oval(62f, 78f, 4f, 4f, ink)
            line(ink.copy(alpha = 0.45f), 3.5f) { move(110f, 46f); quad(117f, 60f, 110f, 74f) }
            line(ink.copy(alpha = 0.25f), 3.5f) { move(114f, 38f); quad(124f, 60f, 114f, 82f) }
        }
        MoMood.Working -> {
            line(ink, 5f) { move(39f, 60f); lineTo(54f, 57f); move(66f, 57f); lineTo(81f, 60f) }
            line(ink, 4f) { move(54f, 78f); lineTo(66f, 77f) }
            line(ink.copy(alpha = 0.35f), 3.5f) { move(2f, 46f); lineTo(9f, 46f); move(0f, 60f); lineTo(8f, 60f); move(2f, 74f); lineTo(9f, 74f) }
        }
        MoMood.YourTurn -> {
            oval(46f, 59f, 8f, 8f, ink); oval(74f, 59f, 8f, 8f, ink); oval(60f, 80f, 5f, 4f, ink)
            oval(49f, 56f, 2.6f, 2.6f, Color.White); oval(77f, 56f, 2.6f, 2.6f, Color.White)
            oval(100f, 22f, 13f, 13f, c.moBadge)
            drawCircle(Color.White, radius = 13f, center = Offset(100f, 22f), style = Stroke(width = 3f))
            line(Color.White, 3.5f) { move(100f, 15f); lineTo(100f, 23f) }
            oval(100f, 29f, 2f, 2f, Color.White)
        }
        MoMood.Done -> {
            line(ink, 5f) { move(40f, 61f); quad(47f, 51f, 54f, 61f); move(66f, 61f); quad(73f, 51f, 80f, 61f) }
            drawPath(path { move(47f, 73f); quad(60f, 89f, 73f, 73f); close() }, ink)
            drawPath(path { move(103f, 12f); lineTo(106f, 21f); lineTo(115f, 24f); lineTo(106f, 27f); lineTo(103f, 36f); lineTo(100f, 27f); lineTo(91f, 24f); lineTo(100f, 21f); close() }, c.moSparkle)
        }
        MoMood.Stuck -> {
            line(ink, 3.5f) { move(38f, 50f); lineTo(52f, 54f); move(68f, 54f); lineTo(82f, 50f) }
            oval(46f, 63f, 4.5f, 4.5f, ink); oval(74f, 63f, 4.5f, 4.5f, ink)
            line(ink, 4f) { move(49f, 81f); quad(54.5f, 76f, 60f, 81f); quad(65.5f, 86f, 71f, 81f) }
            drawPath(path { move(98f, 30f); cubic(98f, 30f, 91f, 40f, 91f, 45f); cubic(91f, 49f, 94f, 52f, 98f, 52f); cubic(102f, 52f, 105f, 49f, 105f, 45f); cubic(105f, 40f, 98f, 30f, 98f, 30f); close() }, c.moDrop)
        }
        MoMood.Sleep -> {
            line(ink, 4.5f) { move(39f, 60f); quad(46.5f, 67f, 54f, 60f); move(66f, 60f); quad(73.5f, 67f, 81f, 60f) }
            line(ink, 3.5f) { move(55f, 79f); quad(60f, 82f, 65f, 79f) }
            line(ink.copy(alpha = 0.55f), 3.5f) { move(92f, 14f); lineTo(104f, 14f); lineTo(92f, 27f); lineTo(104f, 27f) }
            line(ink.copy(alpha = 0.35f), 3f) { move(108f, 2f); lineTo(115f, 2f); lineTo(108f, 10f); lineTo(115f, 10f) }
        }
    }
}

private fun DrawScope.oval(cx: Float, cy: Float, rx: Float, ry: Float, color: Color) =
    drawOval(color, topLeft = Offset(cx - rx, cy - ry), size = Rect(0f, 0f, 2 * rx, 2 * ry).size)

private fun DrawScope.line(color: Color, width: Float, build: Path.() -> Unit) =
    drawPath(Path().apply(build), color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))

private fun path(build: Path.() -> Unit): Path = Path().apply(build)
private fun Path.move(x: Float, y: Float) = moveTo(x, y)
private fun Path.quad(cx: Float, cy: Float, x: Float, y: Float) = quadraticTo(cx, cy, x, y)
private fun Path.cubic(c1x: Float, c1y: Float, c2x: Float, c2y: Float, x: Float, y: Float) = cubicTo(c1x, c1y, c2x, c2y, x, y)
