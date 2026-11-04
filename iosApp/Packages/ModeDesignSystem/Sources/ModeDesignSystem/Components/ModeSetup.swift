import SwiftUI

/// Setup progress: one dot per step, the current one longer; done and current in Mo's orange.
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
        HStack(spacing: ModeSpacing.s - ModeSpacing.xxs) {
            ForEach(1...max(total, 1), id: \.self) { step in
                Capsule()
                    .fill(step <= current ? ModeColor.brand : ModeColor.track)
                    .frame(width: step == current ? ModeSize.stepDotCurrent : ModeSize.stepDot, height: ModeSize.stepDotHeight)
            }
        }
        .animation(ModeMotion.standard, value: current)
        .accessibilityElement()
        .accessibilityLabel(Text(label))
    }
}

/// The top of a setup page: a white round back button on the left, the step dots in the middle.
public struct ModeSetupHeader: View {
    private let backLabel: LocalizedStringResource
    private let onBack: (() -> Void)?
    private let step: (current: Int, total: Int, label: String)?

    /// No [onBack]: no back button (the first page, the result). No [step]: no dots.
    public init(backLabel: LocalizedStringResource, step: (current: Int, total: Int, label: String)?, onBack: (() -> Void)?) {
        self.backLabel = backLabel
        self.onBack = onBack
        self.step = step
    }

    public var body: some View {
        ZStack {
            if let step { ModeStepDots(current: step.current, total: step.total, label: step.label) }
            HStack {
                if let onBack { ModeIconButton(.back, label: backLabel, action: onBack) }
                Spacer(minLength: 0)
            }
        }
        .frame(minHeight: ModeSize.touch)
        .padding(.horizontal, ModeSpacing.screenEdge)
    }
}

/// The Orbit app mark, as on the Welcome board.
public struct ModeMark: View {
    private let size: CGFloat
    private let alwaysLight: Bool

    /// The app mark; large (Welcome) by default, small for headers like the Live Activity's.
    /// [alwaysLight]: the violet mark even on dark surfaces (the Lock Screen, the Dynamic Island).
    public init(size: CGFloat = ModeSize.mark, alwaysLight: Bool = false) {
        self.size = size
        self.alwaysLight = alwaysLight
    }

    public var body: some View {
        mark.environment(\.colorScheme, alwaysLight ? .light : colorScheme)
    }

    @Environment(\.colorScheme) private var colorScheme

    private var mark: some View {
        // Live Activities can't draw large images: small marks use a 96 px copy.
        Image(size <= ModeSize.smallMarkLimit ? "ModeMarkSmall" : "ModeMark", bundle: .module)
            .resizable()
            .scaledToFit()
            .frame(width: size, height: size)
            .clipShape(.rect(cornerRadius: size * ModeSize.markRadius / ModeSize.mark))
            .shadow(color: ModeColor.accent.opacity(size >= ModeSize.mark ? 0.35 : 0), radius: ModeSpacing.xl, y: ModeSpacing.s)
            .accessibilityHidden(true)
    }
}

/// Mo's speech bubble beside Mo ("Hi, I'm Mo."): a white bubble with a small corner by Mo.
public struct ModeSpeechBubble: View {
    private let text: LocalizedStringResource

    public init(_ text: LocalizedStringResource) {
        self.text = text
    }

    public var body: some View {
        let shape = UnevenRoundedRectangle(topLeadingRadius: ModeRadius.setupBubble, bottomLeadingRadius: ModeRadius.bubbleTail,
                                           bottomTrailingRadius: ModeRadius.setupBubble, topTrailingRadius: ModeRadius.setupBubble)
        Text(text)
            .modeText(.subheadlineEmphasis)
            .padding(.horizontal, ModeSpacing.m + ModeSpacing.xxs)
            .padding(.vertical, ModeSpacing.s)
            .background(ModeColor.card, in: shape)
            .modeShadow(in: shape)
    }
}

/// A small label ("Open source"): a white pill with a soft lift.
public struct ModePill: View {
    private let text: LocalizedStringResource

    public init(_ text: LocalizedStringResource) {
        self.text = text
    }

    public var body: some View {
        Text(text)
            .modeText(.footnoteEmphasis)
            .padding(.horizontal, ModeSpacing.m)
            .frame(minHeight: ModeSize.setupPill)
            .background(ModeColor.card, in: .capsule)
            .modeShadow(in: Capsule())
    }
}

