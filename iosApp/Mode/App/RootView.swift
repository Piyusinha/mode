import ModeDesignSystem
import SwiftUI

/// Setup on first launch, then the tabs.
struct RootView: View {
    let app: AppModel

    var body: some View {
        if app.needsOnboarding {
            // Replaced by the setup flow in a later step; until then, go straight in.
            MainTabs(app: app).task { app.onboardingFinished() }
        } else {
            MainTabs(app: app)
        }
    }
}
