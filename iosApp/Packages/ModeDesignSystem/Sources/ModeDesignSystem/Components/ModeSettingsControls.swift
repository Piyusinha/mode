import SwiftUI

/// A page's own heading, as on the Mo theme boards: the 34-point heavy title, 24 points in from the
/// screen's edge, and an optional line under it. It scrolls with the page (the navigation bar keeps
/// only its round back button).
public struct ModePageTitle: View {
    private let title: LocalizedStringResource
    private let subtitle: LocalizedStringResource?

    public init(_ title: LocalizedStringResource, subtitle: LocalizedStringResource? = nil) {
        self.title = title
        self.subtitle = subtitle
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            Text(title).modeText(.largeTitle).accessibilityAddTraits(.isHeader)
            if let subtitle { Text(subtitle).modeText(.callout, color: ModeColor.textSecondary) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, ModeSpacing.noteInset)
    }
}

public extension View {
    /// A page that draws its own [ModePageTitle]: the bar keeps the title for the back menu and VoiceOver,
    /// but doesn't draw it.
    func modePageChrome(_ title: LocalizedStringResource) -> some View {
        navigationTitle(Text(title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(removing: .title)
    }
}

/// A note under a card, 8 points in from the card's edge, with an optional icon before it (a lock).
public struct ModeFootnote: View {
    private let text: Text
    private let style: ModeTextStyle
    private let icon: ModeIcon?

    public init(_ text: LocalizedStringResource, style: ModeTextStyle = .footnote, icon: ModeIcon? = nil) {
        self.text = Text(text)
        self.style = style
        self.icon = icon
    }

    public init(verbatim text: String, style: ModeTextStyle = .footnote, icon: ModeIcon? = nil) {
        self.text = Text(text)
        self.style = style
        self.icon = icon
    }

    public var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.s) {
            if let icon { icon.image.font(ModeIconFont.small).foregroundStyle(ModeColor.textSecondary).accessibilityHidden(true) }
            text.modeText(style, color: ModeColor.textSecondary).lineSpacing(ModeSpacing.xxs)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, ModeSpacing.noteInset)
    }
}

/// A small white capsule button with a soft lift: a sheet's Done.
public struct ModeCapsuleButton: View {
    private let title: LocalizedStringResource
    private let action: () -> Void

    public init(_ title: LocalizedStringResource, action: @escaping () -> Void) {
        self.title = title
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(title).modeText(.subheadlineEmphasis)
                .padding(.horizontal, ModeSpacing.headerGap + ModeSpacing.xs)
                .frame(minHeight: ModeSize.pillButton)
                .background(ModeColor.card, in: .capsule)
                .modeShadow(in: Capsule())
                .frame(minHeight: ModeSize.touch)
                .contentShape(.rect)
        }
        .buttonStyle(ModePressStyle())
    }
}

/// A few options in a capsule, the chosen one filled. [Style.ink]: a white capsule, the chosen option in ink
/// (Phone control / Brain). [Style.soft]: a raised-grey capsule, the chosen option in a white pill (Low / Medium / High).
/// Every option is at least 44 points tall to tap, whatever its drawn height.
public struct ModePillPicker<Option: Hashable>: View {
    public enum Style: Sendable { case ink, soft }

    private let options: [(Option, String)]
    @Binding private var selection: Option
    private let style: Style

    public init(_ options: [(Option, String)], selection: Binding<Option>, style: Style = .ink) {
        self.options = options
        self._selection = selection
        self.style = style
    }

    private var pill: CGFloat { style == .ink ? ModeSize.roleSwitch : ModeSize.effortSwitch }
    /// Extra room above and below each option, so it is 44 tall to tap.
    private var reach: CGFloat { max(0, (ModeSize.touch - pill) / 2 - ModeSpacing.xxs) }

