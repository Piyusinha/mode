import SwiftUI

/// One AI provider in a list (setup's Connect step, Settings' providers sheet): the selection mark,
/// its tile, name (and a tag such as "Recommended" or "In use"), one line about it, and on the right
/// either "Connected" or a Connect button.
public struct ModeProviderRow: View {
    private let title: String
    private let subtitle: String
    private let icon: ModeIcon
    private let tileBackground: Color
    private let tileForeground: Color
    private let tag: String?
    private let selected: Bool
    private let connected: Bool
    private let connectedTitle: String
    private let connectTitle: String
    private let onSelect: () -> Void
    private let onConnect: () -> Void

    public init(title: String, subtitle: String, icon: ModeIcon, tileBackground: Color, tileForeground: Color, tag: String? = nil,
                selected: Bool, connected: Bool, connectedTitle: String, connectTitle: String,
                onSelect: @escaping () -> Void, onConnect: @escaping () -> Void) {
        self.title = title
        self.subtitle = subtitle
        self.icon = icon
        self.tileBackground = tileBackground
        self.tileForeground = tileForeground
        self.tag = tag
        self.selected = selected
        self.connected = connected
        self.connectedTitle = connectedTitle
        self.connectTitle = connectTitle
        self.onSelect = onSelect
        self.onConnect = onConnect
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            Button(action: onSelect) {
                HStack(spacing: ModeSpacing.m) {
                    ModeRadio(selected: selected)
                    ModeLeadingView(.iconTile(icon, background: tileBackground, foreground: tileForeground))
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        // The tag beside the name when it fits, under it when not (never a name broken in two).
                        ViewThatFits(in: .horizontal) {
                            HStack(spacing: ModeSpacing.s) { name; tagView }
                            VStack(alignment: .leading, spacing: ModeSpacing.xxs) { name; tagView }
                        }
                        Text(subtitle).modeText(.subheadline, color: ModeColor.textSecondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(selected ? [.isSelected] : [])
            if connected {
                Label { Text(connectedTitle) } icon: { Circle().fill(ModeColor.success).frame(width: ModeSize.stepDot, height: ModeSize.stepDot) }
                    .labelStyle(.titleAndIcon)
                    .modeText(.captionEmphasis, color: ModeColor.success)
            } else {
                Button(action: onConnect) {
                    Text(connectTitle).modeText(.subheadline, color: selected ? ModeColor.onAccent : ModeColor.textPrimary)
                        .fontWeight(.semibold)
                        .padding(.horizontal, ModeSpacing.m)
                        .frame(minHeight: ModeSize.touch)
                }
                .buttonStyle(.plain)
                .background(selected ? ModeColor.accent : ModeColor.fill, in: .capsule)
            }
        }
        .padding(.vertical, ModeSpacing.m)
    }

    private var name: some View { Text(title).modeText(.headline).lineLimit(1).fixedSize() }

    @ViewBuilder private var tagView: some View {
        if let tag { ModeTag(tag, background: ModeColor.tintSoft, foreground: ModeColor.tint).fixedSize() }
    }
}
