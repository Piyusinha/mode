import SwiftUI

/// One AI provider in a list (setup's Connect step, the AI screen): its tile, name and one short line;
/// on the right a check for the one in use, or "Connect" when it is not connected yet. The whole row
/// is one tap: pick it, or connect it.
public struct ModeProviderRow: View {
    private let title: String
    private let subtitle: String
    private let icon: ModeIcon
    private let tileBackground: Color
    private let tileForeground: Color
    private let inUse: Bool
    private let connected: Bool
    private let connectTitle: String
    private let action: () -> Void

    public init(title: String, subtitle: String, icon: ModeIcon, tileBackground: Color, tileForeground: Color,
                inUse: Bool, connected: Bool, connectTitle: String, action: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.icon = icon
        self.tileBackground = tileBackground
        self.tileForeground = tileForeground
        self.inUse = inUse
        self.connected = connected
        self.connectTitle = connectTitle
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.m) {
                ModeLeadingView(.iconTile(icon, background: tileBackground, foreground: tileForeground))
                VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                    Text(title).modeText(.body).lineLimit(1)
                    Text(subtitle).modeText(.footnote, color: ModeColor.textSecondary).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                if !connected {
                    Text(connectTitle).modeText(.bodyEmphasis, color: ModeColor.tint)
                } else if inUse {
                    ModeIcon.checkmark.image.font(ModeTextStyle.bodyEmphasis.font).foregroundStyle(ModeColor.tint)
                }
            }
            .padding(.vertical, ModeSpacing.s)
            .frame(minHeight: ModeSize.rowMin)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(inUse && connected ? [.isSelected] : [])
    }
}
