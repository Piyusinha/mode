import SwiftUI

/// Setup progress: one dot per step, the current one longer.
public struct ModeStepDots: View {
    private let current: Int
    private let total: Int
    private let label: String

    /// [current] counts from 1; [label] is read by VoiceOver ("Step 1 of 5").
    public init(current: Int, total: Int, label: String) {
        self.current = current
        self.total = total
        self.label = label
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xs) {
            ForEach(1...max(total, 1), id: \.self) { step in
                Capsule()
                    .fill(step <= current ? ModeColor.accent : ModeColor.separator)
                    .frame(width: step == current ? ModeSize.stepDotCurrent : ModeSize.stepDot, height: ModeSize.stepDot)
            }
        }
        .animation(ModeMotion.standard, value: current)
        .accessibilityElement()
        .accessibilityLabel(Text(label))
    }
}

/// The Orbit app mark, as on the Welcome board.
public struct ModeMark: View {
    public init() {}

    public var body: some View {
        Image("ModeMark", bundle: .module)
            .resizable()
            .scaledToFit()
            .frame(width: ModeSize.mark, height: ModeSize.mark)
            .clipShape(.rect(cornerRadius: ModeSize.markRadius))
            .shadow(color: ModeColor.accent.opacity(0.35), radius: ModeSpacing.xl, y: ModeSpacing.s)
            .accessibilityHidden(true)
    }
}

/// A small glass label ("Open source").
public struct ModePill: View {
    private let text: LocalizedStringResource

    public init(_ text: LocalizedStringResource) {
        self.text = text
    }

    public var body: some View {
        Text(text)
            .modeText(.subheadline)
            .fontWeight(.semibold)
            .padding(.horizontal, ModeSpacing.m)
            .padding(.vertical, ModeSpacing.s)
            .glassEffect(.regular, in: .capsule)
    }
}

/// A coloured tag beside a name ("Recommended", "Cheapest").
public struct ModeTag: View {
    private let text: String
    private let background: Color
    private let foreground: Color

    public init(_ text: String, background: Color, foreground: Color) {
        self.text = text
        self.background = background
        self.foreground = foreground
    }

    public var body: some View {
        Text(text)
            .modeText(.captionEmphasis, color: foreground)
            .padding(.horizontal, ModeSpacing.s)
            .padding(.vertical, ModeSpacing.xxs)
            .background(background, in: .capsule)
    }
}

/// The selection mark on a choice row: an empty circle, or a filled check.
public struct ModeRadio: View {
    private let selected: Bool

    public init(selected: Bool) {
        self.selected = selected
    }

    public var body: some View {
        Group {
            if selected {
                ModeIcon.checkmark.image
                    .font(ModeTextStyle.footnote.font.weight(.bold))
                    .foregroundStyle(ModeColor.onAccent)
                    .frame(width: ModeSize.radio, height: ModeSize.radio)
                    .background(ModeColor.accent, in: .circle)
            } else {
                Circle().strokeBorder(ModeColor.separator, lineWidth: ModeSize.separator * 2)
                    .frame(width: ModeSize.radio, height: ModeSize.radio)
            }
        }
        .accessibilityHidden(true)
    }
}

/// A text field in its own card: the label above, the value below, an optional button inside.
public struct ModeFieldCard: View {
    private let label: LocalizedStringResource
    private let prompt: LocalizedStringResource
    @Binding private var text: String
    private let secure: Bool
    private let buttonTitle: LocalizedStringResource?
    private let buttonAction: (() -> Void)?

