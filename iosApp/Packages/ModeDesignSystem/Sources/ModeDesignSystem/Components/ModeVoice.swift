import SwiftUI

// The voice screens (Mode's voice, Listening, a provider's page, the key sheet, Choose a voice) and talking mode,
// as on the Mo theme boards: Mo instead of an orb, and Mo-orange bars for a voice.

// MARK: - Bars

/// Bars of a sound, for a voice that is playing or a microphone that is listening: 3-point bars with round ends.
public struct ModeWaveBars: View {
    private let color: Color
    private let height: CGFloat
    private let count: Int
    private static let shape: [CGFloat] = [0.45, 0.9, 0.6, 1.0, 0.5, 0.75, 0.4]

    public init(color: Color = ModeColor.brand, height: CGFloat = ModeSize.cardWave, count: Int = 5) {
        self.color = color
        self.height = height
        self.count = count
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xxs) {
            ForEach(0..<min(count, Self.shape.count), id: \.self) { i in
                RoundedRectangle(cornerRadius: ModeSize.talkBarRadius).fill(color)
                    .frame(width: ModeSize.smallBar, height: height * Self.shape[i])
            }
        }
        .frame(height: height)
        .accessibilityHidden(true)
    }
}

// MARK: - Tags, tiles, rows

/// A small tag beside a name ("In use", "Most human", "Our pick", "Free download").
public struct ModeVoiceTag: View, Identifiable {
    public enum Tone: Sendable { case green, neutral, orange, amber }

    private let text: String
    private let tone: Tone
    nonisolated public var id: String { text }

    public init(_ text: String, tone: Tone) {
        self.text = text
        self.tone = tone
    }

    public var body: some View {
        Text(text)
            .modeText(.captionEmphasis, color: foreground)
            .lineLimit(1)
            .padding(.horizontal, ModeSpacing.xs + ModeSpacing.xxs + 1)
            .frame(minHeight: ModeSize.voiceTag)
            .background(background, in: .capsule)
            .fixedSize()
    }

    private var foreground: Color {
        switch tone {
        case .green: ModeColor.success
        case .neutral: ModeColor.textPrimary
        case .orange: ModeColor.tint
        case .amber: ModeColor.warning
        }
    }

    private var background: Color {
        switch tone {
        case .green: ModeColor.tagGreen
        case .neutral: ModeColor.cardRaised
        case .orange: ModeColor.tintSoft
        case .amber: ModeColor.tileOrange
        }
    }
}

/// A voice service's square: its logo on ink, or its first letter on raised grey.
public enum ModeVoiceTile: Equatable, Sendable {
    case logo(ModeLogo)
    case letter(String)
}

public struct ModeVoiceTileView: View {
    public enum Size: Sendable {
        /// 32: a row in Settings.
        case small
        /// 40: a provider row.
        case row
        /// 48: the key sheet.
        case sheet
        /// 56: a provider's page.
        case page
    }

    private let tile: ModeVoiceTile
    private let size: Size

    public init(_ tile: ModeVoiceTile, size: Size = .row) {
        self.tile = tile
        self.size = size
    }

    private var side: CGFloat {
        switch size {
        case .small: ModeSize.badge
        case .row: ModeSize.tile
        case .sheet: ModeSize.voiceSheetTile
        case .page: ModeSize.voicePageTile
        }
    }

    private var radius: CGFloat {
        switch size {
        case .small: ModeSpacing.s + 1
        case .row: ModeSize.tileRadius
        case .sheet: ModeSize.voiceSheetTileRadius
        case .page: ModeSize.voicePageTileRadius
        }
    }

    public var body: some View {
        Group {
            switch tile {
            case let .logo(logo):
                let wide = logo == .fishAudio
                logo.image.resizable().scaledToFit()
                    .frame(width: side * (wide ? 0.8 : 0.5), height: side * (wide ? 0.8 : 0.5))
                    .foregroundStyle(ModeColor.onAccent)
                    .frame(width: side, height: side)
                    .background(ModeColor.accent, in: .rect(cornerRadius: radius))
            case let .letter(letter):
                Text(letter)
                    .modeText(size == .page || size == .sheet ? .title3 : .section)
                    .fontWeight(.heavy)
                    .frame(width: side, height: side)
                    .background(ModeColor.cardRaised, in: .rect(cornerRadius: radius))
            }
        }
        .accessibilityHidden(true)
    }
}

/// A coloured dot before a status line: green when connected, grey when not, red when it failed.
public struct ModeVoiceStatus: View {
    private let text: String
    private let dot: Color
    private let color: Color
    private let emphasised: Bool

    public init(_ text: String, dot: Color, color: Color = ModeColor.textSecondary, emphasised: Bool = false) {
        self.text = text
        self.dot = dot
        self.color = color
        self.emphasised = emphasised
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
            Circle().fill(dot).frame(width: ModeSize.voiceDot, height: ModeSize.voiceDot)
            Text(text).modeText(emphasised ? .footnoteEmphasis : .footnote, color: color)
                .fixedSize(horizontal: false, vertical: true)
        }
    }
}

/// One voice service to pick: its tile, its name with tags, a status with a dot, and on the right the radio
/// (connected) or a Connect pill. Tapping a connected one opens its page; the radio picks it.
public struct ModeVoiceProviderRow: View {
    private let title: String
    private let status: String
    private let dot: Color
    private let tile: ModeVoiceTile
    private let tags: [ModeVoiceTag]
    private let selected: Bool
    private let connected: Bool
    private let connectTitle: String
    private let onSelect: () -> Void
    private let onConnect: () -> Void
    private let onOpen: (() -> Void)?

