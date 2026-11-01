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
        var frequency: Frequency = .everyDay
        var days: Set<Int> = Set(1...7)
        var note = ""
        var canSave = false
    }

    enum Event {
        case text(String)
        case time(Date)
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
            frequency: swift(s.frequency),
            days: Set(s.isoDays.map { Int(truncating: $0) }),
            note: s.firstRunAtMs.map { L10n.Repeat.note(Format.moment(epochMs: Int64(truncating: $0))) } ?? "",
            canSave: s.canSave
        )
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
