package com.piyush.mode.design

import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Talking mode, as on the "Talking mode" boards: Mo at the centre, bars for a voice, the conversation as it
// happens, and the dock (Type, the big mic, Cancel / That's all / Later).

/** Seconds since this started, ticking every frame: what the breathing and the bars move with. */
@Composable
private fun rememberClock(): Float {
    val transition = rememberInfiniteTransition(label = "talk")
    val t by transition.animateFloat(0f, CLOCK_SPAN, infiniteRepeatable(tween((CLOCK_SPAN * MS).toInt(), easing = LinearEasing), RepeatMode.Restart), label = "t")
    return t
}

/** Bars of a sound, for a voice that is playing: 3 dp bars with round ends. */
@Composable
public fun ModeWaveBars(modifier: Modifier = Modifier, color: Color = Mode.colors.brand, height: androidx.compose.ui.unit.Dp = ModeSize.cardWave, count: Int = 5) {
    Row(modifier.height(height).clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        repeat(min(count, WAVE.size)) { i ->
            Box(Modifier.size(ModeSize.smallBar, height * WAVE[i]).background(color, RoundedCornerShape(ModeSize.talkBarRadius)))
        }
    }
}

/** Mo at the centre of talking mode: on a soft disc that swells with the user's voice ([level] 0…1) while listening; otherwise it breathes. */
@Composable
public fun ModeTalkMo(mood: MoMood, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier, level: Float = 0f, halo: Boolean = false) {
    val t = rememberClock()
    val breath = BREATH * sin(t * BREATH_SPEED)
    val swell = level.coerceIn(0f, 1f) * SWELL
    Box(modifier.clearAndSetSemantics {}, Alignment.Center) {
        if (halo) Box(Modifier.size(ModeSize.talkHalo).scale(1 + swell + breath).background(Mode.colors.tintSoft, CircleShape))
        ModeMo(mood, size, Modifier.scale(1 + breath))
    }
}

/**
 * The voice as bars across the screen: 4 dp bars that follow the microphone's [level] while listening and sway
 * while Mode speaks; with [fades], the outer four on each side stay small and grey.
 */
@Composable
public fun ModeTalkWave(
    level: Float,
    speaking: Boolean,
    modifier: Modifier = Modifier,
    bars: Int = 34,
    height: androidx.compose.ui.unit.Dp = ModeSize.talkWaveHeight,
    color: Color = Mode.colors.brand,
    fades: Boolean = true,
) {
    val c = Mode.colors
    val t = rememberClock()
    val energy = if (speaking) SPEAKING_ENERGY else QUIET_ENERGY + level.coerceIn(0f, 1f) * (1 - QUIET_ENERGY)
    Row(
        modifier.height(height).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(if (fades) ModeSpacing.xs else ModeSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(bars) { i ->
            val outer = if (fades) min(i, bars - 1 - i) else EDGE.size
            val shape = RoundedCornerShape(ModeSize.talkBarRadius)
            if (outer < EDGE.size) {
                Box(Modifier.size(ModeSize.talkBar, max(ModeSize.talkBar.value, height.value * EDGE[outer]).let { androidx.compose.ui.unit.Dp(it) }).background(c.track, shape))
            } else {
                val motion = SWAY_BASE + SWAY * sin(t * (if (speaking) SPEAK_SPEED else LISTEN_SPEED) + i * BAR_PHASE)
                val h = max(ModeSize.talkBar.value, height.value * SHAPE[i % SHAPE.size] * energy * motion)
                Box(Modifier.size(if (fades) ModeSize.talkBar else ModeSize.smallBar, androidx.compose.ui.unit.Dp(h)).background(color, shape))
            }
        }
    }
}

public enum class ModeTalkTone { Plain, Info, Success }

/** What talking mode is doing, in a pill at the top ("Listening" with a red dot, "Mode needs you" on blue). */
@Composable
public fun ModeTalkStatus(text: String, dot: Color?, modifier: Modifier = Modifier, tone: ModeTalkTone = ModeTalkTone.Plain) {
    val c = Mode.colors
    val (fg, bg) = when (tone) {
        ModeTalkTone.Plain -> c.textPrimary to c.card
        ModeTalkTone.Info -> c.infoText to c.infoSoft
        ModeTalkTone.Success -> c.success to c.tokens.tagGreen
    }
    Row(
        modifier.height(ModeSize.talkStatus).modeShadow(Capsule, tone == ModeTalkTone.Plain).background(bg, Capsule).padding(horizontal = ModeSpacing.m + ModeSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) Box(Modifier.size(ModeSize.voiceDot).background(dot, CircleShape))
        ModeText(text, ModeTextStyle.SmallEmphasis, color = fg, maxLines = 1)
    }
}

/** A short live caption ("HEARING YOU LIVE") with a red dot. */
@Composable
public fun ModeLiveLabel(text: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(
        modifier.height(ModeSize.talkLiveLabel).background(c.tintSoft, Capsule).padding(horizontal = ModeSpacing.s + ModeSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ModeSize.talkLiveDot).background(c.danger, CircleShape))
        ModeText(text.uppercase(), ModeTextStyle.CaptionEmphasis, color = c.tint)
    }
}

