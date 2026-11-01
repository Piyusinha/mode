import ModeDesignSystem
import SwiftUI
import WidgetKit

/// Mode's widget extension: for now, the Live Activity of the running task.
@main
struct ModeWidgetsBundle: WidgetBundle {
    init() {
        _ = ModeTypography.registerFonts()
    }

    var body: some Widget {
        ModeLiveActivity()
    }
}
