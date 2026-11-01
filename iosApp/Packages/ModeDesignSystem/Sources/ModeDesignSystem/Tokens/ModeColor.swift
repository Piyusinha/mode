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
    /// Behind sheets and dialogs.
    public static let scrim = named("Scrim")

    private static func named(_ name: String) -> Color { Color(name, bundle: .module) }
}
