import SwiftUI

/// Spacing on a 4-point grid.
public enum ModeSpacing {
    public static let none: CGFloat = 0
    public static let xxs: CGFloat = 2
    public static let xs: CGFloat = 4
    public static let s: CGFloat = 8
    public static let m: CGFloat = 12
    public static let l: CGFloat = 16
    public static let xl: CGFloat = 20
    public static let xxl: CGFloat = 28
    public static let xxxl: CGFloat = 40
    /// The gap between a screen's edge and its content.
    public static let screenEdge: CGFloat = 16
}

/// Corner radii, concentric with the iPhone's screen corners.
public enum ModeRadius {
    public static let chip: CGFloat = 14
    /// A small rounded square (a screenshot thumbnail).
    public static let thumb: CGFloat = 6
    public static let field: CGFloat = 26
    public static let card: CGFloat = 26
    /// The running task's card on Home.
    public static let hero: CGFloat = 30
    public static let sheet: CGFloat = 34
    /// A chat bubble's corners, and the small one by its tail.
    public static let bubble: CGFloat = 22
    public static let bubbleTail: CGFloat = 6
}

/// Icon sizes for SF Symbols, so an icon never borrows a text style's size.
public enum ModeIconFont {
    /// A chevron at the end of a row.
    public static let chevron = Font.system(size: 14, weight: .semibold)
    public static let small = Font.system(size: 16, weight: .medium)
    /// Most icons: in a circle, in a button, at the start of a row.
    public static let regular = Font.system(size: 19, weight: .medium)
    /// Tab bar icons, a row's status mark.
    public static let large = Font.system(size: 22, weight: .medium)
    /// The Speak button's microphone.
    public static let hero = Font.system(size: 24, weight: .semibold)
    /// A big status mark (a test passed or failed).
    public static let status = Font.system(size: 28, weight: .bold)
}

/// Fixed sizes: touch targets, icons, avatars, rows.
public enum ModeSize {
    /// The smallest comfortable tap target.
    public static let touch: CGFloat = 44
    public static let iconSmall: CGFloat = 14
    public static let icon: CGFloat = 18
    public static let iconLarge: CGFloat = 24
    public static let iconHero: CGFloat = 56
    public static let badge: CGFloat = 32
    public static let avatar: CGFloat = 38
    public static let rowMin: CGFloat = 56
    public static let fieldHeight: CGFloat = 52
    public static let composerHeight: CGFloat = 50
    public static let dayButton: CGFloat = 40
    public static let separator: CGFloat = 1
    public static let thumbnailWidth: CGFloat = 150
    /// The app mark on Welcome, and its corner radius.
    public static let mark: CGFloat = 96
    public static let markRadius: CGFloat = 26
    /// A contact card's initials.
    public static let contactAvatar: CGFloat = 56
    /// The coloured square tiles (model letters, permission icons).
    public static let tile: CGFloat = 40
    public static let tileRadius: CGFloat = 12
    /// The selection circle on a choice row.
    public static let radio: CGFloat = 24
    /// The step dots in setup: a dot, and the current step's longer bar.
    public static let stepDot: CGFloat = 10
    public static let stepDotCurrent: CGFloat = 24
    /// The tab bar, the Speak button and the mini player above them.
    public static let tabBarHeight: CGFloat = 64
    public static let speakButton: CGFloat = 64
    public static let miniPlayerHeight: CGFloat = 56
    /// A suggestion card on Home.
    public static let suggestionWidth: CGFloat = 168
    public static let suggestionHeight: CGFloat = 124
    /// The round icon on a suggestion card, an avatar of initials in a row.
    public static let iconCircle: CGFloat = 40
    /// The main button at the bottom of a screen, and the secondary one.
    public static let primaryButton: CGFloat = 56
    public static let secondaryButton: CGFloat = 50
    /// A small pill button inside a row (Connect, Change, Undo).
    public static let pillButton: CGFloat = 36
    /// The round Send button inside the message bar.
    public static let sendButton: CGFloat = 40
    /// Mo: on a page's header, on the running card, on a row.
    public static let moLarge: CGFloat = 80
    public static let moHero: CGFloat = 56
    public static let moRow: CGFloat = 32
    /// A progress bar's thickness on the running card.
    public static let progressBar: CGFloat = 6
    /// The step dots' height in setup.
    public static let stepDotHeight: CGFloat = 6
    /// The little phones in the setup test illustration.
    public static let testPhoneWidth: CGFloat = 74
    public static let testPhoneHeight: CGFloat = 140
    /// The screenshot beside "What Mode did".
    public static let resultShotWidth: CGFloat = 92
    /// A success badge at the top of a page.
    public static let successBadge: CGFloat = 60
    /// The progress segments on the live panel.
    public static let segmentHeight: CGFloat = 4
    /// The small live dot.
    public static let liveDot: CGFloat = 8
    /// An iPhone screen's width to height, for screenshot cells.
    public static let screenAspect: CGFloat = 9.0 / 19.5
    /// Grid cells on All screens.
    public static let gridSpacing: CGFloat = 10
    /// Replies: the time column and accent bar of an agenda, a list's mark column, a quote's bar,
    /// the small buttons under a reply, and how far the working shimmer travels.
    public static let agendaTime: CGFloat = 52
    public static let agendaBar: CGFloat = 3
    public static let listMark: CGFloat = 14
    public static let quoteBar: CGFloat = 3
    public static let replyAction: CGFloat = 34
    public static let shimmerTravel: CGFloat = 160
    /// The Live Activity: its small mark, the Stop button, and the island's compact mark.
    public static let activityMark: CGFloat = 22
    public static let activityStop: CGFloat = 30
    public static let islandMark: CGFloat = 22
    /// At or below this size the mark uses its small image (Live Activities can't draw large ones).
    public static let smallMarkLimit: CGFloat = 32
    public static let activityButton: CGFloat = 36
    /// The ticking time on the compact Dynamic Island.
    public static let islandTimer: CGFloat = 52
    /// iOS's height limit for a Live Activity (Lock Screen, expanded island), and the compact island's size, for previews.
    public static let activityLimit: CGFloat = 160
    public static let previewIsland: CGFloat = 250
    public static let previewIslandHeight: CGFloat = 37

