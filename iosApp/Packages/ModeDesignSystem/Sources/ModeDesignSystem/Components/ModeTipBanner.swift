import SwiftUI

/// "Did you know?" on Home for the first days: every tip as its own card in a carousel (swipe, or it moves on by
/// itself). A card: Mo with a badge, a short title, one line, a button that opens the setting, dots for the
/// others, and (from day 3) a close button that hides tips for good.
public struct ModeTipBanner: View {
    public struct Tip: Equatable, Sendable {
        let title: String
        let message: String
        let action: String
        let mood: ModeMo.Mood
        let icon: ModeIcon
        let tone: ModeTipTone

        public init(title: String, message: String, action: String, mood: ModeMo.Mood, icon: ModeIcon, tone: ModeTipTone) {
            self.title = title
            self.message = message
            self.action = action
            self.mood = mood
            self.icon = icon
            self.tone = tone
        }
    }

    private let label: LocalizedStringResource
    private let tips: [Tip]
    private let index: Int
    private let closable: Bool
    private let closeLabel: LocalizedStringResource
    private let dotLabel: (Int) -> String
    private let onAction: (Int) -> Void
    private let onClose: () -> Void
    private let onPick: (Int) -> Void
    @State private var position: Int?

    /// [index] is the card showing; [onPick] is called when the user swipes or taps a dot (not when [index]
    /// changes from outside); [dotLabel] names dot n ("Tip 2 of 5").
    public init(
        label: LocalizedStringResource, tips: [Tip], index: Int, closable: Bool, closeLabel: LocalizedStringResource,
        dotLabel: @escaping (Int) -> String, onAction: @escaping (Int) -> Void, onClose: @escaping () -> Void, onPick: @escaping (Int) -> Void
    ) {
        self.label = label
        self.tips = tips
        self.index = index
        self.closable = closable
        self.closeLabel = closeLabel
        self.dotLabel = dotLabel
        self.onAction = onAction
        self.onClose = onClose
        self.onPick = onPick
    }

    public var body: some View {
        ScrollView(.horizontal) {
            HStack(alignment: .top, spacing: ModeSpacing.m) {
                ForEach(tips.indices, id: \.self) { i in
                    card(i)
                        .frame(maxHeight: .infinity, alignment: .top)
                        .background(tips[i].tone.background, in: .rect(cornerRadius: ModeRadius.tipBanner))
                        .containerRelativeFrame(.horizontal)
                        .id(i)
                }
            }
            .fixedSize(horizontal: false, vertical: true)
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.viewAligned)
        .scrollPosition(id: $position)
        .scrollIndicators(.hidden)
        .clipShape(.rect(cornerRadius: ModeRadius.tipBanner))
        .onAppear { position = index }
        .onChange(of: index) { _, new in
            if position != new { withAnimation(ModeMotion.standard) { position = new } }
        }
        .onChange(of: position) { _, new in
            if let new, new != index { onPick(new) }
        }
    }

