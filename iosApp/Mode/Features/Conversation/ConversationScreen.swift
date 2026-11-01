import ModeDesignSystem
import SwiftUI

/// A conversation, as on the Chat board. Draws state, sends events.
struct ConversationScreen: View {
    let state: ConversationViewModel.State
    let thumbnails: [String: [Image]]
    @Binding var draft: String
    let canSend: Bool
    let onEvent: (ConversationViewModel.Event) -> Void

    var body: some View {
        ScrollViewReader { scroller in
            ScrollView {
                VStack(alignment: .leading, spacing: ModeSpacing.l) {
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        // A spoken task can be long: two lines at most, a little smaller rather than cut.
                        Text(state.title).modeText(.title).lineLimit(2).minimumScaleFactor(ModeTextScale.title)
                        Text(state.when).modeText(.subheadline, color: ModeColor.textSecondary)
                    }
                    ForEach(state.messages) { message in
                        MessageView(message: message, thumbnails: message.screens.flatMap { thumbnails[$0.taskId] } ?? [], onEvent: onEvent)
                            .id(message.id)
                    }
                    if let working = state.working {
                        ModeWorkingLine(status: working, stopLabel: L10n.Common.stop, onOpen: { onEvent(.openLive) }, onStop: { onEvent(.stop) })
                            .transition(.opacity)
                    }
                    ForEach(state.remembered) { item in
                        ModeRememberedChip(item.text, undoTitle: L10n.Common.undo) { onEvent(.undo(item.id)) }
                    }
                }
                .padding(.horizontal, ModeSpacing.screenEdge)
                .padding(.bottom, ModeSpacing.l)
            }
            .scrollEdgeEffectStyle(.soft, for: .all)
            .onChange(of: state.messages.last?.id) { _, last in
                if let last { withAnimation(ModeMotion.standard) { scroller.scrollTo(last, anchor: .bottom) } }
            }
        }
        .modeScreen()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .topBarTrailing) {
                ModeToolbarButton(.share, label: L10n.Conversation.share) { onEvent(.share) }
                Menu {
                    Button { onEvent(.repeatTask) } label: { Label { Text(L10n.Conversation.repeatTask) } icon: { ModeIcon.repeatTask.image } }
                    Button(role: .destructive) { onEvent(.askDelete) } label: { Label { Text(L10n.Conversation.delete) } icon: { ModeIcon.trash.image } }
                } label: { ModeIcon.more.image }
                .accessibilityLabel(Text(L10n.Conversation.more))
            }
        }
        .safeAreaInset(edge: .bottom) {
            ModeMessageBar(
                text: $draft, isPrivate: state.isPrivate, canSend: canSend,
                labels: .init(prompt: L10n.Ask.followUpPrompt, add: L10n.Ask.add, send: L10n.Ask.send, speak: L10n.Tab.ask,
                              privateOn: L10n.Ask.privateOn, privateOff: L10n.Ask.privateOff),
                onTogglePrivate: { onEvent(.togglePrivate) }, onSend: { onEvent(.send) }, onSpeak: { onEvent(.speak) }
            ) {
                Button { onEvent(.repeatTask) } label: { Label { Text(L10n.Conversation.repeatTask) } icon: { ModeIcon.repeatTask.image } }
                Button { onEvent(.togglePrivate) } label: { Label { Text(L10n.Ask.privateOff) } icon: { ModeIcon.privateOff.image } }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.s)
        }
    }
}

/// One message: yours in an accent bubble; Mode's as a rich reply (how it ended if it didn't
/// work, the answer, its screens), with Copy, Try again and Share under it.
private struct MessageView: View {
    let message: ConversationViewModel.Message
    let thumbnails: [Image]
    let onEvent: (ConversationViewModel.Event) -> Void

    var body: some View {
        if message.fromUser {
            ModeUserBubble(message.text, tag: message.spoken ? L10n.Conversation.spoken : nil)
        } else {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                if let status = message.status { ModeBadge(status.text, icon: status.icon, tone: status.tone) }
                ModeReply(message.blocks)
                if let screens = message.screens {
                    ModeScreensLink(title: screens.label, thumbnails: thumbnails) { onEvent(.openScreens(screens.taskId)) }
                }
                ModeReplyActions([
                    .init(.copy, label: L10n.Conversation.copy) { onEvent(.copy(message)) },
                    .init(.runAgain, label: L10n.Conversation.tryAgain) { onEvent(.retry(message)) },
                    .init(.share, label: L10n.Conversation.shareReply) { onEvent(.shareReply(message)) },
                ])
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Connects a conversation to its ViewModel and its sheets.
struct ConversationRoute: View {
    @Environment(\.dismiss) private var dismiss
    let app: AppModel
    let conversationId: String
    let navigate: (Route) -> Void

    var body: some View {
        ConversationContent(app: app, conversationId: conversationId, navigate: navigate, close: { dismiss() })
    }
}

private struct ConversationContent: View {
    let app: AppModel
    @State private var model: ConversationViewModel

    init(app: AppModel, conversationId: String, navigate: @escaping (Route) -> Void, close: @escaping () -> Void) {
        self.app = app
        _model = State(initialValue: ConversationViewModel(screens: app.screens, conversationId: conversationId, navigate: navigate, close: close))
    }

    var body: some View {
        ConversationScreen(state: model.state, thumbnails: model.thumbnails, draft: $model.draft, canSend: model.canSend, onEvent: model.send)
            .sheet(item: $model.sharing) { ShareSheet(text: $0.text) }
            .sheet(item: $model.repeating) { RepeatRoute(app: app, text: $0.text) }
            .confirmationDialog(Text(L10n.Conversation.deleteConfirm), isPresented: $model.confirmingDelete, titleVisibility: .visible) {
                Button(role: .destructive) { model.send(.confirmDelete) } label: { Text(L10n.Conversation.delete) }
            }
    }
}
