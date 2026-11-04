import SwiftUI

/// One tab in [ModeTabBar].
public struct ModeTabItem<ID: Hashable>: Identifiable {
    public let id: ID
    let icon: ModeIcon
    let title: LocalizedStringResource

    public init(_ id: ID, icon: ModeIcon, title: LocalizedStringResource) {
        self.id = id
        self.icon = icon
        self.title = title
    }
}

/// The bottom of the main screens, as on the Mo theme canvas: a white capsule of tabs (the selected
/// one in a raised-grey pill that slides between them) and the round ink Speak button beside it.
/// Anything passed as [player] sits above them.
public struct ModeTabBar<ID: Hashable, Player: View>: View {
    private let items: [ModeTabItem<ID>]
    @Binding private var selection: ID
    private let speakLabel: LocalizedStringResource
    private let onSpeak: () -> Void
    private let player: Player
    @Namespace private var lens

    public init(items: [ModeTabItem<ID>], selection: Binding<ID>, speakLabel: LocalizedStringResource, onSpeak: @escaping () -> Void,
                @ViewBuilder player: () -> Player) {
        self.items = items
        self._selection = selection
        self.speakLabel = speakLabel
        self.onSpeak = onSpeak
        self.player = player()
    }

    public var body: some View {
        VStack(spacing: ModeSpacing.s) {
            player
            HStack(spacing: ModeSpacing.m) {
                tabs
                ModeIconButton(.ask, label: speakLabel, prominent: true, size: ModeSize.speakButton, action: onSpeak)
            }
        }
        .padding(.horizontal, ModeSpacing.screenEdge)
    }

