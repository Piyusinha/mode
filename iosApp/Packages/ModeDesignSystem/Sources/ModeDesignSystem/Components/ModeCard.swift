import SwiftUI

/// Grouped content on the page: rows separated by hairlines, in a white card with 26-point corners.
/// [lifted] adds the soft shadow the setup screens use; lists in Settings sit flat.
public struct ModeCard<Content: View>: View {
    private let content: Content
    private let lifted: Bool

    public init(lifted: Bool = false, @ViewBuilder content: () -> Content) {
        self.content = content()
        self.lifted = lifted
    }

    public var body: some View {
        VStack(spacing: 0) {
            Group(subviews: content) { rows in
                ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                    row
                    if index < rows.count - 1 {
                        Rectangle().fill(ModeColor.separator).frame(height: ModeSize.separator)
                    }
                }
            }
        }
        .padding(.horizontal, ModeSpacing.l)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.card), lifted: lifted)
    }
}

/// A heading above a group of rows: Bricolage, 18 bold.
public struct ModeSectionHeader: View {
    private let title: LocalizedStringResource

    public init(_ title: LocalizedStringResource) {
        self.title = title
    }

    public var body: some View {
        Text(title)
            .modeText(.section)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, ModeSpacing.xs)
            .padding(.top, ModeSpacing.m)
            .accessibilityAddTraits(.isHeader)
    }
}