    public init(title: String, status: String, dot: Color, tile: ModeVoiceTile, tags: [ModeVoiceTag], selected: Bool, connected: Bool,
                connectTitle: String, onSelect: @escaping () -> Void, onConnect: @escaping () -> Void, onOpen: (() -> Void)? = nil) {
        self.title = title
        self.status = status
        self.dot = dot
        self.tile = tile
        self.tags = tags
        self.selected = selected
        self.connected = connected
        self.connectTitle = connectTitle
        self.onSelect = onSelect
        self.onConnect = onConnect
        self.onOpen = onOpen
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            Button(action: connected ? (onOpen ?? onSelect) : onConnect) {
                HStack(spacing: ModeSpacing.m) {
                    ModeVoiceTileView(tile)
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs + 1) {
                        ViewThatFits(in: .horizontal) {
                            HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) { name; ForEach(tags) { $0 } }
                            VStack(alignment: .leading, spacing: ModeSpacing.xxs + 1) {
                                name
                                HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) { ForEach(tags) { $0 } }
                            }
                        }
                        ModeVoiceStatus(status, dot: dot)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(selected ? [.isSelected] : [])
            if connected {
                Button(action: onSelect) { ModeRadio(selected: selected).frame(minWidth: ModeSize.touch, minHeight: ModeSize.touch) }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(title))
                    .accessibilityAddTraits(selected ? [.isSelected] : [])
                    .padding(.trailing, -ModeSpacing.m + ModeSpacing.xxs)
            } else {
                Button(action: onConnect) {
                    Text(connectTitle).modeText(.subheadlineEmphasis, color: ModeColor.onAccent)
                        .padding(.horizontal, ModeSpacing.m + ModeSpacing.xxs)
                        .frame(minHeight: ModeSize.pillButton)
                        .background(ModeColor.accent, in: .capsule)
                        .frame(minHeight: ModeSize.touch)
                        .contentShape(.rect)
                }
                .buttonStyle(ModePressStyle())
            }
        }
        .padding(.vertical, ModeSpacing.m)
        .frame(minHeight: ModeSize.voiceProviderRowMin)
    }

    private var name: some View { Text(title).modeText(.calloutEmphasis).lineLimit(1).fixedSize() }
}

/// The voice in use, at the top of Mode's voice: Mo with the provider's badge, the voice's name and where it's from.
public struct ModeVoiceIdentity: View {
    private let name: String
    private let detail: String
    private let badge: ModeVoiceTile

    public init(name: String, detail: String, badge: ModeVoiceTile) {
        self.name = name
        self.detail = detail
        self.badge = badge
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeMo(.idle, size: ModeSize.voiceMo)
                .frame(width: ModeSize.voiceMoFrame, height: ModeSize.voiceMoFrame, alignment: .topLeading)
                .overlay(alignment: .bottomTrailing) {
                    Group {
                        switch badge {
                        case let .logo(logo):
                            logo.image.resizable().scaledToFit()
                                .frame(width: ModeSize.voiceBadgeLogo, height: ModeSize.voiceBadgeLogo)
                                .foregroundStyle(ModeColor.onAccent)
                        case let .letter(letter):
                            Text(letter).modeText(.captionEmphasis, color: ModeColor.onAccent)
                        }
                    }
                    .frame(width: ModeSize.voiceBadge, height: ModeSize.voiceBadge)
                    .background(ModeColor.accent, in: .rect(cornerRadius: ModeSize.voiceBadgeRadius))
                    .overlay(RoundedRectangle(cornerRadius: ModeSize.voiceBadgeRadius).strokeBorder(ModeColor.card, lineWidth: ModeSize.separator * 2))
                    .offset(x: ModeSpacing.xs, y: ModeSpacing.xs)
                }
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(name).modeText(.voiceName).lineLimit(1)
                Text(detail).modeText(.small, color: ModeColor.textSecondary).lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .accessibilityElement(children: .combine)
    }
}

/// The voice boards' buttons: ink, raised grey, soft red or white, 48–56 tall.
public struct ModeVoiceButton: View {
    public enum Style: Sendable { case ink, raised, danger, card, info }

    private let title: LocalizedStringResource
    private let icon: ModeIcon?
    private let style: Style
    private let height: CGFloat
    private let action: () -> Void
    @Environment(\.isEnabled) private var isEnabled

    public init(_ title: LocalizedStringResource, icon: ModeIcon? = nil, style: Style, height: CGFloat = ModeSize.secondaryButton,
                action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.style = style
        self.height = height
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.s) {
                if let icon { icon.image.font(ModeIconFont.small.weight(.semibold)) }
                Text(title)
            }
                .modeText(height > ModeSize.secondaryButton ? .headline : .calloutEmphasis, color: foreground)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .padding(.horizontal, ModeSpacing.l)
                .frame(maxWidth: .infinity, minHeight: height)
                .background(background, in: .capsule)
                .modeShadow(in: Capsule(), lifted: style == .card)
                .contentShape(.capsule)
        }
        .buttonStyle(ModePressStyle())
        .compositingGroup()
        .opacity(isEnabled ? 1 : 0.4)
    }

    private var foreground: Color {
        switch style {
        case .ink: ModeColor.onAccent
        case .raised, .card: ModeColor.textPrimary
        case .danger: ModeColor.danger
        case .info: ModeColor.onInfo
        }
    }

    private var background: Color {
        switch style {
        case .ink: ModeColor.accent
        case .raised: ModeColor.cardRaised
        case .danger: ModeColor.tileRed
        case .card: ModeColor.card
        case .info: ModeColor.info
        }
    }
}

/// An underlined link out of the app ("Get a free key at fish.audio ↗").
public struct ModeVoiceLink: View {
    private let title: String
    private let url: URL
    private let bold: Bool

    public init(_ title: String, url: URL, bold: Bool = false) {
        self.title = title
        self.url = url
        self.bold = bold
    }

    public var body: some View {
        Link(destination: url) {
            HStack(spacing: ModeSpacing.xs) {
                Text(title).underline().modeText(.smallEmphasis).fontWeight(bold ? .bold : .semibold)
                ModeIcon.external.image.font(ModeIconFont.chevron)
            }
            .foregroundStyle(ModeColor.textPrimary)
            .frame(minHeight: ModeSize.touch - ModeSpacing.l)
        }
    }
}

