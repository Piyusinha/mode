import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// Tasks: what runs now, what waits, what repeats, what finished.
@MainActor
@Observable
final class TasksViewModel {
    struct Running: Equatable {
        let title: String
        let status: String
        let waiting: Bool
    }

    struct Item: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
    }

    struct RepeatItem: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
        let enabled: Bool
    }

    struct DoneItem: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
        let conversationId: String?
        let icon: ModeIcon
    }

    struct State: Equatable {
        var running: Running?
        var upNext: [Item] = []
        var repeats: [RepeatItem] = []
        var done: [DoneItem] = []
        var isEmpty: Bool { running == nil && upNext.isEmpty && repeats.isEmpty && done.isEmpty }
    }

    enum Event {
        case openLive
        case stop
        case cancel(String)
        case setRepeat(String, Bool)
        case deleteRepeat(String)
        case newRepeat
        case open(DoneItem)
    }

    private(set) var state = State()
    var showingRepeat = false
    private let presenter: TasksPresenter
    private let navigate: (Route) -> Void
    private var updates: Updates?

    init(screens: Screens, navigate: @escaping (Route) -> Void) {
        presenter = screens.tasks()
        self.navigate = navigate
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .openLive: navigate(.liveRun)
        case .stop: presenter.stopRunning()
        case let .cancel(id): presenter.cancel(taskId: id)
        case let .setRepeat(id, on): presenter.setRepeatEnabled(scheduleId: id, enabled: on)
        case let .deleteRepeat(id): presenter.deleteRepeat(scheduleId: id)
        case .newRepeat: showingRepeat = true
        case let .open(item): if let id = item.conversationId { navigate(.conversation(id: id)) }
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: TasksUiState) -> State {
        State(
            running: s.running.map { Running(title: $0.text, status: $0.status, waiting: $0.question != nil) },
            upNext: s.upNext.map { Item(id: $0.taskId, title: $0.text, subtitle: [String(localized: source($0.source)), Format.day($0.day)].joined(separator: " · ")) },
            repeats: s.repeats.map { r in
                let days = r.isoDays.isEmpty ? String(localized: L10n.Repeat.once) : daysLabel(Set(r.isoDays.map { Int(truncating: $0) }))
                let next = r.nextRunAtMs.map { L10n.Tasks.nextRun(Format.moment(epochMs: Int64(truncating: $0))) }
                return RepeatItem(id: r.scheduleId, title: r.text, subtitle: [Format.time(hour: r.hour, minute: r.minute), days, next].compactMap { $0 }.joined(separator: " · "), enabled: r.enabled)
            },
            done: s.done.map { d in
                let badge = Format.badge(d.badge)
                let steps = d.steps.map { L10n.Tasks.steps(Int(truncating: $0)) }
                return DoneItem(id: d.taskId, title: d.text, subtitle: [String(localized: badge.text), Format.day(d.day), steps].compactMap { $0 }.joined(separator: " · "), conversationId: d.conversationId, icon: badge.icon)
            }
        )
    }

    private static func source(_ s: TaskSource) -> LocalizedStringResource {
        switch s {
        case .app: L10n.Source.app
        case .siri: L10n.Source.siri
        case .watch: L10n.Source.watch
        case .shortcut: L10n.Source.shortcut
        case .schedule: L10n.Source.schedule
        }
    }

    private static func daysLabel(_ days: Set<Int>) -> String {
        switch days {
        case Set(1...7): String(localized: L10n.Repeat.everyDay)
        case Set(1...5): String(localized: L10n.Repeat.weekdays)
        case [6, 7]: String(localized: L10n.Repeat.weekends)
        default: days.sorted().map { Format.weekdayName($0) }.formatted(.list(type: .and, width: .short))
        }
    }
}
