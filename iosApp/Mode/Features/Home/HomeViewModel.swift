import ModeDesignSystem
import ModeKit
import Observation

/// Home: maps the presenter's state to what the screen shows, and handles its events.
@MainActor
@Observable
final class HomeViewModel {
    struct Row: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
        let icon: ModeIcon
    }

    struct RecentRow: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
    }

    struct State: Equatable {
        var greeting = ""
        var suggestions: [Row] = []
        var recent: [RecentRow] = []
        var needsKey = false
        var isEmpty: Bool { suggestions.isEmpty && recent.isEmpty }
    }

    enum Event {
        case suggestion(String)
        case openConversation(String)
        case addKey
        case openSettings
    }

    private(set) var state = State()
    private let presenter: HomePresenter
    private let navigate: (Route) -> Void
    private var updates: Updates?

    init(screens: Screens, navigate: @escaping (Route) -> Void) {
        presenter = screens.home()
        self.navigate = navigate
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case let .suggestion(text):
            if let conversation = presenter.ask(text: text, private: false) { navigate(.conversation(id: conversation)) }
        case let .openConversation(id): navigate(.conversation(id: id))
        case .addKey, .openSettings: navigate(.settings)
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: HomeUiState) -> State {
        State(
            greeting: L10n.Home.greeting(partOfDay(s.partOfDay), name: s.name),
            suggestions: s.suggestions.map { suggestion in
                switch onEnum(of: suggestion.reason) {
                case let .often(o): Row(id: suggestion.text, title: suggestion.text, subtitle: L10n.Home.askedOften(Int(o.times)), icon: .repeatTask)
                case let .learned(l): Row(id: suggestion.text, title: suggestion.text, subtitle: L10n.Home.learned(app: l.app), icon: .skill)
                }
            },
            recent: s.recent.map { c in
                let day = Format.day(c.day)
                let badge = c.badge.map { String(localized: Format.badge($0).text) }
                return RecentRow(id: c.conversationId, title: c.title, subtitle: [day, badge].compactMap { $0 }.joined(separator: " · "))
            },
            needsKey: s.needsKey
        )
    }

    static func partOfDay(_ p: PartOfDay) -> L10n.PartOfDayName {
        switch p {
        case .morning: .morning
        case .afternoon: .afternoon
        case .evening: .evening
        case .night: .night
        }
    }
}
