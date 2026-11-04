import SwiftUI

/// A floating choice of two or three views (Grid / Play), as on All screens: a white capsule, the chosen one
/// in an ink pill that slides between them.
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
                    HStack(spacing: ModeSpacing.s - ModeSpacing.xxs) {
                        option.icon.image.font(ModeIconFont.small)
                        Text(option.title)
                    }
                    .modeText(.subheadlineEmphasis, color: selected ? ModeColor.onAccent : ModeColor.textPrimary)
                    .padding(.horizontal, ModeSpacing.l + ModeSpacing.xxs)
                    .frame(height: ModeSize.touch)
                    .background {
                        if selected { Capsule().fill(ModeColor.accent).matchedGeometryEffect(id: "lens", in: glass) }
                    }
                    .contentShape(.capsule)
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
