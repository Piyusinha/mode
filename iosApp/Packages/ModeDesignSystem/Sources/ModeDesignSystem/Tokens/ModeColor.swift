import SwiftUI

/// Every colour in Mode, each with a light and a dark value (Resources/Colors.xcassets,
/// taken from the Mo theme canvas). Screens use these, never their own colours.
public enum ModeColor {
    /// The screen behind everything: warm paper in light, near-black in dark.
    public static let page = named("Page")
    /// Grouped content on the page.
    public static let card = named("Card")
    /// A step up from a card: icon circles, secondary buttons, the selected tab, chips.
    public static let cardRaised = named("CardRaised")
    /// Text fields and pickers.
    public static let field = named("Field")
    public static let textPrimary = named("TextPrimary")
    public static let textSecondary = named("TextSecondary")
    public static let separator = named("Separator")
    /// The outline of an empty radio circle.
    public static let ring = named("Ring")
    /// The empty part of a progress bar or the steps still to come.
    public static let track = named("Track")
    /// Ink: the main action (Continue, Speak, Send, a selected day). Near-black in light, paper in dark.
    public static let accent = named("Accent")
    /// Text and icons on the accent.
    public static let onAccent = named("OnAccent")
    /// Mo's orange, the same in both themes: Mo, progress, the current step, the voice's bars.
    public static let brand = named("Brand")
    /// Orange that reads as text on the page in both themes: links, remembered notes.
    public static let tint = named("Tint")
    /// A soft orange wash behind a remembered note or a highlight.
    public static let tintSoft = named("TintSoft")
    /// The running task's card: ink in light, a raised grey in dark.
    public static let hero = named("Hero")
    public static let heroText = named("HeroText")
    public static let heroSecondary = named("HeroSecondary")
    public static let success = named("Success")
    public static let warning = named("Warning")
    public static let danger = named("Danger")
    /// Blue for information (a question waiting, a tip).
    public static let info = named("Info")
    public static let infoText = named("InfoText")
    public static let infoSoft = named("InfoSoft")
    /// Mo's lines and dark parts: the same ink whatever the theme, because Mo is always orange.
    public static let moInk = Color(red: 0.102, green: 0.102, blue: 0.122)
    /// The highlights on Mo: a "done" sparkle and a "stuck" drop.
    public static let moSparkle = Color(red: 0.949, green: 0.718, blue: 0.020)
    public static let moDrop = Color(red: 0.498, green: 0.698, blue: 1.0)
    /// Mo's "needs you" badge.
    public static let moBadge = Color(red: 0.141, green: 0.314, blue: 0.902)
    /// Talking mode's orb, in Mo's oranges, the same in both themes.
    public static let orbLight = Color(red: 1.0, green: 0.690, blue: 0.125)
    public static let orbDeep = Color(red: 0.722, green: 0.263, blue: 0.118)
    public static let orbPink = Color(red: 1.0, green: 0.604, blue: 0.471)
    /// Voice avatars: warm, distinct pairs that keep a white initial readable in both themes.
    public static let avatarPairs: [(Color, Color)] = [
        (Color(red: 1.0, green: 0.42, blue: 0.24), Color(red: 0.72, green: 0.26, blue: 0.12)),
        (Color(red: 0.55, green: 0.49, blue: 1.0), Color(red: 0.34, green: 0.28, blue: 0.80)),
        (Color(red: 0.18, green: 0.75, blue: 0.55), Color(red: 0.11, green: 0.48, blue: 0.27)),
        (Color(red: 1.0, green: 0.69, blue: 0.13), Color(red: 0.64, green: 0.31, blue: 0.0)),
        (Color(red: 0.24, green: 0.39, blue: 0.94), Color(red: 0.11, green: 0.25, blue: 0.72)),
        (Color(red: 0.37, green: 0.36, blue: 0.40), Color(red: 0.10, green: 0.10, blue: 0.12)),
    ]
    /// Tags and tiles from the boards.
    public static let tagGreen = named("TagGreen")
    public static let tagGreenText = named("TagGreenText")
    public static let tileOrange = named("TileOrange")
    public static let tileOrangeText = named("TileOrangeText")
    public static let tileRed = named("TileRed")
    public static let tileGray = named("TileGray")
    /// Secondary buttons (No, Pause), the Paste button: the same as a raised card.
    public static let fill = named("Fill")
    /// The Stop button.
    public static let stopRed = named("StopRed")
    /// The soft green glow behind a success page.
    public static let glowGreen = named("GlowGreen")

    /// Behind sheets and dialogs.
    public static let scrim = named("Scrim")
    /// Behind the Live Activity on the Lock Screen: Mode's dark card whatever the phone's appearance.
    public static let lockScreen = Color(red: 0.165, green: 0.165, blue: 0.192)
    /// The Stop button's red disc on dark (the hero card, the Dynamic Island).
    public static let stopDisc = Color(red: 1.0, green: 0.42, blue: 0.38).opacity(0.2)
    public static let stopOnDark = Color(red: 1.0, green: 0.42, blue: 0.38)
    /// A see-through white button on a dark card (Pause on the hero, Cancel on the Lock Screen).
    public static let onDarkFill = Color.white.opacity(0.12)
    /// The empty part of a bar on a dark card.
    public static let onDarkTrack = Color.white.opacity(0.22)

    private static func named(_ name: String) -> Color { Color(name, bundle: .module) }
}
