package com.piyush.mode.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow

// The agent overlay over the user's app (boards O01–O10): a glowing sheet that shrinks to a slim bar, and on the
// user's own screen a small Mo button with a glowing screen edge.

/** Mo's three colours, swept round: the overlay's glow. */
@Composable
private fun glow(): Brush {
    val c = Mode.colors
    return Brush.sweepGradient(listOf(c.tint, c.moMango, c.moLilac, c.tint))
}

/** The sheet over the user's app: a soft glow in Mo's colours behind a rounded card. */
@Composable
public fun ModeOverlaySheet(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.overlay)
    Box(modifier.fillMaxWidth().padding(ModeSpacing.s)) {
        Glow(shape)
        Column(
            Modifier.fillMaxWidth().clip(shape).background(c.card, shape).border(ModeSize.overlayRing, c.separator, shape)
                .padding(start = ModeSpacing.l, end = ModeSpacing.l, top = ModeSpacing.l, bottom = ModeSpacing.m),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.m + ModeSpacing.xxs),
            content = content,
        )
    }
}

/** The soft glow behind the sheet or bar: blurred past its own edge, so it follows the curve, with no box round it. */
@Composable
private fun BoxScope.Glow(shape: RoundedCornerShape) {
    Box(
        Modifier.matchParentSize().padding(ModeSize.overlayGlowInset)
            .blur(ModeSize.overlayGlowBlur, BlurredEdgeTreatment.Unbounded).alpha(GLOW_ALPHA).background(glow(), shape),
    )
}

/** How the overlay's status reads: its dot and words. */
public enum class ModeOverlayTone { Live, Working, Asking, Done, Problem, Turn }

@Composable
private fun toneColor(tone: ModeOverlayTone): Color {
    val c = Mode.colors
    return when (tone) {
        ModeOverlayTone.Live -> c.danger
        ModeOverlayTone.Working -> c.tint
        ModeOverlayTone.Asking -> c.info
        ModeOverlayTone.Done -> c.success
        ModeOverlayTone.Problem -> c.danger
        ModeOverlayTone.Turn -> c.tokens.tileOrangeText
    }
}

/** The sheet's top: Mo in a halo, what's happening ("LISTENING") and a line under it, then shrink and close. */
@Composable
public fun ModeOverlayHeader(
    mood: MoMood,
    status: String,
    tone: ModeOverlayTone,
    sub: String,
    slimLabel: String,
    closeLabel: String,
    onSlim: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Mode.colors
    val dot = toneColor(tone)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(ModeSize.overlayHalo), Alignment.Center) {
            Box(Modifier.matchParentSize().background(Brush.radialGradient(listOf(c.tintSoft, Color.Transparent)), CircleShape))
            ModeMo(mood, ModeSize.overlayMo)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(ModeSize.overlayStatusDot).background(dot, CircleShape))
                ModeText(status.uppercase(), ModeTextStyle.CaptionEmphasis, color = if (tone == ModeOverlayTone.Live || tone == ModeOverlayTone.Working) c.textPrimary else dot, maxLines = 1)
            }
            if (sub.isNotEmpty()) ModeText(sub, ModeTextStyle.Footnote, color = c.textSecondary, maxLines = 1)
        }
        HeaderButton(ModeIcon.Chevron, slimLabel, onSlim, QUARTER_TURN)
        HeaderButton(ModeIcon.Close, closeLabel, onClose)
    }
}

/** A round grey button in the sheet's header (shrink points down). */
@Composable
private fun HeaderButton(icon: ModeIcon, label: String, onClick: () -> Unit, turn: Float = 0f) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Box(
        Modifier.size(ModeSize.touch).alpha(alpha).background(c.fill, CircleShape).semantics { contentDescription = label }
            .clickable(press, indication = null, role = Role.Button, onClick = onClick),
        Alignment.Center,
    ) { ModeIconImage(icon, ModeIconSize.small, c.textPrimary, Modifier.rotate(turn)) }
}

/** A chip Mode suggests or offers ("Read today's calendar", "Try again"): tapping it is like saying it. */
@Composable
public fun ModeOverlayChip(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Box(
        modifier.defaultMinSize(minHeight = ModeSize.overlayChip).alpha(alpha).background(c.fill, Capsule)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick).padding(horizontal = ModeSpacing.m + ModeSpacing.xxs),
        Alignment.Center,
    ) { ModeText(text, ModeTextStyle.SubheadlineEmphasis, maxLines = 1) }
}

