import ModeDesignSystem
import SwiftUI

/// A new task: suggestions to start from, and the message bar.
struct AskScreen: View {
    let state: AskViewModel.State
    @Binding var draft: String
    let onEvent: (AskViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                Text(L10n.Ask.voiceSoon).modeText(.footnote, color: ModeColor.textSecondary)
                ForEach(state.suggestions) { row in
                    ModeChip(row.title, icon: row.icon, actionTitle: L10n.Common.continue) { onEvent(.suggestion(row.title)) }
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .modeScreen()
        .navigationTitle(Text(L10n.Ask.title))
        .safeAreaInset(edge: .bottom) {
            ModeComposer(
                text: $draft,
                isPrivate: state.isPrivate,
                labels: .init(prompt: L10n.Ask.prompt, send: L10n.Ask.send, privateOn: L10n.Ask.privateOn, privateOff: L10n.Ask.privateOff),
                onTogglePrivate: { onEvent(.togglePrivate) },
                onSend: { onEvent(.send) }
            )
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.s)
        }
    }
}

/// Connects Ask to its ViewModel.
struct AskRoute: View {
    @State private var model: AskViewModel

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        _model = State(initialValue: AskViewModel(screens: app.screens, navigate: navigate))
    }

    var body: some View {
        AskScreen(state: model.state, draft: $model.draft, onEvent: model.send)
    }
}
