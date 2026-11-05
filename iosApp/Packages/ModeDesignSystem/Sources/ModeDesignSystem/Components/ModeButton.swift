import SwiftUI

/// Mode's buttons, as on the Mo theme canvas: solid capsules, ink for the one main action.
public struct ModeButton: View {
    public enum Role: Sendable {
        /// The one main action on a screen: an ink capsule, 56 tall when full width.
        case primary
        /// Other actions: a raised-grey capsule.
        case secondary
        /// A text-only action.
        case plain
        /// Deleting or forgetting.
        case destructive
        /// A small pill inside a row or a card (Connect, Change, Undo): raised grey.
        case pill
        /// A small ink pill (the chosen provider's Connect).
        case pillProminent
        // Mode v2: 52-point capsules, as wide as they're given (the loop sheet, guides, first run).
        /// The main action: ink.
        case main
        /// Another action on a card: raised grey.
        case other
        /// Another action straight on the page: white.
        case otherOnPage
        /// Stop: raised grey with red words.
        case stop
        /// An answer Mode is waiting for: blue, used nowhere else.
        case answer
        /// Another answer beside it: white with blue words.
        case answerQuiet
        /// Stop on a soft red, under a blue answer.
        case stopSoft
    }

    private let title: LocalizedStringResource
    private let icon: ModeIcon?
    private let role: Role
    private let fullWidth: Bool
    private let action: () -> Void
    @Environment(\.isEnabled) private var isEnabled

    public init(_ title: LocalizedStringResource, icon: ModeIcon? = nil, role: Role = .primary, fullWidth: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.role = role
        self.fullWidth = fullWidth
        self.action = action
    }

    public var body: some View {
        Button(action: action) { label }
            .buttonStyle(ModePressStyle())
            .opacity(isEnabled ? 1 : 0.45)
    }

    private var isPill: Bool { role == .pill || role == .pillProminent }
    private var isLarge: Bool { [.main, .other, .otherOnPage, .stop, .answer, .answerQuiet, .stopSoft].contains(role) }

    private var height: CGFloat {
        switch role {
        case .primary: fullWidth ? ModeSize.primaryButton : ModeSize.secondaryButton
        case .secondary: ModeSize.secondaryButton
        case .pill, .pillProminent: ModeSize.pillButton
        case .plain, .destructive: ModeSize.touch
        case .main, .other, .otherOnPage, .stop, .answer, .answerQuiet, .stopSoft: ModeSize.loopButton
        }
    }

    private var foreground: Color {
        switch role {
        case .primary, .pillProminent: ModeColor.onAccent
        case .secondary, .pill: ModeColor.textPrimary
        case .plain: ModeColor.textPrimary
        case .destructive, .stop, .stopSoft: ModeColor.danger
        case .main: ModeColor.onAccent
        case .other, .otherOnPage: ModeColor.textPrimary
        case .answer: ModeColor.onAsk
        case .answerQuiet: ModeColor.infoText
        }
    }

    private var background: Color {
        switch role {
        case .primary, .pillProminent: ModeColor.accent
        case .secondary, .pill: ModeColor.cardRaised
        case .plain, .destructive: .clear
        case .main: ModeColor.accent
        case .other, .stop: ModeColor.cardRaised
        case .otherOnPage, .answerQuiet: ModeColor.card
        case .answer: ModeColor.info
        case .stopSoft: ModeColor.tileRed
        }
    }

    private var label: some View {
        HStack(spacing: ModeSpacing.s) {
            if let icon { icon.image.font(isPill ? ModeIconFont.small : ModeIconFont.regular) }
            Text(title)
        }
        .modeText(isPill ? .subheadlineEmphasis : (role == .primary && fullWidth ? .headline : .calloutEmphasis), color: foreground)
        .lineLimit(1)
        .minimumScaleFactor(0.85)
        .padding(.horizontal, isPill ? ModeSpacing.m + ModeSpacing.xxs : ModeSpacing.xl)
        .frame(maxWidth: fullWidth || isLarge ? .infinity : nil, minHeight: height)
        .background(background, in: .capsule)
        .contentShape(.capsule)
    }
}

/// Dims a button a little while it is held.
public struct ModePressStyle: ButtonStyle {
    public init() {}

    public func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.7 : 1)
            .animation(ModeMotion.quick, value: configuration.isPressed)
    }
}

/// An icon button for the navigation bar, which already draws the glass around it.
public struct ModeToolbarButton: View {
    private let icon: ModeIcon
    private let label: LocalizedStringResource
    private let action: () -> Void

    public init(_ icon: ModeIcon, label: LocalizedStringResource, action: @escaping () -> Void) {
        self.icon = icon
        self.label = label
        self.action = action
    }

    public var body: some View {
        Button(action: action) { icon.image.foregroundStyle(ModeColor.textPrimary) }
            .accessibilityLabel(Text(label))
    }
}

/// A round button with only an icon; [label] is read by VoiceOver. A white card with a soft
/// lift, or ink when prominent (Speak, Send).
public struct ModeIconButton: View {
    private let icon: ModeIcon
    private let label: LocalizedStringResource
    private let prominent: Bool
    private let size: CGFloat
    private let action: () -> Void

    public init(_ icon: ModeIcon, label: LocalizedStringResource, prominent: Bool = false, size: CGFloat = ModeSize.touch, action: @escaping () -> Void) {
        self.icon = icon
        self.label = label
        self.prominent = prominent
        self.size = size
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            icon.image
                .font(size >= ModeSize.speakButton ? ModeIconFont.hero : ModeIconFont.regular)
                .foregroundStyle(prominent ? ModeColor.onAccent : ModeColor.textPrimary)
                .frame(width: size, height: size)
                .background(prominent ? ModeColor.accent : ModeColor.card, in: .circle)
                .modeShadow(in: Circle())
                .contentShape(.circle)
        }
        .buttonStyle(ModePressStyle())
        .accessibilityLabel(Text(label))
    }
}
