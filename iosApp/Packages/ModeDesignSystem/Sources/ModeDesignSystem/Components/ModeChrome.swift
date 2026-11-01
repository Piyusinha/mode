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

/// The bottom of the main screens, in Liquid Glass: the running task above,
/// then the tabs (the selected one sits in a lens that morphs between tabs)
/// and the round Speak button beside them. One glass container, so the
/// pieces blend and morph as the mini player comes and goes.
public struct ModeTabBar<ID: Hashable, Player: View>: View {
    private let items: [ModeTabItem<ID>]
    @Binding private var selection: ID
    private let speakLabel: LocalizedStringResource
    private let onSpeak: () -> Void
    private let player: Player
    @Namespace private var glass

    public init(items: [ModeTabItem<ID>], selection: Binding<ID>, speakLabel: LocalizedStringResource, onSpeak: @escaping () -> Void,
                @ViewBuilder player: () -> Player) {
        self.items = items
        self._selection = selection
        self.speakLabel = speakLabel
        self.onSpeak = onSpeak
        self.player = player()
    }

    public var body: some View {
        GlassEffectContainer(spacing: ModeSpacing.m) {
            VStack(spacing: ModeSpacing.s) {
                player
                HStack(spacing: ModeSpacing.m) {
                    tabs
                    Button(action: onSpeak) {
                        ModeIcon.ask.image
                            .font(ModeTextStyle.title2.font)
                            .foregroundStyle(ModeColor.onAccent)
                            .frame(width: ModeSize.speakButton, height: ModeSize.speakButton)
                    }
                    .buttonStyle(.plain)
                    .glassEffect(.regular.tint(ModeColor.accent).interactive(), in: .circle)
                    .glassEffectID("speak", in: glass)
                    .accessibilityLabel(Text(speakLabel))
                }
            }
        }
        .padding(.horizontal, ModeSpacing.screenEdge)
    }

    private var tabs: some View {
        HStack(spacing: 0) {
            ForEach(items) { item in
                let selected = item.id == selection
                Button {
                    withAnimation(ModeMotion.standard) { selection = item.id }
                } label: {
                    VStack(spacing: ModeSpacing.xxs) {
                        item.icon.image.font(ModeTextStyle.title2.font)
                        Text(item.title).modeText(.captionEmphasis, color: selected ? ModeColor.tint : ModeColor.textPrimary)
                    }
                    .foregroundStyle(selected ? ModeColor.tint : ModeColor.textPrimary)
                    .frame(maxWidth: .infinity, minHeight: ModeSize.tabBarHeight - ModeSpacing.s)
                    .background {
                        if selected {
                            Capsule().fill(ModeColor.tintSoft).matchedGeometryEffect(id: "lens", in: glass)
                        }
                    }
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(selected ? .isSelected : [])
            }
        }
        .padding(ModeSpacing.xs)
        .frame(height: ModeSize.tabBarHeight)
        .glassEffect(.regular.interactive(), in: .capsule)
        .glassEffectID("tabs", in: glass)
    }
}

/// The running task above the tab bar: a spinner, what it is, what it's doing, Pause and Stop.
public struct ModeMiniPlayer: View {
    private let title: String
    private let status: String
    private let paused: Bool
    private let pauseLabel: LocalizedStringResource
    private let resumeLabel: LocalizedStringResource
    private let stopLabel: LocalizedStringResource
    private let onPauseToggle: () -> Void
    private let onStop: () -> Void
    private let onOpen: () -> Void

    public init(title: String, status: String, paused: Bool, pauseLabel: LocalizedStringResource, resumeLabel: LocalizedStringResource,
                stopLabel: LocalizedStringResource, onPauseToggle: @escaping () -> Void, onStop: @escaping () -> Void, onOpen: @escaping () -> Void) {
        self.title = title
        self.status = status
        self.paused = paused
        self.pauseLabel = pauseLabel
        self.resumeLabel = resumeLabel
        self.stopLabel = stopLabel
        self.onPauseToggle = onPauseToggle
        self.onStop = onStop
        self.onOpen = onOpen
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            Button(action: onOpen) {
                HStack(spacing: ModeSpacing.m) {
                    ProgressView().tint(ModeColor.tint)
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        Text(title).modeText(.bodyEmphasis).lineLimit(1)
                        Text(status).modeText(.footnote, color: ModeColor.textSecondary).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            Button(action: onPauseToggle) {
                (paused ? ModeIcon.play : ModeIcon.pause).image
                    .font(ModeTextStyle.headline.font)
                    .foregroundStyle(ModeColor.textPrimary)
                    .frame(width: ModeSize.touch, height: ModeSize.touch)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(paused ? resumeLabel : pauseLabel))
            Button(action: onStop) {
                RoundedRectangle(cornerRadius: ModeSpacing.xs).fill(ModeColor.stopRed)
                    .frame(width: ModeSpacing.l, height: ModeSpacing.l)
                    .frame(width: ModeSize.touch, height: ModeSize.touch)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(stopLabel))
        }
        .padding(.leading, ModeSpacing.l)
        .padding(.trailing, ModeSpacing.s)
        .frame(height: ModeSize.miniPlayerHeight)
        .glassEffect(.regular.interactive(), in: .capsule)
        .transition(.move(edge: .bottom).combined(with: .opacity))
    }
}

/// A suggestion on Home: an icon in a soft circle, what to do, and why it's suggested.
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
            VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                icon.image
                    .font(ModeTextStyle.title2.font)
                    .foregroundStyle(ModeColor.tint)
                    .frame(width: ModeSize.tile, height: ModeSize.tile)
                    .background(ModeColor.tintSoft, in: .circle)
                Spacer(minLength: ModeSpacing.s)
                Text(title).modeText(.headline).lineLimit(2).multilineTextAlignment(.leading)
                Text(subtitle).modeText(.subheadline, color: ModeColor.textSecondary).lineLimit(2).multilineTextAlignment(.leading)
            }
            .padding(ModeSpacing.l)
            .frame(width: ModeSize.suggestionWidth, height: ModeSize.suggestionHeight, alignment: .leading)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
        }
        .buttonStyle(.plain)
    }
}
