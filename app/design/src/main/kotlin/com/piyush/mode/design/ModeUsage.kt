package com.piyush.mode.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** A provider's page head: its tile, name and state, and Mo (awake when it's the one in use). */
@Composable
public fun ModeProviderHeader(logo: ModeLogo, tileBackground: Color, tileForeground: Color, title: String, status: String, good: Boolean, modifier: Modifier = Modifier, mood: MoMood? = null) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.headerGap), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clearAndSetSemantics {}.size(ModeSize.providerTile).background(tileBackground, RoundedCornerShape(ModeSize.providerTileRadius)), Alignment.Center) {
            ModeLogoImage(logo, ModeSize.iconLarge, tileForeground)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs)) {
            ModeText(title, ModeTextStyle.Title, Modifier.semantics { heading() }, maxLines = 1)
            ModeStatusLine(status, if (good) Mode.colors.success else Mode.colors.ring)
        }
        if (mood != null) ModeMo(mood, ModeSize.moProvider)
    }
}

/** A week as bars, Monday first, today in Mo's orange, the day letters under them. */
@Composable
public fun ModeWeekBars(values: List<Int>, labels: List<String>, today: Int, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val top = maxOf(values.maxOrNull() ?: 0, 1)
    Row(modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs), verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, v ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs)) {
                Box(Modifier.fillMaxWidth().height(ModeSize.weekBars), Alignment.BottomCenter) {
                    Box(Modifier.fillMaxWidth().height(maxOf(ModeSpacing.xs, ModeSize.weekBars * v / top)).background(if (i == today) c.brand else c.track, RoundedCornerShape(ModeRadius.bar)))
                }
                ModeText(labels.getOrElse(i) { "" }, ModeTextStyle.Caption, color = c.textSecondary)
            }
        }
    }
}

/** How much of something is used: its name and value, a bar, and a line under it. */
@Composable
public fun ModeUsageBar(title: String, value: String, fraction: Double, tint: Color, footnote: String?, modifier: Modifier = Modifier, prominent: Boolean = false) {
    val c = Mode.colors
    Column(modifier.fillMaxWidth().padding(vertical = if (prominent) ModeSpacing.headerGap else ModeSpacing.m), verticalArrangement = Arrangement.spacedBy(if (prominent) ModeSpacing.s else ModeSpacing.xs + ModeSpacing.xxs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModeText(title, ModeTextStyle.SubheadlineEmphasis, Modifier.weight(1f))
            ModeText(value, if (prominent) ModeTextStyle.Title2 else ModeTextStyle.SubheadlineEmphasis)
        }
        Box(Modifier.fillMaxWidth().height(ModeSize.usageBar).background(c.track, Capsule)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0.0, 1.0).toFloat()).height(ModeSize.usageBar).background(tint, Capsule))
        }
        if (footnote != null) ModeText(footnote, ModeTextStyle.Footnote, color = c.textSecondary)
    }
}

/** How a wide button looks: white, raised grey, ink, or red words. */
public enum class ModeWideStyle { Card, Raised, Ink, Danger }

/** A full-width button on a settings page, with an optional icon after its words (an outside link). */
@Composable
public fun ModeWideButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, style: ModeWideStyle = ModeWideStyle.Card, trailingIcon: ModeIcon? = null) {
    val c = Mode.colors
    val ink = when (style) {
        ModeWideStyle.Ink -> c.onAccent
        ModeWideStyle.Danger -> c.danger
        else -> c.textPrimary
    }
    val fill = when (style) {
        ModeWideStyle.Card -> c.card
        ModeWideStyle.Raised -> c.cardRaised
        ModeWideStyle.Ink -> c.accent
        ModeWideStyle.Danger -> c.tokens.tileRed
    }
    val (press, alpha) = rememberPress()
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.wideButton).alpha(alpha).background(fill, Capsule)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick).padding(horizontal = ModeSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeText(title, ModeTextStyle.CalloutEmphasis, color = ink)
        if (trailingIcon != null) ModeIconImage(trailingIcon, ModeIconSize.small, ink)
    }
}
