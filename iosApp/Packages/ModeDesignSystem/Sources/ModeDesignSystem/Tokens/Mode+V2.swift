import SwiftUI

// Sizes from the Mode v2 boards (groups, rows, the loop sheet, the talk bar).

public extension ModeRadius {
    /// A group of rows (the Mo tab, Start Mo, Trust).
    static let group: CGFloat = 22
    /// An icon tile at the start of a row.
    static let tile: CGFloat = 12
    /// The loop sheet's top corners.
    static let loopSheet: CGFloat = 36
    /// A card inside the loop sheet (the plan, the approval).
    static let loopCard: CGFloat = 26
}

public extension ModeSpacing {
    /// Inside a group, left and right of its rows.
    static let groupInset: CGFloat = 14
    /// A group label's inset from the group's edge.
    static let labelInset: CGFloat = 4
    /// Between a group label and its group.
    static let labelGap: CGFloat = 6
    /// Between groups on a page.
    static let groupGap: CGFloat = 14
    /// From the top of the page's scroll area to its first content (below the bar's back button).
    static let pageTop: CGFloat = 4
}

public extension ModeSize {
    /// A group row with an icon tile and a second line.
    static let groupRow: CGFloat = 60
    /// A group row with an icon tile on a page about one choice (Start Mo).
    static let groupRowTall: CGFloat = 64
    /// A compact group row: one line and a value (the Mo tab).
    static let groupRowCompact: CGFloat = 44
    /// The icon tile at the start of a group row.
    static let groupTile: CGFloat = 36
    /// A numbered step's badge.
    static let stepBadge: CGFloat = 24
    /// The loop sheet's and the guide's full-width buttons.
    static let loopButton: CGFloat = 52
    /// The keypad map: a small iPhone with the passcode keys where they sit.
    static let keypadPhoneWidth: CGFloat = 132
    static let keypadPhoneHeight: CGFloat = 264
    static let keypadPhoneRadius: CGFloat = 26
    static let keypadPhoneFrame: CGFloat = 5
    static let keypadNotchWidth: CGFloat = 40
    static let keypadNotchHeight: CGFloat = 12
    static let keypadKey: CGFloat = 28
    static let keypadKeyRing: CGFloat = 1.5
    static let keypadTop: CGFloat = 62
    static let keypadTitleTop: CGFloat = 34
    static let keypadInset: CGFloat = 14
    static let keypadRowGap: CGFloat = 9
    static let keypadHint: CGFloat = 20
    /// Mo beside a note.
    static let moNote: CGFloat = 44
    /// The "Optional" tag at the top of a guide.
    static let tagHeight: CGFloat = 28
}

/// Fixed sizes for drawings that stay small whatever the text size (the keypad map is a picture).
public enum ModeFont {
    public static let keypadTitle = Font.system(size: 9, weight: .semibold)
    public static let keypadKey = Font.system(size: 12, weight: .semibold)
}

public extension ModeSize {
    /// The talk bar above the tabs, and its buttons.
    static let talkBarHeight: CGFloat = 56
    static let talkButton: CGFloat = 44
    /// The talk button while held (the ring grows around it).
    static let talkButtonHeld: CGFloat = 64
    static let talkRing: CGFloat = 3
    /// A Try chip.
    static let tryChip: CGFloat = 38
    /// The Home cards' buttons.
    static let homeButton: CGFloat = 44
    /// Pause and Stop on the running card.
    static let runningControl: CGFloat = 40
    /// A plan-progress bar on the running card.
    static let runningBar: CGFloat = 6
    /// A finished task's check on Home.
    static let doneMark: CGFloat = 32
}

public extension ModeSpacing {
    /// Between the talk bar and the tabs.
    static let barGap: CGFloat = 10
    /// From the bottom of the screen to the tabs (into the home indicator's area, as on the boards).
    static let tabsBottom: CGFloat = 0
}

public extension ModeRadius {
    /// Home's cards (Needs you, Done today).
    static let homeCard: CGFloat = 24
    /// The running task's ink card.
    static let runningCard: CGFloat = 26
}

