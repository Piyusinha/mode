package com.piyush.mode.design

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.toUpperCase
import kotlinx.coroutines.delay

/** A chat's title and when it started. */
@Composable
public fun ModeChatTitle(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = ModeSpacing.chatTitleInset), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
        ModeText(title, ModeTextStyle.Title2, Modifier.semantics { heading() }, maxLines = 2)
        if (!subtitle.isNullOrEmpty()) ModeText(subtitle, ModeTextStyle.Footnote, color = Mode.colors.textSecondary)
    }
}

/** Your message: an ink bubble on the right, with how you sent it ("Typed", "Spoken", "While Mode worked"). */
@Composable
public fun ModeUserBubble(text: String, modifier: Modifier = Modifier, tag: String? = null, icon: ModeIcon = ModeIcon.Microphone) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.bubble, ModeRadius.bubble, ModeRadius.bubbleTail, ModeRadius.bubble)
    Box(modifier.fillMaxWidth(), Alignment.CenterEnd) {
        Column(
            Modifier.widthIn(max = ModeSize.bubbleMaxWidth).background(c.accent, shape)
                .padding(horizontal = ModeSpacing.chatGap, vertical = ModeSpacing.chatInline),
            verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs),
        ) {
            if (tag != null) {
                val faded = c.onAccent.copy(alpha = ModeOpacity.bubbleTag)
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    ModeIconImage(icon, ModeIconSize.chevron, faded)
                    ModeText(tag, ModeTextStyle.Caption, color = faded)
                }
            }
            ModeInlineText(text, ModeTextStyle.Callout, color = c.onAccent)
        }
    }
}

/** Mo beside a reply: its face says how the task went. */
@Composable
public fun ModeMoReply(mood: MoMood, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.chatInline)) {
        Box(Modifier.clearAndSetSemantics {}) { ModeMo(mood, ModeSize.moRow) }
        Box(Modifier.weight(1f).padding(top = ModeSpacing.xs)) { content() }
    }
}

/** "See all 11 screens": three screenshots fanned, the words, the arrow. */
@Composable
public fun ModeScreensLink(title: String, thumbnails: List<ImageBitmap>, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Row(
        modifier.defaultMinSize(minHeight = ModeSize.screensLinkHeight).alpha(alpha).modeShadow(Capsule).background(c.card, Capsule)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick)
            .padding(start = ModeSpacing.s, end = ModeSpacing.chatGap, top = ModeSpacing.chatInline, bottom = ModeSpacing.chatInline),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.chatInline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Three thumbnails fanned: each overlaps the one before it.
        Box(Modifier.clearAndSetSemantics {}) {
            val thumb = RoundedCornerShape(ModeRadius.thumb)
            val step = ModeSize.screensThumbWidth + ModeSize.screensThumbBorder * 2 - ModeSpacing.chatInline
            fun frame(i: Int) = Modifier.padding(start = step * i)
                .background(c.card, RoundedCornerShape(ModeRadius.thumb + ModeSize.screensThumbBorder)).padding(ModeSize.screensThumbBorder)
            if (thumbnails.isEmpty()) {
                listOf(c.cardRaised, c.ring, c.tintSoft).forEachIndexed { i, fill ->
                    Box(frame(i)) { Box(Modifier.size(ModeSize.screensThumbWidth, ModeSize.screensThumbHeight).background(fill, thumb)) }
                }
            } else {
                thumbnails.take(3).forEachIndexed { i, image ->
                    Box(frame(i)) {
                        Image(image, null, Modifier.size(ModeSize.screensThumbWidth, ModeSize.screensThumbHeight).clip(thumb), contentScale = ContentScale.Crop)
                    }
                }
            }
        }
        ModeText(title, ModeTextStyle.SubheadlineEmphasis)
        ModeChevron()
    }
}

/** A small button on a chat card or pill (Undo, Remember, Update in Settings). */
@Composable
public fun ModeChatPillButton(title: String, background: Color, foreground: Color, onClick: () -> Unit, height: androidx.compose.ui.unit.Dp = ModeSize.chatPillButton) {
    val (press, alpha) = rememberPress()
    Box(
        Modifier.defaultMinSize(minHeight = height).alpha(alpha)
            .background(background, Capsule).clickable(press, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = ModeSpacing.m),
        Alignment.Center,
    ) { ModeText(title, ModeTextStyle.FootnoteEmphasis, color = foreground, maxLines = 1) }
}

