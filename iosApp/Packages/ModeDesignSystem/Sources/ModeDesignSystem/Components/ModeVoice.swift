import SwiftUI

/// Bars of a sound, for a voice that is playing or a microphone that is listening.
public struct ModeWaveBars: View {
    private let color: Color
    private let height: CGFloat
    private let count: Int
    private static let shape: [CGFloat] = [0.45, 0.9, 0.6, 1.0, 0.5, 0.75, 0.4]

    public init(color: Color = ModeColor.tint, height: CGFloat = ModeSize.waveHeight, count: Int = 5) {
        self.color = color
        self.height = height
        self.count = count
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.xxs) {
            ForEach(0..<min(count, Self.shape.count), id: \.self) { i in
                Capsule().fill(color).frame(width: ModeSize.waveBar, height: height * Self.shape[i])
            }
        }
        .frame(height: height)
        .accessibilityHidden(true)
    }
}

/// Plays a voice's sample; while it plays, the button is filled and shows the sound.
public struct ModePlayButton: View {
    private let label: String
    private let playing: Bool
    private let size: CGFloat
    private let action: () -> Void

    public init(_ label: String, playing: Bool, size: CGFloat = ModeSize.playButton, action: @escaping () -> Void) {
        self.label = label
        self.playing = playing
        self.size = size
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Group {
                if playing { ModeWaveBars(color: ModeColor.onAccent, height: size * 0.36, count: 4) }
                else { ModeIcon.play.image.font(.system(size: size * 0.34)).foregroundStyle(ModeColor.tint) }
            }
            .frame(width: size, height: size)
            .background(playing ? ModeColor.accent : ModeColor.tintSoft, in: .circle)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(label))
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
        case .success: ModeColor.tagGreenText
        case .accent: ModeColor.tint
        case .neutral: ModeColor.textSecondary
        }
    }

    private var background: Color {
        switch tone {
        case .success: ModeColor.tagGreen
        case .accent: ModeColor.tintSoft
        case .neutral: ModeColor.fill
        }
    }
}

/// A voice's face: its initial on a soft gradient picked from its name (the same voice always gets the same one),
/// with a check when it is the one chosen.
public struct ModeVoiceAvatar: View {
    private let name: String
    private let selected: Bool
    private let size: CGFloat
    private static let palette = ModeColor.avatarPairs

    public init(name: String, selected: Bool, size: CGFloat = ModeSize.voiceAvatar) {
        self.name = name
        self.selected = selected
        self.size = size
    }

    public var body: some View {
        // FNV-1a over the name: the same voice keeps its colours, and names spread across the palette.
        let hash = name.utf8.reduce(UInt32(2_166_136_261)) { ($0 ^ UInt32($1)) &* 16_777_619 }
        let pair = Self.palette[Int((hash >> 16) % UInt32(Self.palette.count))]
        Text(String(name.prefix(1)).uppercased())
            .modeText(.headline, color: ModeColor.onAccent)
            .frame(width: size, height: size)
            .background(LinearGradient(colors: [pair.0, pair.1], startPoint: .topLeading, endPoint: .bottomTrailing), in: .circle)
            .overlay(alignment: .bottomTrailing) {
                if selected {
                    ModeIcon.checkmark.image.font(.system(size: size * 0.2, weight: .bold)).foregroundStyle(ModeColor.onAccent)
                        .frame(width: size * 0.44, height: size * 0.44)
                        .background(ModeColor.accent, in: .circle)
                        .overlay(Circle().strokeBorder(ModeColor.card, lineWidth: ModeSize.separator * 2))
                        .offset(x: size * 0.06, y: size * 0.06)
                }
            }
            .accessibilityHidden(true)
    }
}

/// Plays a voice: a play triangle; while it plays, a stop square inside a turning ring. Tap again to stop.
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
                if playing {
                    Circle().stroke(ModeColor.tintSoft, lineWidth: ModeSize.waveBar)
                    TimelineView(.animation(paused: reduceMotion)) { context in
                        Circle().trim(from: 0, to: 0.3)
                            .stroke(ModeColor.accent, style: StrokeStyle(lineWidth: ModeSize.waveBar, lineCap: .round))
                            .rotationEffect(.degrees(reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate * 360))
                    }
                    RoundedRectangle(cornerRadius: ModeSize.waveBar).fill(ModeColor.accent)
                        .frame(width: ModeSize.playButton * 0.28, height: ModeSize.playButton * 0.28)
                } else {
                    Circle().fill(ModeColor.tintSoft)
                    ModeIcon.play.image.font(.system(size: ModeSize.playButton * 0.34)).foregroundStyle(ModeColor.tint)
                }
            }
            .frame(width: ModeSize.playButton, height: ModeSize.playButton)
            .contentShape(.circle)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(label))
        .accessibilityAddTraits(playing ? .isSelected : [])
    }
}

