import ModeDesignSystem
import SwiftUI

/// Home, as on the board: the greeting, "What can I do for you?", the running task's dark card,
/// suggestion cards and recent conversations. Draws state, sends events.
struct HomeScreen: View {
    let state: HomeViewModel.State
    let running: RunStatusViewModel.Running?
    let onEvent: (HomeViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.none) {
                ModeHomeHeader(greeting: state.greeting, title: L10n.Home.subtitle)
                    .padding(.horizontal, ModeSpacing.xl)
                    .padding(.bottom, ModeSpacing.xl + ModeSpacing.xs)
                if let running {
                    ModeMiniPlayer(
                        title: running.title, status: running.status, paused: running.paused, progress: running.progress,
                        pauseLabel: L10n.Run.pause, resumeLabel: L10n.Run.resume, stopLabel: L10n.Common.stop,
                        onPauseToggle: { onEvent(.pauseToggle) }, onStop: { onEvent(.stop) }, onOpen: { onEvent(.openRun) }
                    )
                    .padding(.horizontal, ModeSpacing.screenEdge)
                    .padding(.bottom, ModeSpacing.m)
                }
                if state.needsKey {
                    ModeCard {
                        ModeRow(String(localized: L10n.Home.needsKeyTitle), subtitle: String(localized: L10n.Home.needsKeyMessage),
                                leading: .icon(.key, ModeColor.warning), emphasis: .prominent, onTap: { onEvent(.addKey) })
                    }
                    .padding(.horizontal, ModeSpacing.screenEdge)
                    .padding(.bottom, ModeSpacing.m)
                }
                if state.isEmpty && running == nil {
                    ModeEmptyState(icon: .ask, title: L10n.Home.emptyTitle, message: L10n.Home.emptyMessage).frame(maxWidth: .infinity)
                }
                if !state.suggestions.isEmpty {
                    section(L10n.Home.suggested)
                    ScrollView(.horizontal) {
                        HStack(alignment: .top, spacing: ModeSpacing.m) {
                            ForEach(state.suggestions) { s in
                                ModeSuggestionCard(icon: s.icon, title: s.title, subtitle: s.subtitle) { onEvent(.suggestion(s.title)) }
                            }
                        }
                        .fixedSize(horizontal: false, vertical: true)
                        .padding(.horizontal, ModeSpacing.screenEdge)
                    }
                    .scrollIndicators(.hidden)
                    .scrollClipDisabled()
                    .padding(.bottom, ModeSpacing.m)
                }
                if !state.recent.isEmpty {
                    section(L10n.Home.recent)
                    ModeCard {
                        ForEach(state.recent) { row in
                            ModeRow(row.title, subtitle: row.subtitle, leading: row.leading, onTap: { onEvent(.openConversation(row.id)) })
                                .frame(minHeight: ModeSize.recentRow)
                        }
                    }
                    .padding(.horizontal, ModeSpacing.screenEdge)
                }
            }
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                ModeIconButton(.profile, label: L10n.Home.profile) { onEvent(.openSettings) }
            }
            .sharedBackgroundVisibility(.hidden)
        }
    }

    /// A section heading: 16 above it (with the gap after what came before), 10 below.
    private func section(_ title: LocalizedStringResource) -> some View {
        ModeSectionHeader(title)
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.s + ModeSpacing.xxs)
    }
}

/// Connects Home to its ViewModel.
struct HomeRoute: View {
    @State private var model: HomeViewModel
    private let run: RunStatusViewModel

    init(app: AppModel, run: RunStatusViewModel, navigate: @escaping (Route) -> Void, openLive: @escaping () -> Void) {
        self.run = run
        _model = State(initialValue: HomeViewModel(screens: app.screens, run: run, navigate: navigate, openLive: openLive))
    }

    var body: some View {
        HomeScreen(state: model.state, running: run.state.running, onEvent: model.send)
    }
}