/** "Remembered: …" under a reply, with Undo. */
@Composable
public fun ModeRememberedChip(text: String, undo: String, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(
        modifier.background(c.tintSoft, Capsule).padding(start = ModeSpacing.m, end = ModeSpacing.chatTight, top = ModeSpacing.chatTight, bottom = ModeSpacing.chatTight),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeIconImage(ModeIcon.Memory, ModeIconSize.chevron, c.tint)
        ModeText(text, ModeTextStyle.Footnote, Modifier.weight(1f, fill = false), color = c.tint, maxLines = 2)
        ModeChatPillButton(undo, c.card, c.textPrimary, onUndo)
    }
}

/** "Also do it this way next time?" with Remember, after steering the running task. */
@Composable
public fun ModeOfferChip(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(
        modifier.padding(start = ModeSize.moRow + ModeSpacing.chatInline).background(c.infoSoft, RoundedCornerShape(ModeRadius.chatNotice))
            .padding(start = ModeSpacing.m, end = ModeSpacing.chatTight, top = ModeSpacing.chatTight, bottom = ModeSpacing.chatTight),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeIconImage(ModeIcon.Memory, ModeIconSize.chevron, c.infoText)
        ModeText(text, ModeTextStyle.FootnoteEmphasis, Modifier.weight(1f, fill = false), color = c.infoText)
        ModeChatPillButton(action, c.info, c.onInfo, onAction)
    }
}

/** A problem in a spoken chat ("Mode couldn't speak with ElevenLabs"), with a way to fix it. */
@Composable
public fun ModeChatNotice(text: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(
        modifier.fillMaxWidth().background(c.tokens.tileOrange, RoundedCornerShape(ModeRadius.chatNotice))
            .padding(start = ModeSpacing.m, end = ModeSpacing.chatInline, top = ModeSpacing.chatInline, bottom = ModeSpacing.chatInline),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.chatInline),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clearAndSetSemantics {}) { ModeMo(MoMood.Stuck, ModeSize.moNotice) }
        ModeText(text, ModeTextStyle.SubheadlineEmphasis, Modifier.weight(1f))
        ModeChatPillButton(action, c.card, c.textPrimary, onAction)
    }
}

/** A capsule button on a chat card: ink when [prominent], raised grey otherwise. */
@Composable
public fun ModeChatButton(title: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ModeIcon? = null, prominent: Boolean = false) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    val ink = if (prominent) c.onAccent else c.textPrimary
    Row(
        modifier.defaultMinSize(minHeight = ModeSize.failedButton).alpha(alpha).background(if (prominent) c.accent else c.cardRaised, Capsule)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick).padding(horizontal = ModeSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.chatTight, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) ModeIconImage(icon, ModeIconSize.small, ink)
        ModeText(title, ModeTextStyle.SubheadlineEmphasis, color = ink, maxLines = 1)
    }
}

/** A task that couldn't finish: Mo stuck, why, What went wrong and Try again. */
@Composable
public fun ModeFailedCard(title: String, detail: String, debug: String, retry: String, onDebug: () -> Unit, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.chatCard)
    Column(
        modifier.fillMaxWidth().modeShadow(shape).background(c.card, shape).padding(horizontal = ModeSpacing.l, vertical = ModeSpacing.chatGap),
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.m),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.clearAndSetSemantics {}) { ModeMo(MoMood.Stuck, ModeSize.moFailed) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s), verticalAlignment = Alignment.CenterVertically) {
                    ModeIconImage(ModeIcon.Warning, ModeIconSize.chevron, c.danger)
                    ModeText(title, ModeTextStyle.SubheadlineEmphasis, color = c.danger)
                }
                ModeText(detail, ModeTextStyle.Subheadline, color = c.textSecondary)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            ModeChatButton(debug, onDebug, Modifier.weight(1f), icon = ModeIcon.Search, prominent = true)
            ModeChatButton(retry, onRetry, icon = ModeIcon.RunAgain)
        }
    }
}