/** What the user said, as an ink bubble on the right. */
@Composable
public fun ModeTalkBubble(text: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.bubble, ModeRadius.bubble, ModeRadius.bubbleTail, ModeRadius.bubble)
    Box(modifier.fillMaxWidth(), Alignment.CenterEnd) {
        ModeText(
            text, ModeTextStyle.Spoken,
            Modifier.widthIn(max = ModeSize.talkBubbleMax).background(c.accent, shape).padding(horizontal = ModeSpacing.l, vertical = ModeSpacing.m),
            color = c.onAccent,
        )
    }
}

/** What Mode said out loud: a speaker, "MODE · SPEAKING", the line, and bars while it speaks. */
@Composable
public fun ModeTalkSays(label: String, text: String, speaking: Boolean, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.talkCard)
    Row(
        modifier.fillMaxWidth().modeShadow(shape).background(c.card, shape).padding(horizontal = ModeSpacing.l, vertical = ModeSpacing.m + ModeSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
    ) {
        Box(Modifier.size(ModeSize.talkSaysIcon).background(c.tintSoft, CircleShape), Alignment.Center) {
            ModeIconImage(ModeIcon.Speaker, ModeIconSize.small, c.tint)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs)) {
            ModeText(label, ModeTextStyle.CaptionEmphasis, color = c.tint)
            ModeText(text, ModeTextStyle.Spoken)
        }
        if (speaking) ModeWaveBars()
    }
}

/** What Mode is doing now, while it works in the background: the step, a line under it, and Mo at work. */
@Composable
public fun ModeTalkWorking(title: String, detail: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.talkCard)
    Row(
        modifier.fillMaxWidth().modeShadow(shape).background(c.card, shape).padding(horizontal = ModeSpacing.l, vertical = ModeSpacing.m + ModeSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            ModeText(title, ModeTextStyle.CalloutEmphasis, maxLines = 2)
            ModeText(detail, ModeTextStyle.Footnote, color = c.textSecondary, maxLines = 2)
        }
        ModeMo(MoMood.Working, ModeSize.talkMoCard)
    }
}

/** A spoken question's answer to tap: soft blue, or solid blue when it's the one being said. */
@Composable
public fun ModeTalkOption(title: String, chosen: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Box(
        modifier.defaultMinSize(minHeight = ModeSize.talkOption).alpha(alpha).background(if (chosen) c.info else c.infoSoft, Capsule)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick).padding(horizontal = ModeSpacing.xl + ModeSpacing.xxs),
        Alignment.Center,
    ) { ModeText(title, ModeTextStyle.Headline, color = if (chosen) c.onInfo else c.infoText) }
}

