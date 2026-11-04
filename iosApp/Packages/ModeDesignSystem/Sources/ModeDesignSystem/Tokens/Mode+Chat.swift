import SwiftUI

/// Sizes from the Mo theme's chat boards (Chat, TeachSteer, TeachLearned, TaskFailedChat, TaskDebug,
/// TaskDebugAsk, VoiceNoticeChat, TeachStep).
public extension ModeSpacing {
    /// Between messages in a chat.
    static let chatGap: CGFloat = 14
    /// Between Mo and its reply, and inside small chat pills.
    static let chatInline: CGFloat = 10
    /// Inside a bubble's label row, a capsule's tight end.
    static let chatTight: CGFloat = 6
    /// A conversation's title block: from the left edge.
    static let chatTitleInset: CGFloat = 4
}

public extension ModeSize {
    /// The widest a bubble of yours gets.
    static let bubbleMaxWidth: CGFloat = 286
    /// Mo on a card: the working card, the "What should Mode have done?" sheet.
    static let moChatCard: CGFloat = 40
    /// Mo on the "Couldn't finish" card.
    static let moFailed: CGFloat = 48
    /// Mo beside What went wrong's title.
    static let moDebugHeader: CGFloat = 64
    /// Mo on a small notice ("Mode couldn't speak with ElevenLabs").
    static let moNotice: CGFloat = 28
    /// "See all 11 screens": one screenshot in the stack, its overlap and border.
    static let screensThumbWidth: CGFloat = 22
    static let screensThumbHeight: CGFloat = 36
    static let screensThumbOverlap: CGFloat = 10
    static let screensThumbBorder: CGFloat = 2
    static let screensLinkHeight: CGFloat = 48
    /// Small buttons inside chat pills (Undo, Remember, Update in Settings).
    static let chatPillButton: CGFloat = 30
    /// Edit and Undo on a saved tip.
    static let lessonButton: CGFloat = 34
    /// The tile beside a saved tip, and beside a finding.
    static let chatTile: CGFloat = 34
    /// Stop on the working card, and its square.
    static let workingStop: CGFloat = 36
    static let stopGlyph: CGFloat = 12
    static let stopGlyphRadius: CGFloat = 3
    /// "● Mode is working": the dot and the pill's height.
    static let workingDot: CGFloat = 6
    static let workingTag: CGFloat = 24
    /// What went wrong and Try again on the "Couldn't finish" card.
    static let failedButton: CGFloat = 42
    /// A finding's row, and the check at its end.
    static let debugRow: CGFloat = 60
    static let debugCheck: CGFloat = 22
    /// An agenda line's time column.
    static let agendaTimeChat: CGFloat = 48
    /// Send on the Ask about this task bar.
    static let askSend: CGFloat = 42
    /// A suggested question.
    static let askChip: CGFloat = 34
    /// The fix sheet's text box (three lines and its padding).
    static let chatFixField: CGFloat = 108
    /// The two-way switch on the fix sheet.
    static let chatSegment: CGFloat = 34
    /// The stroke round a card that is happening now (working, a tip just saved).
    static let chatRing: CGFloat = 2
}

public extension ModeRadius {
    /// Chat cards: working, a saved tip, Couldn't finish.
    static let chatCard: CGFloat = 24
    /// A notice in the chat.
    static let chatNotice: CGFloat = 22
    /// The fix sheet's text box (three lines and its padding).and switch.
    static let chatFixField: CGFloat = 18
    /// A saved tip's tile, a finding's tile.
    static let chatTile: CGFloat = 10
}

public extension ModeColor {
    /// A saved tip's app tile (a neutral grey, white glyph in both looks).
    static let lessonTile = Color(red: 0.478, green: 0.490, blue: 0.525)
}

public extension ModeSpacing {
    /// The message bar's capsule: round its buttons.
    static let composerInset: CGFloat = 5
}

public extension ModeIconFont {
    /// The small glyph in a bubble's label ("Spoken") and in a chat pill.
    static let tag = Font.system(size: 12, weight: .semibold)
    /// Speak and Send in the message bar; the icons on chat buttons.
    static let chatSend = Font.system(size: 17, weight: .semibold)
    static let chatButton = Font.system(size: 15, weight: .semibold)
}

public extension ModeOpacity {
    /// A bubble's label ("Spoken") against the bubble.
    static let bubbleTag: Double = 0.82
}

/// Glyphs the chat boards draw that ModeIcon doesn't name (outlined, as on the canvas).
public enum ModeChatIcon: String, Sendable {
    /// "Couldn't finish", and a finding's warning line.
    case warning = "exclamationmark.triangle"
    /// "✦ Answered with your … plan".
    case sparkle = "sparkle"
    /// A finding: a tap that didn't go through.
    case wrench = "wrench"
    /// A finding: the phone.
    case phone = "iphone"
    /// A finding: reconnected.
    case arrowRight = "arrow.right"
    /// A finding: remembered.
    case brain = "brain"

    public var image: Image { Image(systemName: rawValue) }
}

public extension ModeSize {
    /// Mo beside an answer bubble, and beside a reply whose task couldn't finish.
    static let moAnswer: CGFloat = 36
    /// Letter spacing of the small uppercase labels.
    static let chatKerning: CGFloat = 0.4
}

public extension ModeSize {
    /// The "What should Mode have done?" sheet's height (board TeachStep).
    static let fixSheet: CGFloat = 430
}

public extension ModeSize {
    /// How far the page's fade reaches above a bottom bar.
    static let barFadeOverlap: CGFloat = 40
}
