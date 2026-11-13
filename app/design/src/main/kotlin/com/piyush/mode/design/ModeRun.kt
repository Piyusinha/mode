package com.piyush.mode.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import kotlinx.coroutines.delay

/** Where a plan item is: done, now, or next. */
public enum class ModeSegmentState { Done, Now, Next }
public data class ModeSegment(val label: String, val state: ModeSegmentState)

/** "Live · step 9 · 1:24" at the top of the live run, ticking while it runs. */
@Composable
public fun ModeRunBadge(text: String, sinceMs: Long?, paused: Boolean, modifier: Modifier = Modifier) {
    val c = Mode.colors
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sinceMs, paused) {
        while (sinceMs != null && !paused) {
            now = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }
    val line = if (sinceMs == null || paused) text else {
        val s = ((now - sinceMs) / 1000).coerceAtLeast(0)
        "$text ${s / 60}:${(s % 60).toString().padStart(2, '0')}"
    }
    Row(
        modifier.height(ModeSize.liveBadgeHeight).modeShadow(Capsule).background(c.card, Capsule).padding(horizontal = ModeSpacing.m + ModeSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ModeSize.liveDot).background(if (paused) c.textSecondary else c.brand, CircleShape))
        ModeText(line, ModeTextStyle.SubheadlineEmphasis, maxLines = 1)
    }
}

/** The live run's panel: Mo, the task, what it's doing now, the last steps as bars, Pause and Stop. */
@Composable
public fun ModeRunPanel(
    task: String,
    now: String,
    segments: List<ModeSegment>,
    paused: Boolean,
    words: ModeRunningWords,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.livePanel)
    Column(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.livePanelHeight).modeShadow(shape).background(c.card, shape)
            .padding(start = ModeSpacing.xl, end = ModeSpacing.xl, top = ModeSpacing.s + ModeSpacing.xxs, bottom = ModeSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.m),
    ) {
        Box(Modifier.fillMaxWidth(), Alignment.Center) { Box(Modifier.size(ModeSize.sheetHandle, ModeSize.sheetHandleHeight).background(c.ring, Capsule)) }
        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically) {
            ModeMo(if (paused) MoMood.Sleep else MoMood.Working, ModeSize.moPanel)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                ModeText(task, ModeTextStyle.Footnote, color = c.textSecondary, maxLines = 1)
                ModeText(now, ModeTextStyle.Title3, maxLines = 2)
            }
        }
        if (segments.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s - ModeSpacing.xxs)) {
                segments.forEach { Box(Modifier.weight(1f).height(ModeSize.progressBar).background(if (it.state == ModeSegmentState.Done) c.brand else c.track, Capsule)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                segments.forEachIndexed { i, segment ->
                    val align = when {
                        segments.size < 2 || i == 0 -> TextAlign.Start
                        i == segments.lastIndex -> TextAlign.End
                        else -> TextAlign.Center
                    }
                    ModeText(
                        segment.label, if (segment.state == ModeSegmentState.Now) ModeTextStyle.CaptionEmphasis else ModeTextStyle.Caption, Modifier.weight(1f),
                        color = if (segment.state == ModeSegmentState.Now) c.tint else c.textSecondary, align = align, maxLines = 1,
                    )
                }
            }
        }
        Row(Modifier.padding(top = ModeSpacing.s), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s + ModeSpacing.xxs)) {
            PanelButton(if (paused) words.resume else words.pause, c.textPrimary, c.cardRaised, onPauseToggle, Modifier.weight(1f))
            PanelButton(words.stop, c.danger, c.tokens.tileRed, onStop, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PanelButton(title: String, ink: androidx.compose.ui.graphics.Color, fill: androidx.compose.ui.graphics.Color, onClick: () -> Unit, modifier: Modifier) {
    val (press, alpha) = rememberPress()
    Box(
        modifier.defaultMinSize(minHeight = ModeSize.fieldHeight).alpha(alpha).background(fill, Capsule)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick),
        Alignment.Center,
    ) { ModeText(title, ModeTextStyle.Headline, color = ink) }
}

/** How a task ended, as a coloured word with its icon. */
public enum class ModeBadgeTone { Success, Neutral, Warning, Danger }

@Composable
public fun ModeBadge(text: String, icon: ModeIcon, tone: ModeBadgeTone, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val color = when (tone) {
        ModeBadgeTone.Success -> c.success
        ModeBadgeTone.Neutral -> c.textSecondary
        ModeBadgeTone.Warning -> c.warning
        ModeBadgeTone.Danger -> c.danger
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
        ModeIconImage(icon, ModeIconSize.small, color)
        ModeText(text, ModeTextStyle.CaptionEmphasis, color = color)
    }
}

/** The top of a task's screens: Mo, the task, and a line. */
@Composable
public fun ModeScreensHeader(mood: MoMood, title: String, subtitle: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        ModeMo(mood, ModeSize.moPanel)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            ModeText(title, ModeTextStyle.Heading, Modifier.semantics { heading() })
            ModeText(subtitle, ModeTextStyle.Subheadline, color = Mode.colors.textSecondary, maxLines = 2)
        }
    }
}

