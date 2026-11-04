import SwiftUI

/// One part of Mode and how it is (Health & repair, What went wrong): an icon, its name, what it is now, and a
/// check, a spinner, or the fix.
public struct ModeHealthRow: View {
    public enum Status: Sendable { case checking, ok, warning, repaired, busy, info }

    private let title: String
    private let detail: String
    private let icon: ModeIcon
    private let status: Status
    private let actionTitle: LocalizedStringResource?
    private let action: (() -> Void)?

    public init(_ title: String, detail: String, icon: ModeIcon, status: Status,
                actionTitle: LocalizedStringResource? = nil, action: (() -> Void)? = nil) {
        self.title = title
        self.detail = detail
        self.icon = icon
        self.status = status
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            icon.image.modeText(.callout, color: status == .warning ? ModeColor.tileOrangeText : ModeColor.tint)
                .frame(width: ModeSize.badge, height: ModeSize.badge)
                .background(status == .warning ? ModeColor.tileOrange : ModeColor.tintSoft, in: .rect(cornerRadius: ModeSize.tileRadius))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.body)
                if !detail.isEmpty {
                    HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.xs) {
                        if status == .warning { ModeIcon.warning.image.modeText(.caption, color: ModeColor.tileOrangeText).accessibilityHidden(true) }
                        Text(detail).modeText(.footnote, color: detailColor).fixedSize(horizontal: false, vertical: true)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .padding(.vertical, ModeSpacing.s)
        .frame(minHeight: ModeSize.rowMin)
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
            ModeIcon.checkmark.image.modeText(.caption, color: ModeColor.success)
                .frame(width: ModeSize.badge * 0.7, height: ModeSize.badge * 0.7)
                .background(ModeColor.tagGreen, in: .circle)
        case .busy: ModeIcon.clock.image.modeText(.callout, color: ModeColor.textSecondary)
        case .info: EmptyView()
        case .warning:
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle).modeText(.footnote, color: ModeColor.onAccent).fontWeight(.semibold)
                        .padding(.horizontal, ModeSpacing.m).frame(minHeight: ModeSize.badge)
                        .background(ModeColor.accent, in: .capsule)
                        .frame(minHeight: ModeSize.touch).contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
        }
    }
}

/// The top of Health & repair: everything works, or how many things need attention and a Repair button.
public struct ModeHealthBanner: View {
    private let ok: Bool
    private let title: String
    private let detail: String
    private let actionTitle: LocalizedStringResource?
    private let action: (() -> Void)?

    public init(ok: Bool, title: String, detail: String, actionTitle: LocalizedStringResource? = nil, action: (() -> Void)? = nil) {
        self.ok = ok
        self.title = title
        self.detail = detail
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            (ok ? ModeIcon.done : ModeIcon.warning).image.modeText(.title2, color: ok ? ModeColor.success : ModeColor.tileOrangeText)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.bodyEmphasis, color: ok ? ModeColor.success : ModeColor.tileOrangeText)
                Text(detail).modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle).modeText(.callout, color: ModeColor.onAccent).fontWeight(.bold)
                        .padding(.horizontal, ModeSpacing.l).frame(minHeight: ModeSize.touch - ModeSpacing.xs)
                        .background(ModeColor.accent, in: .capsule)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(ModeSpacing.m)
        .background(ok ? ModeColor.tagGreen : ModeColor.tileOrange, in: .rect(cornerRadius: ModeRadius.field))
    }
}
