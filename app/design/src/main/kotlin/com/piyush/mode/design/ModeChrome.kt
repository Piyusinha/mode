package com.piyush.mode.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/** One tab in the bar: what it is, its icon and its name. */
public data class ModeTabItem<T>(val id: T, val icon: ModeIcon, val title: String)

/**
 * The bottom of the main screens: the running task (when given) above a white capsule of tabs, the
 * picked one on a raised lens, and the ink Speak button beside it.
 */
@Composable
public fun <T> ModeTabBar(
    items: List<ModeTabItem<T>>,
    selection: T,
    onSelect: (T) -> Unit,
    speakLabel: String,
    onSpeak: () -> Unit,
    modifier: Modifier = Modifier,
    player: @Composable () -> Unit = {},
) {
    val c = Mode.colors
    Column(modifier.fillMaxWidth().padding(horizontal = ModeSpacing.screenEdge), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
        player()
        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).height(ModeSize.tabBarHeight).modeShadow(Capsule).background(c.card, Capsule).padding(ModeSpacing.xs),
                horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xxs),
            ) {
                items.forEach { item ->
                    val picked = item.id == selection
                    val lens by animateColorAsState(if (picked) c.cardRaised else c.card.copy(alpha = 0f), label = "lens")
                    val ink = if (picked) c.textPrimary else c.textSecondary
                    Column(
                        Modifier.weight(1f).fillMaxHeight().background(lens, Capsule)
                            .clickable(role = Role.Tab, indication = null, interactionSource = null) { onSelect(item.id) }
                            .semantics { selected = picked },
                        verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        ModeIconImage(item.icon, ModeIconSize.regular, ink)
                        ModeText(item.title, if (picked) ModeTextStyle.CaptionEmphasis else ModeTextStyle.Caption, color = ink, maxLines = 1)
                    }
                }
            }
            ModeIconButton(ModeIcon.Ask, speakLabel, onSpeak, prominent = true, size = ModeSize.speakButton)
        }
    }
}

/** What a running task's card or bar says, and what it can do. */
public class ModeRunningWords(public val pause: String, public val resume: String, public val stop: String)

/**
 * The running task. [compact]: a white bar above the tabs (every tab but Home). Otherwise Home's dark
 * card, with Mo, the status, the task, Pause and Stop, and the plan as bars under it.
 */
@Composable
public fun ModeMiniPlayer(
    title: String,
    status: String,
    paused: Boolean,
    words: ModeRunningWords,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Pair<Int, Int>? = null,
    compact: Boolean = false,
) {
    val c = Mode.colors
    val mood = if (paused) MoMood.Sleep else MoMood.Working
    val (press, alpha) = rememberPress()
    if (compact) {
        Row(
            modifier.fillMaxWidth().height(ModeSize.miniPlayerHeight).modeShadow(Capsule).background(c.card, Capsule)
                .padding(start = ModeSpacing.s + ModeSpacing.xxs, end = ModeSpacing.xs + ModeSpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                Modifier.weight(1f).alpha(alpha).clickable(press, indication = null, role = Role.Button, onClick = onOpen),
                horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeMo(mood, ModeSize.moBar)
                Column(Modifier.weight(1f)) {
                    ModeText(title, ModeTextStyle.SubheadlineEmphasis, maxLines = 1)
                    ModeText(status, ModeTextStyle.Caption, color = c.textSecondary, maxLines = 1)
                }
            }
            RoundControl(if (paused) ModeIcon.Play else ModeIcon.Pause, if (paused) words.resume else words.pause, c.textPrimary, Color.Transparent, onPauseToggle)
            StopControl(words.stop, c.danger, Color.Transparent, onStop)
        }
    } else {
        val shape = RoundedCornerShape(ModeRadius.hero)
        Column(
            modifier.fillMaxWidth().background(c.hero, shape).padding(start = ModeSpacing.l, end = ModeSpacing.m, top = ModeSpacing.l, bottom = ModeSpacing.l),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.m + ModeSpacing.xxs),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).defaultMinSize(minHeight = ModeSize.touch).alpha(alpha)
                        .clickable(press, indication = null, role = Role.Button, onClick = onOpen),
                    horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ModeMo(mood, ModeSize.moHero)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                        ModeText(status, ModeTextStyle.Footnote, color = c.heroSecondary, maxLines = 1)
                        ModeText(title, ModeTextStyle.Title3, color = c.heroText, maxLines = 2)
                    }
                }
                RoundControl(
                    if (paused) ModeIcon.Play else ModeIcon.Pause, if (paused) words.resume else words.pause,
                    c.heroText, c.onDarkFill, onPauseToggle,
                )
                StopControl(words.stop, c.stopOnDark, c.stopDisc, onStop)
            }
            if (progress != null && progress.second > 0) ModeStepBars(progress.first, progress.second)
        }
    }
}

