import SwiftUI

/// A word the user added, with an × to take it off (Access: words that give one task full access).
public struct ModeRemovableChip: View {
    private let text: String
    private let removeLabel: String
    private let onRemove: () -> Void

    public init(_ text: String, removeLabel: String, onRemove: @escaping () -> Void) {
        self.text = text
        self.removeLabel = removeLabel
        self.onRemove = onRemove
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            Text(text).modeText(.subheadline, color: ModeColor.tint).fontWeight(.semibold)
            Button(action: onRemove) {
                ModeIcon.close.image.font(ModeTextStyle.captionEmphasis.font).foregroundStyle(ModeColor.tint)
                    .frame(minWidth: ModeSize.badge, minHeight: ModeSize.badge)
                    .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(removeLabel))
        }
        .padding(.leading, ModeSpacing.m)
        .frame(minHeight: ModeSize.badge + ModeSpacing.xs)
        .background(ModeColor.tintSoft, in: .capsule)
    }
}

/// "+ Add a word": a dashed capsule that adds another chip.
public struct ModeAddChip: View {
    private let title: LocalizedStringResource
    private let action: () -> Void

    public init(_ title: LocalizedStringResource, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Label { Text(title) } icon: { ModeIcon.add.image }
                .modeText(.subheadline)
                .fontWeight(.semibold)
                .padding(.horizontal, ModeSpacing.m)
                .frame(minHeight: ModeSize.badge + ModeSpacing.xs)
                .overlay(Capsule().strokeBorder(ModeColor.separator, style: StrokeStyle(lineWidth: ModeSize.separator, dash: [ModeSpacing.xs])))
                .contentShape(.capsule)
        }
        .buttonStyle(.plain)
    }
}

/// Chips that wrap onto as many lines as they need.
public struct ModeChipFlow: Layout {
    private let spacing: CGFloat

    public init(spacing: CGFloat = ModeSpacing.s) {
        self.spacing = spacing
    }

    public func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let rows = rows(width: proposal.width ?? .infinity, subviews: subviews)
        let height = rows.map(\.height).reduce(0, +) + spacing * CGFloat(max(rows.count - 1, 0))
        return CGSize(width: proposal.width ?? rows.map(\.width).max() ?? 0, height: height)
    }

    public func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var y = bounds.minY
        for row in rows(width: bounds.width, subviews: subviews) {
            var x = bounds.minX
            for index in row.items {
                let size = subviews[index].sizeThatFits(.unspecified)
                subviews[index].place(at: CGPoint(x: x, y: y + (row.height - size.height) / 2), proposal: .unspecified)
                x += size.width + spacing
            }
            y += row.height + spacing
        }
    }

    private struct Row { var items: [Int] = []; var width: CGFloat = 0; var height: CGFloat = 0 }

    private func rows(width: CGFloat, subviews: Subviews) -> [Row] {
        var rows = [Row()]
        for index in subviews.indices {
            let size = subviews[index].sizeThatFits(.unspecified)
            if !rows[rows.count - 1].items.isEmpty, rows[rows.count - 1].width + spacing + size.width > width { rows.append(Row()) }
            var row = rows[rows.count - 1]
            row.width += (row.items.isEmpty ? 0 : spacing) + size.width
            row.height = max(row.height, size.height)
            row.items.append(index)
            rows[rows.count - 1] = row
        }
        return rows
    }
}
