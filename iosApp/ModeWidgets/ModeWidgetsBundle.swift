import ModeDesignSystem
import SwiftUI
import WidgetKit

/// Mode's widget extension: the Live Activity of the running task, and "Mode is connected".
@main
struct ModeWidgetsBundle: WidgetBundle {
    init() {
        _ = ModeTypography.registerFonts()
    }

    var body: some Widget {
        ModeLiveActivity()
        ModeSessionActivity()
    }
}
