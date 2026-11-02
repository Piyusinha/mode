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

/// One voice to choose: its sample button (or a leading view), its name with a tag, what it sounds like, and a radio.
public struct ModeVoiceRow<Leading: View>: View {
    private let name: String
    private let traits: String
    private let tag: String?
    private let selected: Bool
    private let leading: Leading
    private let onSelect: () -> Void

    public init(name: String, traits: String, tag: String?, selected: Bool, onSelect: @escaping () -> Void, @ViewBuilder leading: () -> Leading) {
        self.name = name
        self.traits = traits
        self.tag = tag
        self.selected = selected
        self.leading = leading()
        self.onSelect = onSelect
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            leading
            Button(action: onSelect) {
                HStack(spacing: ModeSpacing.m) {
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        HStack(spacing: ModeSpacing.s) {
                            Text(name).modeText(.bodyEmphasis).lineLimit(1)
                            if let tag { ModeTag(tag, background: ModeColor.tintSoft, foreground: ModeColor.tint).fixedSize() }
                        }
                        if !traits.isEmpty { Text(traits).modeText(.footnote, color: ModeColor.textSecondary).lineLimit(2) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    ModeRadio(selected: selected)
                }
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .accessibilityAddTraits(selected ? [.isSelected] : [])
        }
        .frame(minHeight: ModeSize.rowMin + ModeSpacing.m)
    }
}

/// The big round mark while Mode listens (sound bars) or speaks (a speaker), with soft rings around it.
public struct ModeVoiceOrb: View {
    private let speaking: Bool
    private let size: CGFloat

    public init(speaking: Bool, size: CGFloat = ModeSize.orb) {
        self.speaking = speaking
        self.size = size
    }

    public var body: some View {
        ZStack {
            ForEach(Array(ModeOpacity.orbRings.enumerated()), id: \.offset) { i, opacity in
                Circle().fill(ModeColor.accent.opacity(speaking ? opacity / 2 : opacity))
                    .frame(width: size + ModeSize.orbRing * CGFloat(2 * (ModeOpacity.orbRings.count - i)),
                           height: size + ModeSize.orbRing * CGFloat(2 * (ModeOpacity.orbRings.count - i)))
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
