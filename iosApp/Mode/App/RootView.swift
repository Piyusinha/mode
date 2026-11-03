import ModeDesignSystem
import SwiftUI

/// Setup on first launch, then the tabs.
struct RootView: View {
    let app: AppModel

    var body: some View {
        #if DEBUG && targetEnvironment(simulator)
        if let target = ProcessInfo.processInfo.environment["MODE_OPEN"], target.hasPrefix("sample:") {
            ScreenSample(id: String(target.dropFirst("sample:".count))).screen
        } else {
            content
        }
        #else
        content
        #endif
    }

    @ViewBuilder private var content: some View {
        if app.needsOnboarding {
            OnboardingRoute(app: app)
        } else {
            MainTabs(app: app)
        }
    }
}
