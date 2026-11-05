import SwiftUI

// The Live Activity and the Dynamic Island, from the "Live Activity, Dynamic Island and
// notifications" boards. They sit on the dark Lock Screen card or the black island; a question
// turns the Lock Screen card white with a blue ring.

public extension ModeSize {
    /// Mo on the Lock Screen card, in the expanded island, beside the camera, and when sharing the island.
    static let activityMo: CGFloat = 28
    static let islandExpandedMo: CGFloat = 30
    static let islandCompactMo: CGFloat = 26
    static let islandMinimalMo: CGFloat = 28
    /// The header row (Mo, status, Stop) on the Lock Screen and in the island.
    static let activityHeader: CGFloat = 30
    /// A step's bar.
    static let activityBar: CGFloat = 5
    /// The ring beside the camera that fills with the plan, and its line.
    static let islandRing: CGFloat = 18
    static let islandRingLine: CGFloat = 3.5
    /// The blue ring around the Lock Screen card while Mode asks something.
    static let activityAskRing: CGFloat = 3
    /// The stop square inside the red disc.
    static let activityStopSquare: CGFloat = 11
    /// The ticking time beside the camera.
    static let islandClock: CGFloat = 40
}

public extension ModeSpacing {
    /// The Lock Screen card's padding.
    static let activityHorizontal: CGFloat = 16
    static let activityVertical: CGFloat = 14
    /// Between a status icon and its words.
    static let activityBadgeGap: CGFloat = 5
    /// Between steps' bars.
    static let activityBarGap: CGFloat = 6
    /// The expanded island's content, below the camera.
    static let islandGap: CGFloat = 10
    static let islandHorizontal: CGFloat = 8
    /// A capsule button's padding.
    static let activityButtonPadding: CGFloat = 10
}

public extension ModeColor {
    /// The Lock Screen card while Mode asks something: white whatever the phone's appearance, ringed in blue.
    static let lockScreenAsk = Color.white
    static let lockScreenAskRing = Color(red: 0.141, green: 0.314, blue: 0.902)
    /// Words on the blue answer button.
    static let onAsk = Color.white
    /// A grey button on the black island (a little brighter than on the Lock Screen card).
    static let onIslandFill = Color.white.opacity(0.16)
    /// Quiet words on a dark card: the clock, a step's name.
    /// Words and marks on the Live Activity's dark card and the Dynamic Island.
    static let onDark = Color.white
    static let onDarkSecondary = Color.white.opacity(0.7)
    static let onDarkLabel = Color.white.opacity(0.66)
    /// The step being done now: Mo's orange, softer.
    static let activityNow = Color(red: 1.0, green: 0.42, blue: 0.24).opacity(0.55)
}

/// The type the Live Activity uses besides the main scale.
public enum ModeActivityFont {
    /// What Mode is doing, or asks, on the Lock Screen: Bricolage 17 bold.
    public static var title: Font { bricolage(size: 17, weight: 800, opticalSize: 24) }
    /// The ticking time beside the camera: 14 semibold, figures the same width.
    public static var clock: Font { Font(UIFontMetrics(forTextStyle: .subheadline).scaledFont(for: .systemFont(ofSize: 14, weight: .semibold))).monospacedDigit() }
}

private extension ModeActivityFont {
    /// Bricolage at an optical size of its own (the canvas renders this title narrower than 17's own).
    static func bricolage(size: CGFloat, weight: CGFloat, opticalSize: CGFloat) -> Font {
        let opsz = 0x6F70737A, wght = 0x77676874
        let descriptor = UIFontDescriptor(fontAttributes: [
            .family: ModeTypography.displayFamily,
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [opsz: opticalSize, wght: weight],
        ])
        let font = UIFont(descriptor: descriptor, size: size)
        return Font(UIFontMetrics(forTextStyle: .headline).scaledFont(for: font.familyName == ModeTypography.displayFamily ? font : .systemFont(ofSize: size, weight: .bold)))
    }
}
