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
                    .clipShape(.rect(cornerRadius: ModeRadius.card))
                    .padding(ModeSpacing.l)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.liveScreen))
                    .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.liveScreen))
                    .padding(.horizontal, ModeSpacing.xl)
                    .padding(.top, ModeSpacing.s)
            } else if state.running == nil {
                if let finished = state.finished {
                    ScrollView {
                        VStack(alignment: .leading, spacing: ModeSpacing.m) {
                            ModeBadge(finished.badge.text, icon: finished.badge.icon, tone: finished.badge.tone)
                            ModeReply(finished.reply)
                            Text(finished.line).modeText(.footnote, color: ModeColor.textSecondary)
                        }
                        .padding(ModeSpacing.screenEdge)
                    }
                    .frame(maxHeight: .infinity)
                } else {
                    ModeEmptyState(icon: .running, title: L10n.Live.idleTitle, message: L10n.Live.idleMessage).frame(maxHeight: .infinity)
                }
            } else {
                ProgressView().tint(ModeColor.brand).frame(maxHeight: .infinity)
            }
            if let running = state.running {
                ModeRunPanel(
                    task: running.task, now: running.now, segments: running.segments, paused: running.paused,
                    pauseLabel: L10n.Run.pause, resumeLabel: L10n.Run.resume, stopLabel: L10n.Common.stop,
                    onPauseToggle: { onEvent(.togglePause) }, onStop: { onEvent(.stop) }
                )
                .padding(.horizontal, ModeSpacing.s)
                .padding(.bottom, ModeSpacing.s)
            }
        }
        .ignoresSafeArea(edges: state.running == nil ? [] : .bottom)
        .modeScreen()
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden()
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { ModeIconButton(.back, label: L10n.Common.back) { onEvent(.close) } }
                .sharedBackgroundVisibility(.hidden)
            ToolbarItem(placement: .principal) {
                if let running = state.running { ModeRunBadge(running.badge, since: running.startedAt, paused: running.paused) }
            }
            .sharedBackgroundVisibility(.hidden)
            ToolbarItem(placement: .topBarTrailing) { ModeIconButton(.shrink, label: L10n.Run.shrink) { onEvent(.close) } }
                .sharedBackgroundVisibility(.hidden)
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
