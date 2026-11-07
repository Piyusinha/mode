package com.piyush.mode.design

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp

/** What a button is for, which sets its look (ModeButton.Role on the iPhone). */
public enum class ModeButtonRole {
    /** The one main action on a screen: an ink capsule, 56 tall when full width. */
    Primary,
    /** Other actions: a raised-grey capsule. */
    Secondary,
    /** A text-only action. */
    Plain,
    /** Deleting or forgetting. */
    Destructive,
    /** A small pill inside a row or a card (Connect, Change, Undo): raised grey. */
    Pill,
    /** A small ink pill (the chosen provider's Connect). */
    PillProminent,
    /** The main action on a sheet or guide: ink, 52 tall, as wide as given. */
    Main,
    /** Another action on a card: raised grey. */
    Other,
    /** Another action straight on the page: white. */
    OtherOnPage,
    /** Stop: raised grey with red words. */
    Stop,
    /** An answer Mode is waiting for: blue, used nowhere else. */
    Answer,
    /** Another answer beside it: white with blue words. */
    AnswerQuiet,
    /** Stop on a soft red, under a blue answer. */
    StopSoft,
}

/** Mode's buttons, as on the canvas: solid capsules, ink for the one main action. */
@Composable
public fun ModeButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    role: ModeButtonRole = ModeButtonRole.Primary,
    icon: ModeIcon? = null,
    fullWidth: Boolean = false,
    enabled: Boolean = true,
) {
    val c = Mode.colors
    val pill = role == ModeButtonRole.Pill || role == ModeButtonRole.PillProminent
    val large = role in LARGE
    val height: Dp = when (role) {
        ModeButtonRole.Primary -> if (fullWidth) ModeSize.primaryButton else ModeSize.secondaryButton
        ModeButtonRole.Secondary -> ModeSize.secondaryButton
        ModeButtonRole.Pill, ModeButtonRole.PillProminent -> ModeSize.pillButton
        ModeButtonRole.Plain, ModeButtonRole.Destructive -> ModeSize.touch
        else -> ModeSize.loopButton
    }
    val foreground: Color = when (role) {
        ModeButtonRole.Primary, ModeButtonRole.PillProminent, ModeButtonRole.Main -> c.onAccent
        ModeButtonRole.Destructive, ModeButtonRole.Stop, ModeButtonRole.StopSoft -> c.danger
        ModeButtonRole.Answer -> Color.White
        ModeButtonRole.AnswerQuiet -> c.infoText
        else -> c.textPrimary
    }
    val background: Color = when (role) {
        ModeButtonRole.Primary, ModeButtonRole.PillProminent, ModeButtonRole.Main -> c.accent
        ModeButtonRole.Secondary, ModeButtonRole.Pill, ModeButtonRole.Other, ModeButtonRole.Stop -> c.cardRaised
        ModeButtonRole.Plain, ModeButtonRole.Destructive -> Color.Transparent
        ModeButtonRole.OtherOnPage, ModeButtonRole.AnswerQuiet -> c.card
        ModeButtonRole.Answer -> c.info
        ModeButtonRole.StopSoft -> c.tokens.tileRed
    }
    val text = when {
        pill -> ModeTextStyle.SubheadlineEmphasis
        role == ModeButtonRole.Primary && fullWidth -> ModeTextStyle.Headline
        else -> ModeTextStyle.CalloutEmphasis
    }
    val (press, alpha) = rememberPress()
    Row(
        modifier
            .then(if (fullWidth || large) Modifier.fillMaxWidth() else Modifier)
            .defaultMinSize(minHeight = height)
            .alpha(alpha)
            .enabledAlpha(enabled)
            .background(background, Capsule)
            .clickable(press, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (pill) ModeSpacing.m + ModeSpacing.xxs else ModeSpacing.xl),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) ModeIconImage(icon, if (pill) ModeIconSize.small else ModeIconSize.regular, foreground)
        ModeText(title, text, color = foreground, maxLines = 1)
    }
}

private val LARGE = setOf(
    ModeButtonRole.Main, ModeButtonRole.Other, ModeButtonRole.OtherOnPage, ModeButtonRole.Stop,
    ModeButtonRole.Answer, ModeButtonRole.AnswerQuiet, ModeButtonRole.StopSoft,
)

/**
 * A round button with only an icon; [label] is read by TalkBack. A white card with a soft lift,
 * or ink when [prominent] (Speak, Send).
 */
@Composable
public fun ModeIconButton(
    icon: ModeIcon,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    prominent: Boolean = false,
    size: Dp = ModeSize.touch,
    enabled: Boolean = true,
) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Box(
        modifier
            .size(size)
            .alpha(alpha)
            .enabledAlpha(enabled)
            .modeShadow(CircleShape)
            .background(if (prominent) c.accent else c.card, CircleShape)
            .clickable(press, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        ModeIconImage(icon, if (size >= ModeSize.speakButton) ModeIconSize.hero else ModeIconSize.regular, if (prominent) c.onAccent else c.textPrimary)
    }
}