/// Which provider's voices to show: a raised-grey track with the chosen one on a white pill.
public struct ModeVoiceSwitch<Option: Hashable>: View {
    private let options: [(Option, LocalizedStringResource)]
    @Binding private var selection: Option

    public init(_ options: [(Option, LocalizedStringResource)], selection: Binding<Option>) {
        self.options = options
        self._selection = selection
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xxs) {
            ForEach(options, id: \.0) { option, title in
                let on = option == selection
                Button { selection = option } label: {
                    Text(title).modeText(.smallEmphasis, color: on ? ModeColor.textPrimary : ModeColor.textSecondary)
                        .fontWeight(on ? .bold : .semibold)
                        .lineLimit(1).minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity, minHeight: ModeSize.pillButton)
                        .background {
                            if on { Capsule().fill(ModeColor.card).modeShadow(in: Capsule()) }
                        }
                        .contentShape(.capsule)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? [.isSelected] : [])
            }
        }
        .padding(ModeSpacing.xxs + 1)
        .background(ModeColor.cardRaised, in: .capsule)
        .frame(minHeight: ModeSize.touch)
    }
}

// MARK: - The key sheet

/// The top of a sheet: Mo (optional), its title, and a small round close.
public struct ModeSheetHeader: View {
    private let title: String
    private let mood: ModeMo.Mood?
    private let closeLabel: LocalizedStringResource
    private let onClose: () -> Void

    public init(_ title: String, mood: ModeMo.Mood?, closeLabel: LocalizedStringResource, onClose: @escaping () -> Void) {
        self.title = title
        self.mood = mood
        self.closeLabel = closeLabel
        self.onClose = onClose
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            if let mood { ModeMo(mood, size: ModeSize.voiceSheetMo) }
            Text(title).modeText(.sheetTitle).frame(maxWidth: .infinity, alignment: .leading)
                .accessibilityAddTraits(.isHeader)
            Button(action: onClose) {
                ModeIcon.close.image.font(ModeIconFont.chevron.weight(.bold)).foregroundStyle(ModeColor.textSecondary)
                    .frame(width: ModeSize.sheetClose, height: ModeSize.sheetClose)
                    .background(ModeColor.cardRaised, in: .circle)
                    .frame(width: ModeSize.touch, height: ModeSize.touch)
                    .contentShape(.circle)
            }
            .buttonStyle(ModePressStyle())
            .padding(.trailing, -(ModeSize.touch - ModeSize.sheetClose) / 2)
            .accessibilityLabel(Text(closeLabel))
        }
    }
}

/// Where a key is pasted: its label, the key (only its ending shows once typed), and Paste. The border says
/// what happened: orange while it's checked, red when it failed, green with a tick when it's saved.
public struct ModeKeyField: View {
    public enum Phase: Sendable { case editing, checking, failed, saved }

    private let label: String
    private let prompt: LocalizedStringResource
    @Binding private var text: String
    private let shown: String?
    private let phase: Phase
    private let pasteLabel: LocalizedStringResource
    private let onPaste: () -> Void
    @FocusState private var focused: Bool

    /// [shown]: what to show instead of the field (the saved key, "4f•••••a1").
    public init(_ label: String, prompt: LocalizedStringResource, text: Binding<String>, shown: String? = nil, phase: Phase,
                pasteLabel: LocalizedStringResource, onPaste: @escaping () -> Void) {
        self.label = label
        self.prompt = prompt
        self._text = text
        self.shown = shown
        self.phase = phase
        self.pasteLabel = pasteLabel
        self.onPaste = onPaste
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(label).modeText(.caption, color: ModeColor.textSecondary)
                ZStack(alignment: .leading) {
                    TextField(text: $text) { Text(prompt).foregroundStyle(ModeColor.textSecondary) }
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .textContentType(.password)
                        .focused($focused)
                        .opacity(masked == nil ? 1 : 0)
                    if let masked {
                        Text(masked).tracking(1).lineLimit(1).truncationMode(.head)
                            .allowsHitTesting(false)
                    }
                }
                .modeText(.callout)
                .disabled(phase == .checking || phase == .saved)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .contentShape(.rect)
            .onTapGesture { focused = true }
            trailing
        }
        .padding(.leading, ModeSpacing.l)
        .padding(.trailing, ModeSpacing.s)
        .padding(.vertical, ModeSpacing.s)
        .frame(minHeight: ModeSize.fieldHeight + ModeSpacing.s)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.keyField))
        .overlay(RoundedRectangle(cornerRadius: ModeRadius.keyField).strokeBorder(border, lineWidth: ModeSize.separator * 2))
    }

    /// The key as dots with its last four characters, while the field isn't being typed in.
    private var masked: String? {
        if let shown { return shown }
        guard !focused, !text.isEmpty else { return nil }
        let tail = text.suffix(4)
        return String(repeating: "•", count: min(20, max(0, text.count - tail.count))) + tail
    }

    @ViewBuilder private var trailing: some View {
        switch phase {
        case .checking:
            ProgressView().tint(ModeColor.brand).frame(width: ModeSize.touch, height: ModeSize.touch)
        case .saved:
            ModeIcon.checkmark.image.font(ModeIconFont.small.weight(.bold)).foregroundStyle(ModeColor.onAccent)
                .frame(width: ModeSize.keySaved, height: ModeSize.keySaved)
                .background(ModeColor.success, in: .circle)
                .padding(.trailing, ModeSpacing.xs)
                .accessibilityHidden(true)
        case .editing, .failed:
            Button(action: onPaste) {
                Text(pasteLabel).modeText(.smallEmphasis)
                    .padding(.horizontal, ModeSpacing.m + ModeSpacing.xxs)
                    .frame(minHeight: ModeSize.pillButton)
                    .background(ModeColor.cardRaised, in: .capsule)
                    .frame(minHeight: ModeSize.touch)
                    .contentShape(.rect)
            }
            .buttonStyle(ModePressStyle())
        }
    }

    private var border: Color {
        switch phase {
        case .editing: .clear
        case .checking: ModeColor.brand
        case .failed: ModeColor.danger
        case .saved: ModeColor.success
        }
    }
}

