import SwiftUI

/// A small iPhone showing where the passcode keys sit, and the swipe up that opens the keypad (the Unlock by
/// voice guide: Voice Control records the gesture on a blank screen).
public struct ModeKeypadMap: View {
    private let title: LocalizedStringResource
    private let label: LocalizedStringResource
    private static let keys = ["1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", ""]

    public init(title: LocalizedStringResource, accessibilityLabel: LocalizedStringResource) {
        self.title = title
        self.label = accessibilityLabel
    }

    public var body: some View {
        ZStack(alignment: .top) {
            RoundedRectangle(cornerRadius: ModeSize.keypadPhoneRadius).fill(ModeColor.cardRaised)
            Capsule().fill(ModeColor.moInk)
                .frame(width: ModeSize.keypadNotchWidth, height: ModeSize.keypadNotchHeight)
                .padding(.top, ModeSpacing.xs + ModeSpacing.xxs)
            Text(title)
                .font(ModeFont.keypadTitle)
                .foregroundStyle(ModeColor.textSecondary)
                .padding(.top, ModeSize.keypadTitleTop)
            LazyVGrid(columns: Array(repeating: GridItem(.flexible()), count: 3), spacing: ModeSize.keypadRowGap) {
                ForEach(Array(Self.keys.enumerated()), id: \.offset) { _, key in
                    Text(key)
                        .font(ModeFont.keypadKey)
                        .foregroundStyle(ModeColor.textPrimary)
                        .frame(width: ModeSize.keypadKey, height: ModeSize.keypadKey)
                        .background(key.isEmpty ? Color.clear : ModeColor.card, in: .circle)
                        .overlay(Circle().strokeBorder(key.isEmpty ? Color.clear : ModeColor.ring, lineWidth: ModeSize.keypadKeyRing))
                }
            }
            .padding(.horizontal, ModeSize.keypadInset)
            .padding(.top, ModeSize.keypadTop)
            VStack {
                Spacer()
                ModeIcon.swipeUp.image.font(ModeIconFont.large.weight(.heavy)).foregroundStyle(ModeColor.brand)
                    .padding(.bottom, ModeSpacing.s)
            }
        }
        .frame(width: ModeSize.keypadPhoneWidth, height: ModeSize.keypadPhoneHeight)
        .clipShape(.rect(cornerRadius: ModeSize.keypadPhoneRadius))
        .overlay(RoundedRectangle(cornerRadius: ModeSize.keypadPhoneRadius).strokeBorder(ModeColor.deviceFrame, lineWidth: ModeSize.keypadPhoneFrame))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(label))
    }
}

/// A lettered hint beside the keypad map ("a  Swipe up from the very bottom edge.").
public struct ModeLetteredHint: View {
    private let letter: String
    private let text: LocalizedStringResource

    public init(_ letter: String, _ text: LocalizedStringResource) {
        self.letter = letter
        self.text = text
    }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.s) {
            Text(verbatim: letter)
                .modeText(.captionEmphasis, color: ModeColor.tint)
                .frame(width: ModeSize.keypadHint, height: ModeSize.keypadHint)
                .background(ModeColor.tintSoft, in: .circle)
                .accessibilityHidden(true)
            Text(text).modeText(.subheadline)
        }
    }
}
