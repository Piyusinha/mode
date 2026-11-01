import SwiftUI

/// A floating glass choice of two or three views (Grid / Play): the chosen one sits in a lens.
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
                        .modeText(.bodyEmphasis, color: selected ? ModeColor.tint : ModeColor.textPrimary)
                        .padding(.horizontal, ModeSpacing.l)
                        .frame(minHeight: ModeSize.touch)
                        .background {
                            if selected { Capsule().fill(ModeColor.tintSoft).matchedGeometryEffect(id: "lens", in: glass) }
                        }
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(ModeSpacing.xs)
        .glassEffect(.regular.interactive(), in: .capsule)
    }
}