/// What came of a key: Mo, a title in the result's colour, what it means, and a link to fix it.
public struct ModeKeyResult: View {
    public enum Tone: Sendable { case failed, saved }

    private let tone: Tone
    private let title: String
    private let message: String
    private let link: (title: String, url: URL)?

    public init(_ tone: Tone, title: String, message: String, link: (title: String, url: URL)? = nil) {
        self.tone = tone
        self.title = title
        self.message = message
        self.link = link
    }

    public var body: some View {
        HStack(alignment: tone == .failed ? .top : .center, spacing: ModeSpacing.m) {
            ModeMo(tone == .failed ? .stuck : .done, size: ModeSize.voiceResultMo)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs + 1) {
                Text(title).modeText(.smallEmphasis, color: tone == .failed ? ModeColor.danger : ModeColor.success)
                Text(message).modeText(.footnote).fixedSize(horizontal: false, vertical: true)
                if let link { ModeVoiceLink(link.title, url: link.url, bold: true).padding(.top, ModeSpacing.xxs) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(ModeSpacing.m)
        .background(tone == .failed ? ModeColor.tileRed : ModeColor.tagGreen, in: .rect(cornerRadius: ModeRadius.keyResult))
        .accessibilityElement(children: .combine)
    }
}

/// The card on a provider's page when it couldn't speak or hear: Mo, why, what to do, and when it last tried.
public struct ModeVoiceProblem: View {
    private let title: String
    private let message: String
    private let detail: String?

    public init(title: String, message: String, detail: String? = nil) {
        self.title = title
        self.message = message
        self.detail = detail
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s + ModeSpacing.xxs) {
            HStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                ModeMo(.stuck, size: ModeSize.voiceResultMo)
                Text(title).modeText(.calloutEmphasis, color: ModeColor.danger).fontWeight(.bold)
            }
            Text(message).modeText(.small).fixedSize(horizontal: false, vertical: true)
            if let detail { Text(detail).modeText(.caption, color: ModeColor.textSecondary) }
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.vertical, ModeSpacing.m + ModeSpacing.xxs)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(ModeColor.tileRed, in: .rect(cornerRadius: ModeRadius.talkCard))
        .accessibilityElement(children: .combine)
    }
}

// MARK: - Choosing a voice

/// Plays a voice's sample; while it plays, the button turns ink with the voice's bars.
public struct ModePlayButton: View {
    private let label: String
    private let playing: Bool
    private let barColor: Color
    private let size: CGFloat
    private let action: () -> Void

    public init(_ label: String, playing: Bool, barColor: Color = ModeColor.brand, size: CGFloat = ModeSize.playButton,
                action: @escaping () -> Void) {
        self.label = label
        self.playing = playing
        self.barColor = barColor
        self.size = size
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Group {
                if playing { ModeWaveBars(color: barColor, height: ModeSize.smallWave, count: 4) }
                else { ModeIcon.play.image.font(ModeIconFont.chevron).foregroundStyle(ModeColor.textPrimary) }
            }
            .frame(width: size, height: size)
            .background(playing ? ModeColor.accent : ModeColor.cardRaised, in: .circle)
            .frame(minWidth: ModeSize.touch, minHeight: ModeSize.touch)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(label))
    }
}

/// One voice to choose: its play button, its name with a tag, what it sounds like, and the radio. Previewing
/// never selects a voice; tapping the row does.
public struct ModeVoiceChoiceRow: View {
    private let name: String
    private let traits: String
    private let tag: String?
    private let tagTone: ModeVoiceTag.Tone
    private let selected: Bool
    private let playing: Bool
    private let playLabel: String
    private let barColor: Color
    private let minHeight: CGFloat
    private let onSelect: () -> Void
    private let onPlay: () -> Void

    public init(name: String, traits: String, tag: String? = nil, tagTone: ModeVoiceTag.Tone = .orange, selected: Bool, playing: Bool,
                playLabel: String, barColor: Color = ModeColor.brand, minHeight: CGFloat = ModeSize.voiceChoiceRow,
                onSelect: @escaping () -> Void, onPlay: @escaping () -> Void) {
        self.name = name; self.traits = traits; self.tag = tag; self.tagTone = tagTone
        self.selected = selected; self.playing = playing; self.playLabel = playLabel
        self.barColor = barColor; self.minHeight = minHeight
        self.onSelect = onSelect; self.onPlay = onPlay
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            ModePlayButton(playLabel, playing: playing, barColor: barColor, action: onPlay)
                .padding(.horizontal, -(ModeSize.touch - ModeSize.playButton) / 2)
            Button(action: onSelect) {
                HStack(spacing: ModeSpacing.s) {
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs + 1) {
                        ViewThatFits(in: .horizontal) {
                            HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) { voiceName.fixedSize(); badge }
                            VStack(alignment: .leading, spacing: ModeSpacing.xxs) { voiceName; badge }
                        }
                        if !traits.isEmpty {
                            Text(traits).modeText(.footnote, color: ModeColor.textSecondary)
                                .fixedSize(horizontal: false, vertical: true)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    ModeRadio(selected: selected)
                }
                .padding(.vertical, ModeSpacing.s)
                .frame(minHeight: minHeight)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(selected ? [.isSelected] : [])
        }
        .frame(minHeight: minHeight)
    }

    private var voiceName: some View { Text(name).modeText(.calloutEmphasis).fixedSize(horizontal: false, vertical: true) }
    @ViewBuilder private var badge: some View {
        if let tag { ModeVoiceTag(tag, tone: tagTone) }
    }
}

/// A short state beside a provider ("In use", "Ready", "Off").
public struct ModeStatusPill: View {
    public enum Tone: Sendable { case success, accent, neutral }

