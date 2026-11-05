import SwiftUI

/// The "Did you know?" banner on Home: one tone per tip (a soft card, its words, the badge behind the icon).
public enum ModeTipTone: Sendable, CaseIterable {
    case coral, blue, green, purple, orange

    var background: Color {
        switch self {
        case .coral: Color("TipCoral", bundle: .module)
        case .blue: Color("TipBlue", bundle: .module)
        case .green: Color("TipGreen", bundle: .module)
        case .purple: Color("TipPurple", bundle: .module)
        case .orange: Color("TipOrange", bundle: .module)
        }
    }

    var text: Color {
        switch self {
        case .coral: Color("TipCoralText", bundle: .module)
        case .blue: Color("TipBlueText", bundle: .module)
        case .green: Color("TipGreenText", bundle: .module)
        case .purple: Color("TipPurpleText", bundle: .module)
        case .orange: Color("TipOrangeText", bundle: .module)
        }
    }

    var badge: Color {
        switch self {
        case .coral: Color("TipCoralBadge", bundle: .module)
        case .blue: Color("TipBlueBadge", bundle: .module)
        case .green: Color("TipGreenBadge", bundle: .module)
        case .purple: Color("TipPurpleBadge", bundle: .module)
        case .orange: Color("TipOrangeBadge", bundle: .module)
        }
    }
}

public extension ModeSize {
    /// Mo on the tips banner.
    static let tipMo: CGFloat = 64
    /// The icon badge on Mo's corner, and its ring.
    static let tipBadge: CGFloat = 30
    static let tipBadgeRing: CGFloat = 3
    static let tipBadgeIcon: CGFloat = 13
    /// The close button's visible circle (its tap area is 44).
    static let tipClose: CGFloat = 30
    /// A page dot, the current one, and the tap area around each.
    static let tipDot: CGFloat = 8
    static let tipDotCurrent: CGFloat = 18
    static let tipDotHit: CGFloat = 16
    static let tipDotHitCurrent: CGFloat = 26
    static let tipDotHitHeight: CGFloat = 28
    /// The undo message after closing the tips.
    static let tipToast: CGFloat = 56
}

public extension ModeSize {
    /// The badge on a row's Mo (What Mode knows about you), and its ring.
    static let moRowBadge: CGFloat = 19
    static let moRowBadgeRing: CGFloat = 2
}

public extension ModeIconFont {
    /// The icon in a row's Mo badge.
    static let moBadge = Font.system(size: 10.5, weight: .bold)
}

public extension ModeRadius {
    static let tipBanner: CGFloat = 26
}

public extension ModeSpacing {
    /// How far the badge sits past Mo's edge.
    static let tipBadgeOffset: CGFloat = 5
    /// The "Did you know?" label's letter spacing.
    static let tipLabelTracking: CGFloat = 0.8
}

public extension ModeColor {
    /// The close button's circle and the page dots not showing, on a tip's tone.
    static let tipChip = ModeColor.textPrimary.opacity(0.08)
    static let tipDotOff = ModeColor.textPrimary.opacity(0.2)
}
