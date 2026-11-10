import ModeDesignSystem
import SwiftUI

/// A new task, from the Speak button: suggestions to start from and the message bar.
struct AskScreen: View {
    let state: AskViewModel.State
    @Binding var draft: String
    let canSend: Bool
    let onEvent: (AskViewModel.Event) -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ModeSpacing.m) {
                    ForEach(state.suggestions) { s in
                        ModeChip(s.title, icon: s.icon, actionTitle: L10n.Common.continue) { onEvent(.suggestion(s.title)) }
                    }
                }
                .padding(.horizontal, ModeSpacing.screenEdge)
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .modeScreen()
            .navigationTitle(Text(L10n.Ask.title))
            .navigationBarTitleDisplayMode(.inline)
            .safeAreaInset(edge: .bottom) {
                ModeMessageBar(
                    text: $draft, isPrivate: state.isPrivate, canSend: canSend,
                    labels: .init(prompt: L10n.Ask.prompt, add: L10n.Ask.add, send: L10n.Ask.send, speak: L10n.Tab.ask,
                                  privateOn: L10n.Ask.privateOn, privateOff: L10n.Ask.privateOff),
                    onTogglePrivate: { onEvent(.togglePrivate) }, onSend: { onEvent(.send) }, onSpeak: { onEvent(.speak) }
                ) {
                    Button { onEvent(.togglePrivate) } label: { Label { Text(L10n.Ask.privateOff) } icon: { ModeIcon.privateOff.image } }
                }
                .padding(.horizontal, ModeSpacing.screenEdge)
                .padding(.bottom, ModeSpacing.s)
            }
        }
        .presentationDetents([.medium, .large])
    }
}

/// Connects Ask to its ViewModel.
struct AskRoute: View {
    @LazyState private var model: AskViewModel

    init(app: AppModel, onSent: @escaping (String) -> Void) {
        _model = LazyState(wrappedValue: AskViewModel(screens: app.screens, onSent: onSent))
    }

    var body: some View {
        AskScreen(state: model.state, draft: $model.draft, canSend: model.canSend, onEvent: model.send)
    }
}
