package com.piyush.mode.design

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction

/**
 * Where setup is: done steps and the current one (wider) in Mo's orange, the rest on the track,
 * as on the phone-setup board. [current] counts from 1; [label] is read by TalkBack ("Step 2 of 4").
 */
@Composable
public fun ModeStepDots(current: Int, total: Int, label: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(
        modifier.clearAndSetSemantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s - ModeSpacing.xxs),
    ) {
        for (step in 1..maxOf(total, 1)) {
            Box(
                Modifier.animateContentSize()
                    .size(if (step == current) ModeSize.stepDotCurrent else ModeSize.stepDot, ModeSize.stepDotHeight)
                    .background(if (step <= current) c.tint else c.track, Capsule),
            )
        }
    }
}

/** The top of a setup page: a white round back button on the left, the step dots in the middle. */
@Composable
public fun ModeSetupHeader(backLabel: String, onBack: (() -> Unit)?, modifier: Modifier = Modifier, step: Triple<Int, Int, String>? = null) {
    Box(modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.touch).padding(horizontal = ModeSpacing.screenEdge)) {
        if (step != null) ModeStepDots(step.first, step.second, step.third, Modifier.align(Alignment.Center))
        if (onBack != null) ModeBackButton(backLabel, onBack, Modifier.align(Alignment.CenterStart))
    }
}

/** A first job to tap: a white capsule with the task and an arrow. */
@Composable
public fun ModeJobButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.jobButton).alpha(alpha).enabledAlpha(enabled)
            .modeShadow(Capsule).background(c.card, Capsule)
            .clickable(press, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = ModeSpacing.xl),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeText(title, ModeTextStyle.CalloutEmphasis, Modifier.weight(1f), maxLines = 1)
        ModeIconImage(ModeIcon.ArrowRight, ModeIconSize.small, c.textSecondary)
    }
}

/** Your own first job: a field shaped like the job buttons, with a send arrow once there are words. */
@Composable
public fun ModeJobField(
    text: String,
    onText: (String) -> Unit,
    prompt: String,
    sendLabel: String,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val c = Mode.colors
    val hasWords = text.isNotBlank()
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.jobButton).enabledAlpha(enabled)
            .modeShadow(Capsule).background(c.card, Capsule)
            .padding(start = ModeSpacing.xl, end = ModeSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) ModeText(prompt, ModeTextStyle.CalloutEmphasis, color = c.textSecondary, maxLines = 1)
            BasicTextField(
                value = text,
                onValueChange = onText,
                enabled = enabled,
                singleLine = true,
                textStyle = ModeTextStyle.CalloutEmphasis.style(c.textPrimary),
                cursorBrush = SolidColor(c.tint),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (hasWords) onSend() }),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = prompt },
            )
        }
        if (hasWords) {
            val (press, alpha) = rememberPress()
            Box(
                Modifier.size(ModeSize.jobButton - ModeSpacing.m).alpha(alpha).background(c.accent, CircleShape)
                    .clickable(press, indication = null, role = Role.Button, onClick = onSend)
                    .semantics { contentDescription = sendLabel },
                contentAlignment = Alignment.Center,
            ) { ModeIconImage(ModeIcon.ArrowRight, ModeIconSize.small, c.onAccent) }
        }
    }
}

/**
 * A provider to connect: its mark, its name with a tag, a line, and what to do (the first run's card).
 * [warning]: something stands in the way (the plan can't be used): an orange card with a warning mark.
 */
@Composable
public fun ModeProviderCard(
    logo: ModeLogo,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
    tag: String? = null,
    warning: Boolean = false,
    actions: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.firstRunCard)
    Column(
        modifier.fillMaxWidth().modeShadow(shape).background(if (warning) c.tokens.tileOrange else c.card, shape).padding(ModeSpacing.l),
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.groupInset),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(ModeSize.providerTile).background(c.cardRaised, RoundedCornerShape(ModeRadius.chip)), Alignment.Center) {
                if (warning) ModeIconImage(ModeIcon.Warning, ModeIconSize.regular, c.tokens.tileOrangeText)
                else ModeLogoImage(logo, ModeIconSize.large, c.textPrimary)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s), verticalAlignment = Alignment.CenterVertically) {
                    ModeText(title, ModeTextStyle.Headline)
                    if (tag != null) ModeTag(tag)
                }
                ModeText(detail, ModeTextStyle.Subheadline, color = c.textSecondary)
            }
        }
        actions()
    }
}

/** A coloured tag beside a name ("Recommended"). */
@Composable
public fun ModeTag(text: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    ModeText(
        text, ModeTextStyle.CaptionEmphasis,
        modifier.background(c.tintSoft, Capsule).padding(horizontal = ModeSpacing.s, vertical = ModeSpacing.xxs),
        color = c.tint,
    )
}

/** A row that opens more choices, on a raised card ("More options"). */
@Composable
public fun ModeMoreRow(title: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.moreOptions).alpha(alpha)
            .background(c.cardRaised, RoundedCornerShape(ModeRadius.firstRunCard))
            .clickable(press, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = ModeSpacing.l + ModeSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeText(title, ModeTextStyle.CalloutEmphasis, Modifier.weight(1f))
        ModeChevron()
    }
}

/** A gap that takes what is left in a column (pushes buttons to the bottom). */
@Composable
public fun ColumnScope.ModeFill() {
    Spacer(Modifier.weight(1f))
}

/** A fixed gap, for the few places spacing between siblings isn't even. */
@Composable
public fun ModeGap(height: androidx.compose.ui.unit.Dp) {
    Spacer(Modifier.height(height))
}
