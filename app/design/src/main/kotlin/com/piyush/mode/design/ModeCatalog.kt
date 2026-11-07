package com.piyush.mode.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Every token on one page, for checking the design system against the iPhone's (debug builds
 * show it; the catalogue itself is not a screen of the app). Literal sizes here are the catalogue's
 * own layout.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun ModeCatalog() {
    val c = Mode.colors
    Column(
        Modifier.fillMaxSize().background(c.page).windowInsetsPadding(WindowInsets.safeDrawing).verticalScroll(rememberScrollState()).padding(ModeSpacing.screenEdge),
        verticalArrangement = Arrangement.spacedBy(ModeSpacing.l),
    ) {
        ModeText(if (c.dark) "Mode · dark" else "Mode · light", ModeTextStyle.LargeTitle) // literal-ok
        ModeSectionHeader("Buttons") // literal-ok
        ModeButton("Continue", {}, fullWidth = true) // literal-ok
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            listOf(ModeButtonRole.Secondary, ModeButtonRole.Pill, ModeButtonRole.PillProminent, ModeButtonRole.Plain, ModeButtonRole.Destructive).forEach {
                ModeButton(it.name, {}, role = it) // literal-ok
            }
            ModeButton("Disabled", {}, enabled = false) // literal-ok
            ModeIconButton(ModeIcon.Back, "Back", {}) // literal-ok
            ModeIconButton(ModeIcon.Ask, "Speak", {}, prominent = true, size = ModeSize.speakButton) // literal-ok
        }
        Row(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            Box(Modifier.weight(1f)) { ModeButton("Yes", {}, role = ModeButtonRole.Answer) } // literal-ok
            Box(Modifier.weight(1f)) { ModeButton("Stop", {}, role = ModeButtonRole.StopSoft) } // literal-ok
        }
        ModeSectionHeader("Rows") // literal-ok
        ModeCard(lifted = true, rows = listOf(
            { ModeRow("Phone control", subtitle = "Turned on", leading = ModeRowLeading.IconTile(ModeIcon.Shield, c.tintSoft, c.tint)) {} }, // literal-ok
            { ModeRow("Text Sonali, then calendar", subtitle = "Today 8:13 · 62 s", leading = ModeRowLeading.Mo(MoMood.Done), onClick = {}) }, // literal-ok
            { ModeRow("Claude", subtitle = "Signed in", leading = ModeRowLeading.LogoTile(ModeLogo.Claude, c.accent, c.onAccent), onClick = {}) }, // literal-ok
            { ModeRow("Checking", leading = ModeRowLeading.Progress) }, // literal-ok
            { ModeRow("Asha", leading = ModeRowLeading.Initial("A")) { ModeButton("Change", {}, role = ModeButtonRole.Pill) } }, // literal-ok
            { ModeRow("Failed", leading = ModeRowLeading.Failure) }, // literal-ok
        ))
        ModeText("Mo", ModeTextStyle.Section) // literal-ok
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            MoMood.entries.forEach { ModeMo(it, ModeSize.moHero) }
        }
        ModeText("Type", ModeTextStyle.Section) // literal-ok
        ModeTextStyle.entries.forEach { ModeText("${it.name} · Order my usual coffee", it, maxLines = 1) } // literal-ok
        ModeText("Colours", ModeTextStyle.Section) // literal-ok
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            colorTokens(c.tokens).forEach { (name, color) ->
                Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) { // literal-ok
                    Box(Modifier.size(ModeSize.tile).background(color, RoundedCornerShape(ModeSize.tileRadius)).border(ModeSize.separator, c.separator, RoundedCornerShape(ModeSize.tileRadius)))
                    ModeText(name, ModeTextStyle.Caption, color = c.textSecondary, maxLines = 1)
                }
            }
        }
        ModeText("Icons", ModeTextStyle.Section) // literal-ok
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.m), verticalArrangement = Arrangement.spacedBy(ModeSpacing.m)) {
            ModeIcon.entries.forEach {
                Image(painterResource(it.res), it.name, Modifier.size(ModeIconSize.large), colorFilter = ColorFilter.tint(c.textPrimary))
            }
        }
        ModeText("Logos", ModeTextStyle.Section) // literal-ok
        FlowRow(horizontalArrangement = Arrangement.spacedBy(ModeSpacing.s), verticalArrangement = Arrangement.spacedBy(ModeSpacing.s)) {
            ModeLogo.entries.forEach {
                Row(
                    Modifier.size(ModeSize.tile).background(c.accent, RoundedCornerShape(ModeSize.tileRadius)),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(painterResource(it.res), it.name, Modifier.size(ModeIconSize.regular), colorFilter = ColorFilter.tint(c.onAccent))
                }
            }
        }
    }
}

private fun colorTokens(tokens: ModeColorTokens) = tokens.all()

@Preview(widthDp = 412, heightDp = 1600)
@Composable
private fun CatalogLight() = ModeTheme(dark = false) { ModeCatalog() }

@Preview(widthDp = 412, heightDp = 1600)
@Composable
private fun CatalogDark() = ModeTheme(dark = true) { ModeCatalog() }
