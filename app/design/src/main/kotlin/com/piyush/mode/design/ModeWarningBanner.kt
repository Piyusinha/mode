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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics

/** How serious a warning banner is: a Problem (red) stays; a Warning (orange) or Info (plain) can be closed. */
public enum class ModeWarningTone { Problem, Warning, Info }

/**
 * The banner at the top while Mode is open (boards W01–W06): an icon disc, what's wrong and what it means, one
 * action, and for a Warning or Info a close ✕. It takes its own room: the screen below moves down.
 */
@Composable
public fun ModeWarningBanner(
    tone: ModeWarningTone,
    icon: ModeIcon,
    title: String,
    detail: String,
    action: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
    closeLabel: String? = null,
    onClose: (() -> Unit)? = null,
) {
    val c = Mode.colors
    val (background, disc, accentText) = when (tone) {
        ModeWarningTone.Problem -> Triple(c.tokens.tileRed, c.danger, c.tokens.dangerText)
        ModeWarningTone.Warning -> Triple(c.tokens.tileOrange, c.tokens.tileOrangeText, c.tokens.tileOrangeText)
        ModeWarningTone.Info -> Triple(c.card, c.fill, c.textSecondary)
    }
    Row(
        modifier.fillMaxWidth().background(background, RoundedCornerShape(ModeRadius.card))
            .padding(start = ModeSpacing.m + ModeSpacing.xxs, end = ModeSpacing.s + ModeSpacing.xxs, top = ModeSpacing.m, bottom = ModeSpacing.m)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(ModeSize.warningIcon).background(disc, RoundedCornerShape(ModeSize.warningIconRadius)), Alignment.Center) {
            ModeIconImage(icon, ModeIconSize.regular, if (tone == ModeWarningTone.Info) c.textPrimary else Color.White)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(ModeSpacing.xxs)) {
            ModeText(title, ModeTextStyle.CalloutEmphasis, maxLines = 2)
            ModeText(detail, ModeTextStyle.Footnote, color = accentText, maxLines = 2)
        }
        val (pill, pillText) = when (tone) {
            ModeWarningTone.Problem -> c.danger to Color.White
            ModeWarningTone.Warning -> c.accent to c.onAccent
            ModeWarningTone.Info -> c.fill to c.textPrimary
        }
        val (press, alpha) = rememberPress()
        Box(
            Modifier.defaultMinSize(minHeight = ModeSize.pillButton).alpha(alpha).background(pill, Capsule)
                .clickable(press, indication = null, role = Role.Button, onClick = onAction).padding(horizontal = ModeSpacing.l),
            Alignment.Center,
        ) { ModeText(action, ModeTextStyle.SubheadlineEmphasis, color = pillText, maxLines = 1) }
        if (onClose != null && closeLabel != null) {
            val (closePress, closeAlpha) = rememberPress()
            Box(
                Modifier.size(ModeSize.touch).alpha(closeAlpha).semantics { contentDescription = closeLabel }
                    .clickable(closePress, indication = null, role = Role.Button, onClick = onClose),
                Alignment.Center,
            ) { ModeIconImage(ModeIcon.Close, ModeIconSize.small, accentText) }
        }
    }
}