/** "Saved for next time": a tip taught in this chat, with Edit and Undo. */
@Composable
public fun ModeChatLessonCard(heading: String, context: String, text: String, edit: String, undo: String, onEdit: () -> Unit, onUndo: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.chatCard)
    Column(
        modifier.fillMaxWidth().background(c.card, shape).border(ModeSize.chatRing, c.brand, shape).padding(ModeSpacing.l),
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.chatInline),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.chatInline)) {
            Box(Modifier.clearAndSetSemantics {}.size(ModeSize.chatTileSize).background(c.lessonTile, RoundedCornerShape(ModeRadius.chatTile)), Alignment.Center) {
                ModeIconImage(ModeIcon.Tip, ModeIconSize.small, c.onInfo)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                ModeText(heading.toUpperCase(LocaleList.current), ModeTextStyle.CaptionEmphasis, color = c.tint)
                ModeText(context, ModeTextStyle.Footnote, color = c.textSecondary)
            }
        }
        ModeText(text, ModeTextStyle.CalloutEmphasis)
        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            LessonPill(edit, onEdit)
            LessonPill(undo, onUndo)
        }
    }
}

@Composable
private fun LessonPill(title: String, onClick: () -> Unit) {
    val (press, alpha) = rememberPress()
    Box(
        Modifier.defaultMinSize(minHeight = ModeSize.lessonButton).alpha(alpha).background(Mode.colors.cardRaised, Capsule)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick).padding(horizontal = ModeSpacing.chatGap),
        Alignment.Center,
    ) { ModeText(title, ModeTextStyle.SubheadlineEmphasis) }
}

/**
 * The running task in its chat: Mo working, "Working · 1:24" ticking, what it's doing now (shimmering),
 * and Stop; tapping it opens the live view.
 */
@Composable
public fun ModeWorkingCard(title: String, status: String, sinceMs: Long?, stop: String, onOpen: () -> Unit, onStop: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.chatCard)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(sinceMs) {
        while (sinceMs != null) {
            now = System.currentTimeMillis()
            delay(TICK_MS)
        }
    }
    val (press, alpha) = rememberPress()
    Row(
        modifier.fillMaxWidth().background(c.card, shape).border(ModeSize.chatRing, c.brand, shape)
            .padding(start = ModeSpacing.m, end = ModeSpacing.s, top = ModeSpacing.m, bottom = ModeSpacing.m),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier.weight(1f).alpha(alpha).clickable(press, indication = null, role = Role.Button, onClick = onOpen),
            horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clearAndSetSemantics {}) { ModeMo(MoMood.Working, ModeSize.moChatCard) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                val line = if (sinceMs == null) title else {
                    val seconds = ((now - sinceMs) / 1000).coerceAtLeast(0)
                    "$title · ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
                }
                ModeText(line, ModeTextStyle.FootnoteEmphasis, color = c.tint)
                ModeText(status, ModeTextStyle.SubheadlineEmphasis, Modifier.modeShimmer(), maxLines = 2)
            }
        }
        val (stopPress, stopAlpha) = rememberPress()
        Box(
            Modifier.size(ModeSize.touch).alpha(stopAlpha).clickable(stopPress, indication = null, role = Role.Button, onClick = onStop)
                .semantics { contentDescription = stop },
            Alignment.Center,
        ) {
            Box(Modifier.size(ModeSize.workingStop).background(c.tokens.tileRed, CircleShape), Alignment.Center) {
                Box(Modifier.size(ModeSize.stopGlyph).background(c.stopRed, RoundedCornerShape(ModeSize.stopGlyphRadius)))
            }
        }
    }
}

/** "Mode is working" over the message bar while the task runs (the box steers it). */
@Composable
public fun ModeWorkingTag(text: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(
        modifier.height(ModeSize.workingTag).background(c.tintSoft, Capsule).padding(horizontal = ModeSpacing.chatInline),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.chatTight),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ModeSize.workingDot).background(c.brand, CircleShape))
        ModeText(text, ModeTextStyle.CaptionEmphasis, color = c.tint)
    }
}

