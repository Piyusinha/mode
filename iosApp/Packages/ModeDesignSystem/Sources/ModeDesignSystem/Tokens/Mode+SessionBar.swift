import SwiftUI

/// The session bar: an open voice session, minimised above the tab bar or a chat's message bar.
extension ModeSize {
    static let sessionBarMo: CGFloat = 36
    static let sessionBarWaveWidth: CGFloat = 3
    static let sessionBarWaveHeight: CGFloat = 20
    static let sessionBarWaveGap: CGFloat = 3
    static let sessionBarAction: CGFloat = 44
    static let sessionBarActionIcon: CGFloat = 18
    static let sessionBarRing: CGFloat = 3
}

extension ModeRadius {
    static let sessionBar: CGFloat = 22
}

extension ModeColor {
    /// The bar's dark card, and its quiet second line.
    static let sessionBar = ModeColor.hero
    static let sessionBarText = ModeColor.heroText
    static let sessionBarSecondary = ModeColor.heroSecondary
}

extension ModeMotion {
    /// One beat of the listening bars.
    static let sessionBarWave = Animation.easeInOut(duration: 0.45).repeatForever(autoreverses: true)
}

/// The listening bars' resting heights (fractions of the tallest).
enum ModeSessionBarWave {
    static let rest: [CGFloat] = [0.45, 1.0, 0.7, 0.9]
    static let quiet: CGFloat = 0.35
    static let staggerSeconds: Double = 0.12
}