    private var tabs: some View {
        HStack(spacing: ModeSpacing.xxs) {
            ForEach(items) { item in
                let selected = item.id == selection
                Button {
                    withAnimation(ModeMotion.standard) { selection = item.id }
                } label: {
                    VStack(spacing: ModeSpacing.xxs) {
                        item.icon.image.font(ModeIconFont.large)
                        Text(item.title).modeText(.captionEmphasis, color: selected ? ModeColor.textPrimary : ModeColor.textSecondary)
                    }
                    .foregroundStyle(selected ? ModeColor.textPrimary : ModeColor.textSecondary)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background {
                        if selected {
                            Capsule().fill(ModeColor.cardRaised).matchedGeometryEffect(id: "lens", in: lens)
                        }
                    }
                    .contentShape(.capsule)
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(ModeSpacing.xs)
        .frame(height: ModeSize.tabBarHeight)
        .background(ModeColor.card, in: .capsule)
        .modeShadow(in: Capsule())
    }
}

/// The running task, as Home's dark card: Mo at work, what it's doing, the task, Pause and Stop,
/// and a bar per step. Also used above the tab bar on other tabs ([compact]).
public struct ModeMiniPlayer: View {
    private let title: String
    private let status: String
    private let paused: Bool
    private let progress: (done: Int, total: Int)?
    private let compact: Bool
    private let pauseLabel: LocalizedStringResource
    private let resumeLabel: LocalizedStringResource
    private let stopLabel: LocalizedStringResource
    private let onPauseToggle: () -> Void
    private let onStop: () -> Void
    private let onOpen: () -> Void

    public init(title: String, status: String, paused: Bool, progress: (done: Int, total: Int)? = nil, compact: Bool = false,
                pauseLabel: LocalizedStringResource, resumeLabel: LocalizedStringResource,
                stopLabel: LocalizedStringResource, onPauseToggle: @escaping () -> Void, onStop: @escaping () -> Void, onOpen: @escaping () -> Void) {
        self.title = title
        self.status = status
        self.paused = paused
        self.progress = progress
        self.compact = compact
        self.pauseLabel = pauseLabel
        self.resumeLabel = resumeLabel
        self.stopLabel = stopLabel
        self.onPauseToggle = onPauseToggle
        self.onStop = onStop
        self.onOpen = onOpen
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m + ModeSpacing.xxs) {
            HStack(spacing: ModeSpacing.m) {
                Button(action: onOpen) {
                    HStack(spacing: ModeSpacing.m) {
                        ModeMo(paused ? .sleep : .working, size: compact ? ModeSize.moRow + ModeSpacing.s : ModeSize.moHero)
                        VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                            Text(status).modeText(.footnote, color: ModeColor.heroSecondary).lineLimit(1)
                            Text(title).modeText(compact ? .calloutEmphasis : .title3, color: ModeColor.heroText).lineLimit(compact ? 1 : 2)
                        }
                        .frame(maxWidth: .infinity, minHeight: ModeSize.touch, alignment: .leading)
                    }
                    .contentShape(.rect)
                }
                .buttonStyle(.plain)
                Button(action: onPauseToggle) {
                    (paused ? ModeIcon.play : ModeIcon.pause).image
                        .font(ModeIconFont.small)
                        .foregroundStyle(ModeColor.heroText)
                        .frame(width: ModeSize.touch, height: ModeSize.touch)
                        .background(ModeColor.onDarkFill, in: .circle)
                }
                .buttonStyle(ModePressStyle())
                .accessibilityLabel(Text(paused ? resumeLabel : pauseLabel))
                Button(action: onStop) {
                    RoundedRectangle(cornerRadius: ModeSpacing.xs).fill(ModeColor.stopOnDark)
                        .frame(width: ModeSpacing.l, height: ModeSpacing.l)
                        .frame(width: ModeSize.touch, height: ModeSize.touch)
                        .background(ModeColor.stopDisc, in: .circle)
                }
                .buttonStyle(ModePressStyle())
                .accessibilityLabel(Text(stopLabel))
            }
            if !compact, let progress, progress.total > 0 {
                ModeStepBars(done: progress.done, total: progress.total)
            }
        }
        .padding(.leading, ModeSpacing.l)
        .padding(.trailing, ModeSpacing.m)
        .padding(.vertical, compact ? ModeSpacing.s : ModeSpacing.l)
        .background(ModeColor.hero, in: .rect(cornerRadius: compact ? ModeRadius.card : ModeRadius.hero))
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

/// A bar per step on a dark card: done steps in Mo's orange, the rest faint.
public struct ModeStepBars: View {
    private let done: Int
    private let total: Int

    public init(done: Int, total: Int) {
        self.done = done
        self.total = total
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s - ModeSpacing.xxs) {
            ForEach(0..<max(total, 1), id: \.self) { index in
                Capsule().fill(index < done ? ModeColor.brand : ModeColor.onDarkTrack)
                    .frame(height: ModeSize.progressBar)
            }
        }
        .padding(.trailing, ModeSpacing.xs)
        .accessibilityHidden(true)
    }
}

/// A suggestion on Home: an icon in a raised circle, what to do, and why it's suggested.
public struct ModeSuggestionCard: View {
    private let icon: ModeIcon
    private let title: String
    private let subtitle: String
    private let action: () -> Void

    public init(icon: ModeIcon, title: String, subtitle: String, action: @escaping () -> Void) {
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                icon.image
                    .font(ModeIconFont.regular)
                    .foregroundStyle(ModeColor.textPrimary)
                    .frame(width: ModeSize.iconCircle, height: ModeSize.iconCircle)
                    .background(ModeColor.cardRaised, in: .circle)
                Spacer(minLength: 0)
                VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                    Text(title).modeText(.calloutEmphasis).lineLimit(2).multilineTextAlignment(.leading)
                    Text(subtitle).modeText(.footnote, color: ModeColor.textSecondary).lineLimit(1)
                }
            }
            .padding(.horizontal, ModeSpacing.l)
            .padding(.vertical, ModeSpacing.m + ModeSpacing.xxs)
            .frame(width: ModeSize.suggestionWidth, alignment: .leading)
            .frame(minHeight: ModeSize.suggestionHeight, alignment: .leading)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
        }
        .buttonStyle(ModePressStyle())
    }
}
