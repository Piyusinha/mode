import SwiftUI
import UIKit

/// Sizes for the voice screens (Mode's voice, Listening, a provider's page, the key sheet, Choose a voice)
/// and talking mode, from their boards.
public extension ModeSize {
    /// A provider row (Apple, ElevenLabs, Fish Audio) and a voice row in Choose a voice.
    static let voiceProviderRowMin: CGFloat = 72
    static let voicePickerRow: CGFloat = 68
    /// Mo on Mode's voice card, with the provider's badge on its corner.
    static let voiceMo: CGFloat = 52
    static let voiceMoFrame: CGFloat = 56
    static let voiceBadge: CGFloat = 24
    static let voiceBadgeRadius: CGFloat = 8
    static let voiceBadgeLogo: CGFloat = 11
    /// The provider's tile on its page, and on the key sheet.
    static let voicePageTile: CGFloat = 56
    static let voicePageTileRadius: CGFloat = 18
    static let voiceSheetTile: CGFloat = 48
    static let voiceSheetTileRadius: CGFloat = 14
    /// Mo in the Listening header, on the key sheet's title, and on its result card.
    static let voiceHeaderMo: CGFloat = 64
    static let voiceSheetMo: CGFloat = 40
    static let voiceResultMo: CGFloat = 44
    /// The round close on a sheet (drawn small, tapped at 44).
    static let sheetClose: CGFloat = 32
    /// The key field's tick when a key is saved.
    static let keySaved: CGFloat = 28
    /// A status dot (connected, failed) and the bigger one on a notice.
    static let voiceDot: CGFloat = 8
    /// Talking as one conversation: Mo beside its lines, on a task's card, the card's timeline, and a result row's icon.
    static let talkLineMo: CGFloat = 28
    static let talkCardMo: CGFloat = 34
    static let talkStepDot: CGFloat = 8
    static let talkStepLine: CGFloat = 2
    static let talkStepRail: CGFloat = 14
    static let talkRowIcon: CGFloat = 30
    static let talkChip: CGFloat = 26
    static let talkCardButton: CGFloat = 40
    static let talkStop: CGFloat = 220
    static let talkDivider: CGFloat = 1
    static let noticeDot: CGFloat = 10
    /// A tag's height ("In use", "Most human").
    static let voiceTag: CGFloat = 20
    /// The bars of a voice: talking mode's wave, and the small ones in a button or a card.
    static let talkBar: CGFloat = 4
    static let talkBarRadius: CGFloat = 2
    static let talkWaveHeight: CGFloat = 34
    static let talkWaveQuestion: CGFloat = 26
    static let smallBar: CGFloat = 3
    static let smallWave: CGFloat = 14
    static let cardWave: CGFloat = 16
    /// Talking mode: Mo listening on its soft disc, Mo on the other states, the status pill, the side and stop buttons.
    static let talkHalo: CGFloat = 236
    static let talkMoListening: CGFloat = 176
    static let talkMo: CGFloat = 132
    static let talkMoAsk: CGFloat = 124
    static let talkMoMic: CGFloat = 150
    static let talkMoNotice: CGFloat = 140
    static let talkMoCard: CGFloat = 30
    static let talkMicBadge: CGFloat = 48
    static let talkStatus: CGFloat = 32
    static let talkLiveLabel: CGFloat = 24
    static let talkLiveDot: CGFloat = 6
    static let talkStopSquare: CGFloat = 22
    static let talkStopRadius: CGFloat = 6
    static let talkSideButtonWidth: CGFloat = 92
    static let talkDockGap: CGFloat = 18
    static let talkStopWidth: CGFloat = 220
    static let talkAppTile: CGFloat = 36
    static let talkSaysIcon: CGFloat = 32
    static let talkOption: CGFloat = 48
    static let talkCursorWidth: CGFloat = 3
    static let talkBubbleMax: CGFloat = 280
    /// The key sheet rises to here (the page shows above it), and the room its button takes.
    static let keySheet: CGFloat = 548
}

public extension ModeRadius {
    /// The key field, and the result card under it.
    static let keyField: CGFloat = 20
    static let keyResult: CGFloat = 22
    /// Talking mode's cards (Mode speaking, working) and the "couldn't" card on a provider's page.
    static let talkCard: CGFloat = 24
}

/// Type sizes the voice and talking boards use that the main scale doesn't: Bricolage at 22, 24, 26 and 30,
/// and SF at 14 and 18.
public enum ModeVoiceTextStyle: Sendable {
    /// 22 bold display: the voice's name on Mode's voice.
    case voiceName
    /// 24 heavy display: a sheet's title (Connect Fish Audio), talking mode's notice.
    case sheetTitle
    /// 30 heavy display: a provider's page title, talking mode's headline (Let Mode hear you).
    case pageTitle
    /// 30 bold display: the words heard while listening.
    case liveWords
    /// 26 bold display: the answer heard.
    case answerWords
    /// 34 heavy display: Mode's spoken question.
    case question
    /// 18 semibold: what Mode says, the user's words in a bubble.
    case spoken
    /// 14: a sheet's description, a hint, a status pill, an underlined link.
    case small
    case smallEmphasis

    var font: Font {
        switch self {
        case .voiceName: ModeTypography.display(size: 22, weight: 700, relativeTo: .title2)
        case .sheetTitle: ModeTypography.display(size: 24, weight: 800, relativeTo: .title2)
        case .pageTitle: ModeTypography.display(size: 30, weight: 800, relativeTo: .title)
        case .liveWords: ModeTypography.display(size: 30, weight: 700, relativeTo: .title)
        case .answerWords: ModeTypography.display(size: 26, weight: 700, relativeTo: .title2)
        case .question: ModeTypography.display(size: 34, weight: 800, relativeTo: .largeTitle)
        case .spoken: Self.scaled(18, .semibold, .body)
        case .small: Self.scaled(14, .regular, .subheadline)
        case .smallEmphasis: Self.scaled(14, .semibold, .subheadline)
        }
    }

    var tracking: CGFloat {
        switch self {
        case .voiceName: -0.44
        case .sheetTitle: -0.48
        case .pageTitle, .liveWords: -0.6
        case .answerWords: -0.5
        case .question: -0.7
        default: 0
        }
    }

    private static func scaled(_ size: CGFloat, _ weight: UIFont.Weight, _ style: UIFont.TextStyle) -> Font {
        Font(UIFontMetrics(forTextStyle: style).scaledFont(for: .systemFont(ofSize: size, weight: weight)))
    }
}

public extension View {
    /// Text in one of the voice boards' extra styles.
    func modeText(_ style: ModeVoiceTextStyle, color: Color = ModeColor.textPrimary) -> some View {
        modifier(ModeVoiceTextModifier(style: style, color: color))
    }
}

private struct ModeVoiceTextModifier: ViewModifier {
    let style: ModeVoiceTextStyle
    let color: Color
    @Environment(\.dynamicTypeSize) private var typeSize

    func body(content: Content) -> some View {
        content.font(style.font).tracking(style.tracking).foregroundStyle(color)
    }
}

public extension ModeSize {
    /// The bottom sheet's handle, and how far it's dragged before it closes.
    static let sheetHandleWidth: CGFloat = 38
    static let sheetHandleHeight: CGFloat = 5
    static let sheetDismissDrag: CGFloat = 120
}

public extension ModeColor {
    /// Behind the bottom sheet: ink at 32%, in both themes (as on the key sheet boards).
    static let voiceScrim = Color(red: 0.071, green: 0.071, blue: 0.086).opacity(0.32)
}
