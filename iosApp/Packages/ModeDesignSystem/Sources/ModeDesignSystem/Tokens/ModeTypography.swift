import CoreText
import SwiftUI

/// Mode's type scale, from the Mo theme. Titles and section headings use Bricolage Grotesque
/// (SIL Open Font License, Resources/Fonts/OFL.txt), heavy and tightly spaced; everything else
/// uses SF Pro. Every style scales with the iPhone's text size.
public enum ModeTextStyle: CaseIterable, Sendable {
    /// 34 heavy display: a top-level page (Settings, Tasks, Memory).
    case largeTitle
    /// 32 heavy display: a setup step or a page's headline.
    case title
    /// 30 heavy display: the Mo tab's name.
    case tabTitle
    /// 24 bold display: the words heard while holding the talk button.
    case heard
    /// 28 heavy display: a v2 page's heading (Start Mo, Unlock by voice, Trust).
    case heading
    /// 26 heavy display: a conversation's title, a sheet's title.
    case title2
    /// 20 bold display: the running task's name, Mo's line on a card.
    case title3
    /// 18 bold display: section headings (Suggested, Recent, You, AI).
    case section
    /// 17 semibold: a row that stands out, a primary button.
    case headline
    /// 17: replies and reading text.
    case body
    case bodyEmphasis
    /// 16: rows, fields, secondary buttons.
    case callout
    case calloutEmphasis
    /// 15: a greeting, a small button.
    case subheadline
    case subheadlineEmphasis
    /// 13: the second line of a row, notes under a card.
    case footnote
    case footnoteEmphasis
    /// 12 and 11: labels, tab titles, badges.
    case caption
    case captionEmphasis

    var display: (size: CGFloat, weight: CGFloat, relative: Font.TextStyle)? {
        switch self {
        case .largeTitle: (34, 800, .largeTitle)
        case .title: (32, 800, .title)
        case .tabTitle: (30, 800, .largeTitle)
        case .heard: (24, 700, .title2)
        case .heading: (28, 800, .title)
        case .title2: (26, 800, .title2)
        case .title3: (20, 700, .title3)
        case .section: (18, 700, .headline)
        default: nil
        }
    }

    public var font: Font {
        if let display { return ModeTypography.display(size: display.size, weight: display.weight, relativeTo: display.relative) }
        return switch self {
        case .headline: .headline
        case .body: .body
        case .bodyEmphasis: .body.weight(.semibold)
        case .callout: .callout
        case .calloutEmphasis: .callout.weight(.semibold)
        case .subheadline: .subheadline
        case .subheadlineEmphasis: .subheadline.weight(.semibold)
        case .footnote: .footnote
        case .footnoteEmphasis: .footnote.weight(.semibold)
        case .caption: .caption
        case .captionEmphasis: .caption2.weight(.bold)
        default: .body
        }
    }

    /// Display styles are set 2% tighter, as on the canvas.
    var tracking: CGFloat { display.map { -$0.size * 0.02 } ?? 0 }
}

public enum ModeTypography {
    public static let displayFamily = "Bricolage Grotesque"
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

    /// Bricolage at a size and weight, with its optical size matched to the size (as browsers do),
    /// scaled for the iPhone's text size.
    static func display(size: CGFloat, weight: CGFloat, relativeTo style: Font.TextStyle) -> Font {
        Font(uiDisplay(size: size, weight: weight, textStyle: uiStyle(style)))
    }

    static func uiDisplay(size: CGFloat, weight: CGFloat, textStyle: UIFont.TextStyle) -> UIFont {
        let opsz = 0x6F70737A, wght = 0x77676874
        let descriptor = UIFontDescriptor(fontAttributes: [
            .family: displayFamily,
            UIFontDescriptor.AttributeName(rawValue: kCTFontVariationAttribute as String): [opsz: min(max(size, 12), 96), wght: weight],
        ])
        let base = UIFont(descriptor: descriptor, size: size)
        let font = base.familyName == displayFamily ? base : UIFont.systemFont(ofSize: size, weight: weight >= 800 ? .heavy : .bold)
        return UIFontMetrics(forTextStyle: textStyle).scaledFont(for: font)
    }

    /// UIKit fonts for the navigation bar's titles.
    static func uiDisplay(_ style: UIFont.TextStyle, weight: UIFont.Weight) -> UIFont {
        let size: CGFloat = style == .largeTitle ? 34 : 17
        return uiDisplay(size: size, weight: weight == .heavy ? 800 : 700, textStyle: style)
    }

    private static func uiStyle(_ style: Font.TextStyle) -> UIFont.TextStyle {
        switch style {
        case .largeTitle: .largeTitle
        case .title: .title1
        case .title2: .title2
        case .title3: .title3
        default: .headline
        }
    }
}

public extension View {
    /// Text in one of Mode's styles.
    func modeText(_ style: ModeTextStyle, color: Color = ModeColor.textPrimary) -> some View {
        modifier(ModeTextModifier(style: style, color: color))
    }
}

private struct ModeTextModifier: ViewModifier {
    let style: ModeTextStyle
    let color: Color
    /// Read so display fonts are rebuilt when the text size changes.
    @Environment(\.dynamicTypeSize) private var typeSize

    func body(content: Content) -> some View {
        content.font(style.font).tracking(style.tracking).foregroundStyle(color)
    }
}

/// How far a long line may shrink before it wraps or is cut.
public enum ModeTextScale {
    /// Titles (a conversation named after a long spoken task).
    public static let title: CGFloat = 0.75
}
