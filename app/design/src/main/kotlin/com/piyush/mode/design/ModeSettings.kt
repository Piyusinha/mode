package com.piyush.mode.design

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign

/** A page's big title, and a line under it. */
@Composable
public fun ModePageTitle(title: String, modifier: Modifier = Modifier, subtitle: String? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = ModeSpacing.noteInset), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
        ModeText(title, ModeTextStyle.LargeTitle, Modifier.semantics { heading() })
        if (subtitle != null) ModeText(subtitle, ModeTextStyle.Callout, color = Mode.colors.textSecondary)
    }
}

/** A quiet note under a card. */
@Composable
public fun ModeFootnote(text: String, modifier: Modifier = Modifier, style: ModeTextStyle = ModeTextStyle.Footnote, icon: ModeIcon? = null) {
    val c = Mode.colors
    Row(modifier.fillMaxWidth().padding(horizontal = ModeSpacing.noteInset), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
        if (icon != null) ModeIconImage(icon, ModeIconSize.small, c.textSecondary)
        ModeText(text, style, color = c.textSecondary)
    }
}

/** A dot and a short line ("Connected · Plus plan"). */
@Composable
public fun ModeStatusLine(text: String, dot: Color, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(ModeSize.statusDot).background(dot, CircleShape))
        ModeText(text, ModeTextStyle.Footnote, color = Mode.colors.textSecondary)
    }
}

/** A row with a provider's logo in a dark tile, its name, its value, and (when broken) why. */
@Composable
public fun ModeLogoValueRow(title: String, value: String, logo: ModeLogo, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String? = null, problem: String? = null) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Row(
        modifier.fillMaxWidth()
            .defaultMinSize(minHeight = if (problem != null) ModeSize.logoRowProblem else if (tag != null) ModeSize.logoRowTagged else ModeSize.logoRow)
            .alpha(alpha).clickable(press, indication = null, role = Role.Button, onClick = onClick).padding(vertical = ModeSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.clearAndSetSemantics {}.size(ModeSize.badge).background(c.textPrimary, RoundedCornerShape(ModeSize.smallTileRadius)), Alignment.Center) {
            ModeLogoImage(logo, ModeSize.iconSmall + ModeSpacing.xxs, c.page)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModeText(title, ModeTextStyle.Callout, Modifier.weight(1f))
                if (problem != null) ModeText(value, ModeTextStyle.Subheadline, color = c.textSecondary, maxLines = 1)
            }
            if (problem != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs)) {
                    ModeIconImage(ModeIcon.Warning, ModeIconSize.chevron, c.tokens.tileOrangeText, Modifier.padding(top = ModeSpacing.xxs))
                    ModeText(problem, ModeTextStyle.Footnote, color = c.tokens.tileOrangeText)
                }
            } else if (tag != null) {
                ModeText(tag, ModeTextStyle.CaptionEmphasis, Modifier.background(c.cardRaised, Capsule).padding(horizontal = ModeSpacing.s, vertical = ModeSpacing.xxs))
            }
        }
        if (problem == null) ModeText(value, ModeTextStyle.Subheadline, color = c.textSecondary, maxLines = 1)
        ModeChevron()
    }
}

/** A name and its value on the right (with a dot when it has a state); a chevron when it opens something. */
@Composable
public fun ModeValueRow(title: String, value: String, modifier: Modifier = Modifier, dot: Color? = null, onClick: (() -> Unit)? = null) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = if (onClick == null) ModeSize.valueRow else ModeSize.rowMin)
            .then(if (onClick != null) Modifier.alpha(alpha).clickable(press, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = ModeSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeText(title, ModeTextStyle.Callout)
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            if (dot != null) Box(Modifier.size(ModeSize.statusDot).background(dot, CircleShape))
            ModeText(value, ModeTextStyle.Callout, color = c.textSecondary, align = TextAlign.End, maxLines = 2)
        }
        if (onClick != null) ModeChevron()
    }
}

