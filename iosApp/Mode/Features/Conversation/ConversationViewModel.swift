import Foundation
import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI

/// One conversation, as on the Chat board.
@MainActor
@Observable
final class ConversationViewModel {
    enum Part: Equatable {
        case text(String)
        case times([TimeRow])
    }

    struct TimeRow: Equatable {
        let time: String
        let text: String
    }

    struct Message: Identifiable, Equatable {
        let id: String
        let fromUser: Bool
        let spoken: Bool
        let text: String
        let parts: [Part]
        /// "See all 11 screens" and the task it opens.
        let screens: (label: String, taskId: String)?

        static func == (a: Message, b: Message) -> Bool {
            a.id == b.id && a.text == b.text && a.parts == b.parts && a.screens?.label == b.screens?.label
        }
    }

    struct State: Equatable {
        var title = ""
        var when = ""
        var messages: [Message] = []
        var working: String?
        var remembered: [Remembered] = []
        var isPrivate = false
    }

    struct Remembered: Identifiable, Equatable {
        let id: String
        let text: String
    }

    enum Event {
        case send, speak, togglePrivate, undo(String), openScreens(String), stop, share, repeatTask, askDelete, confirmDelete
    }

    private(set) var state = State()
    var draft = ""
    var canSend: Bool { !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    var sharing: SharedText?
    var repeating: SharedText?
    var confirmingDelete = false
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
                    parts: t.parts.map { part in
                        switch onEnum(of: part) {
                        case let .text(p): .text(p.text)
                        case let .times(p): .times(p.rows.map { TimeRow(time: $0.time, text: $0.text) })
                        }
                    },
                    screens: t.screens > 0 ? t.taskId.map { (L10n.Conversation.seeScreens(Int(t.screens)), $0) } : nil
                )
            },
            working: s.running?.status,
            remembered: s.remembered.map { Remembered(id: $0.itemId, text: L10n.Conversation.remembered($0.text)) },
            isPrivate: s.privateNext
        )
    }
}
