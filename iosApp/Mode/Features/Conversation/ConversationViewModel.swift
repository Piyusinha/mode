import Foundation
import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI
import UIKit

/// One conversation, as on the Chat board.
@MainActor
@Observable
final class ConversationViewModel {
    struct Message: Identifiable, Equatable {
        let id: String
        let fromUser: Bool
        let spoken: Bool
        /// The raw text, for Copy.
        let text: String
        /// Mode's reply, drawn block by block.
        let blocks: [ModeReplyBlock]
        /// How the task ended, when it didn't simply work ("Couldn't finish", "Stopped").
        let status: Format.BadgeStyle?
        /// "See all 11 screens" and the task it opens.
        let screens: (label: String, taskId: String)?

        static func == (a: Message, b: Message) -> Bool {
            a.id == b.id && a.text == b.text && a.blocks == b.blocks && a.status?.text.key == b.status?.text.key
                && a.screens?.label == b.screens?.label
        }
    }

    struct State: Equatable {
        var title = ""
        var when = ""
        var messages: [Message] = []
        /// What Mode is doing now, while this conversation's task runs.
        var working: String?
        var remembered: [Remembered] = []
        var isPrivate = false
        /// Tips taught in this chat, each under the reply it came with.
        var lessons: [Lesson] = []
        /// "Also do it this way next time?" after steering the running task.
        var offerRemember = false
        /// Mode is writing a tip.
        var teaching = false
        /// The task runs: the message box steers it.
        var running = false
    }

    struct Lesson: Identifiable, Equatable {
        let id: String
        let context: String
        let text: String
        let afterTurnId: String
    }

    struct Remembered: Identifiable, Equatable {
        let id: String
        let text: String
    }

    enum Event {
        case send, speak, togglePrivate, undo(String), openScreens(String), stop, share, repeatTask, askDelete, confirmDelete
        case copy(Message), retry(Message), shareReply(Message), openLive
        case remember, notNow, undoLesson(String), editLesson(String), saveLesson(String)
    }

    private(set) var state = State()
    var draft = ""
    var canSend: Bool { !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var sharing: SharedText?
    var repeating: SharedText?
    var confirmingDelete = false
    /// The lesson being edited.
    var editing: Lesson?
    /// The first few screenshots of each task, for "See all N screens".
    private(set) var thumbnails: [String: [Image]] = [:]

    private let screens: Screens
    private let presenter: ConversationPresenter
    private let navigate: (Route) -> Void
    private let close: () -> Void
    private var updates: Updates?

    init(screens: Screens, conversationId: String, navigate: @escaping (Route) -> Void, close: @escaping () -> Void) {
        self.screens = screens
        presenter = screens.conversation(conversationId: conversationId)
        self.navigate = navigate
        self.close = close
        updates = observe(presenter.state) { [weak self] s in self?.apply(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .send: if presenter.send(text: draft) { draft = "" }
        case .speak: break
        case .togglePrivate: presenter.setPrivate(on: !state.isPrivate)
        case let .undo(id): presenter.undoRemembered(itemId: id)
        case let .openScreens(taskId): navigate(.runScreens(taskId: taskId))
        case .stop: presenter.stop()
        case .share: sharing = SharedText(text: presenter.transcript())
        case .repeatTask: repeating = SharedText(text: presenter.taskToRepeat())
        case let .copy(message): UIPasteboard.general.string = message.text
        case let .retry(message): _ = presenter.retry(turnId: message.id)
        case let .shareReply(message): sharing = SharedText(text: message.text)
        case .openLive: navigate(.liveRun)
        case .remember: presenter.remember()
        case .notNow: presenter.dismissRemember()
        case let .undoLesson(id): presenter.undoLesson(itemId: id)
        case let .editLesson(id): editing = state.lessons.first { $0.id == id }
        case let .saveLesson(text):
            if let lesson = editing { presenter.editLesson(itemId: lesson.id, text: text) }
            editing = nil
        case .askDelete: confirmingDelete = true
        case .confirmDelete:
            presenter.delete()
            close()
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private func apply(_ s: ConversationUiState) {
        state = Self.map(s)
        for message in state.messages {
            if let taskId = message.screens?.taskId, thumbnails[taskId] == nil { thumbnails[taskId] = loadThumbnails(taskId) }
        }
    }

    private func loadThumbnails(_ taskId: String) -> [Image] {
        let run = screens.runScreens(taskId: taskId)
        defer { run.close() }
        return run.state.value.shots.prefix(3).compactMap { shot in
            run.image(shot: shot).flatMap { UIImage(data: $0.toData()) }.map { Image(uiImage: $0) }
        }
    }

    private static func map(_ s: ConversationUiState) -> State {
        State(
            title: s.title,
            when: s.turns.first.map { [Format.day($0.day), Format.timeOfDay(epochMs: $0.atMs)].joined(separator: " ") } ?? "",
            messages: s.turns.map { t in
                Message(
                    id: t.id,
                    fromUser: t.fromUser,
                    spoken: t.source == .siri || t.source == .watch,
                    text: t.text,
                    blocks: t.parts.map(ModeReplyBlock.init),
                    status: t.badge.flatMap { $0 == .done ? nil : Format.badge($0) },
                    screens: t.screens > 0 ? t.taskId.map { (L10n.Conversation.seeScreens(Int(t.screens)), $0) } : nil
                )
            },
            working: s.running.map { $0.paused ? String(localized: L10n.Run.paused) : $0.status },
            remembered: s.remembered.map { Remembered(id: $0.itemId, text: L10n.Conversation.remembered($0.text)) },
            isPrivate: s.privateNext,
            lessons: s.lessons.map { l in
                Lesson(id: l.itemId, context: [l.app, l.title].compactMap { $0 }.joined(separator: " · "), text: l.text, afterTurnId: l.afterTurnId)
            },
            offerRemember: s.offerRemember != nil, teaching: s.teaching, running: s.running != nil
        )
    }
}