/// One voice to choose: its avatar, name with a tag, what it sounds like, and its play button. The chosen one is
/// tinted; tapping the row chooses it.
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
                        HStack(spacing: ModeSpacing.s) {
                            Text(name).modeText(.bodyEmphasis).lineLimit(1)
                            if let tag { ModeTag(tag, background: ModeColor.tintSoft, foreground: ModeColor.tint).fixedSize() }
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
        .frame(minHeight: ModeSize.rowMin + ModeSpacing.l)
        .background(selected ? ModeColor.tintSoft : .clear, in: .rect(cornerRadius: ModeRadius.field))
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
            ModeVoiceAvatar(name: name, selected: false, size: ModeSize.badge + ModeSpacing.s)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.subheadline).fontWeight(.semibold)
                Text(line).modeText(.footnote, color: ModeColor.textSecondary).lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            ModeTalkWave(level: 0, speaking: true, bars: 7, height: ModeSize.waveHeight)
        }
        .padding(ModeSpacing.m)
        .glassEffect(.regular, in: .rect(cornerRadius: ModeRadius.field))
    }
}

/// The big round mark while Mode listens (sound bars) or speaks (a speaker), with soft rings around it.
public struct ModeVoiceOrb: View {
    private let speaking: Bool
    private let size: CGFloat
    private let ring: CGFloat
    private let rings: [Double]

    /// [compact]: two thin rings, for screens that need the room (setup's Talk to Mode).
    public init(speaking: Bool, size: CGFloat = ModeSize.orb, compact: Bool = false) {
        self.speaking = speaking
        self.size = size
        ring = compact ? ModeSize.orbRingCompact : ModeSize.orbRing
        rings = compact ? Array(ModeOpacity.orbRings.suffix(2)) : ModeOpacity.orbRings
    }

    public var body: some View {
        ZStack {
            ForEach(Array(rings.enumerated()), id: \.offset) { i, opacity in
                Circle().fill(ModeColor.accent.opacity(speaking ? opacity / 2 : opacity))
                    .frame(width: size + ring * CGFloat(2 * (rings.count - i)),
                           height: size + ring * CGFloat(2 * (rings.count - i)))
            }
            Circle().fill(ModeColor.accent).frame(width: size, height: size)
            if speaking {
                ModeIcon.speaker.image.font(.system(size: size * 0.36)).foregroundStyle(ModeColor.onAccent)
            } else {
                ModeWaveBars(color: ModeColor.onAccent, height: size * 0.34, count: 7)
            }
        }
        .accessibilityHidden(true)
    }
}

// MARK: - Talking mode

/// The living centre of talking mode: a glowing sphere that breathes while Mode speaks and swells with the
/// user's voice while it listens ([level] 0…1, the microphone's loudness). Holds still with Reduce Motion.
public struct ModeTalkOrb: View {
    private let level: Double
    private let speaking: Bool
    private let size: CGFloat
    private let showsRing: Bool
    private let icon: ModeIcon?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    public init(level: Double, speaking: Bool, size: CGFloat, showsRing: Bool = true, icon: ModeIcon? = nil) {
        self.level = level
        self.speaking = speaking
        self.size = size
        self.showsRing = showsRing
        self.icon = icon
    }

    public var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { context in
            let t = reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate
            let breath = speaking ? 0.04 * sin(t * 3.2) + 0.02 * sin(t * 7.1) : 0.015 * sin(t * 1.6)
            let swell = speaking ? 0 : min(max(level, 0), 1) * 0.16
            let scale = 1 + breath + swell
            ZStack {
                if showsRing {
                    Circle()
                        .strokeBorder(ModeColor.accent.opacity(0.25 + swell), lineWidth: ModeSize.separator * 2)
                        .frame(width: size * 1.34, height: size * 1.34)
                        .scaleEffect(1 + swell * 0.6 + breath * 0.5)
                }
                Circle()
                    .fill(RadialGradient(colors: [ModeColor.orbLight, ModeColor.accent, ModeColor.orbDeep],
                                         center: .center, startRadius: 0, endRadius: size * 0.62))
                    .overlay(
                        // A pink light drifts around the bottom of the sphere.
                        Circle().fill(RadialGradient(colors: [ModeColor.orbPink, ModeColor.orbPink.opacity(0)], center: .center, startRadius: 0, endRadius: size * 0.3))
                            .frame(width: size * 0.62, height: size * 0.62)
                            .offset(x: size * 0.14 + size * 0.06 * cos(t * 0.7), y: size * 0.16 + size * 0.05 * sin(t * 0.9))
                    )
                    .overlay(
                        // The highlight, top left, like light on glass.
                        Circle().fill(RadialGradient(colors: [Color.white.opacity(0.8), .clear], center: .center, startRadius: 0, endRadius: size * 0.22))
                            .frame(width: size * 0.5, height: size * 0.5)
                            .offset(x: -size * 0.18, y: -size * 0.2)
                    )
                    .clipShape(.circle)
                    .frame(width: size, height: size)
                    .shadow(color: ModeColor.accent.opacity(0.35 + swell), radius: size * (0.22 + swell * 0.4))
                    .shadow(color: ModeColor.orbPink.opacity(0.18), radius: size * 0.5)
                    .scaleEffect(scale)
                if let icon {
                    icon.image.font(.system(size: size * 0.26, weight: .semibold)).foregroundStyle(ModeColor.onAccent)
                }
            }
        }
        .accessibilityHidden(true)
    }
}