    private let text: String
    private let tone: Tone

    public init(_ text: String, tone: Tone) {
        self.text = text
        self.tone = tone
    }

    public var body: some View {
        Text(text)
            .modeText(.captionEmphasis, color: foreground)
            .padding(.horizontal, ModeSpacing.m)
            .padding(.vertical, ModeSpacing.xs)
            .background(background, in: .capsule)
            .fixedSize()
    }

    private var foreground: Color {
        switch tone {
        case .success: ModeColor.success
        case .accent: ModeColor.tint
        case .neutral: ModeColor.textPrimary
        }
    }

    private var background: Color {
        switch tone {
        case .success: ModeColor.tagGreen
        case .accent: ModeColor.tintSoft
        case .neutral: ModeColor.cardRaised
        }
    }
}

/// A voice's face: its initial in its colour on a soft circle of the same colour (the same voice always gets the
/// same one), with a check when it is the one chosen.
public struct ModeVoiceAvatar: View {
    private let name: String
    private let selected: Bool
    private let size: CGFloat
    private static let palette = ModeColor.avatarPairs

    public init(name: String, selected: Bool, size: CGFloat = ModeSize.tile) {
        self.name = name
        self.selected = selected
        self.size = size
    }

    public var body: some View {
        // FNV-1a over the name: the same voice keeps its colours, and names spread across the palette.
        let hash = name.utf8.reduce(UInt32(2_166_136_261)) { ($0 ^ UInt32($1)) &* 16_777_619 }
        let pair = Self.palette[Int((hash >> 16) % UInt32(Self.palette.count))]
        Text(String(name.prefix(1)).uppercased())
            .modeText(.section, color: pair.1)
            .frame(width: size, height: size)
            .background(pair.0.opacity(0.16), in: .circle)
            .overlay(alignment: .bottomTrailing) {
                if selected {
                    ModeIcon.checkmark.image.font(ModeIconFont.chevron.weight(.heavy)).imageScale(.small).foregroundStyle(ModeColor.onAccent)
                        .frame(width: size * 0.44, height: size * 0.44)
                        .background(ModeColor.accent, in: .circle)
                        .overlay(Circle().strokeBorder(ModeColor.card, lineWidth: ModeSize.separator * 2))
                        .offset(x: size * 0.06, y: size * 0.06)
                }
            }
            .accessibilityHidden(true)
    }
}

/// Plays a voice: a play triangle on a white circle; while it plays, a stop square inside a turning ring.
public struct ModeVoicePlayButton: View {
    private let label: String
    private let playing: Bool
    private let action: () -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(_ label: String, playing: Bool, action: @escaping () -> Void) {
        self.label = label
        self.playing = playing
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            ZStack {
                Circle().fill(ModeColor.card).modeShadow(in: Circle())
                if playing {
                    Circle().stroke(ModeColor.track, lineWidth: ModeSize.smallBar)
                    TimelineView(.animation(paused: reduceMotion)) { context in
                        Circle().trim(from: 0, to: 0.3)
                            .stroke(ModeColor.accent, style: StrokeStyle(lineWidth: ModeSize.smallBar, lineCap: .round))
                            .rotationEffect(.degrees(reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate * 360))
                    }
                    RoundedRectangle(cornerRadius: ModeSize.talkBarRadius).fill(ModeColor.accent)
                        .frame(width: ModeSize.playButton * 0.28, height: ModeSize.playButton * 0.28)
                } else {
                    ModeIcon.play.image.font(ModeIconFont.chevron).foregroundStyle(ModeColor.textPrimary)
                }
            }
            .frame(width: ModeSize.playButton, height: ModeSize.playButton)
            .frame(width: ModeSize.touch, height: ModeSize.touch)
            .contentShape(.circle)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(label))
        .accessibilityAddTraits(playing ? .isSelected : [])
    }
}

/// One voice to choose, with its face: avatar, name with a tag, what it sounds like, and its play button.
/// The chosen one sits on raised grey; tapping the row chooses it.
public struct ModeVoiceRow: View {
    private let name: String
    private let traits: String
    private let tag: String?
    private let selected: Bool
    private let playing: Bool
    private let playLabel: String
    private let onSelect: () -> Void
    private let onPlay: () -> Void

    public init(name: String, traits: String, tag: String?, selected: Bool, playing: Bool, playLabel: String,
                onSelect: @escaping () -> Void, onPlay: @escaping () -> Void) {
        self.name = name
        self.traits = traits
        self.tag = tag
        self.selected = selected
        self.playing = playing
        self.playLabel = playLabel
        self.onSelect = onSelect
        self.onPlay = onPlay
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            Button(action: onSelect) {
                HStack(spacing: ModeSpacing.m) {
                    ModeVoiceAvatar(name: name, selected: selected)
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
                            Text(name).modeText(.calloutEmphasis).lineLimit(1)
                            if let tag { ModeVoiceTag(tag, tone: .green) }
                        }
                        if !traits.isEmpty { Text(traits).modeText(.footnote, color: ModeColor.textSecondary).lineLimit(2) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(selected ? [.isSelected] : [])
            ModeVoicePlayButton(playLabel, playing: playing, action: onPlay)
        }
        .padding(.horizontal, ModeSpacing.s)
        .frame(minHeight: ModeSize.voicePickerRow + ModeSpacing.s)
        .background(selected ? ModeColor.cardRaised : .clear, in: .rect(cornerRadius: ModeRadius.bubble))
    }
}

/// While a sample plays: whose voice it is, the line it says, and the sound.
public struct ModeNowPlaying: View {
    private let name: String
    private let title: String
    private let line: String

    public init(name: String, title: String, line: String) {
        self.name = name
        self.title = title
        self.line = line
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeVoiceAvatar(name: name, selected: false)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.subheadlineEmphasis)
                Text(line).modeText(.footnote, color: ModeColor.textSecondary).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            ModeTalkWave(level: 0, speaking: true, bars: 7, height: ModeSize.cardWave + ModeSpacing.xs, color: ModeColor.textPrimary, fades: false)
                .fixedSize()
        }
        .padding(ModeSpacing.m)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.bubble))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.bubble))
    }
}