/** One step Mode took, for the live stream: done, or the one it's on. */
public data class ModeOverlayStep(val text: String, val time: String, val now: Boolean)

/** Every step as it happens: a tick for each one done, a glowing dot for the one it's on, and when. */
@Composable
public fun ModeStepStream(steps: List<ModeOverlayStep>, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Column(modifier, verticalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs)) {
        steps.forEach { step ->
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                if (step.now) {
                    Box(Modifier.size(ModeSize.overlayStepIcon).background(c.tintSoft, CircleShape), Alignment.Center) {
                        Box(Modifier.size(ModeSize.overlayStepDot).background(c.tint, CircleShape))
                    }
                } else {
                    Box(Modifier.size(ModeSize.overlayStepIcon).background(c.tokens.tagGreen, CircleShape), Alignment.Center) {
                        ModeIconImage(ModeIcon.Checkmark, ModeIconSize.chevron, c.success)
                    }
                }
                ModeText(
                    step.text, if (step.now) ModeTextStyle.SubheadlineEmphasis else ModeTextStyle.Subheadline, Modifier.weight(1f),
                    color = if (step.now) c.textPrimary else c.textSecondary, maxLines = 2,
                )
                ModeText(step.time, ModeTextStyle.Caption, color = c.textSecondary)
            }
        }
    }
}

/** The hidden screen as Mode last saw it, small, with a glowing edge and a "HIDDEN" label. */
@Composable
public fun ModeMiniScreen(screen: ImageBitmap?, label: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.overlayMini)
    Box(
        modifier.size(ModeSize.overlayMiniWidth, ModeSize.overlayMiniHeight).clip(shape).background(c.page, shape)
            .border(ModeSize.overlayRing, c.tint, shape).semantics { contentDescription = label },
    ) {
        if (screen != null) Image(screen, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
        else Box(Modifier.fillMaxSize(), Alignment.Center) { ModeMo(MoMood.Working, ModeSize.talkMoCard) }
        ModeText(
            label.uppercase(), ModeTextStyle.CaptionEmphasis,
            Modifier.align(Alignment.TopStart).padding(ModeSpacing.s).background(c.card, Capsule).padding(horizontal = ModeSpacing.xs + ModeSpacing.xxs),
            color = c.textSecondary,
        )
    }
}

/** Mode is listening, by itself: the mic, the voice as bars, a hint, and typing instead. Blue while answering. */
@Composable
public fun ModeListeningStrip(
    hint: String, level: Float, answering: Boolean, typeLabel: String, onType: () -> Unit, modifier: Modifier = Modifier,
    /** Not listening: the mic is a button that starts listening again, and the wave rests. */
    micLabel: String? = null,
    onMic: (() -> Unit)? = null,
) {
    val c = Mode.colors
    val color = if (answering) c.info else c.tint
    val soft = if (answering) c.infoSoft else c.tintSoft
    Row(
        modifier.fillMaxWidth().height(ModeSize.overlayStrip).background(soft, Capsule).padding(start = ModeSpacing.m, end = ModeSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(ModeSize.overlayStripMic).background(color, CircleShape)
                .then(if (onMic != null) Modifier.semantics { contentDescription = micLabel.orEmpty() }.clickable(role = Role.Button, onClick = onMic) else Modifier),
            Alignment.Center,
        ) {
            ModeIconImage(ModeIcon.Microphone, ModeIconSize.small, c.onInfo)
        }
        if (onMic == null) ModeTalkWave(level, speaking = false, bars = STRIP_BARS, height = ModeSize.talkWaveQuestion, color = color, fades = false)
        ModeText(hint, ModeTextStyle.Footnote, Modifier.weight(1f), color = c.textSecondary, maxLines = 2)
        ModeIconButton(ModeIcon.Keyboard, typeLabel, onType)
    }
}

/** What the slim bar is about: Mo at work (expand, Stop) or the user's turn ("I've entered it"). */
public enum class ModeOverlayBarKind { Working, Turn }

