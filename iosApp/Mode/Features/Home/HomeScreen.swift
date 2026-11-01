import ModeDesignSystem
import SwiftUI

/// Home: a greeting, suggestions and recent conversations. Draws state, sends events.
struct HomeScreen: View {
    let state: HomeViewModel.State
    let onEvent: (HomeViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: ModeSpacing.s) {
                Text(L10n.Home.subtitle)
                    .modeText(.callout, color: ModeColor.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, ModeSpacing.xs)
                if state.needsKey {
                    ModeCard {
                        ModeRow(String(localized: L10n.Home.needsKeyTitle), subtitle: String(localized: L10n.Home.needsKeyMessage), leading: .icon(.key, ModeColor.warning)) { onEvent(.addKey) }
                    }
                }
                if state.isEmpty {
                    ModeEmptyState(icon: .ask, title: L10n.Home.emptyTitle, message: L10n.Home.emptyMessage)
                }
                if !state.suggestions.isEmpty {
                    ModeSectionHeader(L10n.Home.suggested)
                    ModeCard {
                        ForEach(state.suggestions) { row in
                            ModeRow(row.title, subtitle: row.subtitle, leading: .icon(row.icon)) { onEvent(.suggestion(row.title)) }
                        }
                    }
                }
                if !state.recent.isEmpty {
                    ModeSectionHeader(L10n.Home.recent)
                    ModeCard {
                        ForEach(state.recent) { row in
                            ModeRow(row.title, subtitle: row.subtitle) { onEvent(.openConversation(row.id)) }
                        }
                    }
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxxl)
        }
        .modeScreen()
        .navigationTitle(state.greeting)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                ModeIconButton(.settings, label: L10n.Common.settings) { onEvent(.openSettings) }
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
