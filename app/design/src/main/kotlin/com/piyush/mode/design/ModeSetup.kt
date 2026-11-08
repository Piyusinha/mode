package com.piyush.mode.design

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

/** A setup page's head: the big title and a line under it, Mo beside them (the phone-setup boards). */
@Composable
public fun ModeSetupTitle(title: String, subtitle: String, mood: MoMood, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            ModeText(title, ModeTextStyle.Title, Modifier.semantics { heading() })
            ModeText(subtitle, ModeTextStyle.Callout, color = c.textSecondary)
        }
        ModeMo(mood, ModeSize.moSetup)
    }
}

/** What sits at the end of a permission row. */
public sealed interface ModeStatusAction {
    /** A filled blue button: the one setting that is needed ("Turn on"). */
    public data class Prominent(val title: String) : ModeStatusAction
    /** A soft blue button: something optional ("Allow", "Set"). */
    public data class Quiet(val title: String) : ModeStatusAction
    /** Done: a green tick and a word ("On", "Allowed"). */
    public data class Done(val title: String) : ModeStatusAction
    /** Just a quiet word ("Later"). */
    public data class Note(val text: String) : ModeStatusAction
    /** A grey button: a page Mode opens for a setting it can't read back ("Open"). */
    public data class Open(val title: String) : ModeStatusAction
}

/** A permission on "Let Mode use your phone": its tile, its name and why, and what to do about it, top-aligned. */
@Composable
public fun ModePermissionRow(
    title: String,
    why: String,
    icon: ModeIcon,
    status: ModeStatusAction,
    onAllow: () -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    /** The why-line warns (orange): Mode can't check this one, the user confirms it. */
    caution: Boolean = false,
) {
    val c = Mode.colors
    Row(
        modifier.fillMaxWidth().padding(vertical = ModeSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.clearAndSetSemantics {}.size(ModeSize.permissionTile)
                .background(if (highlighted) c.tintSoft else c.cardRaised, RoundedCornerShape(ModeSize.tileRadius)),
            Alignment.Center,
        ) { ModeIconImage(icon, ModeIconSize.regular, if (highlighted) c.tint else c.textPrimary) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs + ModeSpacing.xxs / 2)) {
            ModeText(title, ModeTextStyle.CalloutEmphasis)
            ModeText(why, ModeTextStyle.Footnote, color = if (caution) c.tokens.tileOrangeText else c.textSecondary)
        }
        ModeStatusView(status, onAllow)
    }
}

/** The end of a row: a blue button, a green done mark, or a quiet word. */
@Composable
public fun ModeStatusView(action: ModeStatusAction, onTap: () -> Unit = {}) {
    val c = Mode.colors
    when (action) {
        is ModeStatusAction.Prominent -> ModeAllowButton(action.title, onTap, quiet = false)
        is ModeStatusAction.Quiet -> ModeAllowButton(action.title, onTap, quiet = true)
        is ModeStatusAction.Done -> Row(
            Modifier.defaultMinSize(minHeight = ModeSize.permissionTile),
            horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeIconImage(ModeIcon.Checkmark, ModeIconSize.small, c.success)
            ModeText(action.title, ModeTextStyle.SubheadlineEmphasis, color = c.success)
        }
        is ModeStatusAction.Note -> Box(Modifier.defaultMinSize(minHeight = ModeSize.permissionTile), Alignment.Center) {
            ModeText(action.text, ModeTextStyle.Subheadline, color = c.textSecondary)
        }
        is ModeStatusAction.Open -> {
            val (press, alpha) = rememberPress()
            Box(
                Modifier.defaultMinSize(minHeight = ModeSize.pillButton).alpha(alpha).background(c.fill, Capsule)
                    .clickable(press, indication = null, role = Role.Button, onClick = onTap).padding(horizontal = ModeSpacing.l),
                Alignment.Center,
            ) { ModeText(action.title, ModeTextStyle.SubheadlineEmphasis, maxLines = 1) }
        }
    }
}

/** The small blue button on a permission (44 tall): filled, or soft for something optional. */
@Composable
public fun ModeAllowButton(title: String, onClick: () -> Unit, quiet: Boolean = false) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Box(
        Modifier.defaultMinSize(minHeight = ModeSize.pillButton).alpha(alpha)
            .background(if (quiet) c.infoSoft else c.info, Capsule)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = ModeSpacing.l),
        Alignment.Center,
    ) { ModeText(title, ModeTextStyle.SubheadlineEmphasis, color = if (quiet) c.infoText else Color.White, maxLines = 1) }
}

/** What a guide's steps look like: numbered (to do), or green ticks (what was done). */
public enum class ModeGuideMarks { Numbers, Checks }

/** A guide's steps in a white card: a number (or a tick) and the step, one per row. */
@Composable
public fun ModeGuideSteps(steps: List<String>, modifier: Modifier = Modifier, marks: ModeGuideMarks = ModeGuideMarks.Numbers) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.card)
    Column(modifier.fillMaxWidth().modeShadow(shape).background(c.card, shape).padding(horizontal = ModeSpacing.l, vertical = ModeSpacing.xs)) {
        steps.forEachIndexed { i, step ->
            Row(
                Modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.guideStep).padding(vertical = ModeSpacing.s),
                horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (marks) {
                    ModeGuideMarks.Numbers -> Box(Modifier.clearAndSetSemantics {}.size(ModeSize.guideNumber).background(c.cardRaised, CircleShape), Alignment.Center) {
                        ModeText((i + 1).toString(), ModeTextStyle.FootnoteEmphasis)
                    }
                    ModeGuideMarks.Checks -> Box(Modifier.clearAndSetSemantics {}.size(ModeSize.guideNumber).background(c.tokens.tagGreen, CircleShape), Alignment.Center) {
                        ModeIconImage(ModeIcon.Checkmark, ModeIconSize.small, c.success)
                    }
                }
                ModeText(step, ModeTextStyle.Callout, Modifier.weight(1f))
            }
            if (i < steps.lastIndex) ModeHairline()
        }
    }
}