/// A coloured tag beside a name ("Recommended", "Cheapest").
public struct ModeTag: View {
    private let text: String
    private let icon: ModeIcon?
    private let background: Color
    private let foreground: Color
    private let large: Bool

    /// [large]: the 24-point tag on setup's role cards (with an optional [icon], as "Medium").
    public init(_ text: String, icon: ModeIcon? = nil, background: Color, foreground: Color, large: Bool = false) {
        self.text = text
        self.icon = icon
        self.background = background
        self.foreground = foreground
        self.large = large
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xs) {
            if let icon { icon.image.font(ModeIconFont.setupTag) }
            Text(text)
        }
        .modeText(large ? .caption : .captionEmphasis, color: foreground)
        .fontWeight(.bold)
        .padding(.horizontal, large ? ModeSpacing.s + ModeSpacing.xxs : ModeSpacing.s)
        .padding(.vertical, ModeSpacing.xxs)
        .frame(minHeight: large ? ModeSize.setupTagLarge : nil)
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
                    .font(ModeIconFont.chevron.weight(.heavy))
                    .foregroundStyle(ModeColor.onAccent)
                    .frame(width: ModeSize.radio, height: ModeSize.radio)
                    .background(ModeColor.accent, in: .circle)
            } else {
                Circle().strokeBorder(ModeColor.ring, lineWidth: ModeSize.separator * 2)
                    .frame(width: ModeSize.radio, height: ModeSize.radio)
            }
        }
        .accessibilityHidden(true)
    }
}

/// A text field in its own lifted card: the label above, the value below, an optional button inside.
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
                .modeText(.headline)
                .textInputAutocapitalization(secure ? .never : .words)
                .autocorrectionDisabled(secure)
                .frame(minHeight: ModeSize.setupFieldValue)
            }
            if let buttonTitle, let buttonAction {
                Button(action: buttonAction) {
                    Text(buttonTitle).modeText(.calloutEmphasis).padding(.horizontal, ModeSpacing.l).frame(minHeight: ModeSize.touch)
                }
                .buttonStyle(.plain)
                .background(ModeColor.cardRaised, in: .capsule)
            }
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.vertical, ModeSpacing.s + ModeSpacing.xxs)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.field))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.field))
    }
}

/// The contact card the user picked: initials, name, where it came from, and a check.
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
        HStack(spacing: ModeSpacing.m + ModeSpacing.xxs) {
            Text(initials)
                .modeText(.title3)
                .frame(width: ModeSize.contactAvatar, height: ModeSize.contactAvatar)
                .background(ModeColor.cardRaised, in: .circle)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(name).modeText(.headline)
                Text(subtitle).modeText(.footnote, color: ModeColor.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            ModeRadio(selected: true)
        }
        .padding(ModeSpacing.l)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.card))
        .accessibilityElement(children: .combine)
    }
}

/// A quiet note with an icon ("Your key stays in this iPhone's Keychain").
public struct ModeNote: View {
    private let icon: ModeIcon
    private let text: LocalizedStringResource
    private let inset: CGFloat

    /// [inset]: how far the note sits in from the content's edge.
    public init(icon: ModeIcon, _ text: LocalizedStringResource, inset: CGFloat = ModeSpacing.l) {
        self.icon = icon
        self.text = text
        self.inset = inset
    }

    public var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.s) {
            icon.image.font(ModeIconFont.small).foregroundStyle(ModeColor.textSecondary).accessibilityHidden(true)
            Text(text).modeText(.footnote, color: ModeColor.textSecondary)
        }
        .padding(.horizontal, inset)
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
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.card))
    }
}

/// A setup note in a flat card: an icon (the test's note) or Mo with a bold lead-in ("Why these:").
public struct ModeSetupNote: View {
    public enum Mark: Sendable { case icon(ModeIcon), mo }

    private let mark: Mark
    private let lead: String?
    private let text: String