    public var body: some View {
        HStack(spacing: ModeSpacing.xxs) {
            ForEach(options, id: \.0) { option, title in
                let selected = option == selection
                Button {
                    withAnimation(ModeMotion.quick) { selection = option }
                } label: {
                    Text(title)
                        .modeText(style == .ink || selected ? .subheadlineEmphasis : .subheadline,
                                  color: style == .ink && selected ? ModeColor.onAccent : ModeColor.textPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity, minHeight: pill)
                        .background {
                            if selected {
                                Capsule().fill(style == .ink ? ModeColor.accent : ModeColor.card)
                                    .modeShadow(in: Capsule(), lifted: style == .soft)
                            }
                        }
                        .padding(.vertical, reach)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? [.isSelected] : [])
            }
        }
        .padding(ModeSpacing.xxs)
        .background {
            Capsule().fill(style == .ink ? ModeColor.card : ModeColor.cardRaised)
                .modeShadow(in: Capsule(), lifted: style == .ink)
                .padding(.vertical, reach)
        }
    }
}

/// A wide 48-point button: a white card (See usage, Measure on this iPhone), raised grey (Share report), ink
/// (Use OpenRouter, Check again) or soft red (Sign out). Mo can sit before the title, an icon after it.
public struct ModeWideButton: View {
    public enum Style: Sendable { case card, raised, ink, danger }

    private let title: Text
    private let style: Style
    private let mood: ModeMo.Mood?
    private let trailingIcon: ModeIcon?
    private let action: () -> Void
    @Environment(\.isEnabled) private var isEnabled

    public init(_ title: LocalizedStringResource, style: Style = .card, mood: ModeMo.Mood? = nil, trailingIcon: ModeIcon? = nil,
                action: @escaping () -> Void) {
        self.title = Text(title)
        self.style = style
        self.mood = mood
        self.trailingIcon = trailingIcon
        self.action = action
    }

    public init(verbatim title: String, style: Style = .card, mood: ModeMo.Mood? = nil, trailingIcon: ModeIcon? = nil,
                action: @escaping () -> Void) {
        self.title = Text(title)
        self.style = style
        self.mood = mood
        self.trailingIcon = trailingIcon
        self.action = action
    }

    private var foreground: Color {
        switch style {
        case .card, .raised: ModeColor.textPrimary
        case .ink: ModeColor.onAccent
        case .danger: ModeColor.danger
        }
    }

    private var background: Color {
        switch style {
        case .card: ModeColor.card
        case .raised: ModeColor.cardRaised
        case .ink: ModeColor.accent
        case .danger: ModeColor.tileRed
        }
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.s) {
                if let mood { ModeMo(mood, size: ModeSize.moButton) }
                title.modeText(.calloutEmphasis, color: foreground).lineLimit(1)
                if let trailingIcon {
                    trailingIcon.image.font(ModeIconFont.chevron).foregroundStyle(foreground).accessibilityHidden(true)
                }
            }
            .padding(.horizontal, ModeSpacing.l)
            .frame(maxWidth: .infinity, minHeight: ModeSize.wideButton)
            .background(background, in: .capsule)
            .modeShadow(in: Capsule(), lifted: style == .card)
            .contentShape(.capsule)
        }
        .buttonStyle(ModePressStyle())
        .opacity(isEnabled ? 1 : 0.45)
    }
}

/// A small white capsule with a status dot: "ChatGPT Plus · 4 models on your plan".
public struct ModeInfoChip: View {
    private let text: String
    private let dot: Color

    public init(_ text: String, dot: Color = ModeColor.success) {
        self.text = text
        self.dot = dot
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
            Circle().fill(dot).frame(width: ModeSize.statusDot, height: ModeSize.statusDot).accessibilityHidden(true)
            Text(text).modeText(.footnoteEmphasis).lineLimit(1)
        }
        .padding(.horizontal, ModeSpacing.m)
        .frame(minHeight: ModeSize.infoChip)
        .background(ModeColor.card, in: .capsule)
        .modeShadow(in: Capsule())
    }
}

/// A status line with a dot before it ("Connected · in use", "Plus · 3 providers connected"). The dot stays
/// on the first line when the text wraps.
public struct ModeStatusLine: View {
    private let text: String
    private let dot: Color

    public init(_ text: String, dot: Color) {
        self.text = text
        self.dot = dot
    }

