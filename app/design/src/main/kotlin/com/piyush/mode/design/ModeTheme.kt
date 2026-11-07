package com.piyush.mode.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Every colour in Mode: the named ones from the iPhone's asset catalog ([tokens], light or dark),
 * and the few the iPhone sets in code, which are the same in both themes (Mo is always orange).
 */
@Immutable
public class ModeColors(public val tokens: ModeColorTokens, public val dark: Boolean) {
    public val page: Color get() = tokens.page
    public val card: Color get() = tokens.card
    public val cardRaised: Color get() = tokens.cardRaised
    public val field: Color get() = tokens.field
    public val fill: Color get() = tokens.fill
    public val textPrimary: Color get() = tokens.textPrimary
    public val textSecondary: Color get() = tokens.textSecondary
    public val separator: Color get() = tokens.separator
    public val ring: Color get() = tokens.ring
    public val track: Color get() = tokens.track
    public val accent: Color get() = tokens.accent
    public val onAccent: Color get() = tokens.onAccent
    public val brand: Color get() = tokens.brand
    public val tint: Color get() = tokens.tint
    public val tintSoft: Color get() = tokens.tintSoft
    public val hero: Color get() = tokens.hero
    public val heroText: Color get() = tokens.heroText
    public val heroSecondary: Color get() = tokens.heroSecondary
    public val heroFill: Color get() = tokens.heroFill
    public val success: Color get() = tokens.success
    public val warning: Color get() = tokens.warning
    public val danger: Color get() = tokens.danger
    public val info: Color get() = tokens.info
    public val infoText: Color get() = tokens.infoText
    public val infoSoft: Color get() = tokens.infoSoft
    public val stopRed: Color get() = tokens.stopRed
    public val scrim: Color get() = tokens.scrim

    // The same in both themes (ModeColor.swift sets these in code).
    public val moInk: Color = Color(0xFF1A1A1F)
    public val moCoral: Color = Color(0xFFFF6B3D)
    public val moMango: Color = Color(0xFFFFB020)
    public val moMint: Color = Color(0xFF2DBE8C)
    public val moLilac: Color = Color(0xFF8B7CFF)
    public val moSparkle: Color = Color(0xFFF2B705)
    public val moDrop: Color = Color(0xFF7FB2FF)
    public val moBadge: Color = Color(0xFF2450E6)
    public val deviceFrame: Color = Color(0xFF1C1C1E)
    public val orbLight: Color = Color(0xFFFFB020)
    public val orbDeep: Color = Color(0xFFB8431E)
    public val orbPink: Color = Color(0xFFFF9A78)
    /// Mode's dark card, whatever the phone's theme (the lock screen notice).
    public val lockScreen: Color = Color(0xFF2A2A31)
    public val stopOnDark: Color = Color(0xFFFF6B61)
    public val stopDisc: Color = Color(0x33FF6B61)
    public val onDarkFill: Color = Color(0x1FFFFFFF)
    public val onDarkTrack: Color = Color(0x38FFFFFF)

    /** Voice avatars: warm pairs that keep a white initial readable in both themes. */
    public val avatarPairs: List<Pair<Color, Color>> = listOf(
        Color(0xFFFF6B3D) to Color(0xFFB8431E),
        Color(0xFF8C7DFF) to Color(0xFF5747CC),
        Color(0xFF2EBF8C) to Color(0xFF1C7A45),
        Color(0xFFFFB021) to Color(0xFFA34F00),
        Color(0xFF3D63F0) to Color(0xFF1C40B8),
        Color(0xFF5E5C66) to Color(0xFF1A1A1F),
    )

    public companion object {
        public val Light: ModeColors = ModeColors(ModeColorTokens.Light, dark = false)
        public val Dark: ModeColors = ModeColors(ModeColorTokens.Dark, dark = true)
    }
}

internal val LocalModeColors = staticCompositionLocalOf { ModeColors.Light }

/** Where screens read the theme: `Mode.colors.page`. */
public object Mode {
    public val colors: ModeColors
        @Composable @ReadOnlyComposable get() = LocalModeColors.current
}

/** Mode's look around [content]: the phone's light or dark theme unless [dark] says otherwise. */
@Composable
public fun ModeTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalModeColors provides if (dark) ModeColors.Dark else ModeColors.Light, content = content)
}