    /// [lead] is set in bold before [text]. An icon note sits on a white card; Mo's on a raised grey one.
    public init(_ mark: Mark, lead: String? = nil, text: String) {
        self.mark = mark
        self.lead = lead
        self.text = text
    }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.m) {
            switch mark {
            case let .icon(icon):
                icon.image.font(ModeIconFont.regular).foregroundStyle(ModeColor.textPrimary).accessibilityHidden(true)
            case .mo:
                ModeMo(.idle, size: ModeSize.setupMoNote)
            }
            label.frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.horizontal, ModeSpacing.l - (isMo ? ModeSpacing.xxs : 0))
        .padding(.vertical, ModeSpacing.m + (isMo ? 0 : ModeSpacing.xxs))
        .background(isMo ? ModeColor.cardRaised : ModeColor.card, in: .rect(cornerRadius: ModeRadius.setupNote))
        .accessibilityElement(children: .combine)
    }

    private var isMo: Bool { if case .mo = mark { true } else { false } }

    @ViewBuilder private var label: some View {
        if let lead {
            (Text(lead).bold() + Text(" ") + Text(text)).modeText(.footnote)
        } else {
            Text(text).modeText(isMo ? .footnote : .subheadline, color: isMo ? ModeColor.textPrimary : ModeColor.textSecondary)
        }
    }
}

/// A small status chip under a title or at the end of a row: a green dot and a short line
/// ("ChatGPT · connected" on a white chip, "Connected" on a green one).
public struct ModeConnectedChip: View {
    private let text: String
    private let green: Bool

    public init(_ text: String, green: Bool = false) {
        self.text = text
        self.green = green
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s - ModeSpacing.xxs) {
            Circle().fill(ModeColor.success).frame(width: ModeSize.setupDot, height: ModeSize.setupDot)
            Text(text).modeText(.footnoteEmphasis, color: green ? ModeColor.success : ModeColor.textPrimary).lineLimit(1)
        }
        .padding(.horizontal, green ? ModeSpacing.s + ModeSpacing.xxs : ModeSpacing.m)
        .frame(minHeight: green ? ModeSize.setupTagLarge + ModeSpacing.xxs : ModeSize.setupChip)
        .background(green ? ModeColor.tagGreen : ModeColor.card, in: .capsule)
        .fixedSize()
        .accessibilityElement(children: .combine)
    }
}

/// One step on Welcome's card: a green check (done), a number, or a warning; its name and a line under it.
public struct ModeWelcomeRow: View {
    public enum Mark: Sendable { case done, number(Int), warning }

    private let title: String
    private let subtitle: String
    private let mark: Mark

    public init(_ title: String, subtitle: String, mark: Mark) {
        self.title = title
        self.subtitle = subtitle
        self.mark = mark
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m + ModeSpacing.xxs) {
            markView.frame(width: ModeSize.badge, height: ModeSize.badge).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.calloutEmphasis)
                Text(subtitle).modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, ModeSpacing.s)
        .frame(minHeight: ModeSize.rowMin + ModeSpacing.xs)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private var markView: some View {
        switch mark {
        case .done:
            ModeIcon.checkmark.image.font(ModeIconFont.small.weight(.heavy)).foregroundStyle(Color.white)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(ModeColor.success, in: .circle)
        case let .number(n):
            Text(n, format: .number).modeText(.subheadlineEmphasis).fontWeight(.bold)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(ModeColor.cardRaised, in: .circle)
        case .warning:
            ModeIcon.warning.image.font(ModeIconFont.small).foregroundStyle(ModeColor.warning)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(ModeColor.tileOrange, in: .circle)
        }
    }
}

/// One AI provider on setup's "Connect your AI": a radio, its tile, its name with an optional tag, what it is,
/// and Connect (ink for the picked one) or a green "Connected".
public struct ModeSetupProviderRow: View {
    private let title: String
    private let detail: String
    private let logo: ModeLogo
    private let tileBackground: Color
    private let tileForeground: Color
    private let tag: String?
    private let selected: Bool
    private let connected: Bool
    private let connectTitle: String
    private let connectedTitle: String
    private let onSelect: () -> Void
    private let onConnect: () -> Void

    public init(title: String, detail: String, logo: ModeLogo, tileBackground: Color, tileForeground: Color, tag: String?,
                selected: Bool, connected: Bool, connectTitle: String, connectedTitle: String,
                onSelect: @escaping () -> Void, onConnect: @escaping () -> Void) {
        self.title = title
        self.detail = detail
        self.logo = logo
        self.tileBackground = tileBackground
        self.tileForeground = tileForeground
        self.tag = tag
        self.selected = selected
        self.connected = connected
        self.connectTitle = connectTitle
        self.connectedTitle = connectedTitle
        self.onSelect = onSelect
        self.onConnect = onConnect
    }

