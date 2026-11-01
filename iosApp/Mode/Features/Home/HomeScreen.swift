import ModeDesignSystem
import SwiftUI

/// Home, as on the board: the greeting, "What can I do for you?", suggestion
/// cards and recent conversations. Draws state, sends events.
struct HomeScreen: View {
    let state: HomeViewModel.State
    let onEvent: (HomeViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                    Text(state.greeting).modeText(.callout, color: ModeColor.textSecondary)
                    Text(L10n.Home.subtitle).modeText(.largeTitle)
                }
                .padding(.horizontal, ModeSpacing.screenEdge)
                if state.needsKey {
                    ModeCard {
                        ModeRow(String(localized: L10n.Home.needsKeyTitle), subtitle: String(localized: L10n.Home.needsKeyMessage),
                                leading: .icon(.key, ModeColor.warning), emphasis: .prominent, onTap: { onEvent(.addKey) })
                    }
                    .padding(.horizontal, ModeSpacing.screenEdge)
                }
                if state.isEmpty {
                    ModeEmptyState(icon: .ask, title: L10n.Home.emptyTitle, message: L10n.Home.emptyMessage).frame(maxWidth: .infinity)
                }
                if !state.suggestions.isEmpty {
                    ModeSectionHeader(L10n.Home.suggested).padding(.horizontal, ModeSpacing.screenEdge).padding(.top, ModeSpacing.l)
                    ScrollView(.horizontal) {
                        HStack(spacing: ModeSpacing.m) {
                            ForEach(state.suggestions) { s in
                                ModeSuggestionCard(icon: s.icon, title: s.title, subtitle: s.subtitle) { onEvent(.suggestion(s.title)) }
                            }
                        }
                        .padding(.horizontal, ModeSpacing.screenEdge)
                    }
                    .scrollIndicators(.hidden)
                }
                if !state.recent.isEmpty {
                    ModeSectionHeader(L10n.Home.recent).padding(.horizontal, ModeSpacing.screenEdge).padding(.top, ModeSpacing.l)
                    ModeCard {
                        ForEach(state.recent) { row in
                            ModeRow(row.title, subtitle: row.subtitle, leading: row.leading, onTap: { onEvent(.openConversation(row.id)) })
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
                ModeToolbarButton(.profile, label: L10n.Home.profile) { onEvent(.openSettings) }
            }
        }
    }
}

/// Connects Home to its ViewModel.
struct HomeRoute: View {
    @State private var model: HomeViewModel

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        _model = State(initialValue: HomeViewModel(screens: app.screens, navigate: navigate))
    }

    var body: some View {
        HomeScreen(state: model.state, onEvent: model.send)
    }
}