    public var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.xs + ModeSpacing.xxs) {
            Circle().fill(dot).frame(width: ModeSize.statusDot, height: ModeSize.statusDot)
                .alignmentGuide(.firstTextBaseline) { $0[.bottom] }
                .accessibilityHidden(true)
            Text(text).modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// A row with a small icon in a soft round badge, a title and a line under it (Access: what Mode always asks you for).
public struct ModeBadgeRow: View {
    private let title: String
    private let subtitle: String
    private let icon: ModeIcon
    private let background: Color
    private let foreground: Color

    public init(_ title: String, subtitle: String, icon: ModeIcon, background: Color = ModeColor.tileOrange,
                foreground: Color = ModeColor.tileOrangeText) {
        self.title = title
        self.subtitle = subtitle
        self.icon = icon
        self.background = background
        self.foreground = foreground
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            icon.image.font(ModeIconFont.small)
                .foregroundStyle(foreground)
                .frame(width: ModeSize.badge, height: ModeSize.badge)
                .background(background, in: .circle)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.callout)
                Text(subtitle).modeText(.footnote, color: ModeColor.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, ModeSpacing.s)
        .frame(minHeight: ModeSize.healthRow)
        .accessibilityElement(children: .combine)
    }
}

/// A worked example on a raised-grey card: each line a quiet label and what goes with it
/// (Language: You say “…”, Mode works on “…”, Mode says “…”).
public struct ModeExampleCard: View {
    private let lines: [(LocalizedStringResource, LocalizedStringResource)]

    public init(lines: [(LocalizedStringResource, LocalizedStringResource)]) {
        self.lines = lines
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            ForEach(lines.indices, id: \.self) { i in
                Text("\(Text(lines[i].0).foregroundStyle(ModeColor.textSecondary)) \(Text(lines[i].1))")
                    .modeText(.subheadline, color: ModeColor.textPrimary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.vertical, ModeSpacing.m)
        .padding(.horizontal, ModeSpacing.headerGap)
        .background(ModeColor.cardRaised, in: .rect(cornerRadius: ModeRadius.table))
        .accessibilityElement(children: .combine)
    }
}

/// A Settings row with a provider's logo on a small 32-point tile (Mode's voice, Listening): the title (a tag under
/// it, such as "Most human"), the value on the right, and a chevron. When it failed, the value moves up beside the
/// title and an orange line under them says why.
public struct ModeLogoValueRow: View {
    private let title: String
    private let value: String
    private let logo: ModeLogo
    private let tileBackground: Color
    private let tileForeground: Color
    private let tag: String?
    private let problem: String?
    private let action: () -> Void

    public init(_ title: String, value: String, logo: ModeLogo, tileBackground: Color = ModeColor.textPrimary,
                tileForeground: Color = ModeColor.page, tag: String? = nil, problem: String? = nil, action: @escaping () -> Void) {
        self.title = title
        self.value = value
        self.logo = logo
        self.tileBackground = tileBackground
        self.tileForeground = tileForeground
        self.tag = tag
        self.problem = problem
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.m) {
                logo.image.resizable().scaledToFit()
                    .frame(width: ModeSize.iconSmall + ModeSpacing.xxs, height: ModeSize.iconSmall + ModeSpacing.xxs)
                    .foregroundStyle(tileForeground)
                    .frame(width: ModeSize.badge, height: ModeSize.badge)
                    .background(tileBackground, in: .rect(cornerRadius: ModeSize.smallTileRadius))
                    .accessibilityHidden(true)
                if let problem {
                    VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                        HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.s) {
                            Text(title).modeText(.callout)
                            Spacer(minLength: ModeSpacing.s)
                            Text(value).modeText(.subheadline, color: ModeColor.textSecondary).lineLimit(1).layoutPriority(1)
                        }
                        HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.xs + ModeSpacing.xxs) {
                            ModeIcon.warning.image.font(ModeTextStyle.caption.font).accessibilityHidden(true)
                            Text(problem).fixedSize(horizontal: false, vertical: true)
                        }
                        .modeText(.footnote, color: ModeColor.tileOrangeText)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                } else {
                    VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                        Text(title).modeText(.callout)
                        if let tag { ModeTag(tag, background: ModeColor.cardRaised, foreground: ModeColor.textPrimary).fixedSize() }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    // The value stays whole ("ElevenLabs · Rachel"); the title gives way.
                    Text(value).modeText(.subheadline, color: ModeColor.textSecondary).lineLimit(1).layoutPriority(1)
                }
                ModeChevron()
            }
            .padding(.vertical, ModeSpacing.s)
            .frame(minHeight: problem != nil ? ModeSize.logoRowProblem : tag != nil ? ModeSize.logoRowTagged : ModeSize.logoRow)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine)
    }
}
