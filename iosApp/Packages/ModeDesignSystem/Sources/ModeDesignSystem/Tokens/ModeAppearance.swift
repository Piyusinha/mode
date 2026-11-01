import SwiftUI

/// App-wide settings that SwiftUI can't set per view: navigation bar title fonts.
public enum ModeAppearance {
    @MainActor
    public static func apply() {
        let bar = UINavigationBar.appearance()
        bar.largeTitleTextAttributes = [.font: ModeTypography.uiDisplay(.largeTitle, weight: .bold)]
        bar.titleTextAttributes = [.font: ModeTypography.uiDisplay(.headline, weight: .semibold)]
    }
}