@Composable
private fun RoundControl(icon: ModeIcon, label: String, foreground: Color, background: Color, onClick: () -> Unit) {
    val (press, alpha) = rememberPress()
    Box(
        Modifier.size(ModeSize.touch).alpha(alpha).background(background, CircleShape)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        Alignment.Center,
    ) { ModeIconImage(icon, ModeIconSize.small, foreground) }
}

@Composable
private fun StopControl(label: String, color: Color, background: Color, onClick: () -> Unit) {
    val (press, alpha) = rememberPress()
    Box(
        Modifier.size(ModeSize.touch).alpha(alpha).background(background, CircleShape)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick).semantics { contentDescription = label },
        Alignment.Center,
    ) { Box(Modifier.size(ModeSize.stopSquare).background(color, RoundedCornerShape(ModeSize.stopSquareRadius))) }
}

/** The plan as bars on the dark card: done ones in Mo's orange. */
@Composable
public fun ModeStepBars(done: Int, total: Int, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(modifier.fillMaxWidth().padding(end = ModeSpacing.xs), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s - ModeSpacing.xxs)) {
        for (i in 0 until maxOf(total, 1)) {
            Box(Modifier.weight(1f).height(ModeSize.progressBar).background(if (i < done) c.brand else c.onDarkTrack, Capsule))
        }
    }
}

/** Home's top: the greeting, and the big question under it. */
@Composable
public fun ModeHomeHeader(greeting: String, title: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
        ModeText(greeting, ModeTextStyle.Subheadline, color = Mode.colors.textSecondary)
        ModeText(title, ModeTextStyle.TabTitle, Modifier.semantics { heading() })
    }
}

/** A suggestion on Home: an icon in a circle, the task and why it's suggested, on a white card. */
@Composable
public fun ModeSuggestionCard(icon: ModeIcon, title: String, subtitle: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.card)
    val (press, alpha) = rememberPress()
    Column(
        modifier.width(ModeSize.suggestionWidth).defaultMinSize(minHeight = ModeSize.suggestionHeight).fillMaxHeight().alpha(alpha)
            .background(c.card, shape).clickable(press, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = ModeSpacing.l, vertical = ModeSpacing.m + ModeSpacing.xxs),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Box(Modifier.size(ModeSize.iconCircle).background(c.cardRaised, CircleShape), Alignment.Center) { ModeIconImage(icon, ModeIconSize.regular) }
        Column(Modifier.padding(top = ModeSpacing.s), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            ModeText(title, ModeTextStyle.CalloutEmphasis, maxLines = 2)
            ModeText(subtitle, ModeTextStyle.Footnote, color = c.textSecondary, maxLines = 1)
        }
    }
}

/** An easy task to try: a white pill. */
@Composable
public fun ModeTryPill(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val (press, alpha) = rememberPress()
    Box(
        modifier.defaultMinSize(minHeight = ModeSize.tryPill).alpha(alpha).background(Mode.colors.card, Capsule)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick).padding(horizontal = ModeSpacing.l + ModeSpacing.xxs),
        Alignment.Center,
    ) { ModeText(text, ModeTextStyle.SubheadlineEmphasis, maxLines = 1) }
}

/** Nothing here yet: an icon, a title and a line. */
@Composable
public fun ModeEmptyState(icon: ModeIcon, title: String, message: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Column(
        modifier.fillMaxWidth().padding(ModeSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ModeIconImage(icon, ModeIconSize.status, c.tint)
        ModeText(title, ModeTextStyle.Title2, align = androidx.compose.ui.text.style.TextAlign.Center)
        ModeText(message, ModeTextStyle.Callout, color = c.textSecondary, align = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

/** A short ink toast with an Undo button ("Tips hidden · Undo"). */
@Composable
public fun ModeUndoToast(title: String, detail: String, undo: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.tipToast).background(c.accent, Capsule)
            .padding(start = ModeSpacing.l + ModeSpacing.xxs, end = ModeSpacing.xs + ModeSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            ModeText(title, ModeTextStyle.SubheadlineEmphasis, color = c.onAccent)
            ModeText(detail, ModeTextStyle.Footnote, color = c.onAccent.copy(alpha = ModeOpacity.toastDetail))
        }
        val (press, alpha) = rememberPress()
        Box(
            Modifier.defaultMinSize(minHeight = ModeSize.touch).alpha(alpha).background(c.onAccent.copy(alpha = ModeOpacity.toastButton), Capsule)
                .clickable(press, indication = null, role = Role.Button, onClick = onUndo).padding(horizontal = ModeSpacing.l + ModeSpacing.xxs),
            Alignment.Center,
        ) { ModeText(undo, ModeTextStyle.SubheadlineEmphasis, color = c.onAccent) }
    }
}