/// The voice as bars across the screen: they follow the microphone's [level] while listening, sway softly while
/// Mode speaks, and fade at the edges.
public struct ModeTalkWave: View {
    private let level: Double
    private let speaking: Bool
    private let bars: Int
    private let height: CGFloat
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    private static let shape: [Double] = [0.2, 0.35, 0.5, 0.8, 0.55, 0.95, 0.7, 0.4, 0.6, 1.0, 0.75, 0.5, 0.3, 0.65, 0.9, 0.45, 0.25]

    public init(level: Double, speaking: Bool, bars: Int = 34, height: CGFloat = ModeSize.talkWave) {
        self.level = level
        self.speaking = speaking
        self.bars = bars
        self.height = height
    }

    public var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30, paused: reduceMotion)) { context in
            let t = reduceMotion ? 0 : context.date.timeIntervalSinceReferenceDate
            let energy = speaking ? 0.55 : 0.12 + min(max(level, 0), 1) * 0.88
            HStack(spacing: ModeSpacing.xs) {
                ForEach(0..<bars, id: \.self) { i in
                    let edge = min(Double(i), Double(bars - 1 - i)) / 4
                    let fade = min(1, edge)
                    let motion = 0.6 + 0.4 * sin(t * (speaking ? 5 : 9) + Double(i) * 0.7)
                    let h = max(ModeSize.waveBar, height * Self.shape[i % Self.shape.count] * energy * motion * (0.4 + 0.6 * fade))
                    Capsule().fill(ModeColor.accent.opacity(0.35 + 0.55 * fade))
                        .frame(width: ModeSize.waveBar + 1, height: h)
                }
            }
            .frame(height: height)
        }
        .accessibilityHidden(true)
    }
}

/// A short live caption label ("Hearing you live") with a red dot.
public struct ModeLiveLabel: View {
    private let text: String

    public init(_ text: String) { self.text = text }

    public var body: some View {
        HStack(spacing: ModeSpacing.xs) {
            Circle().fill(ModeColor.stopRed).frame(width: ModeSize.liveDot * 0.75, height: ModeSize.liveDot * 0.75)
            Text(text.uppercased()).modeText(.captionEmphasis, color: ModeColor.tint).tracking(0.3)
        }
        .padding(.horizontal, ModeSpacing.m)
        .padding(.vertical, ModeSpacing.xs)
        .background(ModeColor.tintSoft, in: .capsule)
    }
}

/// A round glass button with a label under it (Type, Cancel, Later) beside the big mic.
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
            VStack(spacing: ModeSpacing.xs) {
                icon.image.font(.system(size: ModeSize.icon, weight: .semibold))
                    .frame(width: ModeSize.talkSide, height: ModeSize.talkSide)
                    .glassEffect(.regular.interactive(), in: .circle)
                Text(label).modeText(.captionEmphasis)
            }
            .frame(width: ModeSize.talkSideWidth)
        }
        .buttonStyle(.plain)
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
                    RoundedRectangle(cornerRadius: ModeRadius.chip / 2).fill(ModeColor.onAccent)
                        .frame(width: ModeSize.talkMic * 0.29, height: ModeSize.talkMic * 0.29)
                } else {
                    ModeIcon.microphone.image.font(.system(size: ModeSize.talkMic * 0.36, weight: .semibold)).foregroundStyle(ModeColor.onAccent)
                }
            }
            .frame(width: ModeSize.talkMic, height: ModeSize.talkMic)
            .background(ModeColor.accent, in: .circle)
            .shadow(color: ModeColor.accent.opacity(0.45), radius: ModeSpacing.l, y: ModeSpacing.m)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(label))
    }
}