/** The voice couldn't be used: a red dot, what happened, and a way to fix it. */
@Composable
public fun ModeVoiceNotice(text: String, actionTitle: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(
        modifier.fillMaxWidth().background(c.tokens.tileRed, RoundedCornerShape(ModeRadius.bubble))
            .padding(start = ModeSpacing.m + ModeSpacing.xxs, end = ModeSpacing.s + ModeSpacing.xxs, top = ModeSpacing.xxs, bottom = ModeSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ModeSize.noticeDot).background(c.danger, CircleShape))
        ModeText(text, ModeTextStyle.SmallEmphasis, Modifier.weight(1f))
        Box(
            Modifier.defaultMinSize(minHeight = ModeSize.touch).clickable(role = Role.Button, indication = null, interactionSource = null, onClick = onAction),
            Alignment.Center,
        ) {
            Box(Modifier.height(ModeSize.talkStatus).background(c.card, Capsule).padding(horizontal = ModeSpacing.m), Alignment.Center) {
                ModeText(actionTitle, ModeTextStyle.FootnoteEmphasis, maxLines = 1)
            }
        }
    }
}

/** A round white button with a label under it (Type, Cancel, Later, That's all) beside the big mic. */
@Composable
public fun ModeTalkSideButton(icon: ModeIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Column(
        modifier.width(ModeSize.talkSideButtonWidth).alpha(alpha).clickable(press, indication = null, role = Role.Button, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs),
    ) {
        Box(Modifier.size(ModeSize.talkSide).modeShadow(CircleShape).background(c.card, CircleShape), Alignment.Center) {
            ModeIconImage(icon, ModeIconSize.small, c.textPrimary)
        }
        ModeText(label, ModeTextStyle.Caption, maxLines = 1)
    }
}

/** The big round button: a stop square while listening (Done), a microphone when it can listen again. */
@Composable
public fun ModeTalkMicButton(listening: Boolean, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Box(
        modifier.size(ModeSize.talkMic).alpha(alpha).modeShadow(CircleShape).background(c.accent, CircleShape)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        Alignment.Center,
    ) {
        if (listening) Box(Modifier.size(ModeSize.talkStopSquare).background(c.onAccent, RoundedCornerShape(ModeSize.talkStopRadius)))
        else ModeIconImage(ModeIcon.Microphone, ModeIconSize.hero, c.onAccent)
    }
}

/** Mo asking to hear you, with the blue microphone badge (Let Mode hear you). */
@Composable
public fun ModeTalkMicAsk(modifier: Modifier = Modifier) {
    val c = Mode.colors
    Box(modifier.size(ModeSize.talkMoMic + ModeSpacing.s).clearAndSetSemantics {}) {
        ModeMo(MoMood.YourTurn, ModeSize.talkMoMic, Modifier.align(Alignment.Center))
        Box(
            Modifier.align(Alignment.BottomEnd).offset(x = -ModeSpacing.xxs, y = -ModeSpacing.xs).size(ModeSize.talkMicBadge)
                .modeShadow(CircleShape).background(c.info, CircleShape),
            Alignment.Center,
        ) { ModeIconImage(ModeIcon.Microphone, ModeIconSize.large, c.onInfo) }
    }
}

public enum class ModeVoiceButtonStyle { Ink, Raised, Danger, Card, Info }

/** A full-width capsule in talking mode (Allow microphone, Send, Stop, Try again). */
@Composable
public fun ModeVoiceButton(
    title: String,
    style: ModeVoiceButtonStyle,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ModeIcon? = null,
    height: androidx.compose.ui.unit.Dp = ModeSize.secondaryButton,
    enabled: Boolean = true,
) {
    val c = Mode.colors
    val (fg, bg) = when (style) {
        ModeVoiceButtonStyle.Ink -> c.onAccent to c.accent
        ModeVoiceButtonStyle.Raised -> c.textPrimary to c.cardRaised
        ModeVoiceButtonStyle.Card -> c.textPrimary to c.card
        ModeVoiceButtonStyle.Danger -> c.danger to c.tokens.tileRed
        ModeVoiceButtonStyle.Info -> c.onInfo to c.info
    }
    val (press, alpha) = rememberPress()
    Row(
        modifier.fillMaxWidth().heightIn(min = height).enabledAlpha(enabled).alpha(alpha).modeShadow(Capsule, style == ModeVoiceButtonStyle.Card)
            .background(bg, Capsule).clickable(press, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = ModeSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) ModeIconImage(icon, ModeIconSize.small, fg)
        ModeText(title, if (height > ModeSize.secondaryButton) ModeTextStyle.Headline else ModeTextStyle.CalloutEmphasis, color = fg, maxLines = 1)
    }
}

