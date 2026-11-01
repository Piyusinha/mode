import CoreText
import SwiftUI

/// Mode's type scale. Headings use Instrument Sans (SIL Open Font License,
/// Resources/Fonts/OFL.txt); body text uses SF Pro. Every style scales with the
/// iPhone's text size.
public enum ModeTextStyle: CaseIterable, Sendable {
    case largeTitle, title, title2, headline, body, bodyEmphasis, callout, subheadline, footnote, caption, captionEmphasis

    public var font: Font {
        switch self {
        case .largeTitle: ModeTypography.display(.largeTitle, weight: .bold)
        case .title: ModeTypography.display(.title, weight: .bold)
        case .title2: ModeTypography.display(.title2, weight: .semibold)
        case .headline: ModeTypography.display(.headline, weight: .semibold)
        case .body: .body
        case .bodyEmphasis: .body.weight(.semibold)
        case .callout: .callout
        case .subheadline: .subheadline
        case .footnote: .footnote
        case .caption: .caption
        case .captionEmphasis: .caption.weight(.semibold)
        }
    }
}

public enum ModeTypography {
    public static let displayFamily = "Instrument Sans"
    /// Code blocks in replies.
    public static let code = Font.system(.callout, design: .monospaced)

    /// Registers the bundled fonts; call once at launch. Returns false if they didn't load
    /// (headings then fall back to SF Pro).
    @discardableResult
    public static func registerFonts() -> Bool {
        let urls = Bundle.module.urls(forResourcesWithExtension: "ttf", subdirectory: nil) ?? []
        for url in urls { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
        return !UIFont.fontNames(forFamilyName: displayFamily).isEmpty
    }

    static func display(_ style: Font.TextStyle, weight: Font.Weight) -> Font {
        .custom(displayFamily, size: baseSize(style), relativeTo: style).weight(weight)
    }

    /// UIKit fonts for the navigation bar's titles.
    static func uiDisplay(_ style: UIFont.TextStyle, weight: UIFont.Weight) -> UIFont {
        let size = UIFont.preferredFont(forTextStyle: style).pointSize
        let descriptor = UIFontDescriptor(fontAttributes: [.family: displayFamily])
            .addingAttributes([.traits: [UIFontDescriptor.TraitKey.weight: weight]])
        return UIFontMetrics(forTextStyle: style).scaledFont(for: UIFont(descriptor: descriptor, size: size))
    }

    private static func baseSize(_ style: Font.TextStyle) -> CGFloat {
        switch style {
        case .largeTitle: 34
        case .title: 28
        case .title2: 22
        case .title3: 20
        case .headline: 17
        default: 17
        }
    }
}

public extension View {
    /// Text in one of Mode's styles.
    func modeText(_ style: ModeTextStyle, color: Color = ModeColor.textPrimary) -> some View {
        font(style.font).foregroundStyle(color)
    }
}

/// How far a long line may shrink before it wraps or is cut.
public enum ModeTextScale {
    /// Titles (a conversation named after a long spoken task).
    public static let title: CGFloat = 0.75
}