    public init(_ label: LocalizedStringResource, prompt: LocalizedStringResource, text: Binding<String>, secure: Bool = false,
                buttonTitle: LocalizedStringResource? = nil, buttonAction: (() -> Void)? = nil) {
        self.label = label
        self.prompt = prompt
        self._text = text
        self.secure = secure
        self.buttonTitle = buttonTitle
        self.buttonAction = buttonAction
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(label).modeText(.footnote, color: ModeColor.textSecondary)
                Group {
                    if secure { SecureField(text: $text) { Text(prompt) } } else { TextField(text: $text) { Text(prompt) } }
                }
                .modeText(.title2)
                .textInputAutocapitalization(secure ? .never : .words)
                .autocorrectionDisabled(secure)
            }
            if let buttonTitle, let buttonAction {
                Button(action: buttonAction) {
                    Text(buttonTitle).modeText(.bodyEmphasis).padding(.horizontal, ModeSpacing.l).frame(minHeight: ModeSize.touch)
                }
                .buttonStyle(.plain)
                .background(ModeColor.fill, in: .capsule)
            }
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.vertical, ModeSpacing.m)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
    }
}

/// The contact card the user picked: initials, name, where it came from.
public struct ModeContactCard: View {
    private let initials: String
    private let name: String
    private let subtitle: LocalizedStringResource

    public init(initials: String, name: String, subtitle: LocalizedStringResource) {
        self.initials = initials
        self.name = name
        self.subtitle = subtitle
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.l) {
            Text(initials)
                .modeText(.title2, color: ModeColor.tint)
                .frame(width: ModeSize.contactAvatar, height: ModeSize.contactAvatar)
                .background(ModeColor.tintSoft, in: .circle)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(name).modeText(.headline)
                Text(subtitle).modeText(.subheadline, color: ModeColor.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            ModeRadio(selected: true)
        }
        .padding(ModeSpacing.l)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
        .accessibilityElement(children: .combine)
    }
}

/// A quiet note with an icon ("Your key stays in this iPhone's Keychain").
public struct ModeNote: View {
    private let icon: ModeIcon
    private let text: LocalizedStringResource

    public init(icon: ModeIcon, _ text: LocalizedStringResource) {
        self.icon = icon
        self.text = text
    }

    public var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.m) {
            icon.image.foregroundStyle(ModeColor.textSecondary).accessibilityHidden(true)
            Text(text).modeText(.subheadline, color: ModeColor.textSecondary)
        }
        .padding(.horizontal, ModeSpacing.l)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A note in a card with a tinted icon (the test's Dynamic Island note).
public struct ModeInfoCard: View {
    private let icon: ModeIcon
    private let text: LocalizedStringResource

    public init(icon: ModeIcon, _ text: LocalizedStringResource) {
        self.icon = icon
        self.text = text
    }

    public var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.m) {
            icon.image.foregroundStyle(ModeColor.tint).accessibilityHidden(true)
            Text(text).modeText(.callout)
        }
        .padding(ModeSpacing.l)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
    }
}

/// A line with a word in the middle ("or").
public struct ModeOrDivider: View {
    private let text: LocalizedStringResource

    public init(_ text: LocalizedStringResource) {
        self.text = text
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.l) {
            Rectangle().fill(ModeColor.separator).frame(height: ModeSize.separator)
            Text(text).modeText(.subheadline, color: ModeColor.textSecondary)
            Rectangle().fill(ModeColor.separator).frame(height: ModeSize.separator)
        }
    }
}

/// What sits at the end of a permission row.
public enum ModeStatusAction: Equatable {
    /// A filled accent button ("Allow").
    case prominent(LocalizedStringResource)
    /// A grey button ("Allow" for something optional).
    case quiet(LocalizedStringResource)
    /// Done: a green check and a word ("Allowed").
    case done(LocalizedStringResource)
    /// Just a word ("Later", "Not checked", "0.9 s").
    case note(String)

    public static func == (a: ModeStatusAction, b: ModeStatusAction) -> Bool {
        String(describing: a) == String(describing: b)
    }
}

/// The trailing part of a row: a button, a done mark, or a quiet word.
public struct ModeStatusView: View {
    private let action: ModeStatusAction
    private let onTap: () -> Void

    public init(_ action: ModeStatusAction, onTap: @escaping () -> Void = {}) {
        self.action = action
        self.onTap = onTap
    }

