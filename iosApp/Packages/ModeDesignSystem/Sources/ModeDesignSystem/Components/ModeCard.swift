import SwiftUI

/// Grouped content on the page: rows separated by hairlines, in a rounded card.
public struct ModeCard<Content: View>: View {
    private let content: Content

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
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
    }
}

/// A heading above a group of rows.
public struct ModeSectionHeader: View {
    private let title: LocalizedStringResource

    public init(_ title: LocalizedStringResource) {
        self.title = title
    }

    public var body: some View {
        Text(title)
            .modeText(.headline)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, ModeSpacing.xs)
            .padding(.top, ModeSpacing.m)
            .accessibilityAddTraits(.isHeader)
    }
}
