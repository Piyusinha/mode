package com.piyush.mode.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/** An icon in a colour, at a size; decorative unless it has a [label]. */
@Composable
public fun ModeIconImage(icon: ModeIcon, size: Dp = ModeIconSize.regular, tint: Color = Mode.colors.textPrimary, modifier: Modifier = Modifier, label: String? = null) {
    Image(painterResource(icon.res), label, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** A provider's or maker's logo, tinted. */
@Composable
public fun ModeLogoImage(logo: ModeLogo, size: Dp = ModeIconSize.regular, tint: Color = Mode.colors.textPrimary, modifier: Modifier = Modifier) {
    Image(painterResource(logo.res), null, modifier.size(size), colorFilter = ColorFilter.tint(tint))
}

/** Mode's page behind a screen: warm paper (light) or near-black (dark), no glows. */
public fun Modifier.modeScreen(colors: ModeColors): Modifier = background(colors.page)

/**
 * The soft lift under something floating: a shadow in light, a hairline ring in dark (as on the canvas).
 * [lifted] false keeps only the dark hairline (a flat card on a list page).
 */
@Composable
public fun Modifier.modeShadow(shape: Shape, lifted: Boolean = true): Modifier {
    val c = Mode.colors
    return if (c.dark) border(ModeSize.separator, Color.White.copy(alpha = DARK_RING), shape)
    else if (lifted) shadow(ModeShadow.elevation, shape, ambientColor = c.moInk.copy(alpha = ModeShadow.opacity), spotColor = c.moInk.copy(alpha = ModeShadow.opacity))
    else this
}

private const val DARK_RING = 0.08f

/** The page fading out over content scrolling under a top or bottom bar: 97% → 60% → clear. */
@Composable
public fun ModeEdgeFade(top: Boolean, modifier: Modifier = Modifier) {
    val page = Mode.colors.page
    val stops = arrayOf(0f to page.copy(alpha = 0.97f), 0.55f to page.copy(alpha = 0.6f), 1f to page.copy(alpha = 0f))
    Box(
        modifier.fillMaxWidth().height(if (top) ModeSize.edgeFadeTop else ModeSize.edgeFadeBottom)
            .background(if (top) Brush.verticalGradient(*stops) else Brush.verticalGradient(*stops.reversedArray())),
    )
}

/** A heading above a group of rows: Bricolage, 18 bold. */
@Composable
public fun ModeSectionHeader(title: String, modifier: Modifier = Modifier) {
    ModeText(
        title, ModeTextStyle.Section,
        modifier.fillMaxWidth().padding(start = ModeSpacing.xs, top = ModeSpacing.m).semantics { heading() },
    )
}

/** Dims what it wraps a little while it is held, the way Mode's buttons respond. */
@Composable
internal fun rememberPress(): Pair<MutableInteractionSource, Float> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    return source to if (pressed) PRESSED else 1f
}

internal const val PRESSED = 0.7f

internal val Capsule = RoundedCornerShape(percent = 50)

/** Disabled controls fade, as on the iPhone. */
internal fun Modifier.enabledAlpha(enabled: Boolean): Modifier = alpha(if (enabled) 1f else ModeOpacity.disabled)
