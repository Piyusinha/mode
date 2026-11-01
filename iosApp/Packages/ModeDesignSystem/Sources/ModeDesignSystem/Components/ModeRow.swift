import SwiftUI

/// One row in a card: something on the left, a title and a line under it, something on the right.
/// What sits at the start of a row.
public enum ModeRowLeading: Equatable, Sendable {
    case none
    case icon(ModeIcon, Color = ModeColor.tint)
    /// A round badge with a letter, for people.
    case initial(String)
    /// A numbered step (Welcome).
    case number(Int)
    /// A green check: done or working.
    case success
    /// A red cross: failed.
    case failure
    /// An empty circle: not checked yet.
    case pending
    /// A spinner: happening now.
    case progress
    /// A rounded square with a letter, in a colour (model makers).
    case letterTile(String, background: Color, foreground: Color)
    /// A rounded square with an icon, in a colour (permissions, waiting tasks).
    case iconTile(ModeIcon, background: Color, foreground: Color)
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
        case let .number(n):
            Text(n, format: .number)
                .modeText(.bodyEmphasis, color: ModeColor.tint)
                .frame(width: ModeSize.badge, height: ModeSize.badge)
                .background(ModeColor.tintSoft, in: .circle)
                .accessibilityHidden(true)
        case .success:
            ModeIcon.done.image.font(ModeTextStyle.title2.font).foregroundStyle(ModeColor.success).accessibilityHidden(true)
        case .failure:
            ModeIcon.failed.image.font(ModeTextStyle.title2.font).foregroundStyle(ModeColor.danger).accessibilityHidden(true)
        case .pending:
            ModeIcon.emptyCircle.image.font(ModeTextStyle.title2.font).foregroundStyle(ModeColor.separator).accessibilityHidden(true)
        case .progress:
            ProgressView().tint(ModeColor.tint).frame(width: ModeSize.badge, height: ModeSize.badge)
        case let .letterTile(letter, background, foreground):
            Text(letter)
                .modeText(.bodyEmphasis, color: foreground)
                .frame(width: ModeSize.tile, height: ModeSize.tile)
                .background(background, in: .rect(cornerRadius: ModeSize.tileRadius))
                .accessibilityHidden(true)
        case let .iconTile(icon, background, foreground):
            icon.image
                .font(ModeTextStyle.callout.font)
                .foregroundStyle(foreground)
                .frame(width: ModeSize.tile, height: ModeSize.tile)
                .background(background, in: .rect(cornerRadius: ModeSize.tileRadius))
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
