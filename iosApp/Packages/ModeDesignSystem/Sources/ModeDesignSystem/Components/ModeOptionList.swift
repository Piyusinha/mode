import SwiftUI

/// Choices to tap, one per row, the recommended one marked: an agent's question with options.
public struct ModeOptionList: View {
    private let options: [String]
    private let recommended: String?
    private let recommendedLabel: String
    private let onPick: (String) -> Void

    public init(_ options: [String], recommended: String?, recommendedLabel: String, onPick: @escaping (String) -> Void) {
        self.options = options
        self.recommended = recommended
        self.recommendedLabel = recommendedLabel
        self.onPick = onPick
    }

    public var body: some View {
        VStack(spacing: ModeSpacing.s) {
            ForEach(options, id: \.self) { option in
                let picked = option == recommended
                Button { onPick(option) } label: {
                    HStack(spacing: ModeSpacing.m) {
                        Text(option).modeText(.body).multilineTextAlignment(.leading)
                        Spacer(minLength: ModeSpacing.s)
                        if picked { ModeTag(recommendedLabel, background: ModeColor.tagGreen, foreground: ModeColor.tagGreenText) }
                        ModeChevron()
                    }
                    .padding(.horizontal, ModeSpacing.l)
                    .frame(minHeight: ModeSize.rowMin)
                    .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.chip))
                    .overlay {
                        if picked { RoundedRectangle(cornerRadius: ModeRadius.chip).stroke(ModeColor.tint, lineWidth: ModeSize.separator * 2) }
                    }
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
        }
    }
}
