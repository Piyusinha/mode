import SwiftUI

/// App-wide settings that SwiftUI can't set per view: navigation bar title fonts.
/// Set on the bar's appearances too, since iOS 26 ignores the bare appearance-proxy attributes
/// once a bar has an appearance of its own.
public enum ModeAppearance {
    @MainActor
    public static func apply() {
        let large: [NSAttributedString.Key: Any] = [
            .font: ModeTypography.uiDisplay(.largeTitle, weight: .heavy),
            .foregroundColor: UIColor(ModeColor.textPrimary),
            .kern: -0.68,
        ]
        let inline: [NSAttributedString.Key: Any] = [
            .font: ModeTypography.uiDisplay(.headline, weight: .bold),
            .foregroundColor: UIColor(ModeColor.textPrimary),
        ]
        let appearance = UINavigationBarAppearance()
        appearance.configureWithTransparentBackground()
        appearance.largeTitleTextAttributes = large
        appearance.titleTextAttributes = inline
        let bar = UINavigationBar.appearance()
        bar.largeTitleTextAttributes = large
        bar.titleTextAttributes = inline
        bar.standardAppearance = appearance
        bar.scrollEdgeAppearance = appearance
        bar.compactAppearance = appearance
    }
}