/// Mo at the head of setup's Talk to Mode: listening, or idle while it speaks.
public struct ModeVoiceOrb: View {
    private let speaking: Bool
    private let size: CGFloat

    public init(speaking: Bool, size: CGFloat = ModeSize.orb, compact: Bool = false, ringCount: Int? = nil) {
        self.speaking = speaking
        self.size = size
    }

    public var body: some View {
        ModeMo(speaking ? .idle : .listening, size: size)
    }
}

// MARK: - Talking mode

/// Mo at the centre of talking mode. While listening it sits on a soft orange disc that swells with the user's
/// voice ([level] 0…1); otherwise it breathes gently. Holds still with Reduce Motion.
public struct ModeTalkMo: View {
    private let mood: ModeMo.Mood
    private let level: Double
    private let size: CGFloat
    private let halo: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(_ mood: ModeMo.Mood, level: Double = 0, size: CGFloat, halo: Bool = false) {
        self.mood = mood
        self.level = level
        self.size = size
        self.halo = halo
    }

    public var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { context in
            let t = reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate
            let breath = 0.015 * sin(t * 1.8)
            let swell = min(max(level, 0), 1) * 0.08
            ZStack {
                if halo {
                    Circle().fill(ModeColor.tintSoft)
                        .frame(width: ModeSize.talkHalo, height: ModeSize.talkHalo)
                        .scaleEffect(1 + swell + breath)
                }
                ModeMo(mood, size: size).scaleEffect(1 + breath)
            }
        }
        .accessibilityHidden(true)
    }
}

/// The voice as bars across the screen: 4-point bars that follow the microphone's [level] while listening and sway
/// while Mode speaks; the outer four on each side stay small and grey.
public struct ModeTalkWave: View {
    private let level: Double
    private let speaking: Bool
    private let bars: Int
    private let height: CGFloat
    private let color: Color
    private let fades: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private static let shape: [Double] = [0.56, 0.94, 0.7, 0.41, 0.59, 1.0, 0.76, 0.5, 0.29, 0.65, 0.91, 0.44, 0.24, 0.21, 0.35, 0.5, 0.79]
    private static let edge: [Double] = [0.12, 0.15, 0.21, 0.32]

    public init(level: Double, speaking: Bool, bars: Int = 34, height: CGFloat = ModeSize.talkWaveHeight,
                color: Color = ModeColor.brand, fades: Bool = true) {
        self.level = level
        self.speaking = speaking
        self.bars = bars
        self.height = height
        self.color = color
        self.fades = fades
    }

    public var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { context in
            let t = reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate
            let energy = speaking ? 0.8 : 0.35 + min(max(level, 0), 1) * 0.65
            HStack(spacing: fades ? ModeSpacing.xs : ModeSpacing.xxs) {
                ForEach(0..<bars, id: \.self) { i in
                    let outer = fades ? min(i, bars - 1 - i) : Self.edge.count
                    if outer < Self.edge.count {
                        RoundedRectangle(cornerRadius: ModeSize.talkBarRadius).fill(ModeColor.track)
                            .frame(width: ModeSize.talkBar, height: max(ModeSize.talkBar, height * Self.edge[outer]))
                    } else {
                        let motion = reduceMotion ? 1 : 0.7 + 0.3 * sin(t * (speaking ? 5 : 8) + Double(i) * 0.9)
                        RoundedRectangle(cornerRadius: ModeSize.talkBarRadius).fill(color)
                            .frame(width: fades ? ModeSize.talkBar : ModeSize.smallBar,
                                   height: max(ModeSize.talkBar, height * Self.shape[i % Self.shape.count] * energy * motion))
                    }
                }
            }
            .frame(height: height)
        }
        .accessibilityHidden(true)
    }
}

/// What talking mode is doing, in a pill at the top ("Listening" with a red dot, "Mode needs you" on blue).
public struct ModeTalkStatus: View {
    public enum Tone: Sendable { case plain, info, success }

    private let text: LocalizedStringResource
    private let dot: Color?
    private let tone: Tone

    public init(_ text: LocalizedStringResource, dot: Color?, tone: Tone = .plain) {
        self.text = text
        self.dot = dot
        self.tone = tone
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            if let dot { Circle().fill(dot).frame(width: ModeSize.voiceDot, height: ModeSize.voiceDot) }
            Text(text).modeText(.smallEmphasis, color: foreground).fontWeight(tone == .plain ? .semibold : .bold)
        }
        .padding(.horizontal, ModeSpacing.m + ModeSpacing.xxs)
        .frame(height: ModeSize.talkStatus)
        .background(background, in: .capsule)
        .modeShadow(in: Capsule(), lifted: tone == .plain)
        .fixedSize()
    }

    private var foreground: Color {
        switch tone {
        case .plain: ModeColor.textPrimary
        case .info: ModeColor.infoText
        case .success: ModeColor.success
        }
    }

    private var background: Color {
        switch tone {
        case .plain: ModeColor.card
        case .info: ModeColor.infoSoft
        case .success: ModeColor.tagGreen
        }
    }
}

/// A short live caption label ("Hearing you live") with a red dot.
public struct ModeLiveLabel: View {
    private let text: String

    public init(_ text: String) { self.text = text }

    public var body: some View {
        HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
            Circle().fill(ModeColor.danger).frame(width: ModeSize.talkLiveDot, height: ModeSize.talkLiveDot)
            Text(text.uppercased()).modeText(.caption, color: ModeColor.tint).fontWeight(.bold).tracking(0.3)
        }
        .padding(.horizontal, ModeSpacing.s + ModeSpacing.xxs)
        .frame(height: ModeSize.talkLiveLabel)
        .background(ModeColor.tintSoft, in: .capsule)
    }
}

