import SwiftUI

/// The parts Health & repair checks, as their icons.
public enum ModeHealthIcon: String, Sendable {
    case paired = "iphone"
    case vpn = "shield"
    case reach = "arrow.right"
    case screen = "eye"
    case ai = "sparkle"
    case actions = "hand.tap"
    case microphone = "mic"
    case listening = "ear"
    case speaking = "speaker.wave.2"
    case notifications = "bell"

    var image: Image { Image(systemName: rawValue) }
}

/// One part of Mode and how it is (Health & repair, What went wrong): an icon in a rounded square, its name, what
/// it is now, and a green check, a spinner, or the fix. A problem turns the icon and its line orange.
public struct ModeHealthRow: View {
    public enum Status: Sendable { case checking, ok, warning, repaired, busy, info }
    /// The fix's button: ink (Mode repairs it) or blue (it opens a page for you).
    public enum ActionStyle: Sendable { case repair, open }

    private let title: String
    private let detail: String
    private let icon: Image
    private let status: Status
    private let actionTitle: LocalizedStringResource?
    private let actionStyle: ActionStyle
    private let action: (() -> Void)?

    public init(_ title: String, detail: String, icon: ModeIcon, status: Status,
                actionTitle: LocalizedStringResource? = nil, action: (() -> Void)? = nil) {
        self.init(title, detail: detail, image: icon.image, status: status, actionTitle: actionTitle, actionStyle: .repair, action: action)
    }

    public init(_ title: String, detail: String, icon: ModeHealthIcon, status: Status,
                actionTitle: LocalizedStringResource? = nil, actionStyle: ActionStyle = .repair, action: (() -> Void)? = nil) {
        self.init(title, detail: detail, image: icon.image, status: status, actionTitle: actionTitle, actionStyle: actionStyle, action: action)
    }

    private init(_ title: String, detail: String, image: Image, status: Status,
                 actionTitle: LocalizedStringResource?, actionStyle: ActionStyle, action: (() -> Void)?) {
        self.title = title
        self.detail = detail
        self.icon = image
        self.status = status
        self.actionTitle = actionTitle
        self.actionStyle = actionStyle
        self.action = action
    }

    private var warning: Bool { status == .warning }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            icon.font(ModeIconFont.small.weight(.regular))
                .foregroundStyle(warning ? ModeColor.tileOrangeText : ModeColor.textPrimary)
                .frame(width: ModeSize.healthTile, height: ModeSize.healthTile)
                .background(warning ? ModeColor.tileOrange : ModeColor.cardRaised, in: .rect(cornerRadius: ModeSize.healthTileRadius))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.callout)
                if !detail.isEmpty {
                    HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.xs) {
                        if warning { ModeIcon.warning.image.font(ModeTextStyle.caption.font).foregroundStyle(ModeColor.tileOrangeText).accessibilityHidden(true) }
                        Text(detail).modeText(.footnote, color: detailColor).fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .padding(.vertical, ModeSpacing.s)
        .frame(minHeight: ModeSize.healthRow)
        .accessibilityElement(children: .combine)
    }

    private var detailColor: Color {
        switch status {
        case .warning: ModeColor.tileOrangeText
        case .repaired: ModeColor.success
        default: ModeColor.textSecondary
        }
    }

    @ViewBuilder private var trailing: some View {
        switch status {
        case .checking: ProgressView()
        case .ok, .repaired:
            ModeIcon.checkmark.image.font(ModeIconFont.chevron.weight(.medium))
                .imageScale(.small)
                .foregroundStyle(ModeColor.success)
                .frame(width: ModeSize.healthCheck, height: ModeSize.healthCheck)
                .background(ModeColor.tagGreen, in: .circle)
        case .busy: ModeIcon.clock.image.font(ModeIconFont.small).foregroundStyle(ModeColor.textSecondary)
        case .info: EmptyView()
        case .warning:
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle).modeText(.footnoteEmphasis, color: actionStyle == .open ? ModeColor.onInfo : ModeColor.onAccent)
                        .lineLimit(1)
                        .padding(.horizontal, ModeSpacing.m)
                        .frame(minHeight: ModeSize.healthAction)
                        .background(actionStyle == .open ? ModeColor.info : ModeColor.accent, in: .capsule)
                        .frame(minHeight: ModeSize.touch)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// The top of Health & repair: Mo and everything works, or how many things need attention and a Repair button.
public struct ModeHealthBanner: View {
    private let ok: Bool
    private let busy: Bool
    private let title: String
    private let detail: String
    private let actionTitle: LocalizedStringResource?
    private let action: (() -> Void)?

    public init(ok: Bool, busy: Bool = false, title: String, detail: String, actionTitle: LocalizedStringResource? = nil, action: (() -> Void)? = nil) {
        self.ok = ok
        self.busy = busy
        self.title = title
        self.detail = detail
        self.actionTitle = actionTitle
        self.action = action
    }

    private var mood: ModeMo.Mood { busy ? .working : ok ? .done : .stuck }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeMo(mood, size: ModeSize.moBanner)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.calloutEmphasis, color: busy ? ModeColor.textPrimary : ok ? ModeColor.success : ModeColor.tileOrangeText)
                    .fontWeight(.bold)
                Text(detail).modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle).modeText(.subheadlineEmphasis, color: ModeColor.onAccent).fontWeight(.bold)
                        .padding(.horizontal, ModeSpacing.l)
                        .frame(minHeight: ModeSize.bannerAction)
                        .background(ModeColor.accent, in: .capsule)
                        .frame(minHeight: ModeSize.touch)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, ModeSpacing.headerGap)
        .padding(.leading, ModeSpacing.l)
        .padding(.trailing, ModeSpacing.headerGap)
        .background(busy ? ModeColor.cardRaised : ok ? ModeColor.tagGreen : ModeColor.tileOrange, in: .rect(cornerRadius: ModeRadius.banner))
    }
}
