import SwiftUI

/// Mode's buttons, in Liquid Glass.
public struct ModeButton: View {
    public enum Role: Sendable {
        /// The one main action on a screen: tinted glass.
        case primary
        /// Other actions: clear glass.
        case secondary
        /// A text-only action.
        case plain
        /// Deleting or forgetting.
        case destructive
    }

    private let title: LocalizedStringResource
    private let icon: ModeIcon?
    private let role: Role
    private let fullWidth: Bool
    private let action: () -> Void

    public init(_ title: LocalizedStringResource, icon: ModeIcon? = nil, role: Role = .primary, fullWidth: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.role = role
        self.fullWidth = fullWidth
        self.action = action
    }

    public var body: some View {
        styled(Button(action: action) { label })
    }

    private var label: some View {
        HStack(spacing: ModeSpacing.s) {
            if let icon { icon.image }
            Text(title)
        }
        .font(ModeTextStyle.bodyEmphasis.font)
        .frame(maxWidth: fullWidth ? .infinity : nil, minHeight: ModeSize.touch)
        .padding(.horizontal, ModeSpacing.s)
    }

    @ViewBuilder
    private func styled(_ button: Button<some View>) -> some View {
        switch role {
        case .primary: button.buttonStyle(.glassProminent).tint(ModeColor.accent)
        case .secondary: button.buttonStyle(.glass).foregroundStyle(ModeColor.textPrimary)
        case .plain: button.buttonStyle(.plain).foregroundStyle(ModeColor.tint)
        case .destructive: button.buttonStyle(.plain).foregroundStyle(ModeColor.danger)
        }
    }
}

/// A round glass button with only an icon; [label] is read by VoiceOver.
public struct ModeIconButton: View {
    private let icon: ModeIcon
    private let label: LocalizedStringResource
    private let prominent: Bool
    private let action: () -> Void

    public init(_ icon: ModeIcon, label: LocalizedStringResource, prominent: Bool = false, action: @escaping () -> Void) {
        self.icon = icon
        self.label = label
        self.prominent = prominent
        self.action = action
    }

    public var body: some View {
        let button = Button(action: action) {
            icon.image
                .font(ModeTextStyle.bodyEmphasis.font)
                .frame(width: ModeSize.touch, height: ModeSize.touch)
        }
        .buttonBorderShape(.circle)
        .accessibilityLabel(Text(label))
        if prominent {
            button.buttonStyle(.glassProminent).tint(ModeColor.accent)
        } else {
            button.buttonStyle(.glass).foregroundStyle(ModeColor.textPrimary)
        }
    }
}
