import SwiftUI

/// A small glass note with an optional action, such as "Remembered: … · Undo".
public struct ModeChip: View {
    private let icon: ModeIcon?
    private let text: String
    private let actionTitle: LocalizedStringResource?
    private let action: (() -> Void)?

    public init(_ text: String, icon: ModeIcon? = nil, actionTitle: LocalizedStringResource? = nil, action: (() -> Void)? = nil) {
        self.icon = icon
        self.text = text
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            if let icon { icon.image.foregroundStyle(ModeColor.tint).accessibilityHidden(true) }
            Text(text).modeText(.footnote).lineLimit(2)
            if let actionTitle, let action {
                Button(action: action) { Text(actionTitle).modeText(.captionEmphasis, color: ModeColor.tint) }
                    .buttonStyle(.plain)
                    .frame(minHeight: ModeSize.touch)
            }
        }
        .padding(.horizontal, ModeSpacing.m)
        .frame(minHeight: ModeSize.touch)
        .glassEffect(.regular, in: .capsule)
    }
}

/// A coloured label for how something ended: done, could not finish, stopped.
public struct ModeBadge: View {
    public enum Tone: Sendable { case success, neutral, warning, danger }

    private let text: LocalizedStringResource
    private let icon: ModeIcon
    private let tone: Tone

    public init(_ text: LocalizedStringResource, icon: ModeIcon, tone: Tone) {
        self.text = text
        self.icon = icon
        self.tone = tone
    }

    public var body: some View {
        Label { Text(text) } icon: { icon.image }
            .modeText(.captionEmphasis, color: color)
    }

    private var color: Color {
        switch tone {
        case .success: ModeColor.success
        case .neutral: ModeColor.textSecondary
        case .warning: ModeColor.warning
        case .danger: ModeColor.danger
        }
    }
}

/// A filter to switch on or off in a row of them ("All", "Anthropic", "OpenAI").
public struct ModeFilterChip: View {
    private let title: String
    private let selected: Bool
    private let action: () -> Void

    public init(_ title: String, selected: Bool, action: @escaping () -> Void) {
        self.title = title
        self.selected = selected
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(title)
                .modeText(.subheadline, color: selected ? ModeColor.onAccent : ModeColor.textPrimary)
                .fontWeight(.semibold)
                .padding(.horizontal, ModeSpacing.l)
                .frame(minHeight: ModeSize.touch)
        }
        .buttonStyle(.plain)
        .background(selected ? ModeColor.accent : ModeColor.card, in: .capsule)
        .accessibilityAddTraits(selected ? [.isSelected] : [])
    }
}
