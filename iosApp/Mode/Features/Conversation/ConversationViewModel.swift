import ModeDesignSystem
import ModeKit
import Observation

/// One conversation: its turns, the task running in it, and the message bar.
@MainActor
@Observable
final class ConversationViewModel {
    struct Bubble: Identifiable, Equatable {
        let id: String
        let fromUser: Bool
        let text: String
        let day: String
        let badge: Format.BadgeStyle?
        /// "See all 11 screens", with the task it opens.
        let screens: (label: String, taskId: String)?

        static func == (a: Bubble, b: Bubble) -> Bool {
            a.id == b.id && a.text == b.text && a.day == b.day && a.badge?.text.key == b.badge?.text.key && a.screens?.label == b.screens?.label
        }
    }

    struct State: Equatable {
        var title = ""
        var bubbles: [Bubble] = []
        var working: String?
        var remembered: [(id: String, text: String)] = []
        var isPrivate = false

        static func == (a: State, b: State) -> Bool {
            a.title == b.title && a.bubbles == b.bubbles && a.working == b.working && a.isPrivate == b.isPrivate
                && a.remembered.map(\.id) == b.remembered.map(\.id)
        }
    }

    enum Event {
        case send
        case togglePrivate
        case undo(String)
        case openScreens(String)
        case stop
    }

    private(set) var state = State()
    var draft = ""
    private let presenter: ConversationPresenter
    private let navigate: (Route) -> Void
    private var updates: Updates?

    init(screens: Screens, conversationId: String, navigate: @escaping (Route) -> Void) {
        presenter = screens.conversation(conversationId: conversationId)
        self.navigate = navigate
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .send:
            if presenter.send(text: draft) { draft = "" }
        case .togglePrivate: presenter.setPrivate(on: !state.isPrivate)
        case let .undo(id): presenter.undoRemembered(itemId: id)
        case let .openScreens(taskId): navigate(.runScreens(taskId: taskId))
        case .stop: presenter.stop()
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: ConversationUiState) -> State {
        State(
            title: s.title,
            bubbles: s.turns.map { t in
                Bubble(
                    id: t.id,
                    fromUser: t.fromUser,
                    text: t.text,
                    day: Format.day(t.day),
                    badge: t.badge.map(Format.badge),
                    screens: t.screens > 0 ? t.taskId.map { (L10n.Conversation.seeScreens(Int(t.screens)), $0) } : nil
                )
            },
            working: s.running?.status,
            remembered: s.remembered.map { ($0.itemId, L10n.Conversation.remembered($0.text)) },
            isPrivate: s.privateNext
        )
    }
}