/** What a note on a guide is saying: a fact, a heads-up, or that something went wrong. */
public enum class ModeNoteKind { Info, Warning, Problem }

/** A tinted note on a guide: blue for a fact, orange for a heads-up, red for a problem. */
@Composable
public fun ModeGuideNote(text: String, kind: ModeNoteKind, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val (background, foreground, icon) = when (kind) {
        ModeNoteKind.Info -> Triple(c.infoSoft, c.infoText, ModeIcon.Info)
        ModeNoteKind.Warning -> Triple(c.tokens.tileOrange, c.tokens.tileOrangeText, ModeIcon.Warning)
        ModeNoteKind.Problem -> Triple(c.tokens.tileRed, c.danger, ModeIcon.Warning)
    }
    Row(
        modifier.fillMaxWidth().background(background, RoundedCornerShape(ModeRadius.note))
            .padding(horizontal = ModeSpacing.m + ModeSpacing.xxs, vertical = ModeSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs),
        verticalAlignment = Alignment.Top,
    ) {
        ModeIconImage(icon, ModeIconSize.small, foreground)
        ModeText(text, ModeTextStyle.Footnote, Modifier.weight(1f), color = foreground)
    }
}

/** "Waiting for you to turn it on": a softly pulsing orange dot and a quiet line. */
@Composable
public fun ModeWaitingLine(text: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val pulse by rememberInfiniteTransition(label = "waiting").animateFloat(
        initialValue = 1f, targetValue = ModeOpacity.disabled,
        animationSpec = infiniteRepeatable(tween(ModeMotion.shimmerMs / 2), RepeatMode.Reverse), label = "dot",
    )
    Row(
        modifier.fillMaxWidth().padding(horizontal = ModeSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ModeSize.waitingDot).alpha(pulse).background(c.tint, CircleShape))
        ModeText(text, ModeTextStyle.Subheadline, color = c.textSecondary)
    }
}

/** A row on a card that opens another page: its tile, name and line, a word at the end, and the arrow. */
@Composable
public fun ModeLinkCard(
    title: String,
    subtitle: String,
    icon: ModeIcon,
    trailing: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingDone: Boolean = false,
) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.card)
    val (press, alpha) = rememberPress()
    Row(
        modifier.fillMaxWidth().alpha(alpha).modeShadow(shape).background(c.card, shape)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = ModeSpacing.l, vertical = ModeSpacing.m + ModeSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clearAndSetSemantics {}.size(ModeSize.permissionTile).background(c.cardRaised, RoundedCornerShape(ModeSize.tileRadius)), Alignment.Center) {
            ModeIconImage(icon, ModeIconSize.regular, c.textPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            ModeText(title, ModeTextStyle.CalloutEmphasis)
            ModeText(subtitle, ModeTextStyle.Footnote, color = c.textSecondary)
        }
        ModeText(trailing, ModeTextStyle.SubheadlineEmphasis, color = if (trailingDone) c.success else c.infoText)
        ModeChevron()
    }
}

/** A one-pixel line between rows. */
@Composable
public fun ModeHairline(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(ModeSize.separator).background(Mode.colors.separator))
}

/** A page's top bar: the round back button on the left, actions on the right. */
@Composable
public fun ModePageBar(backLabel: String, onBack: () -> Unit, modifier: Modifier = Modifier, trailing: @Composable () -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.touch).padding(horizontal = ModeSpacing.screenEdge),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeBackButton(backLabel, onBack)
        Box(Modifier.weight(1f))
        trailing()
    }
}

/** How one requirement stands (boards D06–D08). */
public enum class ModeRequirementState { Met, Unmet, Waiting }

/** One thing a feature needs: what it is, how this phone does ("Android 16", "Not connected"), and whether it's met. */
public data class ModeRequirement(val title: String, val detail: String, val state: ModeRequirementState)

/** A card of requirements with a tick, a cross or a clock for each. */
@Composable
public fun ModeRequirementList(rows: List<ModeRequirement>, modifier: Modifier = Modifier) {
    val c = Mode.colors
    ModeCard(
        lifted = true,
        modifier = modifier,
        rows = rows.map { row ->
            {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = ModeSpacing.m),
                    horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val (fill, tint, icon) = when (row.state) {
                        ModeRequirementState.Met -> Triple(c.tokens.tagGreen, c.success, ModeIcon.Checkmark)
                        ModeRequirementState.Unmet -> Triple(c.tokens.tileRed, c.danger, ModeIcon.Close)
                        ModeRequirementState.Waiting -> Triple(c.tokens.tileOrange, c.tokens.tileOrangeText, ModeIcon.Clock)
                    }
                    Box(Modifier.clearAndSetSemantics {}.size(ModeSize.guideNumber).background(fill, CircleShape), Alignment.Center) {
                        ModeIconImage(icon, ModeIconSize.chevron, tint)
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                        ModeText(row.title, ModeTextStyle.Callout)
                        ModeText(row.detail, ModeTextStyle.Footnote, color = if (row.state == ModeRequirementState.Met) c.textSecondary else tint)
                    }
                }
            }
        },
    )
}