    private var name: some View { Text(title).modeText(.bodyEmphasis).lineLimit(1).fixedSize() }
    @ViewBuilder private var tagView: some View {
        if let tag { ModeTag(tag, background: ModeColor.cardRaised, foreground: ModeColor.textPrimary).fixedSize() }
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            Button(action: onSelect) {
                HStack(spacing: ModeSpacing.m) {
                    ModeRadio(selected: selected)
                    ModeLeadingView(.logoTile(logo, background: tileBackground, foreground: tileForeground))
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        // The tag goes under the name when both don't fit beside Connect.
                        ViewThatFits(in: .horizontal) {
                            HStack(spacing: ModeSpacing.s - ModeSpacing.xxs) { name; tagView }
                            VStack(alignment: .leading, spacing: ModeSpacing.xxs) { name; tagView }
                        }
                        Text(detail).modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(selected ? [.isSelected] : [])
            if connected {
                ModeConnectedChip(connectedTitle, green: true)
            } else {
                Button(action: onConnect) {
                    Text(connectTitle).modeText(.subheadlineEmphasis, color: selected ? ModeColor.onAccent : ModeColor.textPrimary)
                        .padding(.horizontal, ModeSpacing.m + ModeSpacing.xxs)
                        .frame(minHeight: ModeSize.pillButton)
                        .background(selected ? ModeColor.accent : ModeColor.cardRaised, in: .capsule)
                        .frame(minHeight: ModeSize.touch)
                        .contentShape(.rect)
                }
                .buttonStyle(ModePressStyle())
                .fixedSize()
            }
        }
        .padding(.vertical, ModeSpacing.m + ModeSpacing.xxs)
    }
}

/// A permission on "Allow what Mode needs": its tile, its name and why, and what to do about it, top-aligned.
public struct ModePermissionRow: View {
    private let title: String
    private let why: String
    private let icon: ModeIcon
    private let tileBackground: Color
    private let tileForeground: Color
    private let status: ModeStatusAction
    private let onAllow: () -> Void

    public init(_ title: String, why: String, icon: ModeIcon, tileBackground: Color, tileForeground: Color,
                status: ModeStatusAction, onAllow: @escaping () -> Void) {
        self.title = title
        self.why = why
        self.icon = icon
        self.tileBackground = tileBackground
        self.tileForeground = tileForeground
        self.status = status
        self.onAllow = onAllow
    }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.m) {
            icon.image.font(ModeIconFont.small)
                .foregroundStyle(tileForeground)
                .frame(width: ModeSize.setupPermissionTile, height: ModeSize.setupPermissionTile)
                .background(tileBackground, in: .rect(cornerRadius: ModeSize.tileRadius))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                Text(title).modeText(.calloutEmphasis)
                Text(why).modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            trailing
        }
        .padding(.vertical, ModeSpacing.l)
    }

    @ViewBuilder private var trailing: some View {
        switch status {
        case let .prominent(title): allow(title, quiet: false)
        case let .quiet(title): allow(title, quiet: true)
        case let .done(title):
            Label { Text(title) } icon: { ModeIcon.checkmark.image.fontWeight(.bold) }
                .labelStyle(.titleAndIcon)
                .modeText(.subheadlineEmphasis, color: ModeColor.success)
                .frame(minHeight: ModeSize.badge)
        case let .note(text):
            Text(text).modeText(.subheadline, color: ModeColor.textSecondary).frame(minHeight: ModeSize.badge)
        }
    }

    private func allow(_ title: LocalizedStringResource, quiet: Bool) -> some View {
        ModeAllowButton(title, quiet: quiet, action: onAllow)
    }
}

/// The small blue Allow button on a permission (34 tall; its tap area stays 44): filled, or quiet for
/// something optional.
public struct ModeAllowButton: View {
    private let title: LocalizedStringResource
    private let quiet: Bool
    private let action: () -> Void

