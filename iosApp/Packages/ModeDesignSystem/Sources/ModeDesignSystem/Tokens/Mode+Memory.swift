import SwiftUI

// Sizes from the "Memory, screen by screen" boards.

public extension ModeSize {
    /// A row on the Memory hub (About You, Things Mode Can Do, App Tips), and its round icon or Mo.
    static let memoryKindRow: CGFloat = 76
    static let memoryKindIcon: CGFloat = 40
    /// A row in App Tips, and its initial.
    static let memoryAppRow: CGFloat = 64
    static let memoryAppInitial: CGFloat = 36
    /// The Learn from my tasks card.
    static let memoryToggleRow: CGFloat = 60
    /// The "Worked 3 of 3 times" chip, and the smaller "You taught this" one.
    static let memoryChip: CGFloat = 24
    static let memoryBadge: CGFloat = 20
    /// Mo inside the "Mode learned" badge.
    static let memoryBadgeMo: CGFloat = 16
    /// The editor's text box, at least this tall.
    static let memoryEditorMin: CGFloat = 200
    /// The editor's Delete button.
    static let memoryDelete: CGFloat = 52
    /// The editor's notes' icons.
    static let memoryNoteIcon: CGFloat = 18
    /// One app's page: the app's tile beside its name.
    static let memoryAppTile: CGFloat = 48
}

public extension ModeColor {
    /// One app's tile (its letter, as Mode has no app icons): the canvas's grey, the same in both themes.
    static let memoryAppTile = Color(red: 0.478, green: 0.490, blue: 0.525)
    static let onMemoryAppTile = Color.white
}

public extension ModeRadius {
    /// One remembered item's card, and the editor's fields.
    static let memoryCard: CGFloat = 22
}

public extension ModeSpacing {
    /// Between a row's leading icon and its text.
    static let memoryRowGap: CGFloat = 14
    /// Between the cards in a list, and inside a card's padding (vertical).
    static let memoryCardGap: CGFloat = 10
    static let memoryCardPadding: CGFloat = 14
    /// Between a card's title, text and footer.
    static let memoryCardLine: CGFloat = 6
    /// Space for the top bar on the hub (no back button), and more on a page with one.
    static let memoryHeaderTop: CGFloat = 0
    static let memorySubpageTop: CGFloat = 8
    /// The capsule buttons in the top bar (Edit, Save).
    static let memoryBarButton: CGFloat = 18
}

/// Text sizes the Memory boards use that the main scale doesn't have.
public enum ModeMemoryFont {
    /// 14: a hub or app row's second line, the editor's notes.
    static var detail: Font { Font(UIFontMetrics(forTextStyle: .subheadline).scaledFont(for: .systemFont(ofSize: 14))) }
    /// 16 heavy display: an app's initial.
    static var initial: Font { ModeTypography.display(size: 16, weight: 800, relativeTo: .callout) }
    /// 30 heavy display: one app's name beside its tile.
    static var appTitle: Font { ModeTypography.display(size: 30, weight: 800, relativeTo: .title) }
    /// 20 heavy display: the app's letter in its tile.
    static var appLetter: Font { ModeTypography.display(size: 20, weight: 800, relativeTo: .title3) }
    /// 11 bold: "You taught this", "Mode learned".
    static var badge: Font { Font(UIFontMetrics(forTextStyle: .caption2).scaledFont(for: .systemFont(ofSize: 11, weight: .bold))) }
}
