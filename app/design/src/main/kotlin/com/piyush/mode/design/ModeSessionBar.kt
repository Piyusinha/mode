package com.piyush.mode.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription

/** What an open voice session is doing, as its bar shows it. */
public enum class ModeSessionKind {
    /** Listening for the next thing to do. */
    Listening,
    /** Mode is talking: the title is what it says. */
    Speaking,
    /** A task is running in another app (the mic is off meanwhile). */
    Working,
    /** Mode asked something only the user can answer. */
    NeedsYou,
    /** The session is open but not listening. */
    Paused,
}

/** What the bar's buttons are called. */
public class ModeSessionWords(
    public val open: String,
    public val pause: String,
    public val resume: String,
    public val end: String,
    public val stop: String,
    public val answer: String,
)

/**
 * An open voice session, minimised (board: Voice session flow › The session bar). It sits above the tab bar, or
 * above a chat's message bar, and always says what the session is doing now. A tap on it opens the voice screen
 * again; the mic pauses or resumes listening; × ends the session. Draws what it's given, sends taps back.
 */
@Composable
public fun ModeSessionBar(
    kind: ModeSessionKind,
    title: String,
    subtitle: String,
    words: ModeSessionWords,
    onOpen: () -> Unit,
    onMic: () -> Unit,
    onEnd: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Mode.colors
    val asking = kind == ModeSessionKind.NeedsYou
    val shape = RoundedCornerShape(ModeRadius.sessionBar)
    // The bar follows the theme like the tab bar; "needs you" is blue in both themes, with white words.
    val background by animateColorAsState(if (asking) c.info else c.card, tween(ModeMotion.standardMs), label = "sessionBar")
    val titleColor = if (asking) c.heroText else c.textPrimary
    val subtitleColor = if (asking) c.heroText.copy(alpha = ModeOpacity.sessionBarAskDetail) else c.textSecondary
    val (press, alpha) = rememberPress()
    Row(
        modifier.fillMaxWidth().modeShadow(shape).background(background, shape)
            .then(if (asking || c.dark) Modifier else Modifier.border(ModeSize.separator, c.separator, shape))
            .padding(start = ModeSpacing.m, end = ModeSpacing.s, top = ModeSpacing.s, bottom = ModeSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).defaultMinSize(minHeight = ModeSize.sessionBarAction).alpha(alpha)
                .clickable(press, indication = null, role = Role.Button, onClick = onOpen)
                .semantics(mergeDescendants = true) { contentDescription = words.open; stateDescription = title },
            horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            when (kind) {
                ModeSessionKind.Working -> Box(Modifier.size(ModeSize.sessionBarMo), Alignment.Center) { ModeSpinner() }
                ModeSessionKind.NeedsYou -> Unit
                ModeSessionKind.Speaking -> ModeMo(MoMood.Listening, ModeSize.sessionBarMo)
                ModeSessionKind.Paused -> ModeMo(MoMood.Sleep, ModeSize.sessionBarMo)
                ModeSessionKind.Listening -> ModeMo(MoMood.Idle, ModeSize.sessionBarMo)
            }
            Column(Modifier.weight(1f)) {
                ModeText(title, ModeTextStyle.CalloutEmphasis, color = titleColor, maxLines = 1)
                ModeText(subtitle, ModeTextStyle.Footnote, color = subtitleColor, maxLines = 1)
            }
            if (kind == ModeSessionKind.Listening) SessionWave()
        }
        when (kind) {
            ModeSessionKind.Working -> Pill(words.stop, c.brand, c.onAccent, onStop)
            ModeSessionKind.NeedsYou -> Pill(words.answer, c.card, c.info, onOpen)
            ModeSessionKind.Paused -> {
                Round(ModeIcon.Microphone, words.resume, c.brand, c.onAccent, onMic)
                Round(ModeIcon.Close, words.end, c.fill, c.textPrimary, onEnd)
            }
            ModeSessionKind.Listening, ModeSessionKind.Speaking -> {
                Round(ModeIcon.Microphone, words.pause, c.fill, c.textPrimary, onMic)
                Round(ModeIcon.Close, words.end, c.fill, c.textPrimary, onEnd)
            }
        }
    }
}

@Composable
private fun Round(icon: ModeIcon, label: String, fill: Color, tint: Color, onClick: () -> Unit) {
    val (press, alpha) = rememberPress()
    Box(
        Modifier.size(ModeSize.sessionBarAction).alpha(alpha).background(fill, CircleShape)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        Alignment.Center,
    ) { ModeIconImage(icon, ModeSize.sessionBarActionIcon, tint) }
}

@Composable
private fun Pill(label: String, fill: Color, tint: Color, onClick: () -> Unit) {
    val (press, alpha) = rememberPress()
    Box(
        Modifier.defaultMinSize(minHeight = ModeSize.sessionBarAction).alpha(alpha).background(fill, CircleShape)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = ModeSpacing.l),
        Alignment.Center,
    ) { ModeText(label, ModeTextStyle.CalloutEmphasis, color = tint, maxLines = 1) }
}

/** The bars that move while the session listens. */
@Composable
private fun SessionWave() {
    val c = Mode.colors
    val beat = rememberInfiniteTransition(label = "sessionWave")
    Row(
        Modifier.height(ModeSize.sessionBarWaveHeight).clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(ModeSize.sessionBarWaveGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeSessionWave.rest.forEachIndexed { i, tall ->
            val height by beat.animateFloat(
                ModeSessionWave.quiet, tall,
                infiniteRepeatable(tween(ModeSessionWave.beatMs), RepeatMode.Reverse, StartOffset(i * ModeSessionWave.staggerMs)),
                label = "bar",
            )
            Box(Modifier.width(ModeSize.sessionBarWaveWidth).height(ModeSize.sessionBarWaveHeight * height).background(c.brand, CircleShape))
        }
    }
}

/** The listening bars: their resting heights (fractions of the tallest), the quiet height, and the beat. */
internal object ModeSessionWave {
    val rest: List<Float> = listOf(0.45f, 1.0f, 0.7f, 0.9f)
    const val quiet: Float = 0.35f
    const val beatMs: Int = 450
    const val staggerMs: Int = 120
}