/// What the user said, as an ink bubble on the right.
public struct ModeTalkBubble: View {
    private let text: String

    public init(_ text: String) { self.text = text }

    public var body: some View {
        Text(text).modeText(.spoken, color: ModeColor.onAccent)
            .padding(.horizontal, ModeSpacing.l)
            .padding(.vertical, ModeSpacing.m)
            .background(ModeColor.accent, in: UnevenRoundedRectangle(topLeadingRadius: ModeRadius.bubble, bottomLeadingRadius: ModeRadius.bubble,
                                                                     bottomTrailingRadius: ModeRadius.bubbleTail, topTrailingRadius: ModeRadius.bubble))
            .frame(maxWidth: ModeSize.talkBubbleMax, alignment: .trailing)
            .frame(maxWidth: .infinity, alignment: .trailing)
    }
}

/// What Mode said out loud: a speaker, "MODE · SPEAKING", the line, and bars while it speaks.
public struct ModeTalkSays: View {
    private let label: LocalizedStringResource
    private let text: String
    private let speaking: Bool

    public init(label: LocalizedStringResource, text: String, speaking: Bool) {
        self.label = label
        self.text = text
        self.speaking = speaking
    }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.m) {
            ModeIcon.speaker.image.font(ModeIconFont.small).foregroundStyle(ModeColor.tint)
                .frame(width: ModeSize.talkSaysIcon, height: ModeSize.talkSaysIcon)
                .background(ModeColor.tintSoft, in: .circle)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                Text(label).modeText(.caption, color: ModeColor.tint).fontWeight(.bold).tracking(0.3)
                Text(text).modeText(.spoken).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if speaking { ModeWaveBars(height: ModeSize.cardWave) }
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.vertical, ModeSpacing.m + ModeSpacing.xxs)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.talkCard))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.talkCard))
        .accessibilityElement(children: .combine)
    }
}

/// What Mode is doing now, while it works in the background: the step, a line under it, and Mo at work.
public struct ModeTalkWorking: View {
    private let title: String
    private let detail: String

    public init(title: String, detail: String) {
        self.title = title
        self.detail = detail
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.calloutEmphasis).lineLimit(2)
                Text(detail).modeText(.footnote, color: ModeColor.textSecondary).lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            ModeMo(.working, size: ModeSize.talkMoCard)
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.vertical, ModeSpacing.m + ModeSpacing.xxs)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.talkCard))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.talkCard))
        .accessibilityElement(children: .combine)
    }
}

/// A spoken question's answer to tap: soft blue, or solid blue when it's the one being said.
public struct ModeTalkOption: View {
    private let title: String
    private let chosen: Bool
    private let action: () -> Void

    public init(_ title: String, chosen: Bool, action: @escaping () -> Void) {
        self.title = title
        self.chosen = chosen
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(title).modeText(.headline, color: chosen ? ModeColor.onInfo : ModeColor.infoText)
                .padding(.horizontal, ModeSpacing.xl + ModeSpacing.xxs)
                .frame(minHeight: ModeSize.talkOption)
                .background(chosen ? ModeColor.info : ModeColor.infoSoft, in: .capsule)
        }
        .buttonStyle(ModePressStyle())
    }
}

/// The voice couldn't be used: a red dot, what happened, and a way to fix it.
public struct ModeVoiceNotice: View {
    private let text: String
    private let actionTitle: LocalizedStringResource
    private let action: () -> Void

    public init(_ text: String, actionTitle: LocalizedStringResource, action: @escaping () -> Void) {
        self.text = text
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
            Circle().fill(ModeColor.danger).frame(width: ModeSize.noticeDot, height: ModeSize.noticeDot)
            Text(text).modeText(.smallEmphasis).frame(maxWidth: .infinity, alignment: .leading)
            Button(action: action) {
                Text(actionTitle).modeText(.footnoteEmphasis).fontWeight(.bold).lineLimit(1)
                    .padding(.horizontal, ModeSpacing.m)
                    .frame(minHeight: ModeSize.talkStatus)
                    .background(ModeColor.card, in: .capsule)
                    .frame(minHeight: ModeSize.touch)
                    .contentShape(.rect)
            }
            .buttonStyle(ModePressStyle())
            .fixedSize()
        }
        .padding(.leading, ModeSpacing.m + ModeSpacing.xxs)
        .padding(.trailing, ModeSpacing.s + ModeSpacing.xxs)
        .padding(.vertical, ModeSpacing.xxs)
        .background(ModeColor.tileRed, in: .rect(cornerRadius: ModeRadius.bubble))
    }
}

/// A round white button with a label under it (Type, Cancel, Later) beside the big stop button.
public struct ModeTalkSideButton: View {
    private let icon: ModeIcon
    private let label: LocalizedStringResource
    private let action: () -> Void

    public init(_ icon: ModeIcon, label: LocalizedStringResource, action: @escaping () -> Void) {
        self.icon = icon
        self.label = label
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            VStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
                icon.image.font(ModeIconFont.small.weight(.semibold))
                    .frame(width: ModeSize.talkSide, height: ModeSize.talkSide)
                    .background(ModeColor.card, in: .circle)
                    .modeShadow(in: Circle())
                Text(label).modeText(.caption).fontWeight(.semibold)
            }
            .foregroundStyle(ModeColor.textPrimary)
            .frame(width: ModeSize.talkSideButtonWidth)
            .contentShape(.rect)
        }
        .buttonStyle(ModePressStyle())
    }
}

/// The big round button in talking mode: a stop square while listening (Done), a mic when it can listen again.
public struct ModeTalkMicButton: View {
    private let listening: Bool
    private let label: LocalizedStringResource
    private let action: () -> Void

