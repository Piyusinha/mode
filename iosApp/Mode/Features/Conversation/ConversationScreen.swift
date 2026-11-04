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
                    ForEach(Array(state.messages.enumerated()), id: \.element.id) { index, message in
                        // Mode's quick acknowledgements (to a steer, or a lesson saved) have no Copy / Try again / Share.
                        let acknowledgement = (index > 0 && state.messages[index - 1].steered) || state.lessons.contains { $0.afterTurnId == message.id }
                        MessageView(message: message, thumbnails: message.screens.flatMap { thumbnails[$0.taskId] } ?? [], actions: !acknowledgement, onEvent: onEvent)
                            .id(message.id)
                        ForEach(state.lessons.filter { $0.afterTurnId == message.id }) { lesson in
                            ModeLessonCard(heading: L10n.Conversation.savedForNextTime, context: lesson.context, text: lesson.text,
                                           editTitle: L10n.Conversation.edit, undoTitle: L10n.Common.undo,
                                           onEdit: { onEvent(.editLesson(lesson.id)) }, onUndo: { onEvent(.undoLesson(lesson.id)) })
                        }
                    }
                    if let notice = state.voiceNotice {
                        ModeChip(notice, icon: .warning, actionTitle: L10n.Voice.updateInSettings) { onEvent(.openVoiceSettings) }
                            .contextMenu { Button { onEvent(.dismissVoiceNotice) } label: { Text(L10n.Talk.close) } }
                    }
                    if state.offerRemember {
                        ModeChip(String(localized: L10n.Conversation.alsoNextTime), icon: .memory, actionTitle: L10n.Conversation.remember) { onEvent(.remember) }
                    }
                    if state.teaching {
                        HStack(spacing: ModeSpacing.s) {
                            ProgressView().tint(ModeColor.tint)
                            Text(L10n.Conversation.saving).modeText(.subheadline, color: ModeColor.textSecondary)
                        }
                    }
                    if let working = state.working {
                        ModeWorkingCard(title: L10n.Conversation.workingTitle, status: working, since: state.workingSince, stopLabel: L10n.Common.stop,
                                        onOpen: { onEvent(.openLive) }, onStop: { onEvent(.stop) })
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
          VStack(spacing: ModeSpacing.s) {
            if state.running {
                ModeTag(String(localized: L10n.Conversation.modeIsWorking), background: ModeColor.tintSoft, foreground: ModeColor.tint)
            }
            ModeMessageBar(
                text: $draft, isPrivate: state.isPrivate, canSend: canSend,
                labels: .init(prompt: state.running ? L10n.Conversation.steerPrompt : L10n.Conversation.teachPrompt, add: L10n.Ask.add, send: L10n.Ask.send, speak: L10n.Tab.ask,
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
}

/// One message: yours in an accent bubble; Mode's as a rich reply (how it ended if it didn't
/// work, the answer, its screens), with Copy, Try again and Share under it.
private struct MessageView: View {
    let message: ConversationViewModel.Message
    let thumbnails: [Image]
    var actions = true
    let onEvent: (ConversationViewModel.Event) -> Void

    var body: some View {
        if message.fromUser {
            if message.steered {
                ModeUserBubble(message.text, tag: L10n.Conversation.whileWorking, icon: .arrowRight)
            } else if message.spoken {
                ModeUserBubble(message.text, tag: L10n.Conversation.spoken, icon: .microphone)
            } else {
                ModeUserBubble(message.text, tag: L10n.Conversation.typed, icon: .keyboard)
            }
        } else {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                if let status = message.status { ModeBadge(status.text, icon: status.icon, tone: status.tone) }
                ModeReply(message.blocks)
                if let screens = message.screens {
                    ModeScreensLink(title: screens.label, thumbnails: thumbnails) { onEvent(.openScreens(screens.taskId)) }
                }
                if let taskId = message.debugTaskId {
                    FailedTaskCard(onDebug: { onEvent(.debug(taskId)) }, onRetry: { onEvent(.retry(message)) })
                } else if actions { ModeReplyActions([
                    .init(.copy, label: L10n.Conversation.copy) { onEvent(.copy(message)) },
                    .init(.runAgain, label: L10n.Conversation.tryAgain) { onEvent(.retry(message)) },
                    .init(.share, label: L10n.Conversation.shareReply) { onEvent(.shareReply(message)) },
                ]) }
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
            .sheet(item: $model.editing) { lesson in LessonEditor(text: lesson.text) { model.send(.saveLesson($0)) } }
            .confirmationDialog(Text(L10n.Conversation.deleteConfirm), isPresented: $model.confirmingDelete, titleVisibility: .visible) {
                Button(role: .destructive) { model.send(.confirmDelete) } label: { Text(L10n.Conversation.delete) }
            }
    }
}

/// Edit a tip taught in this chat, in the user's own words.
private struct LessonEditor: View {
    @State var text: String
    let save: (String) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.l) {
            Text(L10n.Conversation.editLesson).modeText(.title2)
            TextField(text: $text, axis: .vertical) { Text(L10n.Conversation.editLessonPrompt) }
                .modeText(.body)
                .lineLimit(3...8)
                .padding(ModeSpacing.l)
                .background(ModeColor.field, in: .rect(cornerRadius: ModeRadius.field))
            ModeButton(L10n.Common.save, fullWidth: true) { save(text); dismiss() }
                .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
        }
        .padding(ModeSpacing.screenEdge)
        .frame(maxHeight: .infinity, alignment: .top)
        .modeScreen()
        .presentationDetents([.medium])
    }
}

/// Under a reply whose task couldn't finish (board TaskFailedChat): What went wrong, and Try again.
private struct FailedTaskCard: View {
    let onDebug: () -> Void
    let onRetry: () -> Void

    var body: some View {
        ModeCard {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                Label { Text(L10n.Debug.couldntFinish) } icon: { ModeIcon.warning.image }
                    .modeText(.bodyEmphasis, color: ModeColor.tileOrangeText)
                Text(L10n.Debug.cardDetail).modeText(.footnote, color: ModeColor.textSecondary)
                HStack(spacing: ModeSpacing.s) {
                    ModeButton(L10n.Debug.whatWentWrong, fullWidth: true, action: onDebug)
                    ModeButton(L10n.Conversation.tryAgain, icon: .runAgain, role: .secondary, action: onRetry)
                }
            }
            .padding(.vertical, ModeSpacing.m)
        }
    }
}
