import Foundation
import ModeDesignSystem
import ModeKit

/// Turns the presenters' semantic values into words, in the user's language.
/// ViewModels call these; views only show the result.
enum Format {
    static func day(_ day: RelativeDay) -> String {
        switch onEnum(of: day) {
        case .today: String(localized: L10n.Day.today)
        case .yesterday: String(localized: L10n.Day.yesterday)
        case let .weekday(w): weekdayName(Int(w.isoDay))
        case let .date(d): Date(timeIntervalSince1970: TimeInterval(d.epochMs) / 1000).formatted(.dateTime.day().month())
        }
    }

    /// 1 = Monday … 7 = Sunday.
    static func weekdayName(_ isoDay: Int) -> String {
        let symbols = Calendar.current.weekdaySymbols
        return symbols[isoDay % 7]
    }

    /// One-letter day names, Monday first.
    static var shortDays: [String] {
        let symbols = Calendar.current.veryShortStandaloneWeekdaySymbols
        return (1...7).map { symbols[$0 % 7] }
    }

    static func time(hour: Int32, minute: Int32) -> String {
        let date = Calendar.current.date(from: DateComponents(hour: Int(hour), minute: Int(minute))) ?? .now
        return date.formatted(date: .omitted, time: .shortened)
    }

    static func moment(epochMs: Int64) -> String {
        Date(timeIntervalSince1970: TimeInterval(epochMs) / 1000).formatted(.relative(presentation: .named))
    }

    static func money(_ usd: Double) -> String {
        usd.formatted(.currency(code: "USD").precision(.fractionLength(usd < 0.01 ? 3 : 2)))
    }

    static func seconds(_ value: Double) -> String {
        Duration.seconds(value).formatted(.units(allowed: [.minutes, .seconds], width: .narrow))
    }

    struct BadgeStyle {
        let text: LocalizedStringResource
        let icon: ModeIcon
        let tone: ModeBadge.Tone
    }

    static func badge(_ badge: TaskBadge) -> BadgeStyle {
        switch badge {
        case .done: BadgeStyle(text: L10n.Badge.done, icon: .done, tone: .success)
        case .gaveUp: BadgeStyle(text: L10n.Badge.gaveUp, icon: .warning, tone: .warning)
        case .stopped: BadgeStyle(text: L10n.Badge.stopped, icon: .stopped, tone: .neutral)
        case .failed: BadgeStyle(text: L10n.Badge.failed, icon: .failed, tone: .danger)
        case .notStarted: BadgeStyle(text: L10n.Badge.notStarted, icon: .info, tone: .neutral)
        }
    }
}