    public init(listening: Bool, label: LocalizedStringResource, action: @escaping () -> Void) {
        self.listening = listening
        self.label = label
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Group {
                if listening {
                    RoundedRectangle(cornerRadius: ModeSize.talkStopRadius).fill(ModeColor.onAccent)
                        .frame(width: ModeSize.talkStopSquare, height: ModeSize.talkStopSquare)
                } else {
                    ModeIcon.microphone.image.font(ModeIconFont.hero).foregroundStyle(ModeColor.onAccent)
                }
            }
            .frame(width: ModeSize.talkMic, height: ModeSize.talkMic)
            .background(ModeColor.accent, in: .circle)
            .modeShadow(in: Circle())
        }
        .buttonStyle(ModePressStyle())
        .accessibilityLabel(Text(label))
    }
}

/// Mo asking to hear you, with the blue microphone badge (Let Mode hear you).
public struct ModeTalkMicAsk: View {
    public init() {}

    public var body: some View {
        ModeMo(.yourTurn, size: ModeSize.talkMoMic)
            .frame(width: ModeSize.talkMoMic + ModeSpacing.s, height: ModeSize.talkMoMic + ModeSpacing.s)
            .overlay(alignment: .bottomTrailing) {
                ModeIcon.microphone.image.font(ModeIconFont.large)
                    .foregroundStyle(ModeColor.onInfo)
                    .frame(width: ModeSize.talkMicBadge, height: ModeSize.talkMicBadge)
                    .background(ModeColor.info, in: .circle)
                    .modeShadow(in: Circle())
                    .offset(x: -ModeSpacing.xxs, y: -ModeSpacing.xs)
            }
            .accessibilityHidden(true)
    }
}

// MARK: - The bottom sheet

public extension EnvironmentValues {
    /// Closes the [ModeBottomSheet] this view sits in (with its slide down); nil outside one.
    @Entry var modeSheetDismiss: (() -> Void)?
}

/// A bottom sheet as on the boards: edge to edge, 34-point top corners, a handle, over a dark scrim. It rises
/// when shown and slides away when dragged down by its top, when the scrim is tapped, or through
/// `modeSheetDismiss`; [onDismiss] runs once it's gone. Its content keeps clear of the keyboard.
public struct ModeBottomSheet<Content: View>: View {
    private let height: CGFloat
    private let onDismiss: () -> Void
    private let content: Content
    @State private var shown = false
    @State private var drag: CGFloat = 0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(height: CGFloat, onDismiss: @escaping () -> Void, @ViewBuilder content: () -> Content) {
        self.height = height
        self.onDismiss = onDismiss
        self.content = content()
    }

    public var body: some View {
        ZStack(alignment: .bottom) {
            ModeColor.voiceScrim
                .opacity(shown ? 1 : 0)
                .ignoresSafeArea()
                .onTapGesture(perform: close)
                .accessibilityHidden(true)
            if shown {
                content
                    .environment(\.modeSheetDismiss, close)
                    .frame(maxWidth: .infinity)
                    .frame(maxHeight: height)
                    .background {
                        UnevenRoundedRectangle(topLeadingRadius: ModeRadius.sheet, topTrailingRadius: ModeRadius.sheet)
                            .fill(ModeColor.page)
                            .shadow(color: ModeColor.moInk.opacity(0.25), radius: ModeSpacing.xl, y: -ModeSpacing.s - ModeSpacing.xxs)
                            .ignoresSafeArea(.container, edges: .bottom)
                    }
                    .overlay(alignment: .top) { handle }
                    .offset(y: drag)
                    .transition(.move(edge: .bottom))
                    .accessibilityAddTraits(.isModal)
            }
        }
        .onAppear { withAnimation(reduceMotion ? nil : ModeMotion.standard) { shown = true } }
    }

    /// The handle, and the strip around it that drags the sheet down.
    private var handle: some View {
        Capsule().fill(ModeColor.ring)
            .frame(width: ModeSize.sheetHandleWidth, height: ModeSize.sheetHandleHeight)
            .padding(.top, ModeSpacing.s)
            .frame(maxWidth: .infinity, minHeight: ModeSize.touch, alignment: .top)
            .contentShape(.rect)
            .gesture(
                DragGesture()
                    .onChanged { drag = max(0, $0.translation.height) }
                    .onEnded { value in
                        if value.translation.height > ModeSize.sheetDismissDrag || value.predictedEndTranslation.height > height / 2 {
                            close()
                        } else {
                            withAnimation(ModeMotion.quick) { drag = 0 }
                        }
                    }
            )
            .accessibilityHidden(true)
    }

    private func close() {
        withAnimation(reduceMotion ? nil : ModeMotion.standard) {
            shown = false
        } completion: {
            onDismiss()
        }
    }
}

public extension View {
    /// Shows [content] in a [ModeBottomSheet] above everything (the navigation bar too) while [item] is set.
    func modeBottomSheet<Item: Identifiable, Content: View>(item: Binding<Item?>, height: CGFloat, onDismiss: @escaping () -> Void = {},
                                                            @ViewBuilder content: @escaping (Item) -> Content) -> some View {
        modifier(ModeBottomSheetPresenter(item: item, height: height, onDismiss: onDismiss, sheet: content))
    }
}

/// Mirrors [item] into a full-screen cover that appears and goes without its own slide (the sheet inside animates).
private struct ModeBottomSheetPresenter<Item: Identifiable, Sheet: View>: ViewModifier {
    @Binding var item: Item?
    let height: CGFloat
    let onDismiss: () -> Void
    let sheet: (Item) -> Sheet
    @State private var presented: Item?

    func body(content: Content) -> some View {
        content
            .onAppear { plain { presented = item } }
            .onChange(of: item?.id) { _, _ in plain { presented = item } }
            .fullScreenCover(item: $presented, onDismiss: onDismiss) { value in
                ModeBottomSheet(height: height, onDismiss: { plain { presented = nil; item = nil } }) { sheet(value) }
                    .presentationBackground(.clear)
            }
    }

    private func plain(_ change: () -> Void) {
        var transaction = Transaction()
        transaction.disablesAnimations = true
        withTransaction(transaction, change)
    }
}
