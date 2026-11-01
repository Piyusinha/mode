import ModeDesignSystem
import SwiftUI

/// A conversation: your messages, Mode's replies, what's running, and the message bar.
struct ConversationScreen: View {
    let state: ConversationViewModel.State
    @Binding var draft: String
    let onEvent: (ConversationViewModel.Event) -> Void

    var body: some View {
        ScrollViewReader { scroller in
            ScrollView {
                LazyVStack(spacing: ModeSpacing.m) {
                    ForEach(state.bubbles) { bubble in
                        BubbleView(bubble: bubble, onScreens: { onEvent(.openScreens($0)) }).id(bubble.id)
                    }
                    if let working = state.working {
                        ModeRunningBar(title: String(localized: L10n.Conversation.working), status: working, stopLabel: L10n.Common.stop) { onEvent(.stop) }
                            .padding(.vertical, ModeSpacing.s)
                            .glassEffect(.regular, in: .capsule)
                    }
                    ForEach(state.remembered, id: \.id) { item in
                        ModeChip(item.text, icon: .sparkle, actionTitle: L10n.Common.undo) { onEvent(.undo(item.id)) }
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.horizontal, ModeSpacing.screenEdge)
                .padding(.bottom, ModeSpacing.l)
            }
            .onChange(of: state.bubbles.last?.id) { _, last in
                if let last { withAnimation(ModeMotion.standard) { scroller.scrollTo(last, anchor: .bottom) } }
            }
        }
        .modeScreen()
        .navigationTitle(state.title)
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            ModeComposer(
                text: $draft,
                isPrivate: state.isPrivate,
                labels: .init(prompt: L10n.Ask.followUpPrompt, send: L10n.Ask.send, privateOn: L10n.Ask.privateOn, privateOff: L10n.Ask.privateOff),
                onTogglePrivate: { onEvent(.togglePrivate) },
                onSend: { onEvent(.send) }
            )
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.s)
        }
    }
}

private struct BubbleView: View {
    let bubble: ConversationViewModel.Bubble
    let onScreens: (String) -> Void

    var body: some View {
        VStack(alignment: bubble.fromUser ? .trailing : .leading, spacing: ModeSpacing.xs) {
            Text(bubble.text)
                .modeText(.body, color: bubble.fromUser ? ModeColor.onAccent : ModeColor.textPrimary)
                .padding(ModeSpacing.m)
                .background(bubble.fromUser ? ModeColor.accent : ModeColor.card, in: .rect(cornerRadius: ModeRadius.field))
            HStack(spacing: ModeSpacing.s) {
                if let badge = bubble.badge { ModeBadge(badge.text, icon: badge.icon, tone: badge.tone) }
                Text(bubble.day).modeText(.caption, color: ModeColor.textSecondary)
            }
            if let screens = bubble.screens {
                Button { onScreens(screens.taskId) } label: {
                    Label { Text(screens.label) } icon: { ModeIcon.screens.image }
                        .modeText(.subheadline, color: ModeColor.tint)
                        .frame(minHeight: ModeSize.touch)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(maxWidth: .infinity, alignment: bubble.fromUser ? .trailing : .leading)
    }
}

/// Connects a conversation to its ViewModel.
struct ConversationRoute: View {
    @State private var model: ConversationViewModel

    init(app: AppModel, conversationId: String, navigate: @escaping (Route) -> Void) {
        _model = State(initialValue: ConversationViewModel(screens: app.screens, conversationId: conversationId, navigate: navigate))
    }

    var body: some View {
        ConversationScreen(state: model.state, draft: $model.draft, onEvent: model.send)
    }
}