/** Share and More at the top right of a chat, in one white capsule. */
@Composable
public fun ModeChatActions(shareLabel: String, moreLabel: String, onShare: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(modifier.modeShadow(Capsule).background(c.card, Capsule).padding(horizontal = ModeSpacing.xxs)) {
        listOf(Triple(ModeIcon.Share, shareLabel, onShare), Triple(ModeIcon.More, moreLabel, onMore)).forEach { (icon, label, action) ->
            val (press, alpha) = rememberPress()
            Box(
                Modifier.size(ModeSize.touch).alpha(alpha).clickable(press, indication = null, role = Role.Button, onClick = action)
                    .semantics { contentDescription = label },
                Alignment.Center,
            ) { ModeIconImage(icon, ModeIconSize.regular) }
        }
    }
}

/** What the message bar says for each button. */
public class ModeMessageBarWords(
    public val prompt: String,
    public val add: String,
    public val send: String,
    public val speak: String,
    public val privateOn: String,
    public val privateOff: String,
)

/**
 * The chat's message bar: + (more), the box, private, and Send (or the mic while empty). While the task
 * runs ([steering]) it is just the box and a big Send, to steer it.
 */
@Composable
public fun ModeMessageBar(
    text: String,
    onText: (String) -> Unit,
    isPrivate: Boolean,
    canSend: Boolean,
    words: ModeMessageBarWords,
    onTogglePrivate: () -> Unit,
    onSend: () -> Unit,
    onSpeak: () -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
    steering: Boolean = false,
) {
    val c = Mode.colors
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.chatInline), verticalAlignment = Alignment.Bottom) {
        if (!steering) ModeIconButton(ModeIcon.Add, words.add, onAdd, size = ModeSize.composerHeight)
        val shape = RoundedCornerShape(ModeSize.composerHeight / 2)
        Row(
            Modifier.weight(1f).defaultMinSize(minHeight = ModeSize.composerHeight).modeShadow(shape).background(c.card, shape)
                .padding(start = if (steering) ModeSpacing.l + ModeSpacing.xxs else ModeSpacing.l, end = ModeSpacing.composerInset, top = ModeSpacing.composerInset, bottom = ModeSpacing.composerInset),
            horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
            verticalAlignment = Alignment.Bottom,
        ) {
            Box(Modifier.weight(1f).defaultMinSize(minHeight = ModeSize.sendButton).padding(vertical = ModeSpacing.xxs), Alignment.CenterStart) {
                if (text.isEmpty()) ModeText(words.prompt, ModeTextStyle.Callout, color = c.textSecondary, maxLines = 1)
                BasicTextField(
                    text, onText, Modifier.fillMaxWidth().semantics { contentDescription = words.prompt },
                    textStyle = ModeTextStyle.Callout.style(c.textPrimary), maxLines = MAX_LINES,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(c.tint),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                )
            }
            if (!steering) {
                Box(
                    Modifier.size(ModeSize.sendButton).clickable(role = Role.Switch, indication = null, interactionSource = null, onClick = onTogglePrivate)
                        .semantics { contentDescription = if (isPrivate) words.privateOn else words.privateOff },
                    Alignment.Center,
                ) { ModeIconImage(if (isPrivate) ModeIcon.PrivateOn else ModeIcon.PrivateOff, ModeIconSize.regular, if (isPrivate) c.tint else c.textSecondary) }
                SpeakOrSend(canSend, words, ModeSize.sendButton, onSend, onSpeak)
            }
        }
        if (steering) SpeakOrSend(canSend, words, ModeSize.composerHeight, onSend, onSpeak)
    }
}

@Composable
private fun SpeakOrSend(canSend: Boolean, words: ModeMessageBarWords, size: androidx.compose.ui.unit.Dp, onSend: () -> Unit, onSpeak: () -> Unit) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Box(
        Modifier.size(size).alpha(alpha).background(c.accent, CircleShape)
            .clickable(press, indication = null, role = Role.Button, onClick = if (canSend) onSend else onSpeak)
            .semantics { contentDescription = if (canSend) words.send else words.speak },
        Alignment.Center,
    ) { ModeIconImage(if (canSend) ModeIcon.Send else ModeIcon.Microphone, ModeIconSize.regular, c.onAccent) }
}

private const val TICK_MS = 1_000L
private const val MAX_LINES = 5
