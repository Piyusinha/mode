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
    /** Inside a first-run card, between its head and its buttons. */
    public val groupInset: Dp = 14.dp
    /** How far a note under a card sits in from the card's edge. */
    public val labelInset: Dp = 4.dp
    /** A chat: between messages, inside a bubble or pill, tight gaps, the title's inset, the message bar's inset. */
    public val chatGap: Dp = 14.dp
    public val chatInline: Dp = 10.dp
    public val chatTight: Dp = 6.dp
    public val chatTitleInset: Dp = 4.dp
    public val composerInset: Dp = 5.dp
    /** Notes and titles sit a little in from the cards' edge; a section's gap under its header. */
    public val noteInset: Dp = 8.dp
    public val headerGap: Dp = 14.dp
    /** A setup page's sides: titles sit a little further in than cards. */
    public val page: Dp = 24.dp
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
    /** The "Did you know?" cards on Home. */
    public val tipBanner: Dp = 26.dp
    /** Chat cards (working, failed, a lesson), notices and the small tile on a lesson. */
    public val chatCard: Dp = 24.dp
    public val chatNotice: Dp = 22.dp
    public val chatTile: Dp = 10.dp
    /** The live run: the screen's frame, the panel; a screenshot cell; a repeat's note. */
    public val liveScreen: Dp = 36.dp
    public val livePanel: Dp = 40.dp
    public val screenCell: Dp = 22.dp
    public val repeatNote: Dp = 22.dp
    /** The field and switch on "What should Mode have done?". */
    public val chatFixField: Dp = 18.dp
    /** Health's banner. */
    public val banner: Dp = 22.dp
    /** First-run cards (a provider, More options). */
    public val firstRunCard: Dp = 24.dp
    /** A tinted note on a setup guide. */
    public val note: Dp = 18.dp
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
    /** The icon in Mo's corner badge. */
    public val moBadge: Dp = 11.dp
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
    /** Capsules on sheets and guides (the loop sheet, first run). */
    public val loopButton: Dp = 52.dp
    /** A small pill button inside a row (Connect, Change, Undo). */
    public val pillButton: Dp = 44.dp
    public val sendButton: Dp = 40.dp
    public val moLarge: Dp = 80.dp
    public val moHero: Dp = 56.dp
    public val moRow: Dp = 32.dp
    /** The badge on Mo's corner in a row, and its ring. */
    public val moRowBadge: Dp = 19.dp
    public val moRowBadgeRing: Dp = 2.dp
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
    /** First run: the step dots (done and current are wider), job capsules, Mo, provider tiles. */
    public val progressDot: Dp = 8.dp
    public val progressDotCurrent: Dp = 22.dp
    public val progressRing: Dp = 1.5.dp
    public val jobButton: Dp = 54.dp
    public val moWelcome: Dp = 150.dp
    public val moFirstJob: Dp = 120.dp
    public val providerTile: Dp = 44.dp
    public val moreOptions: Dp = 56.dp
    /** Phone setup: Mo beside the title, a row's tile, a guide step's number and row, the waiting dot. */
    public val moSetup: Dp = 56.dp
    public val permissionTile: Dp = 36.dp
    public val guideNumber: Dp = 28.dp
    public val guideStep: Dp = 60.dp
    public val waitingDot: Dp = 10.dp
    /** Home: the running task's Mo in the bar, its stop square, a recent row, the room the tab bar takes. */
    public val moBar: Dp = 34.dp
    public val stopSquare: Dp = 11.dp
    public val stopSquareRadius: Dp = 3.dp
    public val recentRow: Dp = 62.dp
    /** Chat: a bubble's widest, Mo on cards and notices, screenshot thumbnails, the small buttons, the working tag. */
    public val bubbleMaxWidth: Dp = 286.dp
    public val moChatCard: Dp = 40.dp
    public val moFailed: Dp = 48.dp
    public val moNotice: Dp = 28.dp
    public val screensThumbWidth: Dp = 22.dp
    public val screensThumbHeight: Dp = 36.dp
    public val screensThumbBorder: Dp = 2.dp
    public val screensLinkHeight: Dp = 48.dp
    public val chatPillButton: Dp = 30.dp
    public val lessonButton: Dp = 34.dp
    public val chatTileSize: Dp = 34.dp
    public val workingStop: Dp = 36.dp
    public val stopGlyph: Dp = 12.dp
    public val stopGlyphRadius: Dp = 3.dp
    public val workingDot: Dp = 6.dp
    public val workingTag: Dp = 24.dp
    public val failedButton: Dp = 42.dp
    public val agendaTimeChat: Dp = 48.dp
    public val chatRing: Dp = 2.dp
    /** The live run and its screens: Mo on cards and the panel, the badge, the panel's least height, cells. */
    public val moCard: Dp = 36.dp
    public val moPanel: Dp = 40.dp
    public val liveBadgeHeight: Dp = 36.dp
    public val livePanelHeight: Dp = 246.dp
    public val screenCell: Dp = 196.dp
    public val screenBadge: Dp = 20.dp
    /** The field on "What should Mode have done?". */
    public val chatFixField: Dp = 108.dp
    /** The time wheel on Repeat: its height, a row, a column. */
    public val timeWheel: Dp = 112.dp
    public val timeWheelRow: Dp = 38.dp
    public val timeWheelColumn: Dp = 64.dp
    /** What went wrong: Mo by the title and an answer, a row, its tick; Ask about it's send and chips. */
    public val moDebugHeader: Dp = 64.dp
    public val moAnswer: Dp = 36.dp
    public val debugRow: Dp = 60.dp
    public val debugCheck: Dp = 22.dp
    public val askSend: Dp = 42.dp
    public val askChip: Dp = 34.dp
    /** Settings: a status dot, small logo tiles, value and logo rows, a tile row, filter chips, the switch. */
    public val statusDot: Dp = 8.dp
    public val smallTileRadius: Dp = 9.dp
    public val valueRow: Dp = 52.dp
    public val logoRow: Dp = 60.dp
    public val logoRowTagged: Dp = 62.dp
    public val logoRowProblem: Dp = 72.dp
    public val tileRow: Dp = 72.dp
    public val filterChip: Dp = 34.dp
    public val switchWidth: Dp = 52.dp
    public val switchHeight: Dp = 32.dp
    public val switchThumb: Dp = 26.dp
    /** Health: a row, its tile and tick, the Fix button; the banner's Mo and button. */
    public val healthRow: Dp = 60.dp
    public val healthTile: Dp = 34.dp
    public val healthTileRadius: Dp = 10.dp
    public val healthCheck: Dp = 22.dp
    public val healthAction: Dp = 32.dp
    public val moBanner: Dp = 40.dp
    public val bannerAction: Dp = 38.dp
    /** A routine's step number on a memory card. */
    public val memoryStep: Dp = 20.dp
    /** The grab handle on top of a sheet. */
    public val sheetHandle: Dp = 36.dp
    public val sheetHandleHeight: Dp = 5.dp
    /** Tasks: a row, and the Scheduled / Done pill switch. */
    public val tasksRow: Dp = 72.dp
    public val segmentsHeight: Dp = 42.dp
    public val segmentsCompactHeight: Dp = 38.dp
    public val segmentsInset: Dp = 3.dp
    public val tabBarClearance: Dp = 120.dp
    public val tryPill: Dp = 44.dp
    /** Home's tip cards: Mo, its badge and ring, the close button, the dots and their tap areas, Undo. */
    public val tipMo: Dp = 64.dp
    public val tipBadge: Dp = 30.dp
    public val tipBadgeRing: Dp = 3.dp
    public val tipBadgeOffset: Dp = 5.dp
    public val tipClose: Dp = 30.dp
    public val tipDot: Dp = 8.dp
    public val tipDotCurrent: Dp = 18.dp
    public val tipDotHit: Dp = 16.dp
    public val tipDotHitCurrent: Dp = 26.dp
    public val tipDotHitHeight: Dp = 28.dp
    public val tipToast: Dp = 56.dp
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
    /** The "Typed" / "Spoken" tag on your bubble. */
    public const val bubbleTag: Float = 0.82f
    /** The light sweep over a working line takes half its width. */
    public const val shimmerWidth: Float = 0.5f
    /** Words and fills on ink (Undo). */
    public const val toastDetail: Float = 0.72f
    public const val toastButton: Float = 0.16f
    /** Tip cards: the close chip and the dots not showing. */
    public const val tipChip: Float = 0.08f
    public const val tipDotOff: Float = 0.2f
    public val orbRings: List<Float> = listOf(0.08f, 0.14f, 0.22f)
}
