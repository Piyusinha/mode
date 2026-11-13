package com.piyush.mode.design

import androidx.compose.foundation.background
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics

/** Where a part of Mode stands on Health. */
public enum class ModeHealthStatus { Checking, Ok, Warning, Repaired, Busy, Info }

/** One part on Health: its tile, name and state, and a tick, a spinner, or Fix (ink: Mode repairs; blue: opens a page). */
@Composable
public fun ModeHealthRow(
    title: String,
    detail: String,
    icon: ModeIcon,
    status: ModeHealthStatus,
    modifier: Modifier = Modifier,
    action: String? = null,
    opens: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    val c = Mode.colors
    val warning = status == ModeHealthStatus.Warning
    Row(modifier.fillMaxWidth().defaultMinSize(minHeight = ModeSize.healthRow).padding(vertical = ModeSpacing.s), horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.clearAndSetSemantics {}.size(ModeSize.healthTile).background(if (warning) c.tokens.tileOrange else c.cardRaised, RoundedCornerShape(ModeSize.healthTileRadius)), Alignment.Center) {
            ModeIconImage(icon, ModeIconSize.small, if (warning) c.tokens.tileOrangeText else c.textPrimary)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            ModeText(title, ModeTextStyle.Callout)
            if (detail.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.xs)) {
                    if (warning) ModeIconImage(ModeIcon.Warning, ModeIconSize.chevron, c.tokens.tileOrangeText, Modifier.padding(top = ModeSpacing.xxs))
                    ModeText(detail, ModeTextStyle.Footnote, color = when (status) {
                        ModeHealthStatus.Warning -> c.tokens.tileOrangeText
                        ModeHealthStatus.Repaired -> c.success
                        else -> c.textSecondary
                    })
                }
            }
        }
        when (status) {
            ModeHealthStatus.Checking -> ModeSpinner()
            ModeHealthStatus.Ok, ModeHealthStatus.Repaired -> Box(Modifier.size(ModeSize.healthCheck).background(c.tokens.tagGreen, CircleShape), Alignment.Center) {
                ModeIconImage(ModeIcon.Checkmark, ModeIconSize.chevron, c.success)
            }
            ModeHealthStatus.Busy -> ModeIconImage(ModeIcon.Clock, ModeIconSize.small, c.textSecondary)
            ModeHealthStatus.Info -> Unit
            ModeHealthStatus.Warning -> if (action != null && onAction != null) {
                Box(Modifier.defaultMinSize(minHeight = ModeSize.touch).clickable(role = Role.Button, indication = null, interactionSource = null, onClick = onAction), Alignment.Center) {
                    ModeText(
                        action, ModeTextStyle.FootnoteEmphasis,
                        Modifier.defaultMinSize(minHeight = ModeSize.healthAction).background(if (opens) c.info else c.accent, Capsule).padding(horizontal = ModeSpacing.m, vertical = ModeSpacing.s - ModeSpacing.xxs),
                        color = if (opens) c.onInfo else c.onAccent, maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Health's banner: all good (green), checking (grey), or what needs attention (orange) with Repair. */
@Composable
public fun ModeHealthBanner(ok: Boolean, title: String, detail: String, modifier: Modifier = Modifier, busy: Boolean = false, action: String? = null, onAction: (() -> Unit)? = null) {
    val c = Mode.colors
    Row(
        modifier.fillMaxWidth().background(if (busy) c.cardRaised else if (ok) c.tokens.tagGreen else c.tokens.tileOrange, RoundedCornerShape(ModeRadius.banner))
            .padding(start = ModeSpacing.l, end = ModeSpacing.headerGap, top = ModeSpacing.headerGap, bottom = ModeSpacing.headerGap),
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ModeMo(if (busy) MoMood.Working else if (ok) MoMood.Done else MoMood.Stuck, ModeSize.moBanner)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            ModeText(title, ModeTextStyle.CalloutEmphasis, color = if (busy) c.textPrimary else if (ok) c.success else c.tokens.tileOrangeText)
            ModeText(detail, ModeTextStyle.Footnote, color = c.textSecondary)
        }
        if (action != null && onAction != null) {
            ModeButton(action, onAction, role = ModeButtonRole.PillProminent)
        }
    }
}
