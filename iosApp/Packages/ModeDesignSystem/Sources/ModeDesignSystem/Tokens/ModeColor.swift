import SwiftUI

/// Every colour in Mode, each with a light and a dark value (Resources/Colors.xcassets,
/// taken from the design canvas). Screens use these, never their own colours.
public enum ModeColor {
    /// The screen behind everything.
    public static let page = named("Page")
    /// Grouped content on the page.
    public static let card = named("Card")
    /// Text fields and pickers.
    public static let field = named("Field")
    public static let textPrimary = named("TextPrimary")
    public static let textSecondary = named("TextSecondary")
    public static let separator = named("Separator")
    /// Mode's violet: the main action.
    public static let accent = named("Accent")
    /// Text and icons on the accent.
    public static let onAccent = named("OnAccent")
    /// Selected tabs, links, highlighted icons: readable on the page in both modes.
    public static let tint = named("Tint")
    /// A soft tint behind icons and chips.
    public static let tintSoft = named("TintSoft")
    public static let success = named("Success")
    public static let warning = named("Warning")
    public static let danger = named("Danger")
    /// The two soft glows at the top of the page.
    public static let glowViolet = named("GlowViolet")
    public static let glowPink = named("GlowPink")
    /// Talking mode's orb: a light violet core, Mode's accent, a pink light, the same in both themes.
    public static let orbLight = Color(red: 0.557, green: 0.549, blue: 1.0)
    public static let orbDeep = Color(red: 0.247, green: 0.239, blue: 0.722)
    public static let orbPink = Color(red: 1.0, green: 0.494, blue: 0.714)
    /// Voice avatars: strong gradient pairs that keep a white initial readable in both themes.
    public static let avatarPairs: [(Color, Color)] = [
        (Color(red: 1.0, green: 0.56, blue: 0.72), Color(red: 0.70, green: 0.42, blue: 1.0)),
        (Color(red: 1.0, green: 0.72, blue: 0.42), Color(red: 1.0, green: 0.42, blue: 0.42)),
        (Color(red: 0.42, green: 0.84, blue: 1.0), Color(red: 0.42, green: 0.48, blue: 1.0)),
        (Color(red: 0.48, green: 0.88, blue: 0.63), Color(red: 0.17, green: 0.69, blue: 0.63)),
        (Color(red: 0.63, green: 0.66, blue: 1.0), Color(red: 0.37, green: 0.36, blue: 0.90)),
        (Color(red: 1.0, green: 0.83, blue: 0.42), Color(red: 1.0, green: 0.62, blue: 0.26)),
    ]
    /// Tags and tiles from the boards.
    public static let tagGreen = named("TagGreen")
    public static let tagGreenText = named("TagGreenText")
    public static let tileOrange = named("TileOrange")
    public static let tileOrangeText = named("TileOrangeText")
    public static let tileRed = named("TileRed")
    public static let tileGray = named("TileGray")
    /// Grey fills: secondary buttons (No, Pause), the Paste button.
    public static let fill = named("Fill")
    /// The Stop button.
    public static let stopRed = named("StopRed")
    /// The soft green glow behind a success page.
    public static let glowGreen = named("GlowGreen")

    /// Behind sheets and dialogs.
    public static let scrim = named("Scrim")
    /// Behind the Live Activity on the Lock Screen: dark glass whatever the phone's appearance.
    public static let lockScreen = Color.black.opacity(0.55)
    /// The Stop button's dark red disc on the Dynamic Island.
    public static let stopDisc = stopRed.opacity(0.22)

    private static func named(_ name: String) -> Color { Color(name, bundle: .module) }
}
