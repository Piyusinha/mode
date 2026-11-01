import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// Tasks, as on its board: Now (running, waiting for you, up next, repeats), Scheduled, Done.
@MainActor
@Observable
final class TasksViewModel {
    enum Filter: Hashable { case now, scheduled, done }

    struct Row: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
        let leading: ModeRowLeading
    }

    struct RepeatRow: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
        let enabled: Bool
    }

    struct DoneRow: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
        let leading: ModeRowLeading
        let conversationId: String?
    }

    struct State: Equatable {
        var running: Row?
        var waiting: Row?
        var upNext: [Row] = []
        var repeats: [RepeatRow] = []
        var done: [DoneRow] = []
        var nowIsEmpty: Bool { running == nil && waiting == nil && upNext.isEmpty && repeats.isEmpty }
    }

    enum Event {
        case openLive
        case cancel(String)
        case setRepeat(String, Bool)
        case deleteRepeat(String)
        case newRepeat
        case newTask
        case open(DoneRow)
    }

    private(set) var state = State()
    var filter: Filter = .now { didSet { presenter.setFilter(f: Self.kotlin(filter)) } }
    var showingRepeat = false
    private let presenter: TasksPresenter
    private let navigate: (Route) -> Void
    private let newTask: () -> Void
    private var updates: Updates?

    init(screens: Screens, navigate: @escaping (Route) -> Void, newTask: @escaping () -> Void) {
        presenter = screens.tasks()
        self.navigate = navigate
        self.newTask = newTask
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .openLive: navigate(.liveRun)
        case let .cancel(id): presenter.cancel(taskId: id)
        case let .setRepeat(id, on): presenter.setRepeatEnabled(scheduleId: id, enabled: on)
        case let .deleteRepeat(id): presenter.deleteRepeat(scheduleId: id)
        case .newRepeat: showingRepeat = true
        case .newTask: newTask()
        case let .open(row): if let id = row.conversationId { navigate(.conversation(id: id)) }
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func kotlin(_ f: Filter) -> TaskFilter {
        switch f {
        case .now: .now
        case .scheduled: .scheduled
        case .done: .done
        }
    }

    private static func map(_ s: TasksUiState) -> State {
        let running = s.running.flatMap { r in r.question == nil ? r : nil }
        let waiting = s.running.flatMap { r in r.question != nil ? r : nil }
        return State(
            running: running.map { r in
                Row(id: r.taskId, title: r.text, subtitle: r.paused ? String(localized: L10n.Run.paused) : r.status, leading: .progress)
            },
            waiting: waiting.map { r in
                Row(id: r.taskId, title: r.text, subtitle: r.question?.text ?? "", leading: .iconTile(.lockFilled, background: ModeColor.tileOrange, foreground: ModeColor.tileOrangeText))
            },
            upNext: s.upNext.map { q in
                Row(id: q.taskId, title: q.text, subtitle: [Format.day(q.day), Format.timeOfDay(epochMs: q.queuedAtMs)].joined(separator: " "),
                    leading: .symbol(.clock, ModeColor.textSecondary))
            },
            repeats: s.repeats.map { r in
                let days = r.isoDays.isEmpty ? String(localized: L10n.Repeat.once) : daysLabel(Set(r.isoDays.map { Int(truncating: $0) }))
                return RepeatRow(id: r.scheduleId, title: r.text, subtitle: [Format.time(hour: r.hour, minute: r.minute), days].joined(separator: " · "), enabled: r.enabled)
            },
            done: s.done.map { d in
                let badge = Format.badge(d.badge)
                let steps = d.steps.map { L10n.Tasks.steps(Int(truncating: $0)) }
                return DoneRow(id: d.taskId, title: d.text, subtitle: [Format.day(d.day), steps].compactMap { $0 }.joined(separator: " · "),
                               leading: leading(d.badge), conversationId: d.conversationId)
            }
        )
    }

    private static func leading(_ badge: TaskBadge) -> ModeRowLeading {
        switch badge {
        case .done: .success
        case .failed: .failure
        case .gaveUp: .icon(.warning, ModeColor.warning)
        case .stopped, .notStarted: .icon(.stopped, ModeColor.textSecondary)
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
