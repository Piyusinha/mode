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