/** A switch: green when on. */
@Composable
public fun ModeSwitch(on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, label: String? = null) {
    val c = Mode.colors
    val track by animateColorAsState(if (on) c.success else c.track, label = "track")
    val travel by animateDpAsState(if (on) ModeSize.switchWidth - ModeSize.switchThumb - (ModeSize.switchHeight - ModeSize.switchThumb) else ModeSpacing.none, label = "thumb")
    Box(
        modifier.size(ModeSize.switchWidth, ModeSize.switchHeight).background(track, Capsule)
            .clickable(role = Role.Switch, indication = null, interactionSource = null) { onChange(!on) }
            .semantics { if (label != null) contentDescription = label; stateDescription = if (on) "on" else "off" }
            .padding((ModeSize.switchHeight - ModeSize.switchThumb) / 2),
        Alignment.CenterStart,
    ) { Box(Modifier.offset(x = travel).size(ModeSize.switchThumb).background(Color.White, CircleShape)) }
}

/** A setting that's on or off: its name, a line under it, and the switch. */
@Composable
public fun ModeToggleRow(title: String, on: Boolean, onChange: (Boolean) -> Unit, modifier: Modifier = Modifier, subtitle: String? = null) {
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.rowMin).clickable(indication = null, interactionSource = null) { onChange(!on) }
            .padding(vertical = ModeSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            ModeText(title, ModeTextStyle.Callout)
            if (subtitle != null) ModeText(subtitle, ModeTextStyle.Footnote, color = Mode.colors.textSecondary)
        }
        ModeSwitch(on, onChange, label = title)
    }
}

/** A number to step up or down (a cost limit): its name and value, and − / +. */
@Composable
public fun ModeStepperRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, minusLabel: String, plusLabel: String, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.rowMin).padding(vertical = ModeSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            ModeText(title, ModeTextStyle.Callout)
            ModeText(value, ModeTextStyle.Footnote, color = c.textSecondary)
        }
        Row(Modifier.background(c.cardRaised, Capsule)) {
            StepperHalf(MINUS, minusLabel, onMinus)
            StepperHalf(PLUS, plusLabel, onPlus)
        }
    }
}

@Composable
private fun StepperHalf(glyph: String, label: String, onClick: () -> Unit) {
    val (press, alpha) = rememberPress()
    Box(
        Modifier.size(ModeSize.touch, ModeSize.pillButton).alpha(alpha).clickable(press, indication = null, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        Alignment.Center,
    ) { ModeText(glyph, ModeTextStyle.Title3) }
}

/** The selection mark on a choice: an empty ring, or a filled tick. */
@Composable
public fun ModeRadio(selected: Boolean, modifier: Modifier = Modifier) {
    val c = Mode.colors
    if (selected) {
        Box(modifier.size(ModeSize.radio).background(c.accent, CircleShape), Alignment.Center) { ModeIconImage(ModeIcon.Checkmark, ModeIconSize.chevron, c.onAccent) }
    } else {
        Box(modifier.size(ModeSize.radio).border(ModeSize.progressRing, c.ring, CircleShape))
    }
}

/** A coloured tag beside a choice's name ("Recommended", "Cheapest"). */
public data class ModeChoiceTag(val text: String, val background: Color, val foreground: Color)

/** One choice: its tile, its name with a tag, a line, and the selection mark. */
@Composable
public fun ModeChoiceRow(
    title: String,
    detail: String,
    leading: ModeRowLeading,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tag: ModeChoiceTag? = null,
    emphasis: ModeRowEmphasis = ModeRowEmphasis.Prominent,
) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = if (leading == ModeRowLeading.None) ModeSize.rowMin else ModeSize.tileRow).alpha(alpha)
            .clickable(press, indication = null, role = Role.RadioButton, onClick = onClick).semantics { this.selected = selected }
            .padding(vertical = if (emphasis == ModeRowEmphasis.Prominent) ModeSpacing.headerGap else ModeSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeLeading(leading)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                ModeText(title, if (emphasis == ModeRowEmphasis.Prominent) ModeTextStyle.CalloutEmphasis else ModeTextStyle.Callout, Modifier.weight(1f, fill = false))
                if (tag != null) {
                    ModeText(tag.text, ModeTextStyle.CaptionEmphasis, Modifier.background(tag.background, Capsule).padding(horizontal = ModeSpacing.s, vertical = ModeSpacing.xxs), color = tag.foreground, maxLines = 1)
                }
            }
            ModeText(detail, ModeTextStyle.Footnote, color = c.textSecondary, maxLines = DETAIL_LINES)
        }
        ModeRadio(selected)
    }
}