/** One screenshot in a task's grid: its number on the corner and what Mode did there under it. */
@Composable
public fun ModeScreenCell(number: String, label: String, image: ImageBitmap?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.screenCell)
    Column(modifier.clickable(role = Role.Button, indication = null, interactionSource = null, onClick = onClick), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s - ModeSpacing.xxs)) {
        Box(Modifier.fillMaxWidth().height(ModeSize.screenCell).modeShadow(shape).background(c.card, shape).clip(shape)) {
            if (image != null) Image(image, null, Modifier.fillMaxWidth().height(ModeSize.screenCell), contentScale = ContentScale.Crop, alignment = Alignment.TopCenter)
            ModeText(
                number, ModeTextStyle.CaptionEmphasis,
                Modifier.padding(ModeSpacing.s - ModeSpacing.xxs).defaultMinSize(ModeSize.screenBadge, ModeSize.screenBadge)
                    .background(c.cardRaised, Capsule).padding(horizontal = ModeSpacing.s - ModeSpacing.xxs),
                align = TextAlign.Center,
            )
        }
        ModeText(label, ModeTextStyle.Caption, color = c.textSecondary, maxLines = 1)
    }
}

/** A note on Repeat with Mo beside it. */
@Composable
public fun ModeRepeatNote(text: String, modifier: Modifier = Modifier, mood: MoMood = MoMood.Idle) {
    val c = Mode.colors
    Row(
        modifier.fillMaxWidth().background(c.card, RoundedCornerShape(ModeRadius.repeatNote)).padding(horizontal = ModeSpacing.m + ModeSpacing.xxs, vertical = ModeSpacing.s + ModeSpacing.xxs),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeMo(mood, ModeSize.moCard)
        ModeText(text, ModeTextStyle.Subheadline, Modifier.weight(1f), color = c.textSecondary)
    }
}

/** Repeat's top: Cancel on the left, the title, Save (ink) on the right. */
@Composable
public fun ModeRepeatHeader(title: String, cancel: String, save: String, canSave: Boolean, onCancel: () -> Unit, onSave: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Box(modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.touch), Alignment.Center) {
        ModeText(title, ModeTextStyle.Section, Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val (press, alpha) = rememberPress()
            Box(
                Modifier.height(ModeSize.touch).alpha(alpha).modeShadow(Capsule).background(c.card, Capsule)
                    .clickable(press, indication = null, role = Role.Button, onClick = onCancel).padding(horizontal = ModeSpacing.l),
                Alignment.Center,
            ) { ModeText(cancel, ModeTextStyle.Callout) }
            Box(Modifier.weight(1f))
            ModeButton(save, onSave, role = ModeButtonRole.PillProminent, enabled = canSave)
        }
    }
}

/** A time picked on two wheels, hours and minutes, the middle row chosen. */
@Composable
public fun ModeTimeWheel(hour: Int, minute: Int, onChange: (Int, Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(
        modifier.fillMaxWidth().height(ModeSize.timeWheel).background(c.card, RoundedCornerShape(ModeRadius.card)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Wheel(HOURS, hour) { onChange(it, minute) }
        ModeText(TIME_SEPARATOR, ModeTextStyle.Title2)
        Wheel(MINUTES, minute) { onChange(hour, it) }
    }
}

@Composable
private fun Wheel(count: Int, value: Int, onPick: (Int) -> Unit) {
    val c = Mode.colors
    val state = rememberLazyListState(initialFirstVisibleItemIndex = value)
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress to state.firstVisibleItemIndex }.collect { (scrolling, first) ->
            if (!scrolling && first != value) onPick(first.coerceIn(0, count - 1))
        }
    }
    val pad = (ModeSize.timeWheel - ModeSize.timeWheelRow) / 2
    LazyColumn(
        Modifier.width(ModeSize.timeWheelColumn).height(ModeSize.timeWheel),
        state = state,
        contentPadding = PaddingValues(vertical = pad),
        flingBehavior = rememberSnapFlingBehavior(state),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        items(count) { i ->
            val picked = i == value
            Box(Modifier.height(ModeSize.timeWheelRow), Alignment.Center) {
                ModeText(if (count == MINUTES) i.toString().padStart(2, '0') else i.toString(), if (picked) ModeTextStyle.Title2 else ModeTextStyle.Body, color = if (picked) c.textPrimary else c.textSecondary)
            }
        }
    }
}

