import SwiftUI

/// Sizes for Settings and its pages (AI providers, models, Access, Health & repair, Language).
public extension ModeSize {
    /// The small status dot before "Connected", "Available", a plan's name.
    static let statusDot: CGFloat = 8
    /// A row that holds a 40-point tile (providers, models): 40 + 16 above and below.
    static let tileRow: CGFloat = 72
    /// A value row on a provider's page (Signed in as, Plan, Today).
    static let valueRow: CGFloat = 52
    /// Health & repair: a row, its icon tile, the check at its end, and the small Fix button.
    static let healthRow: CGFloat = 60
    static let healthTile: CGFloat = 34
    static let healthTileRadius: CGFloat = 10
    static let healthCheck: CGFloat = 22
    static let healthAction: CGFloat = 32
    /// The Repair button on Health's banner.
    static let bannerAction: CGFloat = 38
    /// Mo on Health's banner, and on a provider's page.
    static let moBanner: CGFloat = 40
    static let moProvider: CGFloat = 44
    /// Mo inside the "Measure on this iPhone" button.
    static let moButton: CGFloat = 26
    /// A provider's page: its big tile and the tile's corners.
    static let providerTile: CGFloat = 56
    static let providerTileRadius: CGFloat = 18
    /// A wide button that is not the screen's main action (See usage, Measure, Sign out).
    static let wideButton: CGFloat = 48
    /// The phone control / brain switch and the effort switch inside a model.
    static let roleSwitch: CGFloat = 38
    static let effortSwitch: CGFloat = 32
    /// The "ChatGPT Plus · 4 models" chip.
    static let infoChip: CGFloat = 28
    /// A filter chip as drawn (its tap area stays 44).
    static let filterChip: CGFloat = 34
    /// A tag beside a name ("Recommended", "In use").
    static let tag: CGFloat = 20
    /// Week bars on a provider's page, and a usage bar's thickness.
    static let weekBars: CGFloat = 48
    static let usageBar: CGFloat = 8
    /// Settings → Voice: the 32-point logo tile's corners, and its rows (plain, with a tag, with a problem).
    static let smallTileRadius: CGFloat = 9
    static let logoRow: CGFloat = 60
    static let logoRowTagged: CGFloat = 62
    static let logoRowProblem: CGFloat = 72
    /// The width of the speed and accuracy columns in the effort table.
    static let effortColumn: CGFloat = 92
}

public extension ModeRadius {
    /// Health's banner, the key field on the OpenRouter sheet.
    static let banner: CGFloat = 22
    /// The effort table, and the chosen effort's row inside it.
    static let table: CGFloat = 16
    static let tableRow: CGFloat = 12
    /// A week bar's corners.
    static let bar: CGFloat = 4
}

public extension ModeSpacing {
    /// Notes under a card sit 8 points in from the card's edge; headings 4.
    static let noteInset: CGFloat = 8
    /// The gap in the provider page's heading, and inside a card's banner.
    static let headerGap: CGFloat = 14
}

/// How much of the screen a Settings sheet takes before it is pulled up.
public enum ModeSheetHeight {
    /// AI providers.
    public static let providers: CGFloat = 0.66
    /// Connect OpenRouter.
    public static let connect: CGFloat = 0.64
}

public extension ModeColor {
    /// Text on a blue (info) button: Health's Fix.
    static let onInfo = Color.white
}