    /// Voice: the play button on a voice, the voice card's logo, the wave's bar, the listening orb and its rings.
    /// Talk to Mode v3: shared by setup previews and the full voice picker.
    public static let voiceChoiceRow: CGFloat = 52
    public static let voiceProviderRow: CGFloat = 60
    public static let voiceProviderLogo: CGFloat = 36
    public static let voiceChangeHeight: CGFloat = 34
    public static let voiceChangePadding: CGFloat = 14
    public static let voiceSectionHeading: CGFloat = 22
    public static let playButton: CGFloat = 40
    public static let voiceAvatar: CGFloat = 46
    public static let voiceLogo: CGFloat = 52
    public static let waveBar: CGFloat = 3
    public static let waveHeight: CGFloat = 18
    public static let orb: CGFloat = 104
    public static let orbRing: CGFloat = 26
    /// The compact orb's ring (setup's Talk to Mode).
    public static let orbRingCompact: CGFloat = 10
    public static let setupOrb: CGFloat = 56
    public static let dragHandle: CGFloat = 16
    /// Talking mode: the orb, the voice's bars, the big mic and the round buttons beside it.
    public static let talkOrb: CGFloat = 180
    public static let talkOrbSmall: CGFloat = 112
    public static let talkWave: CGFloat = 36
    public static let talkMic: CGFloat = 76
    public static let talkSide: CGFloat = 52
    public static let talkSideWidth: CGFloat = 88

    /// Readable line length for blocks of text.
    public static let readableWidth: CGFloat = 520
    /// How tall the fade is where content scrolls under the top bar and under the tab bar.
    public static let edgeFadeTop: CGFloat = 120
    public static let edgeFadeBottom: CGFloat = 150
}

/// How things move.
public enum ModeMotion {
    public static let quick = Animation.snappy(duration: 0.2)
    public static let standard = Animation.smooth(duration: 0.35)
    /// One sweep of the working shimmer.
    public static let shimmerSeconds: Double = 1.4
}

/// The soft lift under floating things (cards in setup, the tab bar, round buttons).
public enum ModeShadow {
    public static let radius: CGFloat = 12
    public static let y: CGFloat = 8
    public static let opacity: Double = 0.10
}

/// Opacities for effects.
public enum ModeOpacity {
    /// The brightest point of the working shimmer, and its width as a share of the text.
    public static let shimmer: Double = 0.55
    public static let shimmerWidth: CGFloat = 0.5
    /// A quieter version of a colour beside its full one (the other days in a week's bars).
    public static let muted: Double = 0.28
    /// The rings around the listening orb, outermost first.
    public static let orbRings: [Double] = [0.08, 0.14, 0.22]
}
