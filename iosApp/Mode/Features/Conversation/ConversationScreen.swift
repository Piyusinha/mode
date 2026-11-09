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
                VStack(alignment: .leading, spacing: ModeSpacing.chatGap) {
                    ModeChatTitle(state.title, subtitle: state.when)
                        .padding(.bottom, ModeSpacing.l)
                    ForEach(state.messages) { message in
                        MessageView(message: message, thumbnails: message.screens.flatMap { thumbnails[$0.taskId] } ?? [], onEvent: onEvent)
                            .id(message.id)
                        if let working = state.working, state.workingAfterId == message.id {
                            ModeWorkingCard(title: L10n.Conversation.workingTitle, status: working, since: state.workingSince, stopLabel: L10n.Common.stop,
                                            onOpen: { onEvent(.openLive) }, onStop: { onEvent(.stop) })
                                .transition(.opacity)
                        }
                        ForEach(state.lessons.filter { $0.afterTurnId == message.id }) { lesson in
                            ModeChatLessonCard(heading: L10n.Conversation.savedForNextTime, context: lesson.context, text: lesson.text,
                                               editTitle: L10n.Conversation.edit, undoTitle: L10n.Common.undo,
                                               onEdit: { onEvent(.editLesson(lesson.id)) }, onUndo: { onEvent(.undoLesson(lesson.id)) })
                        }
                    }
                    if let working = state.working, state.workingAfterId == nil {
                        ModeWorkingCard(title: L10n.Conversation.workingTitle, status: working, since: state.workingSince, stopLabel: L10n.Common.stop,
                                        onOpen: { onEvent(.openLive) }, onStop: { onEvent(.stop) })
                    }
                    if let notice = state.voiceNotice {
                        ModeChatNotice(notice, actionTitle: L10n.Voice.updateInSettings) { onEvent(.openVoiceSettings) }
                            .contextMenu { Button { onEvent(.dismissVoiceNotice) } label: { Text(L10n.Talk.close) } }
                    }
                    if state.offerRemember {
                        ModeOfferChip(L10n.Conversation.alsoNextTime, actionTitle: L10n.Conversation.remember) { onEvent(.remember) }
                    }
                    if state.teaching {
                        HStack(spacing: ModeSpacing.s) {
                            ProgressView().tint(ModeColor.tint)
                            Text(L10n.Conversation.saving).modeText(.subheadline, color: ModeColor.textSecondary)
                        }
                    }
                    ForEach(state.remembered) { item in
                        ModeRememberedChip(item.text, undoTitle: L10n.Common.undo) { onEvent(.undo(item.id)) }
                    }
                }
                .padding(.horizontal, ModeSpacing.screenEdge)
                .padding(.bottom, ModeSpacing.l)
            }
            .scrollEdgeEffectHidden(true, for: .all)
            .modeEdgeFade(.top)
            .onChange(of: state.messages.last?.id) { _, last in
                if let last { withAnimation(ModeMotion.standard) { scroller.scrollTo(last, anchor: .bottom) } }
            }
        }
        .modeScreen()
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                ModeChatActions(shareLabel: L10n.Conversation.share, moreLabel: L10n.Conversation.more, onShare: { onEvent(.share) }) {
                    Button { onEvent(.repeatTask) } label: { Label { Text(L10n.Conversation.repeatTask) } icon: { ModeIcon.repeatTask.image } }
                    Button(role: .destructive) { onEvent(.askDelete) } label: { Label { Text(L10n.Conversation.delete) } icon: { ModeIcon.trash.image } }
                }
            }
            .sharedBackgroundVisibility(.hidden)
        }
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: ModeSpacing.s) {
                if state.running { ModeWorkingTag(L10n.Conversation.modeIsWorking) }
                ModeMessageBar(
                    text: $draft, isPrivate: state.isPrivate, canSend: canSend,
                    labels: .init(prompt: state.running ? L10n.Conversation.steerPrompt : L10n.Conversation.teachPrompt, add: L10n.Ask.add, send: L10n.Ask.send, speak: L10n.Tab.ask,
                                  privateOn: L10n.Ask.privateOn, privateOff: L10n.Ask.privateOff),
                    steering: state.running,
                    onTogglePrivate: { onEvent(.togglePrivate) }, onSend: { onEvent(.send) }, onSpeak: { onEvent(.speak) }
                ) {
                    Button { onEvent(.repeatTask) } label: { Label { Text(L10n.Conversation.repeatTask) } icon: { ModeIcon.repeatTask.image } }
                    Button { onEvent(.togglePrivate) } label: { Label { Text(L10n.Ask.privateOff) } icon: { ModeIcon.privateOff.image } }
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.s)
            .modeBarFade()
        }
    }
}

/// One message: yours in an ink bubble; Mode's beside Mo (its face says how the task went), with
/// "See all N screens" after the first line, the rest of the reply, and What went wrong when it couldn't finish.
/// Copy, Try again and Share are on a long press.
private struct MessageView: View {
    let message: ConversationViewModel.Message
    let thumbnails: [Image]
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
            VStack(alignment: .leading, spacing: ModeSpacing.chatGap) {
                ModeMoReply(mood: message.mood) { ModeReply(message.lead) }
                    .contextMenu {
                        Button { onEvent(.copy(message)) } label: { Label { Text(L10n.Conversation.copy) } icon: { ModeIcon.copy.image } }
                        Button { onEvent(.retry(message)) } label: { Label { Text(L10n.Conversation.tryAgain) } icon: { ModeIcon.runAgain.image } }
                        Button { onEvent(.shareReply(message)) } label: { Label { Text(L10n.Conversation.shareReply) } icon: { ModeIcon.share.image } }
                    }
                if let screens = message.screens {
                    ModeScreensLink(title: screens.label, thumbnails: thumbnails) { onEvent(.openScreens(screens.taskId)) }
                }
                if !message.rest.isEmpty { ModeReply(message.rest) }
                if let taskId = message.debugTaskId {
                    ModeFailedCard(title: L10n.Debug.couldntFinish, detail: L10n.Debug.cardDetail, debugTitle: L10n.Debug.whatWentWrong,
                                   retryTitle: L10n.Conversation.tryAgain, onDebug: { onEvent(.debug(taskId)) }, onRetry: { onEvent(.retry(message)) })
                }
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
    @LazyState private var model: ConversationViewModel

    init(app: AppModel, conversationId: String, navigate: @escaping (Route) -> Void, close: @escaping () -> Void) {
        self.app = app
        _model = LazyState(wrappedValue: ConversationViewModel(screens: app.screens, conversationId: conversationId, navigate: navigate, close: close))
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
