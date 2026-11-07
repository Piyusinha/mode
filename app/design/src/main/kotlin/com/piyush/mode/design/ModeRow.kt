package com.piyush.mode.design

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics

/** What sits at the start of a row (ModeRowLeading on the iPhone). */
public sealed interface ModeRowLeading {
    public data object None : ModeRowLeading
    /** An icon in a raised circle. */
    public data class Icon(val icon: ModeIcon, val color: Color? = null) : ModeRowLeading
    /** Mo, in a mood (a recent task, How Mode acts). */
    public data class Mo(val mood: MoMood) : ModeRowLeading
    /** Mo with a small badge on its corner (What Mode knows about you). */
    public data class MoBadge(val mood: MoMood, val icon: ModeIcon) : ModeRowLeading
    /** A round badge with a letter, for people. */
    public data class Initial(val letter: String) : ModeRowLeading
    /** A numbered step. */
    public data class Number(val n: Int) : ModeRowLeading
    /** A green check in a filled circle: done or working. */
    public data object Success : ModeRowLeading
    /** A plain green tick (lists of what happened). */
    public data object Check : ModeRowLeading
    /** A plain icon, no background. */
    public data class Symbol(val icon: ModeIcon, val color: Color) : ModeRowLeading
    /** A red cross: failed. */
    public data object Failure : ModeRowLeading
    /** An empty circle: not checked yet. */
    public data object Pending : ModeRowLeading
    /** Working on it now. */
    public data object Progress : ModeRowLeading
    /** A rounded square with a letter, in a colour (model makers). */
    public data class LetterTile(val letter: String, val background: Color, val foreground: Color) : ModeRowLeading
    /** A rounded square with an icon, in a colour (permissions, waiting tasks). */
    public data class IconTile(val icon: ModeIcon, val background: Color, val foreground: Color) : ModeRowLeading
    /** A rounded square with a provider's or maker's logo (AI providers, models). */
    public data class LogoTile(val logo: ModeLogo, val background: Color, val foreground: Color) : ModeRowLeading
}

/** Plain rows (lists) or prominent ones (setup, choices): a stronger title. */
public enum class ModeRowEmphasis { Standard, Prominent }

/**
 * One row in a card: something at the start, a title and a line under it, something at the end.
 * With [onClick] the whole row is a button; [chevron] adds the arrow of a row that opens something.
 */
@Composable
public fun ModeRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: ModeRowLeading = ModeRowLeading.None,
    emphasis: ModeRowEmphasis = ModeRowEmphasis.Standard,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val c = Mode.colors
    val (press, alpha) = rememberPress()
    Row(
        modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = ModeSize.rowMin)
            .then(if (onClick != null) Modifier.alpha(alpha).clickable(press, indication = null, role = Role.Button, onClick = onClick) else Modifier)
            .padding(vertical = ModeSpacing.s),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeLeading(leading)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            ModeText(title, if (emphasis == ModeRowEmphasis.Prominent) ModeTextStyle.Headline else ModeTextStyle.Callout)
            if (subtitle != null) ModeText(subtitle, ModeTextStyle.Footnote, color = c.textSecondary)
        }
        trailing()
        if (chevron) ModeChevron()
    }
}

/** The small arrow at the end of a row that opens something. */
@Composable
public fun ModeChevron() {
    ModeIconImage(ModeIcon.Chevron, ModeIconSize.chevron, Mode.colors.textSecondary)
}

