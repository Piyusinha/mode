import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// Home: maps the presenter's state to what the Home board shows, and handles its events.
@MainActor
@Observable
final class HomeViewModel {
    struct Suggestion: Identifiable, Equatable {
        var id: String { title }
        let title: String
        let subtitle: String
        let icon: ModeIcon
    }

    struct RecentRow: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
        let leading: ModeRowLeading
    }

    struct State: Equatable {
        var greeting = ""
        var suggestions: [Suggestion] = []
        var recent: [RecentRow] = []
        var needsKey = false
        var isEmpty: Bool { suggestions.isEmpty && recent.isEmpty }
    }

    enum Event {
        case suggestion(String)
        case openConversation(String)
        case addKey
        case openSettings
        case pauseToggle
        case stop
        case openRun
    }

    private(set) var state = State()
    private let presenter: HomePresenter
    private let navigate: (Route) -> Void
    private let run: RunStatusViewModel?
    private let openLive: () -> Void
    private var updates: Updates?

    init(screens: Screens, run: RunStatusViewModel?, navigate: @escaping (Route) -> Void, openLive: @escaping () -> Void) {
        presenter = screens.home()
        self.run = run
        self.navigate = navigate
        self.openLive = openLive
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case let .suggestion(text):
            if let conversation = presenter.ask(text: text, private: false) { navigate(.conversation(id: conversation)) }
        case let .openConversation(id): navigate(.conversation(id: id))
        case .addKey, .openSettings: navigate(.settings)
        case .pauseToggle: run?.togglePause()
        case .stop: run?.stop()
        case .openRun: openLive()
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    static func map(_ s: HomeUiState) -> State {
        State(
            greeting: L10n.Home.greeting(partOfDay(s.partOfDay), name: s.name),
            suggestions: s.suggestions.map { suggestion in
                switch onEnum(of: suggestion.reason) {
                case let .often(o): Suggestion(title: suggestion.text, subtitle: L10n.Home.askedOften(Int(o.times)), icon: .repeatTask)
                case let .learned(l): Suggestion(title: suggestion.text, subtitle: L10n.Home.learned(app: l.app), icon: .skill)
                }
            },
            recent: s.recent.map { c in
                RecentRow(id: c.conversationId, title: c.title, subtitle: when(c), leading: leading(c.badge))
            },
            needsKey: s.needsKey
        )
    }

    /// "Today 8:13 · 62 s", "Yesterday · 37 s".
    private static func when(_ c: RecentConversation) -> String {
        let day = Format.day(c.day)
        var time: String?
        if case .today = onEnum(of: c.day) { time = Format.timeOfDay(epochMs: c.atMs) }
        let duration = c.durationSeconds.map { Format.seconds(Double(truncating: $0)) }
        return [[day, time].compactMap { $0 }.joined(separator: " "), duration].compactMap { $0 }.joined(separator: " · ")
    }

    private static func leading(_ badge: TaskBadge?) -> ModeRowLeading {
        switch badge {
        case .done: .mo(.done)
        case .failed, .gaveUp: .mo(.stuck)
        case .stopped, .notStarted: .mo(.sleep)
        case nil: .mo(.working)
        }
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
