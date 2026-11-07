package com.piyush.mode.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.toUpperCase

/** What went wrong's title, a line under it, and Mo beside them. */
@Composable
public fun ModeDebugHeader(title: String, detail: String?, mood: MoMood?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = ModeSpacing.s), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.chatGap), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            ModeText(title, ModeTextStyle.Title, Modifier.semantics { heading() })
            if (detail != null) ModeText(detail, ModeTextStyle.Callout, color = Mode.colors.textSecondary)
        }
        if (mood != null) Box(Modifier.clearAndSetSemantics {}) { ModeMo(mood, ModeSize.moDebugHeader) }
    }
}

/** How a finding reads: a problem (red), plain, fine (a tick), or repaired (green). */
public enum class ModeDebugTone { Problem, Neutral, Ok, Repaired }

/** One finding: its tile, what it is, and a line under it. */
@Composable
public fun ModeDebugRow(title: String, detail: String, icon: ModeIcon, tone: ModeDebugTone, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val (tileFill, tileInk) = when (tone) {
        ModeDebugTone.Problem -> c.tokens.tileRed to c.danger
        ModeDebugTone.Repaired -> c.tokens.tagGreen to c.success
        else -> c.cardRaised to c.textPrimary
    }
    val detailColor = when (tone) {
        ModeDebugTone.Problem -> c.danger
        ModeDebugTone.Repaired -> c.success
        else -> c.textSecondary
    }
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.debugRow).padding(vertical = ModeSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clearAndSetSemantics {}.size(ModeSize.chatTileSize).background(tileFill, RoundedCornerShape(ModeRadius.chatTile)), Alignment.Center) {
            ModeIconImage(icon, ModeIconSize.small, tileInk)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            ModeText(title, ModeTextStyle.Callout)
            if (detail.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs)) {
                    if (tone == ModeDebugTone.Problem) ModeIconImage(ModeIcon.Warning, ModeIconSize.chevron, c.danger, Modifier.padding(top = ModeSpacing.xxs))
                    ModeText(detail, ModeTextStyle.Footnote, color = detailColor)
                }
            }
        }
        if (tone == ModeDebugTone.Ok || tone == ModeDebugTone.Repaired) {
            Box(Modifier.size(ModeSize.debugCheck).background(c.tokens.tagGreen, CircleShape), Alignment.Center) {
                ModeIconImage(ModeIcon.Checkmark, ModeIconSize.chevron, c.success)
            }
        }
    }
}

/** Mode's answer in Ask about it: Mo beside a white bubble, with "Answered with …" over it. */
@Composable
public fun ModeAnswerBubble(label: String?, mood: MoMood, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.bubbleTail, ModeRadius.bubble, ModeRadius.bubble, ModeRadius.bubble)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.chatInline)) {
        Box(Modifier.clearAndSetSemantics {}) { ModeMo(mood, ModeSize.moAnswer) }
        Column(
            Modifier.weight(1f).modeShadow(shape).background(c.card, shape).padding(horizontal = ModeSpacing.l, vertical = ModeSpacing.chatGap),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        ) {
            if (label != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.chatTight), verticalAlignment = Alignment.CenterVertically) {
                    ModeIconImage(ModeIcon.Sparkle, ModeIconSize.chevron, c.tint)
                    ModeText(label.toUpperCase(LocaleList.current), ModeTextStyle.CaptionEmphasis, color = c.tint)
                }
            }
            content()
        }
    }
}

/** A question to tap, in soft blue. */
@Composable
public fun ModeSuggestionChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Box(
        modifier.defaultMinSize(minHeight = ModeSize.askChip).alpha(alpha).enabledAlpha(enabled).background(c.infoSoft, Capsule)
            .clickable(press, indication = null, enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = ModeSpacing.m + ModeSpacing.xxs),
        Alignment.Center,
    ) { ModeText(text, ModeTextStyle.SubheadlineEmphasis, color = c.infoText, maxLines = 1) }
}

/** A plain box with a send button (Ask about it). */
@Composable
public fun ModeAskBar(text: String, onText: (String) -> Unit, prompt: String, send: String, canSend: Boolean, onSend: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeSize.composerHeight / 2)
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.composerHeight).modeShadow(shape).background(c.card, shape)
            .padding(start = ModeSpacing.l + ModeSpacing.xxs, end = ModeSpacing.xs, top = ModeSpacing.xs, bottom = ModeSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        verticalAlignment = Alignment.Bottom,
    ) {
        Box(Modifier.weight(1f).defaultMinSize(minHeight = ModeSize.askSend), Alignment.CenterStart) {
            if (text.isEmpty()) ModeText(prompt, ModeTextStyle.Callout, color = c.textSecondary, maxLines = 1)
            BasicTextField(
                text, onText, Modifier.fillMaxWidth().semantics { contentDescription = prompt }, textStyle = ModeTextStyle.Callout.style(c.textPrimary),
                maxLines = ASK_LINES, cursorBrush = SolidColor(c.tint), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
            )
        }
        val (press, alpha) = rememberPress()
        Box(
            Modifier.size(ModeSize.askSend).alpha(alpha).enabledAlpha(canSend).background(c.accent, CircleShape)
                .clickable(press, indication = null, enabled = canSend, role = Role.Button, onClick = onSend).semantics { contentDescription = send },
            Alignment.Center,
        ) { ModeIconImage(ModeIcon.Send, ModeIconSize.regular, c.onAccent) }
    }
}

private const val ASK_LINES = 5
