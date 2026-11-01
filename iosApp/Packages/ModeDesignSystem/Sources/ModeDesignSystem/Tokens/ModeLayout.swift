import SwiftUI

/// Spacing on a 4-point grid.
public enum ModeSpacing {
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
    public static let field: CGFloat = 22
    public static let card: CGFloat = 26
    public static let sheet: CGFloat = 34
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
    /// Readable line length for blocks of text.
    public static let readableWidth: CGFloat = 520
    /// The glows behind the page's top edge, as a share of its width and height.
    public static let glowWidth: CGFloat = 0.9
    public static let glowHeight: CGFloat = 0.42
}

/// How things move.
public enum ModeMotion {
    public static let quick = Animation.snappy(duration: 0.2)
    public static let standard = Animation.smooth(duration: 0.35)
}
