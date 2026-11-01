import ModeDesignSystem
import SwiftUI

/// The task running now, each step as it happens. Draws state, sends events.
struct LiveScreen: View {
    let state: LiveViewModel.State
    let onEvent: (LiveViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                if state.steps.isEmpty && state.finished == nil {
                    ModeEmptyState(icon: .running, title: L10n.Live.idleTitle, message: L10n.Live.idleMessage).frame(maxWidth: .infinity)
                }
                if state.isRunning {
                    Text(state.title).modeText(.title2)
                    Text(state.status).modeText(.callout, color: ModeColor.textSecondary)
                }
                if let finished = state.finished {
                    ModeCard {
                        VStack(alignment: .leading, spacing: ModeSpacing.s) {
                            ModeBadge(finished.badge.text, icon: finished.badge.icon, tone: finished.badge.tone)
                            Text(finished.summary).modeText(.body)
                            Text(finished.line).modeText(.footnote, color: ModeColor.textSecondary)
                        }
                        .padding(.vertical, ModeSpacing.m)
                    }
                }
                if !state.steps.isEmpty {
                    ModeCard {
                        ForEach(state.steps) { step in
                            ModeStepRow(index: step.id, title: step.title, detail: step.detail, state: step.state)
                        }
                    }
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
        }
        .modeScreen()
        .navigationTitle(Text(L10n.Live.title))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            if state.isRunning {
                ToolbarItem(placement: .topBarTrailing) { ModeButton(L10n.Common.stop, icon: .stop, role: .destructive) { onEvent(.stop) } }
            }
        }
    }
}

struct LiveRoute: View {
    @State private var model: LiveViewModel

    init(app: AppModel) {
        _model = State(initialValue: LiveViewModel(screens: app.screens))
    }

    var body: some View {
        LiveScreen(state: model.state, onEvent: model.send)
    }
}