/** Mo's own words in the conversation (its greeting, its reply to a task): small Mo, quiet text. */
@Composable
public fun ModeTalkMoLine(text: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs)) {
        ModeMo(MoMood.Idle, ModeSize.talkLineMo)
        ModeText(text, ModeTextStyle.Subheadline, Modifier.weight(1f), color = Mode.colors.textSecondary)
    }
}

/** Mo's line after a task ("Anything else?"), and when it offers something, the two answers to tap. */
@Composable
public fun ModeTalkNext(text: String, modifier: Modifier = Modifier, yes: String? = null, no: String? = null, onYes: () -> Unit = {}, onNo: () -> Unit = {}) {
    val c = Mode.colors
    val offers = yes != null && no != null
    val shape = RoundedCornerShape(ModeRadius.bubbleTail, ModeRadius.bubble, ModeRadius.bubble, ModeRadius.bubble)
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs)) {
        ModeMo(MoMood.Listening, ModeSize.talkLineMo)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            ModeText(
                text, ModeTextStyle.BodyEmphasis,
                Modifier.background(if (offers) c.infoSoft else c.card, shape).padding(horizontal = ModeSpacing.m + ModeSpacing.xxs, vertical = ModeSpacing.m - ModeSpacing.xxs),
            )
            if (yes != null && no != null) {
                // Side by side when they fit; a long answer goes under the other instead of breaking its words.
                ModeChipFlow {
                    ModeTalkOption(yes, chosen = true, onClick = onYes)
                    ModeTalkOption(no, chosen = false, onClick = onNo)
                }
            }
        }
    }
}

