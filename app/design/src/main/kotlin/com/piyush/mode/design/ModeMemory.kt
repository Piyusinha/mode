package com.piyush.mode.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics

/** Where a memory came from, as a small coloured tag. */
public sealed interface ModeMemoryBadge {
    public val text: String
    /** The user taught it. */
    public data class Taught(override val text: String) : ModeMemoryBadge
    /** Mode picked it up. */
    public data class Learned(override val text: String) : ModeMemoryBadge
    /** A routine that worked this often. */
    public data class Worked(override val text: String) : ModeMemoryBadge
}

/** A memory page's title and what's on it. */
@Composable
public fun ModeMemoryHeader(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = ModeSpacing.xs), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
        ModeText(title, ModeTextStyle.LargeTitle, Modifier.semantics { heading() })
        ModeText(subtitle, ModeTextStyle.Callout, color = Mode.colors.textSecondary)
    }
}

/**
 * One thing Mode remembers: its title, what it says (or a routine's steps), when and how it was learned,
 * and its tag. [selected] is null outside editing; in editing a ring and a mark show the choice.
 */
@Composable
public fun ModeMemoryCard(
    title: String,
    text: String?,
    steps: List<String>,
    footer: String?,
    badge: ModeMemoryBadge?,
    selected: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.card)
    val (press, alpha) = rememberPress()
    Row(
        modifier.fillMaxWidth().alpha(alpha).modeShadow(shape).background(c.card, shape)
            .then(if (selected == true) Modifier.border(ModeSize.chatRing, c.accent, shape) else Modifier)
            .clickable(press, indication = null, role = Role.Button, onClick = onClick).semantics { if (selected != null) this.selected = selected }
            .padding(ModeSpacing.l),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
    ) {
        if (selected != null) ModeRadio(selected, Modifier.padding(top = ModeSpacing.xxs))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            ModeText(title, ModeTextStyle.Headline)
            if (text != null) ModeInlineText(text, ModeTextStyle.Subheadline, color = c.textSecondary)
            if (steps.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(ModeSpacing.xs)) {
                    steps.forEachIndexed { i, step ->
                        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
                            Box(Modifier.size(ModeSize.memoryStep).background(c.cardRaised, CircleShape), Alignment.Center) {
                                ModeText((i + 1).toString(), ModeTextStyle.CaptionEmphasis)
                            }
                            ModeInlineText(step, ModeTextStyle.Subheadline, Modifier.weight(1f))
                        }
                    }
                }
            }
            if (footer != null || badge != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s), verticalAlignment = Alignment.CenterVertically) {
                    if (badge != null) {
                        val (fill, ink) = when (badge) {
                            is ModeMemoryBadge.Taught -> c.tintSoft to c.tint
                            is ModeMemoryBadge.Learned -> c.infoSoft to c.infoText
                            is ModeMemoryBadge.Worked -> c.tokens.tagGreen to c.tokens.tagGreenText
                        }
                        ModeText(badge.text, ModeTextStyle.CaptionEmphasis, Modifier.background(fill, Capsule).padding(horizontal = ModeSpacing.s, vertical = ModeSpacing.xxs), color = ink)
                    }
                    if (footer != null) ModeText(footer, ModeTextStyle.Caption, color = c.textSecondary)
                }
            }
        }
    }
}

/** A row to another part of memory: its tile, name and summary, and the arrow. */
@Composable
public fun ModeMemoryRow(title: String, subtitle: String, leading: ModeRowLeading, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ModeRow(title, modifier.defaultMinSize(minHeight = ModeSize.tileRow), subtitle = subtitle, leading = leading, emphasis = ModeRowEmphasis.Prominent, onClick = onClick)
}