public extension ModeSize {
    /// Mo on Home's quiet-day card.
    static let moHomeIdle: CGFloat = 64
}

public extension ModeSize {
    /// Mo at the top of the loop sheet, and smaller on a card.
    static let moLoop: CGFloat = 64
    /// A step's mark in the ledger, and the ring around the current one.
    static let ledgerMark: CGFloat = 24
    static let ledgerRing: CGFloat = 2
    static let ledgerDot: CGFloat = 8
    /// The voice level bars while talking.
    static let voiceBarWidth: CGFloat = 4
    static let voiceBarsHeight: CGFloat = 40
    /// The plan's app chip.
    static let appChip: CGFloat = 30
    /// A small answer pill (Just this time, Always…).
    static let scopePill: CGFloat = 32
    /// The answer buttons on a question card.
    static let answerButton: CGFloat = 48
    /// A Mo learned card's buttons.
    static let learnedButton: CGFloat = 40
    /// The countdown bar under Go.
    static let countdownBar: CGFloat = 4
    /// A row in What Mo touched or a payment line.
    static let touchedRow: CGFloat = 44
    static let paymentHeader: CGFloat = 36
    static let paymentTotal: CGFloat = 48
}

public extension ModeSpacing {
    /// Inside the loop sheet.
    static let sheetSide: CGFloat = 20
    static let sheetGap: CGFloat = 16
    /// Inside the plan card.
    static let planPadding: CGFloat = 18
}

public extension ModeSize {
    /// How far the loop sheet is dragged down to close.
    static let dismissDrag: CGFloat = 120
}

/// Timings the screens show (they match the presenters').
public enum ModeTiming {
    /// The plan's countdown before it starts by itself, in seconds.
    public static let autoGo: Double = 1.5
    /// How long Undo stays on Done, in seconds.
    public static let undoSeconds: Int = 30
}

public extension ModeSize {
    /// The undo countdown bar's width.
    static let undoBar: CGFloat = 120
}

public extension ModeSize {
    /// Trust's three-way picker.
    static let levelButton: CGFloat = 34
    /// Mo on the personality page, and the plain dot that stands in for it.
    static let moPersonality: CGFloat = 150
    static let moPersonalityDot: CGFloat = 64
    static let moPersonalityArea: CGFloat = 150
    /// A colour swatch.
    static let swatch: CGFloat = 40
    static let swatchRing: CGFloat = 2
    /// The name field on the personality page.
    static let nameField: CGFloat = 52
}

public extension ModeRadius {
    /// Trust's picker, and a button in it.
    static let levelPicker: CGFloat = 14
    static let levelButton: CGFloat = 11
}

public extension ModeSize {
    /// The loop sheet's grabber area (above its content).
    static let sheetChrome: CGFloat = 29
}

public extension ModeSize {
    /// The first run's step dots.
    static let stepDotV2: CGFloat = 8
    static let stepDotCurrentV2: CGFloat = 22
    /// A first job to tap, and the first run's main button.
    static let jobButton: CGFloat = 54
    /// Mo on Welcome, and on the first job.
    static let moWelcome: CGFloat = 150
    static let moFirstJob: CGFloat = 120
    /// The provider's tile on Connect your AI.
    static let providerTileV2: CGFloat = 44
    /// "More options".
    static let moreOptions: CGFloat = 56
}

public extension ModeRadius {
    /// The first run's provider card and More options.
    static let firstRunCard: CGFloat = 24
}

public extension ModeSize {
    /// The Now / Scheduled / Done control.
    static let segmentButton: CGFloat = 36
    /// A task row's mark, and its Cancel.
    static let taskMark: CGFloat = 36
    static let taskCancel: CGFloat = 32
    static let taskRowV2: CGFloat = 60
}

public extension ModeRadius {
    static let segmentTrack: CGFloat = 16
    static let segmentButton: CGFloat = 13
    /// The week card on Tasks.
    static let weekCard: CGFloat = 20
}

public extension ModeSize {
    /// Mo on the recap, and a number tile's minimum height.
    static let moRecap: CGFloat = 92
    static let recapRow: CGFloat = 42
}

public extension ModeRadius {
    static let statTile: CGFloat = 18
}
