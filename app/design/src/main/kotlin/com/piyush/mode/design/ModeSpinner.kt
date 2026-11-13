package com.piyush.mode.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Working on it: a turning arc in Mo's orange. */
@Composable
public fun ModeSpinner(modifier: Modifier = Modifier, size: Dp = ModeIconSize.large, color: Color = Mode.colors.brand) {
    val turn by rememberInfiniteTransition(label = "spinner").animateFloat(
        0f, 360f, infiniteRepeatable(tween(SPIN_MS, easing = LinearEasing), RepeatMode.Restart), label = "turn",
    )
    Canvas(modifier.size(size)) {
        val stroke = STROKE.toPx()
        rotate(turn) {
            drawArc(color, 0f, SWEEP, false, style = Stroke(stroke, cap = StrokeCap.Round),
                topLeft = androidx.compose.ui.geometry.Offset(stroke / 2, stroke / 2),
                size = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke))
        }
    }
}

private const val SPIN_MS = 900
private const val SWEEP = 270f
private val STROKE = 2.5.dp