/** The slim bar while Mode works (O04) or waits for the user's part (O06): the app above stays usable. */
@Composable
public fun ModeOverlayBar(
    kind: ModeOverlayBarKind,
    top: String,
    main: String,
    action: String,
    onMain: () -> Unit,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    expandLabel: String = "",
) {
    val c = Mode.colors
    val working = kind == ModeOverlayBarKind.Working
    val shape = RoundedCornerShape(ModeRadius.overlay)
    Box(modifier.fillMaxWidth().padding(ModeSpacing.s)) {
        Glow(shape)
        Row(
            Modifier.fillMaxWidth().height(ModeSize.overlayBar).clip(shape).background(if (working) c.hero else c.card, shape)
                .clickable(role = Role.Button, indication = null, interactionSource = null, onClick = onMain)
                .padding(start = ModeSpacing.m, end = ModeSpacing.s),
            horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeMo(if (working) MoMood.Working else MoMood.YourTurn, ModeSize.overlayBarMo)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                ModeText(top.uppercase(), ModeTextStyle.CaptionEmphasis, color = if (working) c.heroSecondary else c.textSecondary, maxLines = 1)
                ModeText(main, ModeTextStyle.SubheadlineEmphasis, color = if (working) c.heroText else c.textPrimary, maxLines = 1)
            }
            if (working) {
                Box(
                    Modifier.size(ModeSize.touch).background(c.heroFill, CircleShape).semantics { contentDescription = expandLabel }
                        .clickable(role = Role.Button, indication = null, interactionSource = null, onClick = onMain),
                    Alignment.Center,
                ) { ModeIconImage(ModeIcon.Chevron, ModeIconSize.small, c.heroText, Modifier.rotate(-QUARTER_TURN)) }
                Box(
                    Modifier.size(ModeSize.touch).background(c.stopRed, CircleShape).semantics { contentDescription = action }
                        .clickable(role = Role.Button, indication = null, interactionSource = null, onClick = onAction),
                    Alignment.Center,
                ) { ModeIconImage(ModeIcon.Stop, ModeIconSize.small, c.onInfo) }
            } else {
                Box(
                    Modifier.height(ModeSize.touch).background(c.accent, Capsule)
                        .clickable(role = Role.Button, indication = null, interactionSource = null, onClick = onAction)
                        .padding(horizontal = ModeSpacing.l),
                    Alignment.Center,
                ) { ModeText(action, ModeTextStyle.SubheadlineEmphasis, color = c.onAccent, maxLines = 1) }
            }
        }
    }
}

/** On the user's own screen while Mode taps it (O09): a small Mo button with a glowing ring, and the step beside it. */
@Composable
public fun ModeOverlayButton(bubble: String?, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, mood: MoMood = MoMood.Working) {
    val c = Mode.colors
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
        if (!bubble.isNullOrBlank()) {
            ModeText(
                bubble, ModeTextStyle.FootnoteEmphasis,
                Modifier.widthIn(max = ModeSize.overlayBubbleMax)
                    .background(c.hero, RoundedCornerShape(ModeRadius.bubble, ModeRadius.bubble, ModeRadius.bubbleTail, ModeRadius.bubble))
                    .padding(horizontal = ModeSpacing.m, vertical = ModeSpacing.s),
                color = c.heroText, maxLines = 2,
            )
        }
        Box(
            Modifier.size(ModeSize.overlayButton).background(c.hero, CircleShape).border(ModeSize.overlayGlowEdge, c.tint, CircleShape)
                .semantics { contentDescription = label }
                .clickable(role = Role.Button, indication = null, interactionSource = null, onClick = onClick),
            Alignment.Center,
        ) { ModeMo(mood, ModeSize.overlayButtonMo) }
    }
}

/** The glowing edge round the user's whole screen while Mode is driving it (O09). Never takes a touch. */
@Composable
public fun ModeScreenGlow(modifier: Modifier = Modifier) {
    val brush = glow()
    Spacer(
        modifier.fillMaxSize().drawBehind {
            val edge = ModeSize.overlayGlowEdge.toPx()
            drawRect(brush, style = Stroke(edge))
            drawRect(brush, alpha = GLOW_ALPHA, style = Stroke(edge * GLOW_SPREAD))
        },
    )
}

/** The big line in the sheet: the greeting, Mode's question, the result's headline. */
@Composable
public fun ModeOverlayLine(text: String, modifier: Modifier = Modifier, quiet: Boolean = false) {
    ModeText(text, ModeTextStyle.Heading, modifier, color = if (quiet) Mode.colors.textSecondary else Mode.colors.textPrimary)
}

private const val GLOW_ALPHA = 0.55f
private const val QUARTER_TURN = 90f
private const val GLOW_SPREAD = 6f
private const val STRIP_BARS = 8