/** The start of a row on its own, for rows built from other pieces. */
@Composable
public fun ModeLeading(leading: ModeRowLeading) {
    val c = Mode.colors
    val tile = RoundedCornerShape(ModeSize.tileRadius)
    val hidden = Modifier.clearAndSetSemantics {}
    when (leading) {
        ModeRowLeading.None -> Unit
        is ModeRowLeading.Icon -> Box(hidden.size(ModeSize.badge).background(c.cardRaised, CircleShape), Alignment.Center) {
            ModeIconImage(leading.icon, ModeIconSize.small, leading.color ?: c.textPrimary)
        }
        is ModeRowLeading.Mo -> ModeMo(leading.mood, ModeSize.moRow)
        is ModeRowLeading.MoBadge -> Box(hidden) {
            ModeMo(leading.mood, ModeSize.moRow)
            Box(
                Modifier.align(Alignment.BottomEnd).offset(ModeSpacing.xs, ModeSpacing.xxs).size(ModeSize.moRowBadge)
                    .background(c.accent, CircleShape).border(ModeSize.moRowBadgeRing, c.card, CircleShape),
                Alignment.Center,
            ) { ModeIconImage(leading.icon, ModeIconSize.moBadge, c.onAccent) }
        }
        is ModeRowLeading.Initial -> Box(hidden.size(ModeSize.avatar).background(c.cardRaised, CircleShape), Alignment.Center) {
            ModeText(leading.letter, ModeTextStyle.Title3)
        }
        is ModeRowLeading.Number -> Box(hidden.size(ModeSize.badge).background(c.cardRaised, CircleShape), Alignment.Center) {
            ModeText(leading.n.toString(), ModeTextStyle.CalloutEmphasis)
        }
        ModeRowLeading.Success -> ModeIconImage(ModeIcon.Done, ModeIconSize.large, c.success, hidden)
        is ModeRowLeading.Symbol -> Box(hidden.width(ModeSize.badge), Alignment.Center) { ModeIconImage(leading.icon, ModeIconSize.large, leading.color) }
        ModeRowLeading.Check -> Box(hidden.width(ModeSize.badge), Alignment.Center) { ModeIconImage(ModeIcon.Checkmark, ModeIconSize.small, c.success) }
        ModeRowLeading.Failure -> ModeIconImage(ModeIcon.Failed, ModeIconSize.large, c.danger, hidden)
        ModeRowLeading.Pending -> ModeIconImage(ModeIcon.EmptyCircle, ModeIconSize.large, c.ring, hidden)
        ModeRowLeading.Progress -> Box(Modifier.size(ModeSize.badge), Alignment.Center) { ModeSpinner() }
        is ModeRowLeading.LetterTile -> Box(hidden.size(ModeSize.tile).background(leading.background, tile), Alignment.Center) {
            ModeText(leading.letter, ModeTextStyle.CalloutEmphasis, color = leading.foreground)
        }
        is ModeRowLeading.IconTile -> Box(hidden.size(ModeSize.tile).background(leading.background, tile), Alignment.Center) {
            ModeIconImage(leading.icon, ModeIconSize.regular, leading.foreground)
        }
        is ModeRowLeading.LogoTile -> Box(hidden.size(ModeSize.tile).background(leading.background, tile), Alignment.Center) {
            ModeLogoImage(leading.logo, ModeSize.icon + ModeSpacing.xs, leading.foreground)
        }
    }
}

/**
 * Grouped content on the page: [rows] separated by hairlines, in a white card with 26-dp corners.
 * [lifted] adds the soft shadow the setup screens use; lists in Settings sit flat.
 */
@Composable
public fun ModeCard(modifier: Modifier = Modifier, lifted: Boolean = false, rows: List<@Composable () -> Unit>) {
    val c = Mode.colors
    val shape = RoundedCornerShape(ModeRadius.card)
    Column(modifier.fillMaxWidth().modeShadow(shape, lifted).background(c.card, shape).padding(horizontal = ModeSpacing.l)) {
        rows.forEachIndexed { i, row ->
            row()
            if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().height(ModeSize.separator).background(c.separator))
        }
    }
}

/** A card holding free content (no separators). */
@Composable
public fun ModeCardBox(modifier: Modifier = Modifier, lifted: Boolean = false, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(ModeRadius.card)
    Box(modifier.fillMaxWidth().modeShadow(shape, lifted).background(Mode.colors.card, shape).padding(ModeSpacing.l)) { content() }
}
