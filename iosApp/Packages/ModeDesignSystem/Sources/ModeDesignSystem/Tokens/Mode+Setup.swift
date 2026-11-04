import SwiftUI

/// Sizes for first-run setup (Welcome → Find the problem), from its boards.
public extension ModeSize {
    /// Mo on Welcome, on the test result, on Talk to Mode, on Find the problem, in a test phone, beside "Why these".
    static let setupMoWelcome: CGFloat = 124
    static let setupMoResult: CGFloat = 96
    static let setupMoTalk: CGFloat = 60
    static let setupMoDiagnose: CGFloat = 52
    static let setupMoPhone: CGFloat = 44
    static let setupMoNote: CGFloat = 28
    /// A small pill (Welcome's "Open source"), a tag beside a name, and the larger tag on a role card.
    static let setupPill: CGFloat = 32
    static let setupTag: CGFloat = 20
    static let setupTagLarge: CGFloat = 24
    /// The status chip under a title ("ChatGPT · connected"), and its dot.
    static let setupChip: CGFloat = 28
    static let setupDot: CGFloat = 8
    /// A role card's row on "Pick how Mode thinks", a model row, a permission's tile.
    static let setupRoleRow: CGFloat = 76
    static let setupModelRow: CGFloat = 72
    static let setupPickerRow: CGFloat = 66
    static let setupPickerTile: CGFloat = 34
    static let setupPermissionTile: CGFloat = 36
    /// The small Allow button on a permission (its tap area stays 44).
    static let setupAllowButton: CGFloat = 34
    /// The name field's value line, the "How hard it thinks" segments.
    static let setupFieldValue: CGFloat = 30
    static let setupSegment: CGFloat = 52
    /// A column of the test illustration, and how far its arrows sit from the top.
    static let setupTestColumn: CGFloat = 96
    static let setupTestArrowTop: CGFloat = 60
    /// The test illustration's list: a short header bar, its rows, and the tap ring.
    static let setupMockHeader: CGFloat = 6
    static let setupMockHeaderWidth: CGFloat = 30
    static let setupMockRow: CGFloat = 16
    static let setupMockRing: CGFloat = 30
    /// The screenshot beside "What Mode did", its bars, and a row on Find the problem.
    static let setupShotHeight: CGFloat = 176
    static let setupShotBar: CGFloat = 14
    static let setupShotBarTop: CGFloat = 20
    static let setupShotHeader: CGFloat = 7
    static let setupShotHeaderWidth: CGFloat = 34
    static let setupCheckRow: CGFloat = 46
    static let setupCheckMark: CGFloat = 20
    /// The yes/no buttons under "Did you see…", and the Diagnose buttons.
    static let setupAnswerButton: CGFloat = 50
    static let setupFooterButton: CGFloat = 54
}

/// Colours only setup uses.
public extension ModeColor {
    /// Claude's tile on "Pick how Mode thinks": Anthropic's clay, white mark, in both themes.
    static let setupClaudeTile = Color(red: 0.851, green: 0.467, blue: 0.341)
    static let setupOnClaudeTile = Color.white
}

/// Corner radii for setup.
public extension ModeRadius {
    /// Mo's speech bubble ("Hi, I'm Mo."): round, with a small corner by Mo.
    static let setupBubble: CGFloat = 18
    /// A test phone, the screenshot beside "What Mode did", a mock list row.
    static let setupPhone: CGFloat = 18
    static let setupShot: CGFloat = 16
    static let setupMockRow: CGFloat = 5
    /// The note cards (test note, "Why these").
    static let setupNote: CGFloat = 22
    /// The "How hard it thinks" control and its segments.
    static let setupSegments: CGFloat = 18
    static let setupSegment: CGFloat = 14
}

/// Gaps that recur on setup pages.
public extension ModeSpacing {
    /// Between a setup page's title and the line under it.
    static let setupTitleGap: CGFloat = 8
    /// Between the step's header (back button) and its title, on most pages.
    static let setupTop: CGFloat = 32
}

/// Icon sizes only setup uses.
public extension ModeIconFont {
    /// The small icon inside a tag (the effort bars on "Medium").
    static let setupTag = Font.system(size: 10, weight: .bold)
}
