package com.piyush.mode.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.toUpperCase
import androidx.compose.ui.text.intl.LocaleList

/** A tip card's colours: its background, its words, its badge. */
public enum class ModeTipTone { Coral, Blue, Green, Purple, Orange }

private class ToneColors(val background: Color, val text: Color, val badge: Color)

@Composable
private fun ModeTipTone.colors(): ToneColors {
    val t = Mode.colors.tokens
    return when (this) {
        ModeTipTone.Coral -> ToneColors(t.tipCoral, t.tipCoralText, t.tipCoralBadge)
        ModeTipTone.Blue -> ToneColors(t.tipBlue, t.tipBlueText, t.tipBlueBadge)
        ModeTipTone.Green -> ToneColors(t.tipGreen, t.tipGreenText, t.tipGreenBadge)
        ModeTipTone.Purple -> ToneColors(t.tipPurple, t.tipPurpleText, t.tipPurpleBadge)
        ModeTipTone.Orange -> ToneColors(t.tipOrange, t.tipOrangeText, t.tipOrangeBadge)
    }
}

/** One tip: its words, Mo's face, the badge on Mo and its colour. */
public data class ModeTip(val title: String, val message: String, val action: String, val mood: MoMood, val icon: ModeIcon, val tone: ModeTipTone)

/**
 * "Did you know?" on Home: one tinted card per tip, swiped sideways, with its action, dots to pick
 * another, and a close button when [closable]. [index] is the tip showing; a swipe calls [onPick].
 */
@Composable
public fun ModeTipBanner(
    label: String,
    tips: List<ModeTip>,
    index: Int,
    closable: Boolean,
    closeLabel: String,
    dotLabel: (Int) -> String,
    onAction: (Int) -> Unit,
    onClose: () -> Unit,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pager = rememberPagerState(initialPage = index) { tips.size }
    LaunchedEffect(index) { if (pager.currentPage != index && index in tips.indices) pager.animateScrollToPage(index) }
    LaunchedEffect(pager) {
        snapshotFlow { pager.settledPage }.collect { page -> if (page != index) onPick(page) }
    }
    HorizontalPager(
        state = pager,
        modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(ModeRadius.tipBanner)),
        pageSpacing = ModeSpacing.m,
    ) { i ->
        TipCard(label, tips[i], i, tips.size, closable, closeLabel, dotLabel, onAction, onClose, onPick)
    }
}

@Composable
private fun TipCard(
    label: String,
    tip: ModeTip,
    i: Int,
    count: Int,
    closable: Boolean,
    closeLabel: String,
    dotLabel: (Int) -> String,
    onAction: (Int) -> Unit,
    onClose: () -> Unit,
    onPick: (Int) -> Unit,
) {
    val c = Mode.colors
    val tone = tip.tone.colors()
    Column(
        Modifier.fillMaxWidth().background(tone.background, RoundedCornerShape(ModeRadius.tipBanner))
            .padding(top = ModeSpacing.s + ModeSpacing.xxs, start = ModeSpacing.m + ModeSpacing.xxs, end = ModeSpacing.m + ModeSpacing.xxs, bottom = ModeSpacing.m),
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs),
    ) {
        Row(Modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.tipClose), verticalAlignment = Alignment.CenterVertically) {
            ModeText(label.toUpperCase(LocaleList.current), ModeTextStyle.CaptionEmphasis, Modifier.weight(1f), color = tone.text)
            if (closable) {
                val (press, alpha) = rememberPress()
                Box(
                    Modifier.offset(x = ModeSpacing.s).size(ModeSize.touch).alpha(alpha)
                        .clickable(press, indication = null, role = Role.Button, onClick = onClose).semantics { contentDescription = closeLabel },
                    Alignment.Center,
                ) {
                    Box(Modifier.size(ModeSize.tipClose).background(c.textPrimary.copy(alpha = ModeOpacity.tipChip), CircleShape), Alignment.Center) {
                        ModeIconImage(ModeIcon.Close, ModeIconSize.tiny, tone.text)
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.Top) {
            Box(Modifier.clearAndSetSemantics {}) {
                ModeMo(tip.mood, ModeSize.tipMo)
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(ModeSize.tipBadgeOffset, ModeSpacing.xs).size(ModeSize.tipBadge)
                        .background(tone.badge, CircleShape).border(ModeSize.tipBadgeRing, tone.background, CircleShape),
                    Alignment.Center,
                ) { ModeIconImage(tip.icon, ModeIconSize.tiny, c.onAccent) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                ModeText(tip.title, ModeTextStyle.TipTitle, Modifier.semantics { heading() })
                ModeText(tip.message, ModeTextStyle.Footnote, color = c.textSecondary)
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val (press, alpha) = rememberPress()
            Row(
                Modifier.defaultMinSize(minHeight = ModeSize.tipButton).alpha(alpha).background(c.accent, Capsule)
                    .clickable(press, indication = null, role = Role.Button) { onAction(i) }
                    .padding(horizontal = ModeSpacing.m + ModeSpacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeText(tip.action, ModeTextStyle.SmallEmphasis, color = c.onAccent, maxLines = 1)
                ModeIconImage(ModeIcon.ArrowRight, ModeIconSize.tiny, c.onAccent)
            }
            Box(Modifier.weight(1f))
            if (count > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                    for (d in 0 until count) {
                        val current = d == i
                        Box(
                            Modifier.size(if (current) ModeSize.tipDotHitCurrent else ModeSize.tipDotHit, ModeSize.tipDotHitHeight)
                                .clickable(role = Role.Button, indication = null, interactionSource = null) { onPick(d) }
                                .semantics { contentDescription = dotLabel(d); selected = current },
                            Alignment.Center,
                        ) {
                            Box(
                                Modifier.size(if (current) ModeSize.tipDotCurrent else ModeSize.tipDot, ModeSize.tipDot)
                                    .background(if (current) tone.text else c.textPrimary.copy(alpha = ModeOpacity.tipDotOff), Capsule),
                            )
                        }
                    }
                }
            }
        }
    }
}