/** Why Mo can't hear while it works: the microphone is off while it uses the apps. */
@Composable
public fun ModeTalkMicOff(text: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(
        modifier.fillMaxWidth().background(c.fill, RoundedCornerShape(ModeRadius.bubble)).padding(horizontal = ModeSpacing.m + ModeSpacing.xxs, vertical = ModeSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeIconImage(ModeIcon.MicOff, ModeIconSize.small, c.textSecondary)
        ModeText(text, ModeTextStyle.Subheadline, Modifier.weight(1f), color = c.textSecondary)
    }
}

public enum class ModeTalkCardStatus { Working, Done, Partial, Failed, Stopped }

public data class ModeTalkStep(val text: String, val time: String)

public data class ModeTalkRow(val title: String, val detail: String, val done: Boolean)

/** A task in the conversation: how it's going (or went), each line Mo said with its time, then the result. */
@Composable
public fun ModeTalkTaskCard(
    status: ModeTalkCardStatus,
    label: String,
    title: String,
    chip: String?,
    steps: List<ModeTalkStep>,
    resultLabel: String,
    headline: String,
    rows: List<ModeTalkRow>,
    openLabel: String,
    wrongLabel: String,
    onOpen: () -> Unit,
    onWrong: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Mode.colors
    val working = status == ModeTalkCardStatus.Working
    val (text, soft, mood) = when (status) {
        ModeTalkCardStatus.Working -> Triple(c.tint, c.tintSoft, MoMood.Working)
        ModeTalkCardStatus.Done -> Triple(c.tokens.tagGreenText, c.tokens.tagGreen, MoMood.Done)
        ModeTalkCardStatus.Partial -> Triple(c.tokens.tileOrangeText, c.tokens.tileOrange, MoMood.Stuck)
        ModeTalkCardStatus.Failed -> Triple(c.danger, c.tokens.tileRed, MoMood.Stuck)
        ModeTalkCardStatus.Stopped -> Triple(c.textSecondary, c.fill, MoMood.Idle)
    }
    val shape = RoundedCornerShape(ModeRadius.talkCard)
    Column(
        modifier.fillMaxWidth().modeShadow(shape).background(c.card, shape)
            .padding(start = ModeSpacing.l, end = ModeSpacing.l, top = ModeSpacing.m + ModeSpacing.xxs, bottom = ModeSpacing.l),
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.m),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            ModeMo(mood, ModeSize.talkCardMo)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                ModeText(label, ModeTextStyle.CaptionEmphasis, color = text)
                ModeText(title, ModeTextStyle.CalloutEmphasis, maxLines = 1)
            }
            if (chip != null) {
                Box(Modifier.height(ModeSize.talkChip).background(soft, Capsule).padding(horizontal = ModeSpacing.s + ModeSpacing.xxs), Alignment.Center) {
                    ModeText(chip, ModeTextStyle.CaptionEmphasis, color = text)
                }
            }
        }
        if (steps.isNotEmpty()) {
            Column {
                steps.forEachIndexed { i, step ->
                    val last = i == steps.lastIndex
                    val now = working && last
                    Row(Modifier.height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs)) {
                        Column(Modifier.width(ModeSize.talkStepRail), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                            Box(Modifier.padding(top = ModeSpacing.xs + ModeSpacing.xxs).size(ModeSize.talkStepDot).background(if (now) c.tint else c.textSecondary, CircleShape))
                            if (!last) Box(Modifier.width(ModeSize.talkStepLine).weight(1f).background(c.separator))
                        }
                        Column(Modifier.weight(1f).padding(bottom = if (last) ModeSpacing.none else ModeSpacing.s + ModeSpacing.xxs), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                            ModeText(step.text, ModeTextStyle.Subheadline, color = if (now) c.textPrimary else c.textSecondary)
                            ModeText(step.time, ModeTextStyle.Caption, color = c.textSecondary)
                        }
                    }
                }
            }
        }
        if (!working) {
            Column(verticalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs)) {
                Box(Modifier.fillMaxWidth().padding(bottom = ModeSpacing.xxs).height(ModeSize.talkDivider).background(c.separator))
                ModeText(resultLabel.uppercase(), ModeTextStyle.CaptionEmphasis, color = c.textSecondary)
                if (headline.isNotEmpty()) ModeText(headline, ModeTextStyle.Headline)
                rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().background(c.fill, RoundedCornerShape(ModeRadius.bubble)).padding(horizontal = ModeSpacing.m, vertical = ModeSpacing.s + ModeSpacing.xxs),
                        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(ModeSize.talkRowIcon).background(if (row.done) c.tokens.tagGreen else c.tokens.tileRed, CircleShape), Alignment.Center) {
                            ModeIconImage(if (row.done) ModeIcon.Checkmark else ModeIcon.Close, ModeIconSize.small, if (row.done) c.tokens.tagGreenText else c.danger)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                            ModeText(row.title, ModeTextStyle.CalloutEmphasis)
                            if (row.detail.isNotEmpty()) ModeText(row.detail, ModeTextStyle.Footnote, color = c.textSecondary)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                    TalkCardButton(openLabel, onOpen)
                    if (status == ModeTalkCardStatus.Partial || status == ModeTalkCardStatus.Failed) TalkCardButton(wrongLabel, onWrong)
                }
            }
        }
    }
}

@Composable
private fun TalkCardButton(title: String, onClick: () -> Unit) {
    val (press, alpha) = rememberPress()
    Box(
        Modifier.height(ModeSize.talkCardButton).alpha(alpha).background(Mode.colors.fill, Capsule)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick).padding(horizontal = ModeSpacing.l),
        Alignment.Center,
    ) { ModeText(title, ModeTextStyle.SubheadlineEmphasis, maxLines = 1) }
}

