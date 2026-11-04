import Foundation
import ModeKit
import Observation

/// The Repeat sheet: a task, a time and which days.
@MainActor
@Observable
final class RepeatViewModel {
    enum Frequency: Hashable { case everyDay, weekdays, weekends, once, custom }

    struct State: Equatable {
        var text = ""
        var time = Date()
        var hour = 8
        var minute = 0
        var frequency: Frequency = .everyDay
        var days: Set<Int> = Set(1...7)
        var note = ""
        var canSave = false
    }

    enum Event {
        case text(String)
        case time(Date)
        case hour(Int)
        case minute(Int)
        case frequency(Frequency)
        case toggleDay(Int)
        case save
    }

    private(set) var state = State()
    let dayLabels = Format.shortDays
    private let presenter: RepeatPresenter
    private let dismiss: () -> Void
    private var updates: Updates?

    init(screens: Screens, text: String, dismiss: @escaping () -> Void) {
        presenter = screens.repeat(text: text)
        self.dismiss = dismiss
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case let .text(text): presenter.setText(text: text)
        case let .time(date):
            let parts = Calendar.current.dateComponents([.hour, .minute], from: date)
            presenter.setTime(hour: Int32(parts.hour ?? 0), minute: Int32(parts.minute ?? 0))
        case let .hour(h): presenter.setTime(hour: Int32(h), minute: Int32(state.minute))
        case let .minute(m): presenter.setTime(hour: Int32(state.hour), minute: Int32(m))
        case let .frequency(f): presenter.setFrequency(frequency: Self.kotlin(f))
        case let .toggleDay(day): presenter.toggleDay(isoDay: Int32(day))
        case .save: if presenter.save() { dismiss() }
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: RepeatUiState) -> State {
        State(
            text: s.text,
            time: Calendar.current.date(from: DateComponents(hour: Int(s.hour), minute: Int(s.minute))) ?? .now,
            hour: Int(s.hour),
            minute: Int(s.minute),
            frequency: swift(s.frequency),
            days: Set(s.isoDays.map { Int(truncating: $0) }),
            note: note(s),
            canSave: s.canSave
        )
    }

    /// "Runs at 8:00 on weekdays while Mode is open. If it isn't, …"
    private static func note(_ s: RepeatUiState) -> String {
        guard s.firstRunAtMs != nil else { return "" }
        let time = Format.time(hour: s.hour, minute: s.minute)
        let days: String
        switch swift(s.frequency) {
        case .everyDay: days = String(localized: L10n.Repeat.onEveryDay)
        case .weekdays: days = String(localized: L10n.Repeat.onWeekdays)
        case .weekends: days = String(localized: L10n.Repeat.onWeekends)
        case .once: days = s.firstRunAtMs.map { Format.moment(epochMs: Int64(truncating: $0)) } ?? ""
        case .custom: days = L10n.Repeat.onDays(s.isoDays.map { Format.weekdayName(Int(truncating: $0)) }.formatted(.list(type: .and, width: .short)))
        }
        return swift(s.frequency) == .once ? L10n.Repeat.noteOnce(days) : L10n.Repeat.note(time: time, days: days)
    }

    private static func swift(_ f: ModeKit.Frequency) -> Frequency {
        switch f {
        case .everyDay: .everyDay
        case .weekdays: .weekdays
        case .weekends: .weekends
        case .once: .once
        case .custom: .custom
        }
    }

    private static func kotlin(_ f: Frequency) -> ModeKit.Frequency {
        switch f {
        case .everyDay: .everyDay
        case .weekdays: .weekdays
        case .weekends: .weekends
        case .once: .once
        case .custom: .custom
        }
    }
}
