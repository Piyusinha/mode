import SwiftUI

/// Mode's typeface for titles and headings: Instrument Sans (SIL Open Font
/// License, see Fonts/OFL.txt). Body text stays SF Pro. Both scale with the
/// iPhone's text-size setting.
extension Font {
    static let modeFamily = "Instrument Sans"

    /// A heading in Instrument Sans, sized like the system [style] and scaling with it.
    static func mode(_ style: TextStyle, weight: Weight = .bold) -> Font {
        .custom(modeFamily, size: baseSize(style), relativeTo: style).weight(weight)
    }

    private static func baseSize(_ style: TextStyle) -> CGFloat {
        switch style {
        case .largeTitle: 34
        case .title: 28
        case .title2: 22
        case .title3: 20
        case .headline: 17
        case .subheadline: 15
        case .callout: 16
        case .footnote: 13
        case .caption: 12
        case .caption2: 11
        default: 17
        }
    }
}

/// True when the app's bundled fonts loaded (checked once at launch, logged if not).
enum ModeFonts {
    static func check() {
        if UIFont(name: "InstrumentSans-Regular", size: 17) == nil,
           UIFont.fontNames(forFamilyName: Font.modeFamily).isEmpty {
            NSLog("Mode: Instrument Sans did not load; headings fall back to SF Pro")
        } else {
            NSLog("Mode: Instrument Sans loaded: %@", UIFont.fontNames(forFamilyName: Font.modeFamily).joined(separator: ", "))
        }
    }
}
