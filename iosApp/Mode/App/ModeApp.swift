import ModeDesignSystem
import SwiftUI

@main
struct ModeApp: App {
    @State private var app = AppModel()

    init() {
        if !ModeTypography.registerFonts() { NSLog("Mode: Instrument Sans did not load; headings use SF Pro") }
        ModeAppearance.apply()
    }

    var body: some Scene {
        WindowGroup {
            RootView(app: app)
        }
    }
}
