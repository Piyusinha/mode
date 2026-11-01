import ModeDesignSystem
import SwiftUI

/// Every screen of a task, side by side, with what Mode did on each.
struct RunScreensScreen: View {
    let state: RunScreensViewModel.State

    var body: some View {
        Group {
            if state.shots.isEmpty {
                ModeEmptyState(icon: .screens, title: L10n.Screens.emptyTitle, message: L10n.Screens.emptyMessage)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else {
                ScrollView(.horizontal) {
                    LazyHStack(alignment: .top, spacing: ModeSpacing.m) {
                        ForEach(state.shots) { shot in
                            VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                                if let image = shot.image {
                                    Image(uiImage: image)
                                        .resizable()
                                        .scaledToFit()
                                        .frame(width: ModeSize.thumbnailWidth)
                                        .clipShape(.rect(cornerRadius: ModeRadius.chip))
                                }
                                Text(shot.title).modeText(.captionEmphasis)
                                Text(shot.action).modeText(.caption, color: ModeColor.textSecondary).lineLimit(2)
                            }
                            .frame(width: ModeSize.thumbnailWidth)
                        }
                    }
                    .padding(ModeSpacing.screenEdge)
                }
            }
        }
        .modeScreen()
        .navigationTitle(Text(L10n.Screens.title))
        .navigationBarTitleDisplayMode(.inline)
    }
}

struct RunScreensRoute: View {
    @State private var model: RunScreensViewModel

    init(app: AppModel, taskId: String) {
        _model = State(initialValue: RunScreensViewModel(screens: app.screens, taskId: taskId))
    }

    var body: some View {
        RunScreensScreen(state: model.state)
    }
}