    public init(_ title: LocalizedStringResource, quiet: Bool = false, action: @escaping () -> Void) {
        self.title = title
        self.quiet = quiet
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(title).modeText(.subheadlineEmphasis, color: quiet ? ModeColor.infoText : Color.white)
                .padding(.horizontal, ModeSpacing.l)
                .frame(minHeight: ModeSize.setupAllowButton)
                .background(quiet ? ModeColor.infoSoft : ModeColor.info, in: .capsule)
                .padding(.vertical, (ModeSize.touch - ModeSize.setupAllowButton) / 2)
                .contentShape(.rect)
        }
        .buttonStyle(ModePressStyle())
        .padding(.vertical, -(ModeSize.touch - ModeSize.setupAllowButton) / 2)
        .fixedSize()
    }
}

/// "How hard it thinks": Low / Medium / High in a raised track, the picked one in ink, the recommended one labelled.
public struct ModeEffortPicker: View {
    private let options: [(id: String, title: String)]
    private let selection: String
    private let recommended: String
    private let recommendedLabel: String
    private let onSelect: (String) -> Void

    public init(options: [(id: String, title: String)], selection: String, recommended: String, recommendedLabel: String,
                onSelect: @escaping (String) -> Void) {
        self.options = options
        self.selection = selection
        self.recommended = recommended
        self.recommendedLabel = recommendedLabel
        self.onSelect = onSelect
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xs) {
            ForEach(options, id: \.id) { option in
                let picked = option.id == selection
                Button { onSelect(option.id) } label: {
                    VStack(spacing: ModeSpacing.none) {
                        Text(option.title).modeText(.subheadline, color: picked ? ModeColor.onAccent : ModeColor.textPrimary).fontWeight(.bold)
                        if option.id == recommended {
                            Text(recommendedLabel).textCase(.uppercase)
                                .modeText(.captionEmphasis, color: picked ? ModeColor.onAccent : ModeColor.textSecondary)
                        }
                    }
                    .frame(maxWidth: .infinity, minHeight: ModeSize.setupSegment)
                    .background(picked ? ModeColor.accent : Color.clear, in: .rect(cornerRadius: ModeRadius.setupSegment))
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(picked ? [.isSelected] : [])
            }
        }
        .padding(ModeSpacing.xs)
        .background(ModeColor.cardRaised, in: .rect(cornerRadius: ModeRadius.setupSegments))
        .animation(ModeMotion.quick, value: selection)
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
        HStack(alignment: .top, spacing: ModeSpacing.s - ModeSpacing.xxs) {
            ForEach(Array(labels.enumerated()), id: \.offset) { index, label in
                VStack(spacing: ModeSpacing.s) {
                    phone(index)
                    Text(label).modeText(.footnote, color: ModeColor.textSecondary).multilineTextAlignment(.center)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(width: ModeSize.setupTestColumn)
                if index < labels.count - 1 {
                    ModeIcon.arrowRight.image.font(ModeIconFont.small.weight(.semibold)).foregroundStyle(ModeColor.textSecondary)
                        .padding(.top, ModeSize.setupTestArrowTop).accessibilityHidden(true)
                }
            }
        }
        .frame(maxWidth: .infinity)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder
    private func phone(_ index: Int) -> some View {
        let phase = index < phases.count ? phases[index] : .pending
        let shape = RoundedRectangle(cornerRadius: ModeRadius.setupPhone)
        shape
            .fill(ModeColor.card)
            .overlay { if phase == .running { shape.strokeBorder(ModeColor.brand, lineWidth: ModeSize.separator * 2) } }
            .frame(width: ModeSize.testPhoneWidth, height: ModeSize.testPhoneHeight)
            .overlay {
                switch phase {
                case .done: ModeIcon.checkmark.image.font(ModeIconFont.status).foregroundStyle(ModeColor.success)
                case .failed: ModeIcon.close.image.font(ModeIconFont.status).foregroundStyle(ModeColor.danger)
                case .running, .pending: content(index)
                }
            }
            .modeShadow(in: shape)
    }

    @ViewBuilder
    private func content(_ index: Int) -> some View {
        switch index {
        case 0: ModeMo(.working, size: ModeSize.setupMoPhone)
        case 1:
            VStack(alignment: .leading, spacing: ModeSpacing.s - ModeSpacing.xxs) {
                Capsule().fill(ModeColor.track).frame(width: ModeSize.setupMockHeaderWidth, height: ModeSize.setupMockHeader)
                ForEach(0..<3, id: \.self) { row in
                    RoundedRectangle(cornerRadius: ModeRadius.setupMockRow).fill(row == 0 ? ModeColor.tintSoft : ModeColor.track)
                        .frame(height: ModeSize.setupMockRow)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, ModeSpacing.s - ModeSpacing.xxs)
            .padding(.vertical, ModeSpacing.m)
            .overlay(alignment: .topLeading) {
                Circle().strokeBorder(ModeColor.brand, lineWidth: ModeSize.separator * 2)
                    .frame(width: ModeSize.setupMockRing, height: ModeSize.setupMockRing)
                    .padding(.leading, ModeSize.setupMockRing - ModeSpacing.s)
                    .padding(.top, ModeSize.setupMockRing - ModeSpacing.xs)
            }
        default: ModeMo(.done, size: ModeSize.setupMoPhone)
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
            .font(ModeIconFont.status)
            .foregroundStyle(ModeColor.onAccent)
            .frame(width: ModeSize.successBadge, height: ModeSize.successBadge)
            .background(success ? ModeColor.success : ModeColor.warning, in: .circle)
            .accessibilityHidden(true)
    }
}

/// The screenshot stand-in beside "What Mode did" when there is no real one: a little list.
public struct ModeShotPlaceholder: View {
    public init() {}

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s - ModeSpacing.xxs) {
            Capsule().fill(ModeColor.track).frame(width: ModeSize.setupShotHeaderWidth, height: ModeSize.setupShotHeader)
            RoundedRectangle(cornerRadius: ModeRadius.setupMockRow).fill(ModeColor.tintSoft).frame(height: ModeSize.setupShotBarTop)
            ForEach(0..<3, id: \.self) { _ in
                RoundedRectangle(cornerRadius: ModeRadius.setupMockRow).fill(ModeColor.track).frame(height: ModeSize.setupShotBar)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, ModeSpacing.s)
        .padding(.vertical, ModeSpacing.m)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(ModeColor.cardRaised)
        .accessibilityHidden(true)
    }
}

/// Setup's one-off display text: Welcome's big name and line, the result's and Find the problem's titles.
public struct ModeSetupText: View {
    public enum Style: Sendable {
        /// "Mode" on Welcome: 42 heavy display.
        case welcomeTitle
        /// The line under it: 19, secondary.
        case welcomeTagline
        /// A compact page title (the test result, Talk to Mode): 28 heavy display.
        case compactTitle
        /// Find the problem: 30 heavy display.
        case diagnoseTitle
    }

    private let text: LocalizedStringResource
    private let style: Style

    public init(_ text: LocalizedStringResource, style: Style) {
        self.text = text
        self.style = style
    }

    public var body: some View {
        switch style {
        case .welcomeTitle: display(42, relativeTo: .largeTitle)
        case .compactTitle: display(28, relativeTo: .title)
        case .diagnoseTitle: display(30, relativeTo: .title)
        case .welcomeTagline:
            Text(text).font(Font(UIFontMetrics(forTextStyle: .title3).scaledFont(for: .systemFont(ofSize: 19))))
                .foregroundStyle(ModeColor.textSecondary)
                .lineSpacing(ModeSpacing.xxs)
        }
    }

    private func display(_ size: CGFloat, relativeTo style: Font.TextStyle) -> some View {
        Text(text).font(ModeTypography.display(size: size, weight: 800, relativeTo: style)).tracking(-size * 0.02)
            .foregroundStyle(ModeColor.textPrimary)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Setup's own buttons: the lifted white and ink pair at the bottom of Find the problem (54 tall),
/// and the blue yes/no answers under "Did you see…" (50 tall).
public struct ModeSetupButton: View {
    public enum Style: Sendable { case ink, lifted, blue, blueQuiet }

    private let title: LocalizedStringResource
    private let icon: ModeIcon?
    private let style: Style
    private let action: () -> Void
    @Environment(\.isEnabled) private var isEnabled

    public init(_ title: LocalizedStringResource, icon: ModeIcon? = nil, style: Style, action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.style = style
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.s - ModeSpacing.xxs) {
                if let icon { icon.image.font(ModeIconFont.small) }
                Text(title).lineLimit(1).minimumScaleFactor(0.85)
            }
            .modeText(style == .blue || style == .blueQuiet ? .bodyEmphasis : .calloutEmphasis, color: foreground)
            .padding(.horizontal, ModeSpacing.l)
            .frame(maxWidth: .infinity, minHeight: style == .blue || style == .blueQuiet ? ModeSize.setupAnswerButton : ModeSize.setupFooterButton)
            .background(background, in: .capsule)
            .modeShadow(in: Capsule(), lifted: style == .lifted)
            .contentShape(.capsule)
        }
        .buttonStyle(ModePressStyle())
        .opacity(isEnabled ? 1 : 0.45)
    }

    private var foreground: Color {
        switch style {
        case .ink: ModeColor.onAccent
        case .lifted: ModeColor.textPrimary
        case .blue: Color.white
        case .blueQuiet: ModeColor.infoText
        }
    }

    private var background: Color {
        switch style {
        case .ink: ModeColor.accent
        case .lifted, .blueQuiet: ModeColor.card
        case .blue: ModeColor.info
        }
    }
}

/// A line under "What Mode did": a tick (or a cross, a spinner, an empty circle), what happened, and how long.
public struct ModeDidRow: View {
    private let title: String
    private let detail: String?
    private let mark: ModeRowLeading

    public init(_ title: String, detail: String?, mark: ModeRowLeading) {
        self.title = title
        self.detail = detail
        self.mark = mark
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            markView.frame(width: ModeSize.icon, height: ModeSize.icon).accessibilityHidden(true)
            Text(title).modeText(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
            if let detail { Text(detail).modeText(.footnote, color: ModeColor.textSecondary) }
        }
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private var markView: some View {
        switch mark {
        case .check: ModeIcon.checkmark.image.font(ModeIconFont.chevron.weight(.bold)).foregroundStyle(ModeColor.success)
        case .failure: ModeIcon.failed.image.font(ModeIconFont.small).foregroundStyle(ModeColor.danger)
        case .progress: ProgressView().controlSize(.small).tint(ModeColor.brand)
        default: Circle().strokeBorder(ModeColor.ring, lineWidth: ModeSize.separator * 2)
        }
    }
}

/// One check on Find the problem: passed (a tick and how long), running, failed (in red, with why and the fix
/// under it), or not checked yet (quiet).
public struct ModeCheckRow: View {
    public enum State: Equatable, Sendable { case passed, running, failed, notChecked }

    private let title: String
    private let state: State
    private let detail: String?
    private let trailing: String?

    public init(_ title: String, state: State, detail: String?, trailing: String?) {
        self.title = title
        self.state = state
        self.detail = detail
        self.trailing = trailing
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            HStack(spacing: ModeSpacing.m) {
                mark.frame(width: ModeSize.setupCheckMark, height: ModeSize.setupCheckMark).accessibilityHidden(true)
                Text(title)
                    .modeText(state == .failed ? .subheadlineEmphasis : .subheadline,
                              color: state == .failed ? ModeColor.danger : state == .notChecked ? ModeColor.textSecondary : ModeColor.textPrimary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                if let trailing { Text(trailing).modeText(.footnote, color: ModeColor.textSecondary) }
            }
            if let detail {
                Text(detail).modeText(.footnote, color: ModeColor.textSecondary)
                    .fixedSize(horizontal: false, vertical: true)
                    .padding(.leading, ModeSize.setupCheckMark + ModeSpacing.m)
            }
        }
        .padding(.vertical, state == .failed ? ModeSpacing.m : ModeSpacing.none)
        .frame(minHeight: state == .notChecked ? ModeSize.touch : ModeSize.setupCheckRow)
        .accessibilityElement(children: .combine)
    }

    @ViewBuilder private var mark: some View {
        switch state {
        case .passed: ModeIcon.checkmark.image.font(ModeIconFont.chevron.weight(.bold)).foregroundStyle(ModeColor.success)
        case .running: ProgressView().controlSize(.small).tint(ModeColor.brand)
        case .failed: ModeIcon.failed.image.resizable().scaledToFit().symbolRenderingMode(.palette).foregroundStyle(Color.white, ModeColor.danger)
        case .notChecked: Circle().strokeBorder(ModeColor.ring, lineWidth: ModeSize.separator * 2)
        }
    }
}