    private func card(_ i: Int) -> some View {
        let tip = tips[i]
        return VStack(alignment: .leading, spacing: ModeSpacing.s + ModeSpacing.xxs) {
            HStack {
                Text(label).modeText(.captionEmphasis, color: tip.tone.text)
                    .textCase(.uppercase)
                    .tracking(ModeSpacing.tipLabelTracking)
                Spacer(minLength: ModeSpacing.s)
                if closable {
                    Button(action: onClose) {
                        ModeIcon.close.image.font(ModeIconFont.chevron.weight(.bold)).foregroundStyle(tip.tone.text)
                            .frame(width: ModeSize.tipClose, height: ModeSize.tipClose)
                            .background(ModeColor.tipChip, in: .circle)
                            .frame(width: ModeSize.touch, height: ModeSize.touch)
                            .contentShape(.circle)
                    }
                    .buttonStyle(ModePressStyle())
                    .accessibilityLabel(Text(closeLabel))
                    .padding(.vertical, -ModeSpacing.s)
                    .padding(.trailing, -ModeSpacing.s - ModeSpacing.xxs)
                }
            }
            .frame(minHeight: ModeSize.tipClose)

            HStack(alignment: .top, spacing: ModeSpacing.m + ModeSpacing.xxs) {
                ZStack(alignment: .bottomTrailing) {
                    ModeMo(tip.mood, size: ModeSize.tipMo)
                    tip.icon.image.font(ModeIconFont.chevron.weight(.bold)).foregroundStyle(ModeColor.onAccent)
                        .frame(width: ModeSize.tipBadge, height: ModeSize.tipBadge)
                        .background(tip.tone.badge, in: .circle)
                        .overlay(Circle().stroke(tip.tone.background, lineWidth: ModeSize.tipBadgeRing))
                        .offset(x: ModeSpacing.tipBadgeOffset, y: ModeSpacing.xs)
                        .accessibilityHidden(true)
                }
                VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                    Text(tip.title).modeText(.title3).accessibilityAddTraits(.isHeader)
                    Text(tip.message).modeText(.subheadline, color: ModeColor.textSecondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .padding(.top, ModeSpacing.xxs)
                .frame(maxWidth: .infinity, alignment: .leading)
            }

            Spacer(minLength: ModeSpacing.none)

            HStack(spacing: ModeSpacing.m) {
                Button { onAction(i) } label: {
                    HStack(spacing: ModeSpacing.s) {
                        Text(tip.action).modeText(.subheadlineEmphasis, color: ModeColor.onAccent).lineLimit(1)
                        ModeIcon.arrowRight.image.font(ModeIconFont.chevron.weight(.bold)).foregroundStyle(ModeColor.onAccent)
                    }
                    .padding(.horizontal, ModeSpacing.l + ModeSpacing.xxs)
                    .frame(minHeight: ModeSize.touch)
                    .background(ModeColor.accent, in: .capsule)
                }
                .buttonStyle(ModePressStyle())
                Spacer(minLength: ModeSpacing.none)
                if tips.count > 1 {
                    HStack(spacing: ModeSpacing.xxs) {
                        ForEach(tips.indices, id: \.self) { d in
                            Button { onPick(d) } label: {
                                Capsule().fill(d == i ? tip.tone.text : ModeColor.tipDotOff)
                                    .frame(width: d == i ? ModeSize.tipDotCurrent : ModeSize.tipDot, height: ModeSize.tipDot)
                                    .frame(width: d == i ? ModeSize.tipDotHitCurrent : ModeSize.tipDotHit, height: ModeSize.tipDotHitHeight)
                                    .contentShape(.rect)
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(Text(dotLabel(d)))
                            .accessibilityAddTraits(d == i ? .isSelected : [])
                        }
                    }
                }
            }
            .padding(.top, ModeSpacing.xxs)
        }
        .padding(.top, ModeSpacing.m + ModeSpacing.xxs)
        .padding(.horizontal, ModeSpacing.l)
        .padding(.bottom, ModeSpacing.l)
    }
}

/// A short dark message at the bottom with one action (Undo), after something was hidden.
public struct ModeUndoToast: View {
    private let title: LocalizedStringResource
    private let detail: LocalizedStringResource
    private let undo: LocalizedStringResource
    private let onUndo: () -> Void

    public init(title: LocalizedStringResource, detail: LocalizedStringResource, undo: LocalizedStringResource, onUndo: @escaping () -> Void) {
        self.title = title
        self.detail = detail
        self.undo = undo
        self.onUndo = onUndo
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            VStack(alignment: .leading, spacing: ModeSpacing.none) {
                Text(title).modeText(.subheadlineEmphasis, color: ModeColor.onAccent)
                Text(detail).modeText(.footnote, color: ModeColor.onAccent.opacity(0.72))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button(action: onUndo) {
                Text(undo).modeText(.subheadlineEmphasis, color: ModeColor.onAccent)
                    .padding(.horizontal, ModeSpacing.l + ModeSpacing.xxs)
                    .frame(minHeight: ModeSize.touch)
                    .background(ModeColor.onAccent.opacity(0.16), in: .capsule)
            }
            .buttonStyle(ModePressStyle())
        }
        .padding(.leading, ModeSpacing.l + ModeSpacing.xxs)
        .padding(.trailing, ModeSpacing.xs + ModeSpacing.xxs)
        .frame(minHeight: ModeSize.tipToast)
        .background(ModeColor.accent, in: .capsule)
        .accessibilityElement(children: .contain)
    }
}