/** The words heard so far: settled ones in ink, the one still forming lighter, then a cursor. */
@Composable
public fun ModeTalkLiveWords(settled: String, forming: String, style: ModeTextStyle, cursor: Color, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val text = buildAnnotatedString {
        if (settled.isNotEmpty()) withStyle(SpanStyle(color = c.textPrimary)) { append("$settled ") }
        withStyle(SpanStyle(color = c.textSecondary)) { append(forming) }
        withStyle(SpanStyle(color = cursor)) { append(CURSOR) }
    }
    BasicText(text, modifier.fillMaxWidth(), style = style.style(c.textPrimary).copy(textAlign = TextAlign.Center))
}

/** Typing instead of speaking: a big centred field on a card; Send on the keyboard sends it. */
@Composable
public fun ModeTalkTypeBox(text: String, onText: (String) -> Unit, prompt: String, onSend: () -> Unit, modifier: Modifier = Modifier, focus: Boolean = true) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.card)
    val focuser = remember { FocusRequester() }
    LaunchedEffect(focus) { if (focus) runCatching { focuser.requestFocus() } }
    Box(modifier.fillMaxWidth().modeShadow(shape).background(c.card, shape).padding(ModeSpacing.l), Alignment.Center) {
        if (text.isEmpty()) ModeText(prompt, ModeTextStyle.Title2, color = c.textSecondary, align = TextAlign.Center)
        BasicTextField(
            text, onText, Modifier.fillMaxWidth().focusRequester(focuser).semantics { contentDescription = prompt },
            textStyle = ModeTextStyle.Title2.style(c.textPrimary).copy(textAlign = TextAlign.Center),
            cursorBrush = SolidColor(c.tint),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
        )
    }
}

private val WAVE = floatArrayOf(0.45f, 0.9f, 0.6f, 1.0f, 0.5f, 0.75f, 0.4f)
private val SHAPE = floatArrayOf(0.56f, 0.94f, 0.7f, 0.41f, 0.59f, 1.0f, 0.76f, 0.5f, 0.29f, 0.65f, 0.91f, 0.44f, 0.24f, 0.21f, 0.35f, 0.5f, 0.79f)
private val EDGE = floatArrayOf(0.12f, 0.15f, 0.21f, 0.32f)
private const val CURSOR = "|"
private const val MS = 1000f
/** The clock wraps after a whole number of breaths and sways, so nothing jumps. */
private const val CLOCK_SPAN = (20 * PI).toFloat()
private const val BREATH = 0.015f
private const val BREATH_SPEED = 1.8f
private const val SWELL = 0.08f
private const val SPEAKING_ENERGY = 0.8f
private const val QUIET_ENERGY = 0.35f
private const val SWAY_BASE = 0.7f
private const val SWAY = 0.3f
private const val SPEAK_SPEED = 5f
private const val LISTEN_SPEED = 8f
private const val BAR_PHASE = 0.9f

/** A small grey tag on a picture of a screen ("Hidden screen", board R01). */
@Composable
public fun ModeScreenTag(text: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    ModeText(text, ModeTextStyle.FootnoteEmphasis, modifier.background(c.fill, Capsule).padding(horizontal = ModeSpacing.m, vertical = ModeSpacing.xs), color = c.textSecondary)
}

/**
 * Mode in a small floating window while it works on the hidden screen (board R02): the screen as Mode last saw
 * it, and a dark bar with Mo at work and what it's in.
 */
@Composable
public fun ModePipView(screen: androidx.compose.ui.graphics.ImageBitmap?, label: String, title: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Column(modifier.background(c.card)) {
        Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) {
            if (screen != null) {
                androidx.compose.foundation.Image(screen, null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop, alignment = Alignment.TopCenter)
            } else {
                ModeMo(MoMood.Working, ModeSize.talkMoCard)
            }
        }
        Row(
            Modifier.fillMaxWidth().background(c.hero).padding(horizontal = ModeSpacing.s, vertical = ModeSpacing.xs),
            horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ModeMo(MoMood.Working, ModeSize.talkMoCard)
            Column(Modifier.weight(1f)) {
                ModeText(label, ModeTextStyle.Caption, color = c.heroSecondary, maxLines = 1)
                ModeText(title, ModeTextStyle.FootnoteEmphasis, color = c.heroText, maxLines = 1)
            }
        }
    }
}