private const val TICK_MS = 1_000L
private const val HOURS = 24
private const val MINUTES = 60
private const val TIME_SEPARATOR = ":"

/** A floating switch between two views (Grid / Play): the picked one in ink. */
@Composable
public fun <T> ModeGlassSwitch(options: List<ModeTabItem<T>>, selection: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(modifier.modeShadow(Capsule).background(c.card, Capsule).padding(ModeSpacing.xs), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs)) {
        options.forEach { option ->
            val picked = option.id == selection
            val ink = if (picked) c.onAccent else c.textPrimary
            Row(
                Modifier.height(ModeSize.touch).background(if (picked) c.accent else c.card, Capsule)
                    .clickable(role = Role.Tab, indication = null, interactionSource = null) { onSelect(option.id) }
                    .padding(horizontal = ModeSpacing.l + ModeSpacing.xxs),
                horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s - ModeSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ModeIconImage(option.icon, ModeIconSize.small, ink)
                ModeText(option.title, ModeTextStyle.SubheadlineEmphasis, color = ink)
            }
        }
    }
}

/** "What should Mode have done?" for a step it got wrong: the words, then just this time or every time. */
@Composable
public fun ModeFixStepForm(
    title: String,
    stepLine: String,
    prompt: String,
    text: String,
    onText: (String) -> Unit,
    everyTime: Boolean,
    onEveryTime: (Boolean) -> Unit,
    justThisTime: String,
    everyTimeTitle: String,
    note: String,
    save: String,
    onSave: () -> Unit,
) {
    val c = Mode.colors
    Column(Modifier.fillMaxWidth().padding(horizontal = ModeSpacing.xl).padding(top = ModeSpacing.l, bottom = ModeSpacing.xl), verticalArrangement = Arrangement.spacedBy(ModeSpacing.chatGap)) {
        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically) {
            ModeMo(MoMood.YourTurn, ModeSize.moChatCard)
            ModeText(title, ModeTextStyle.Title2, Modifier.semantics { heading() })
        }
        ModeText(stepLine, ModeTextStyle.Subheadline, color = c.textSecondary)
        Box(Modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.chatFixField).background(c.cardRaised, RoundedCornerShape(ModeRadius.chatFixField)).padding(horizontal = ModeSpacing.chatGap, vertical = ModeSpacing.m)) {
            if (text.isEmpty()) ModeText(prompt, ModeTextStyle.Callout, color = c.textSecondary)
            androidx.compose.foundation.text.BasicTextField(
                text, onText, Modifier.fillMaxWidth(), textStyle = ModeTextStyle.Callout.style(c.textPrimary),
                minLines = FIX_MIN_LINES, maxLines = FIX_MAX_LINES, cursorBrush = androidx.compose.ui.graphics.SolidColor(c.tint),
            )
        }
        ModePillSegments(listOf(false to justThisTime, true to everyTimeTitle), everyTime, onEveryTime)
        ModeText(note, ModeTextStyle.Footnote, color = c.textSecondary)
        ModeButton(save, onSave, fullWidth = true, enabled = text.isNotBlank())
    }
}

private const val FIX_MIN_LINES = 3
private const val FIX_MAX_LINES = 6

/** The days a repeat runs: one round button per day, Monday first, the chosen ones in ink. */
@Composable
public fun ModeDayPicker(labels: List<String>, selected: Set<Int>, onToggle: (Int) -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEachIndexed { i, label ->
            val day = i + 1
            val on = day in selected
            Box(
                Modifier.size(ModeSize.touch).clickable(role = Role.Checkbox, indication = null, interactionSource = null) { onToggle(day) }
                    .semantics { this.selected = on },
                Alignment.Center,
            ) {
                Box(Modifier.size(ModeSize.dayButton).background(if (on) c.accent else c.card, CircleShape), Alignment.Center) {
                    ModeText(label, ModeTextStyle.SubheadlineEmphasis, color = if (on) c.onAccent else c.textPrimary)
                }
            }
        }
    }
}
