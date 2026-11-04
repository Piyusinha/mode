import SwiftUI

/// One AI provider, as on the "AI providers" and "Connect your AI" boards: its tile; its name with a tag
/// ("In use", "Recommended"); a status line with a dot (green when connected); and on the right either the
/// selection (a radio, filled when picked) or a filled "Connect" button. Selection is a state, Connect an
/// action: they share the accent but never the shape. Setup puts the radio first, Settings last.
public struct ModeProviderRow: View {
    public enum RadioPlacement: Sendable { case leading, trailing }

    private let title: String
    private let status: String
    private let logo: ModeLogo
    private let tileBackground: Color
    private let tileForeground: Color
    private let tag: String?
    /// The tag's colours: Mo's soft orange unless given ("In use" is green).
    private let tagColors: (background: Color, foreground: Color)?
    private let selected: Bool
    private let connected: Bool
    private let connectTitle: String
    private let radio: RadioPlacement
    private let onSelect: () -> Void
    private let onConnect: () -> Void
    /// Settings: the row opens a connected provider's page (the radio still picks it).
    private let onOpen: (() -> Void)?

    public init(title: String, status: String, logo: ModeLogo, tileBackground: Color, tileForeground: Color, tag: String? = nil,
                tagColors: (background: Color, foreground: Color)? = nil, selected: Bool, connected: Bool, connectTitle: String, radio: RadioPlacement = .trailing,
                onSelect: @escaping () -> Void, onConnect: @escaping () -> Void, onOpen: (() -> Void)? = nil) {
        self.onOpen = onOpen
        self.title = title
        self.status = status
        self.logo = logo
        self.tileBackground = tileBackground
        self.tileForeground = tileForeground
        self.tag = tag
        self.tagColors = tagColors
        self.selected = selected
        self.connected = connected
        self.connectTitle = connectTitle
        self.radio = radio
        self.onSelect = onSelect
        self.onConnect = onConnect
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            Button(action: connected ? (onOpen ?? onSelect) : (radio == .leading ? onSelect : onConnect)) {
                HStack(spacing: ModeSpacing.m) {
                    if radio == .leading { ModeRadio(selected: selected) }
                    ModeLeadingView(.logoTile(logo, background: tileBackground, foreground: tileForeground))
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
                            Text(title).modeText(.calloutEmphasis).lineLimit(1).fixedSize()
                            if let tag {
                                ModeTag(tag, background: tagColors?.background ?? ModeColor.tintSoft, foreground: tagColors?.foreground ?? ModeColor.tint)
                                    .fixedSize()
                            }
                        }
                        ModeStatusLine(status, dot: connected ? ModeColor.success : ModeColor.ring).lineLimit(2)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(selected ? [.isSelected] : [])
            if !connected {
                Button(action: onConnect) {
                    Text(connectTitle).modeText(.subheadlineEmphasis, color: ModeColor.onAccent)
                        .lineLimit(1)
                        .padding(.horizontal, ModeSpacing.headerGap)
                        .frame(minHeight: ModeSize.pillButton)
                        .background(ModeColor.accent, in: .capsule)
                        .frame(minHeight: ModeSize.touch)
                        .contentShape(.rect)
                }
                .buttonStyle(ModePressStyle())
            } else if radio == .trailing {
                Button(action: onSelect) {
                    ModeRadio(selected: selected).frame(minWidth: ModeSize.touch, minHeight: ModeSize.touch, alignment: .trailing).contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, ModeSpacing.m)
        .frame(minHeight: ModeSize.tileRow)
    }
}
