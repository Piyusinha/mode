import SwiftUI

/// A floating choice of two or three views (Grid / Play): a white capsule, the chosen one in a raised pill.
public struct ModeGlassSwitch<ID: Hashable>: View {
    private let options: [ModeTabItem<ID>]
    @Binding private var selection: ID
    @Namespace private var glass

    public init(_ options: [ModeTabItem<ID>], selection: Binding<ID>) {
        self.options = options
        self._selection = selection
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xs) {
            ForEach(options) { option in
                let selected = option.id == selection
                Button {
                    withAnimation(ModeMotion.standard) { selection = option.id }
                } label: {
                    Label { Text(option.title) } icon: { option.icon.image }
                        .modeText(.subheadlineEmphasis, color: selected ? ModeColor.textPrimary : ModeColor.textSecondary)
                        .padding(.horizontal, ModeSpacing.l)
                        .frame(minHeight: ModeSize.touch)
                        .background {
                            if selected { Capsule().fill(ModeColor.cardRaised).matchedGeometryEffect(id: "lens", in: glass) }
                        }
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(ModeSpacing.xs)
        .background(ModeColor.card, in: .capsule)
        .modeShadow(in: Capsule())
    }
}