/** A search box in a white capsule, with a clear button once there's text. */
@Composable
public fun ModeSearchField(prompt: String, text: String, onText: (String) -> Unit, modifier: Modifier = Modifier, clearLabel: String = prompt) {
    val c = Mode.colors
    Row(
        modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.touch).modeShadow(Capsule).background(c.card, Capsule).padding(horizontal = ModeSpacing.headerGap),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeIconImage(ModeIcon.Search, ModeIconSize.small, c.textSecondary)
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) ModeText(prompt, ModeTextStyle.Body, color = c.textSecondary, maxLines = 1)
            BasicTextField(
                text, onText, Modifier.fillMaxWidth().semantics { contentDescription = prompt }, textStyle = ModeTextStyle.Body.style(c.textPrimary),
                singleLine = true, cursorBrush = SolidColor(c.tint), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, autoCorrectEnabled = false),
            )
        }
        if (text.isNotEmpty()) {
            Box(Modifier.size(ModeSize.touch).clickable(role = Role.Button, indication = null, interactionSource = null) { onText("") }.semantics { contentDescription = clearLabel }, Alignment.Center) {
                ModeIconImage(ModeIcon.Close, ModeIconSize.small, c.textSecondary)
            }
        }
    }
}

/** A filter chip: ink when picked, white otherwise. */
@Composable
public fun ModeFilterChip(title: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Mode.colors
    Box(
        modifier.defaultMinSize(minHeight = ModeSize.touch).clickable(role = Role.Tab, indication = null, interactionSource = null, onClick = onClick).semantics { this.selected = selected },
        Alignment.Center,
    ) {
        ModeText(
            title, ModeTextStyle.SubheadlineEmphasis,
            Modifier.defaultMinSize(minHeight = ModeSize.filterChip).background(if (selected) c.accent else c.card, Capsule).padding(horizontal = ModeSpacing.headerGap, vertical = ModeSpacing.s - ModeSpacing.xxs),
            color = if (selected) c.onAccent else c.textPrimary,
        )
    }
}

private const val DETAIL_LINES = 3
private const val MINUS = "−"
private const val PLUS = "+"

/**
 * An AI provider in a list: its tile, name with a tag, its state, and Connect (when not connected) or the
 * selection mark. Tapping a connected one opens its page ([onOpen]) or picks it.
 */
@Composable
public fun ModeProviderRow(
    title: String,
    status: String,
    tile: ModeRowLeading,
    selected: Boolean,
    connected: Boolean,
    connectTitle: String,
    onSelect: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
    tag: ModeChoiceTag? = null,
    radioLeading: Boolean = false,
    onOpen: (() -> Unit)? = null,
) {
    val c = Mode.colors
    Row(modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.tileRow).padding(vertical = ModeSpacing.m), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        val main = if (connected) onOpen ?: onSelect else if (radioLeading) onSelect else onConnect
        Row(
            Modifier.weight(1f).clickable(role = Role.Button, indication = null, interactionSource = null, onClick = main).semantics { this.selected = selected },
            horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically,
        ) {
            if (radioLeading) ModeRadio(selected)
            ModeLeading(tile)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs + ModeSpacing.xxs), verticalAlignment = Alignment.CenterVertically) {
                    ModeText(title, ModeTextStyle.CalloutEmphasis, maxLines = 1)
                    if (tag != null) {
                        ModeText(tag.text, ModeTextStyle.CaptionEmphasis, Modifier.background(tag.background, Capsule).padding(horizontal = ModeSpacing.s, vertical = ModeSpacing.xxs), color = tag.foreground, maxLines = 1)
                    }
                }
                ModeStatusLine(status, if (connected) c.success else c.ring)
            }
        }
        if (!connected) {
            ModeButton(connectTitle, onConnect, role = ModeButtonRole.PillProminent)
        } else if (!radioLeading) {
            Box(Modifier.size(ModeSize.touch).clickable(role = Role.RadioButton, indication = null, interactionSource = null, onClick = onSelect), Alignment.CenterEnd) { ModeRadio(selected) }
        }
    }
}
