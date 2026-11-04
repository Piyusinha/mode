import SwiftUI

/// One row in a card: something on the left, a title and a line under it, something on the right.
/// What sits at the start of a row.
public enum ModeRowLeading: Equatable, Sendable {
    case none
    case icon(ModeIcon, Color = ModeColor.textPrimary)
    /// Mo, in a mood (a recent task, How Mode acts).
    case mo(ModeMo.Mood)
    /// A round badge with a letter, for people.
    case initial(String)
    /// A numbered step (Welcome).
    case number(Int)
    /// A green check in a filled circle: done or working (Welcome).
    case success
    /// A plain green tick (lists of what happened).
    case check
    /// A plain icon, no background (a clock on a waiting task).
    case symbol(ModeIcon, Color)
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
    /// A rounded square with a provider's or maker's logo, in a colour (AI providers, models).
    case logoTile(ModeLogo, background: Color, foreground: Color)
}

/// Plain rows (lists) or prominent ones (setup, choices): a semibold title and a larger second line.
public enum ModeRowEmphasis: Sendable { case standard, prominent }

public struct ModeRow<Trailing: View>: View {
    public typealias Leading = ModeRowLeading
    public typealias Emphasis = ModeRowEmphasis

    private let title: String
    private let subtitle: String?
    private let leading: Leading
    private let trailing: Trailing
    private let action: (() -> Void)?
    private let emphasis: Emphasis

    public init(_ title: String, subtitle: String? = nil, leading: Leading = .none, emphasis: Emphasis = .standard, action: (() -> Void)? = nil,
                @ViewBuilder trailing: () -> Trailing) {
        self.title = title
        self.subtitle = subtitle
        self.leading = leading
        self.trailing = trailing()
        self.action = action
        self.emphasis = emphasis
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
                Text(title).modeText(emphasis == .prominent ? .headline : .callout)
                if let subtitle { Text(subtitle).modeText(.footnote, color: ModeColor.textSecondary) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .padding(.vertical, ModeSpacing.s)
        .frame(minHeight: ModeSize.rowMin)
        .contentShape(.rect)
    }

    private var leadingView: some View {
        ModeLeadingView(leading)
    }
}

/// The start of a row on its own, for rows built from other pieces.
public struct ModeLeadingView: View {
    private let leading: ModeRowLeading

    public init(_ leading: ModeRowLeading) {
        self.leading = leading
    }

    public var body: some View {
        switch leading {
        case .none: EmptyView()
        case let .icon(icon, color):
            icon.image
                .font(ModeIconFont.small)
                .foregroundStyle(color)
                .frame(width: ModeSize.badge, height: ModeSize.badge)
                .background(ModeColor.cardRaised, in: .circle)
                .accessibilityHidden(true)
        case let .mo(mood):
            ModeMo(mood, size: ModeSize.moRow)
        case let .initial(letter):
            Text(letter)
                .modeText(.title3)
                .frame(width: ModeSize.avatar, height: ModeSize.avatar)
                .background(ModeColor.cardRaised, in: .circle)
                .accessibilityHidden(true)
        case let .number(n):
            Text(n, format: .number)
                .modeText(.calloutEmphasis)
                .frame(width: ModeSize.badge, height: ModeSize.badge)
                .background(ModeColor.cardRaised, in: .circle)
                .accessibilityHidden(true)
        case .success:
            ModeIcon.done.image.font(ModeIconFont.large).foregroundStyle(ModeColor.success).accessibilityHidden(true)
        case let .symbol(icon, color):
            icon.image.font(ModeIconFont.large).foregroundStyle(color).frame(width: ModeSize.badge).accessibilityHidden(true)
        case .check:
            ModeIcon.checkmark.image.font(ModeIconFont.small.weight(.bold)).foregroundStyle(ModeColor.success).frame(width: ModeSize.badge).accessibilityHidden(true)
        case .failure:
            ModeIcon.failed.image.font(ModeIconFont.large).foregroundStyle(ModeColor.danger).accessibilityHidden(true)
        case .pending:
            ModeIcon.emptyCircle.image.font(ModeIconFont.large).foregroundStyle(ModeColor.ring).accessibilityHidden(true)
        case .progress:
            ProgressView().tint(ModeColor.brand).frame(width: ModeSize.badge, height: ModeSize.badge)
        case let .letterTile(letter, background, foreground):
            Text(letter)
                .modeText(.calloutEmphasis, color: foreground)
                .frame(width: ModeSize.tile, height: ModeSize.tile)
                .background(background, in: .rect(cornerRadius: ModeSize.tileRadius))
                .accessibilityHidden(true)
        case let .iconTile(icon, background, foreground):
            icon.image
                .font(ModeIconFont.regular)
                .foregroundStyle(foreground)
                .frame(width: ModeSize.tile, height: ModeSize.tile)
                .background(background, in: .rect(cornerRadius: ModeSize.tileRadius))
                .accessibilityHidden(true)
        case let .logoTile(logo, background, foreground):
            logo.image
                .resizable()
                .scaledToFit()
                .frame(width: ModeSize.icon + ModeSpacing.xs, height: ModeSize.icon + ModeSpacing.xs)
                .foregroundStyle(foreground)
                .frame(width: ModeSize.tile, height: ModeSize.tile)
                .background(background, in: .rect(cornerRadius: ModeSize.tileRadius))
                .accessibilityHidden(true)
        }
    }
}

public extension ModeRow where Trailing == ModeChevron {
    /// A row that opens something, with a chevron. (A labelled argument, so a trailing
    /// view is never mistaken for the tap action.)
    init(_ title: String, subtitle: String? = nil, leading: Leading = .none, emphasis: Emphasis = .standard, onTap: @escaping () -> Void) {
        self.init(title, subtitle: subtitle, leading: leading, emphasis: emphasis, action: onTap) { ModeChevron() }
    }
}

public extension ModeRow where Trailing == EmptyView {
    init(_ title: String, subtitle: String? = nil, leading: Leading = .none, emphasis: Emphasis = .standard) {
        self.init(title, subtitle: subtitle, leading: leading, emphasis: emphasis, action: nil) { EmptyView() }
    }
}

/// The small arrow at the end of a row that opens something.
public struct ModeChevron: View {
    public init() {}

    public var body: some View {
        ModeIcon.chevron.image
            .font(ModeIconFont.chevron)
            .foregroundStyle(ModeColor.textSecondary)
            .accessibilityHidden(true)
    }
}
