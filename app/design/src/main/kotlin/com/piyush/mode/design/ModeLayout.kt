package com.piyush.mode.design

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing on a 4-dp grid (ModeSpacing on the iPhone). */
public object ModeSpacing {
    public val none: Dp = 0.dp
    public val xxs: Dp = 2.dp
    public val xs: Dp = 4.dp
    public val s: Dp = 8.dp
    public val m: Dp = 12.dp
    public val l: Dp = 16.dp
    public val xl: Dp = 20.dp
    public val xxl: Dp = 28.dp
    public val xxxl: Dp = 40.dp
    /** The gap between a screen's edge and its content. */
    public val screenEdge: Dp = 16.dp
}

/** Corner radii. */
public object ModeRadius {
    public val chip: Dp = 14.dp
    /** A small rounded square (a screenshot thumbnail). */
    public val thumb: Dp = 6.dp
    public val field: Dp = 26.dp
    public val card: Dp = 26.dp
    /** The running task's card on Home. */
    public val hero: Dp = 30.dp
    public val sheet: Dp = 34.dp
    /** A chat bubble's corners, and the small one by its tail. */
    public val bubble: Dp = 22.dp
    public val bubbleTail: Dp = 6.dp
}

/** Icon sizes, so an icon never borrows a text style's size. */
public object ModeIconSize {
    public val chevron: Dp = 14.dp
    public val small: Dp = 16.dp
    /** Most icons: in a circle, in a button, at the start of a row. */
    public val regular: Dp = 20.dp
    /** Tab bar icons, a row's status mark. */
    public val large: Dp = 22.dp
    /** The Speak button's microphone. */
    public val hero: Dp = 24.dp
    /** A big status mark. */
    public val status: Dp = 28.dp
}

/** Fixed sizes: touch targets, icons, avatars, rows (ModeSize on the iPhone). */
public object ModeSize {
    /** The smallest comfortable tap target (Android asks for 48). */
    public val touch: Dp = 48.dp
    public val iconSmall: Dp = 14.dp
    public val icon: Dp = 18.dp
    public val iconLarge: Dp = 24.dp
    public val iconHero: Dp = 56.dp
    public val badge: Dp = 32.dp
    public val avatar: Dp = 38.dp
    public val rowMin: Dp = 56.dp
    public val fieldHeight: Dp = 52.dp
    public val composerHeight: Dp = 50.dp
    public val dayButton: Dp = 40.dp
    public val separator: Dp = 1.dp
    public val thumbnailWidth: Dp = 150.dp
    public val mark: Dp = 96.dp
    public val markRadius: Dp = 26.dp
    public val contactAvatar: Dp = 56.dp
    /** The coloured square tiles (model letters, permission icons). */
    public val tile: Dp = 40.dp
    public val tileRadius: Dp = 12.dp
    public val radio: Dp = 24.dp
    public val stepDot: Dp = 10.dp
    public val stepDotCurrent: Dp = 24.dp
    public val stepDotHeight: Dp = 6.dp
    public val tabBarHeight: Dp = 64.dp
    public val speakButton: Dp = 64.dp
    public val miniPlayerHeight: Dp = 56.dp
    public val suggestionWidth: Dp = 168.dp
    public val suggestionHeight: Dp = 124.dp
    public val iconCircle: Dp = 40.dp
    public val primaryButton: Dp = 56.dp
    public val secondaryButton: Dp = 50.dp
    /** A small pill button inside a row (Connect, Change, Undo). */
    public val pillButton: Dp = 44.dp
    public val sendButton: Dp = 40.dp
    public val moLarge: Dp = 80.dp
    public val moHero: Dp = 56.dp
    public val moRow: Dp = 32.dp
    public val progressBar: Dp = 6.dp
    public val segmentHeight: Dp = 4.dp
    public val liveDot: Dp = 8.dp
    public val successBadge: Dp = 60.dp
    public val resultShotWidth: Dp = 92.dp
    public val gridSpacing: Dp = 10.dp
    public val agendaTime: Dp = 52.dp
    public val agendaBar: Dp = 3.dp
    public val listMark: Dp = 14.dp
    public val quoteBar: Dp = 3.dp
    public val replyAction: Dp = 34.dp
    public val shimmerTravel: Dp = 160.dp
    public val voiceChoiceRow: Dp = 52.dp
    public val voiceProviderRow: Dp = 60.dp
    public val voiceProviderLogo: Dp = 36.dp
    public val playButton: Dp = 40.dp
    public val voiceAvatar: Dp = 46.dp
    public val voiceLogo: Dp = 52.dp
    public val waveBar: Dp = 3.dp
    public val waveHeight: Dp = 18.dp
    public val orb: Dp = 104.dp
    public val orbRing: Dp = 26.dp
    public val setupOrb: Dp = 56.dp
    public val dragHandle: Dp = 16.dp
    public val talkOrb: Dp = 180.dp
    public val talkOrbSmall: Dp = 112.dp
    public val talkWave: Dp = 36.dp
    public val talkMic: Dp = 76.dp
    public val talkSide: Dp = 52.dp
    public val readableWidth: Dp = 520.dp
    public val edgeFadeTop: Dp = 120.dp
    public val edgeFadeBottom: Dp = 150.dp
    /** A phone screen's width to height, for screenshot cells. */
    public const val screenAspect: Float = 9f / 19.5f
}

/** How things move (milliseconds). */
public object ModeMotion {
    public const val quickMs: Int = 200
    public const val standardMs: Int = 350
    public const val shimmerMs: Int = 1400
}

/** The soft lift under floating things (cards in setup, the tab bar, round buttons). */
public object ModeShadow {
    public val elevation: Dp = 8.dp
    public const val opacity: Float = 0.10f
}

/** Opacities for effects. */
public object ModeOpacity {
    public const val shimmer: Float = 0.55f
    public const val muted: Float = 0.28f
    public const val disabled: Float = 0.4f
    public val orbRings: List<Float> = listOf(0.08f, 0.14f, 0.22f)
}
