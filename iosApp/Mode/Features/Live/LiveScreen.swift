import ModeDesignSystem
import SwiftUI

/// Live run, as on its board: the app Mode is using right now, and a glass
/// panel with what it's doing, the steps so far, Pause and Stop.
struct LiveScreen: View {
    let state: LiveViewModel.State
    let onEvent: (LiveViewModel.Event) -> Void

    var body: some View {
        VStack(spacing: ModeSpacing.m) {
            if let screen = state.screen {
                Image(uiImage: screen)
                    .resizable()
                    .scaledToFit()
                    .clipShape(.rect(cornerRadius: ModeRadius.sheet))
                    .shadow(color: ModeColor.scrim, radius: ModeSpacing.l, y: ModeSpacing.s)
                    .frame(maxHeight: .infinity)
                    .padding(.horizontal, ModeSpacing.xl)
            } else if state.running == nil {
                if let finished = state.finished {
                    VStack(spacing: ModeSpacing.m) {
                        ModeBadge(finished.badge.text, icon: finished.badge.icon, tone: finished.badge.tone)
                        Text(finished.summary).modeText(.body).multilineTextAlignment(.center)
                        Text(finished.line).modeText(.footnote, color: ModeColor.textSecondary)
                    }
                    .padding(ModeSpacing.xl)
                    .frame(maxHeight: .infinity)
                } else {
                    ModeEmptyState(icon: .running, title: L10n.Live.idleTitle, message: L10n.Live.idleMessage).frame(maxHeight: .infinity)
                }
            } else {
                ProgressView().tint(ModeColor.tint).frame(maxHeight: .infinity)
            }
            if let running = state.running {
                ModeLivePanel(
                    task: running.task, now: running.now, steps: running.steps, paused: running.paused,
                    pauseLabel: L10n.Run.pause, resumeLabel: L10n.Run.resume, stopLabel: L10n.Common.stop,
                    onPauseToggle: { onEvent(.togglePause) }, onStop: { onEvent(.stop) }
                )
                .padding(.horizontal, ModeSpacing.s)
            }
        }
        .modeScreen()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { ModeToolbarButton(.back, label: L10n.Common.back) { onEvent(.close) } }
            ToolbarItem(placement: .principal) {
                if let running = state.running { ModeLiveBadge(running.badge, since: running.startedAt, paused: running.paused) }
            }
            ToolbarItem(placement: .topBarTrailing) { ModeToolbarButton(.shrink, label: L10n.Run.shrink) { onEvent(.close) } }
        }
    }
}

struct LiveRoute: View {
    @Environment(\.dismiss) private var dismiss
    let app: AppModel

    var body: some View {
        LiveContent(app: app) { dismiss() }
    }
}

private struct LiveContent: View {
    @State private var model: LiveViewModel

    init(app: AppModel, close: @escaping () -> Void) {
        _model = State(initialValue: LiveViewModel(screens: app.screens, close: close))
    }

    var body: some View {
        LiveScreen(state: model.state, onEvent: model.send)
    }
}
