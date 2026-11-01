import ModeDesignSystem
import SwiftUI

/// Setup on first launch, then the tabs.
struct RootView: View {
    let app: AppModel

    var body: some View {
        if app.needsOnboarding {
            OnboardingRoute(app: app)
        } else {
            MainTabs(app: app)
        }
    }
}
