import SwiftUI

/// One row in a card: something on the left, a title and a line under it, something on the right.
/// What sits at the start of a row.
public enum ModeRowLeading: Equatable, Sendable {
    case none
    case icon(ModeIcon, Color = ModeColor.tint)
    /// A round badge with a letter, for people.
    case initial(String)
}

public struct ModeRow<Trailing: View>: View {
    public typealias Leading = ModeRowLeading

    private let title: String
    private let subtitle: String?
    private let leading: Leading
    private let trailing: Trailing
    private let action: (() -> Void)?

    public init(_ title: String, subtitle: String? = nil, leading: Leading = .none, action: (() -> Void)? = nil, @ViewBuilder trailing: () -> Trailing) {
        self.title = title
        self.subtitle = subtitle
        self.leading = leading
        self.trailing = trailing()
        self.action = action
    }

    public var body: some View {
        if let action {
            Button(action: action) { content }.buttonStyle(.plain)
        } else {
            content
        }
    }

    private var content: some View {
        HStack(spacing: ModeSpacing.m) {
            leadingView
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.body)
                if let subtitle { Text(subtitle).modeText(.footnote, color: ModeColor.textSecondary) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .padding(.vertical, ModeSpacing.s)
        .frame(minHeight: ModeSize.rowMin)
        .contentShape(.rect)
    }

    @ViewBuilder
    private var leadingView: some View {
        switch leading {
        case .none: EmptyView()
        case let .icon(icon, color):
            icon.image
                .font(ModeTextStyle.callout.font)
                .foregroundStyle(color)
                .frame(width: ModeSize.badge, height: ModeSize.badge)
                .background(ModeColor.tintSoft, in: .circle)
                .accessibilityHidden(true)
        case let .initial(letter):
            Text(letter)
                .modeText(.bodyEmphasis, color: ModeColor.tint)
                .frame(width: ModeSize.avatar, height: ModeSize.avatar)
                .background(ModeColor.tintSoft, in: .circle)
                .accessibilityHidden(true)
        }
    }
}

public extension ModeRow where Trailing == ModeChevron {
    /// A row that opens something, with a chevron.
    init(_ title: String, subtitle: String? = nil, leading: Leading = .none, action: @escaping () -> Void) {
        self.init(title, subtitle: subtitle, leading: leading, action: action) { ModeChevron() }
    }
}

public extension ModeRow where Trailing == EmptyView {
    init(_ title: String, subtitle: String? = nil, leading: Leading = .none) {
        self.init(title, subtitle: subtitle, leading: leading, action: nil) { EmptyView() }
    }
}

/// The small arrow at the end of a row that opens something.
public struct ModeChevron: View {
    public init() {}

    public var body: some View {
        ModeIcon.chevron.image
            .font(ModeTextStyle.footnote.font.weight(.semibold))
            .foregroundStyle(ModeColor.textSecondary)
            .accessibilityHidden(true)
    }
}
