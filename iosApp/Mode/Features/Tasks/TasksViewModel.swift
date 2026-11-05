import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// Tasks (its boards): Now (running, waiting for you, up next, or easy tasks to try), Scheduled (one-offs and
/// repeats), Done (today and yesterday).
@MainActor
@Observable
final class TasksViewModel {
    enum Filter: Hashable { case now, scheduled, done }

    struct Row: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
        let leading: ModeRowLeading
        /// When the running task started, for its ticking time.
        var since: Date?
        var paused = false
    }

    /// A one-off or a repeat: its time, and whether it's on.
    struct ScheduleRow: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
        let icon: ModeIcon
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
        var scheduled: [ScheduleRow] = []
        var repeats: [ScheduleRow] = []
        var doneToday: [DoneRow] = []
        var doneYesterday: [DoneRow] = []
        var tryThese: [String] = []
        var nowIsEmpty: Bool { running == nil && waiting == nil && upNext.isEmpty }
        var scheduledIsEmpty: Bool { scheduled.isEmpty && repeats.isEmpty }
        var doneIsEmpty: Bool { doneToday.isEmpty && doneYesterday.isEmpty }
    }

    enum Event {
        case openLive
        case cancel(String)
        case setRepeat(String, Bool)
        case deleteRepeat(String)
        case tryTask(String)
        case open(DoneRow)
    }

    private(set) var state = State()
    var filter: Filter = .now { didSet { presenter.setFilter(f: Self.kotlin(filter)) } }
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
        case let .cancel(id): presenter.cancel(taskId: id)
        case let .setRepeat(id, on): presenter.setRepeatEnabled(scheduleId: id, enabled: on)
        case let .deleteRepeat(id): presenter.deleteRepeat(scheduleId: id)
        case let .tryTask(text): if let conversation = presenter.tryTask(text: text) { navigate(.conversation(id: conversation)) }
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

    static func map(_ s: TasksUiState) -> State {
        let running = s.running.flatMap { r in r.question == nil ? r : nil }
        let waiting = s.running.flatMap { r in r.question != nil ? r : nil }
        let done = s.done.map { d in (d, doneRow(d)) }
        return State(
            running: running.map { r in
                Row(id: r.taskId, title: r.text, subtitle: RunStatusViewModel.status(r), leading: .mo(.working),
                    since: r.startedAtMs.map { Date(timeIntervalSince1970: TimeInterval(truncating: $0) / 1000) }, paused: r.paused)
            },
            waiting: waiting.map { r in
                Row(id: r.taskId, title: r.text, subtitle: r.question?.text ?? "", leading: .mo(.yourTurn))
            },
            upNext: s.upNext.map { q in
                Row(id: q.taskId, title: q.text, subtitle: [Format.day(q.day), Format.timeOfDay(epochMs: q.queuedAtMs)].joined(separator: " "),
                    leading: .icon(.clock, ModeColor.textSecondary))
            },
            scheduled: s.repeats.filter { $0.isoDays.isEmpty && $0.nextRunAtMs != nil }.map { r in
                let when = r.nextRunAtMs.map { upcoming(epochMs: Int64(truncating: $0)) } ?? ""
                return ScheduleRow(id: r.scheduleId, title: r.text, subtitle: withPaused(when, r.enabled), icon: .clock, enabled: r.enabled)
            },
            repeats: s.repeats.filter { !$0.isoDays.isEmpty }.map { r in
                let days = daysLabel(Set(r.isoDays.map { Int(truncating: $0) }))
                return ScheduleRow(id: r.scheduleId, title: r.text, subtitle: withPaused("\(days) \(Format.time(hour: r.hour, minute: r.minute))", r.enabled),
                                   icon: .repeatTask, enabled: r.enabled)
            },
            doneToday: done.filter { if case .today = onEnum(of: $0.0.day) { true } else { false } }.map(\.1),
            doneYesterday: done.filter { if case .yesterday = onEnum(of: $0.0.day) { true } else { false } }.map(\.1),
            tryThese: s.tryThese
        )
    }

    /// "Texted Priya · 17:42 · took 42 s", or "Pay electricity bill · Couldn't sign in · 10:25 · stopped at 31 s".
    private static func doneRow(_ d: FinishedTask) -> DoneRow {
        let finished = d.badge == .done
        let duration = d.seconds.map { L10n.Tasks.duration(Int(truncating: $0)) }
        let how = duration.map { finished ? L10n.Tasks.took($0) : L10n.Tasks.stoppedAt($0) }
        return DoneRow(
            id: d.taskId,
            title: [d.text, d.why].compactMap { $0 }.joined(separator: " · "),
            subtitle: [Format.timeOfDay(epochMs: d.endedAtMs), how].compactMap { $0 }.joined(separator: " · "),
            leading: leading(d.badge),
            conversationId: d.conversationId
        )
    }

    /// "Today 19:00", "Tomorrow 9:00", "Thu 7 Nov · 10:00".
    private static func upcoming(epochMs: Int64) -> String {
        let date = Date(timeIntervalSince1970: TimeInterval(epochMs) / 1000)
        let time = date.formatted(date: .omitted, time: .shortened)
        if Calendar.current.isDateInToday(date) { return "\(String(localized: L10n.Day.today)) \(time)" }
        if Calendar.current.isDateInTomorrow(date) { return "\(String(localized: L10n.Day.tomorrow)) \(time)" }
        return "\(date.formatted(.dateTime.weekday(.abbreviated).day().month(.abbreviated))) · \(time)"
    }

    private static func withPaused(_ text: String, _ enabled: Bool) -> String {
        enabled ? text : "\(text) · \(L10n.Tasks.paused)"
    }

    /// The same Mo faces as Home's recent list.
    private static func leading(_ badge: TaskBadge) -> ModeRowLeading {
        switch badge {
        case .done: .mo(.done)
        case .failed, .gaveUp: .mo(.stuck)
        case .stopped, .notStarted: .mo(.sleep)
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
