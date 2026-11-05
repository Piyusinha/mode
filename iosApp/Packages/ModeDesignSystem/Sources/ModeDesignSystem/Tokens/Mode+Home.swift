import SwiftUI

/// Sizes for Home, Tasks, Repeat, Live run and All screens, from their boards.
public extension ModeSize {
    /// A row in Home's Recent card, and in Tasks' Up next and Repeats.
    static let recentRow: CGFloat = 62
    static let taskRow: CGFloat = 64
    /// Mo on a Tasks card (running, waiting), on the white running bar above the tabs, on the live panel.
    static let moCard: CGFloat = 36
    static let moBar: CGFloat = 34
    static let moPanel: CGFloat = 40
    /// The Tasks running card and the waiting card.
    static let taskCardMin: CGFloat = 72
    /// The bars on the Tasks running card, and how far they're inset (past Mo).
    static let taskBar: CGFloat = 5
    static let taskBarInset: CGFloat = 48
    /// The Now / Scheduled / Done switch, and the smaller one in Repeat.
    static let segmentsHeight: CGFloat = 42
    static let segmentsCompactHeight: CGFloat = 38
    static let segmentsInset: CGFloat = 3
    /// The stop square inside a round Stop button.
    static let stopSquare: CGFloat = 11
    static let stopSquareRadius: CGFloat = 3
    /// A cell on All screens, its number badge, and the Grid / Play switch.
    static let screenCell: CGFloat = 196
    static let screenBadge: CGFloat = 20
    static let switchHeight: CGFloat = 52
    /// The live run: its bottom panel's grabber.
    static let grabberWidth: CGFloat = 36
    static let grabberHeight: CGFloat = 5
    /// The badge at the top of the live run.
    static let liveBadgeHeight: CGFloat = 36
    /// The live run's bottom panel, at least.
    static let livePanelHeight: CGFloat = 246
    /// Repeat's time wheels: three rows tall, and each column's width.
    static let timeWheel: CGFloat = 112
    /// The wheel is drawn taller and clipped, so the rows above and below aren't squeezed.
    static let timeWheelPicker: CGFloat = 180
    static let timeWheelColumn: CGFloat = 44
    static let timeWheelRow: CGFloat = 38
}

public extension ModeRadius {
    /// The screen Mode is using, on the live run, and the panel under it.
    static let liveScreen: CGFloat = 36
    static let livePanel: CGFloat = 40
    /// A cell on All screens, the Mo note in Repeat.
    static let screenCell: CGFloat = 22
    static let note: CGFloat = 22
}

public extension ModeSize {
    /// Tasks rows, a little roomier than other lists (Up next, Repeats, Done).
    static let tasksRow: CGFloat = 72
    /// Room under a tab's last item so the bar (tabs, the Speak button, its fade) never covers it.
    static let tabBarClearance: CGFloat = 120
    /// A task to try, as a pill on Home.
    static let tryPill: CGFloat = 44
}