    public var body: some View {
        switch action {
        case let .prominent(title):
            Button(action: onTap) { Text(title).modeText(.bodyEmphasis, color: ModeColor.onAccent).padding(.horizontal, ModeSpacing.l).frame(minHeight: ModeSize.touch) }
                .buttonStyle(.plain)
                .background(ModeColor.accent, in: .capsule)
        case let .quiet(title):
            Button(action: onTap) { Text(title).modeText(.bodyEmphasis).padding(.horizontal, ModeSpacing.l).frame(minHeight: ModeSize.touch) }
                .buttonStyle(.plain)
                .background(ModeColor.fill, in: .capsule)
        case let .done(title):
            Label { Text(title) } icon: { ModeIcon.checkmark.image }
                .modeText(.bodyEmphasis, color: ModeColor.success)
        case let .note(text):
            Text(text).modeText(.subheadline, color: ModeColor.textSecondary)
        }
    }
}

/// The three little phones of the setup test: open Settings → tap Wi-Fi → come back.
public struct ModeTestStrip: View {
    public enum Phase: Sendable { case pending, running, done, failed }

    private let labels: [String]
    private let phases: [Phase]

    /// Three labels and three phases, in order.
    public init(labels: [String], phases: [Phase]) {
        self.labels = labels
        self.phases = phases
    }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.s) {
            ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                VStack(spacing: ModeSpacing.s) {
                    phone(index)
                    Text(label).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                if index < labels.count - 1 {
                    ModeIcon.arrowRight.image.foregroundStyle(ModeColor.textSecondary)
                        .frame(height: ModeSize.testPhoneHeight).accessibilityHidden(true)
                }
            }
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private func phone(_ index: Int) -> some View {
        let phase = index < phases.count ? phases[index] : .pending
        RoundedRectangle(cornerRadius: ModeRadius.field)
            .fill(ModeColor.card)
            .overlay(RoundedRectangle(cornerRadius: ModeRadius.field).strokeBorder(phase == .running ? ModeColor.accent : ModeColor.separator, lineWidth: ModeSize.separator * 2))
            .frame(width: ModeSize.testPhoneWidth, height: ModeSize.testPhoneHeight)
            .overlay {
                switch phase {
                case .done: ModeIcon.checkmark.image.font(ModeTextStyle.title.font.weight(.bold)).foregroundStyle(ModeColor.success)
                case .failed: ModeIcon.close.image.font(ModeTextStyle.title.font.weight(.bold)).foregroundStyle(ModeColor.danger)
                case .running: ProgressView().tint(ModeColor.tint)
                case .pending: content(index)
                }
            }
    }

    @ViewBuilder
    private func content(_ index: Int) -> some View {
        switch index {
        case 0:
            ModeMark().frame(width: ModeSize.tile, height: ModeSize.tile).scaleEffect(ModeSize.tile / ModeSize.mark)
        case 1:
            VStack(spacing: ModeSpacing.xs) {
                ForEach(0..<3, id: \.self) { row in
                    Capsule().fill(row == 0 ? ModeColor.tintSoft : ModeColor.fill).frame(height: ModeSpacing.m)
                }
            }
            .padding(.horizontal, ModeSpacing.s)
            .overlay(alignment: .top) {
                Circle().strokeBorder(ModeColor.accent, lineWidth: ModeSize.separator * 2)
                    .frame(width: ModeSpacing.xxl, height: ModeSpacing.xxl)
                    .offset(y: -ModeSpacing.s)
            }
        default:
            ModeIcon.checkmark.image.font(ModeTextStyle.title.font.weight(.bold)).foregroundStyle(ModeColor.success.opacity(0.5))
        }
    }
}

/// A big round badge at the top of a page: a green check, or a warning.
public struct ModeResultBadge: View {
    private let success: Bool

    public init(success: Bool) {
        self.success = success
    }

    public var body: some View {
        (success ? ModeIcon.checkmark : ModeIcon.warning).image
            .font(ModeTextStyle.title.font.weight(.bold))
            .foregroundStyle(ModeColor.onAccent)
            .frame(width: ModeSize.successBadge, height: ModeSize.successBadge)
            .background(success ? ModeColor.success : ModeColor.warning, in: .circle)
            .accessibilityHidden(true)
    }
}
