import SwiftUI

/// A conversation's title and when it started (board Chat): Bricolage 26 heavy, then a small grey line.
public struct ModeChatTitle: View {
    private let title: String
    private let subtitle: String?

    public init(_ title: String, subtitle: String?) {
        self.title = title
        self.subtitle = subtitle
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
            // A spoken task can be long: two lines at most, a little smaller rather than cut.
            Text(title).modeText(.title2).lineLimit(2).minimumScaleFactor(ModeTextScale.title)
                .fixedSize(horizontal: false, vertical: true)
                .accessibilityAddTraits(.isHeader)
            if let subtitle, !subtitle.isEmpty { Text(subtitle).modeText(.footnote, color: ModeColor.textSecondary) }
        }
        .padding(.horizontal, ModeSpacing.chatTitleInset)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Share and More in one white capsule, top right of a conversation.
public struct ModeChatActions<MoreMenu: View>: View {
    private let shareLabel: LocalizedStringResource
    private let moreLabel: LocalizedStringResource
    private let onShare: () -> Void
    private let more: MoreMenu

    public init(shareLabel: LocalizedStringResource, moreLabel: LocalizedStringResource, onShare: @escaping () -> Void, @ViewBuilder more: () -> MoreMenu) {
        self.shareLabel = shareLabel
        self.moreLabel = moreLabel
        self.onShare = onShare
        self.more = more()
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.none) {
            Button(action: onShare) { glyph(.share) }
                .buttonStyle(ModePressStyle())
                .accessibilityLabel(Text(shareLabel))
            Menu { more } label: { glyph(.more) }
                .accessibilityLabel(Text(moreLabel))
        }
        .padding(.horizontal, ModeSpacing.xxs)
        .background(ModeColor.card, in: .capsule)
        .modeShadow(in: Capsule())
    }

    private func glyph(_ icon: ModeIcon) -> some View {
        icon.image.font(ModeIconFont.chatButton).foregroundStyle(ModeColor.textPrimary)
            .frame(width: ModeSize.touch, height: ModeSize.touch)
            .contentShape(.rect)
    }
}

/// "● Mode is working", above the message bar while the chat's task runs.
public struct ModeWorkingTag: View {
    private let text: LocalizedStringResource

    public init(_ text: LocalizedStringResource) {
        self.text = text
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.chatTight) {
            Circle().fill(ModeColor.brand).frame(width: ModeSize.workingDot, height: ModeSize.workingDot)
            Text(text).modeText(.captionEmphasis, color: ModeColor.tint)
        }
        .padding(.horizontal, ModeSpacing.chatInline)
        .frame(height: ModeSize.workingTag)
        .background(ModeColor.tintSoft, in: .capsule)
    }
}

/// "Also do it this way next time?" with Remember (board TeachSteer): a soft blue pill under Mode's reply.
public struct ModeOfferChip: View {
    private let text: LocalizedStringResource
    private let actionTitle: LocalizedStringResource
    private let action: () -> Void

    public init(_ text: LocalizedStringResource, actionTitle: LocalizedStringResource, action: @escaping () -> Void) {
        self.text = text
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            ModeIcon.memory.image.font(ModeIconFont.tag).accessibilityHidden(true)
            Text(text).fixedSize(horizontal: false, vertical: true)
            Button(action: action) {
                Text(actionTitle).modeText(.footnoteEmphasis, color: ModeColor.onInfo)
                    .fontWeight(.bold)
                    .lineLimit(1)
                    .fixedSize()
                    .padding(.horizontal, ModeSpacing.m)
                    .frame(minHeight: ModeSize.chatPillButton)
                    .background(ModeColor.info, in: .capsule)
                    .contentShape(Rectangle().inset(by: (ModeSize.chatPillButton - ModeSize.touch) / 2))
            }
            .buttonStyle(ModePressStyle())
        }
        .modeText(.footnoteEmphasis, color: ModeColor.infoText)
        .padding(.leading, ModeSpacing.m)
        .padding(.trailing, ModeSpacing.chatTight)
        .padding(.vertical, ModeSpacing.chatTight)
        .background(ModeColor.infoSoft, in: .rect(cornerRadius: ModeRadius.chatNotice))
        .padding(.leading, ModeSize.moRow + ModeSpacing.chatInline)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// A notice in the chat with Mo and one action ("Mode couldn't speak with ElevenLabs" · Update in Settings).
public struct ModeChatNotice: View {
    private let text: String
    private let actionTitle: LocalizedStringResource
    private let action: () -> Void

    public init(_ text: String, actionTitle: LocalizedStringResource, action: @escaping () -> Void) {
        self.text = text
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.chatInline) {
            ModeMo(.stuck, size: ModeSize.moNotice).accessibilityHidden(true)
            Text(text).modeText(.subheadlineEmphasis)
                .frame(maxWidth: .infinity, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
            ModeChatPillButton(actionTitle, background: ModeColor.card, foreground: ModeColor.textPrimary, action: action)
        }
        .padding(.leading, ModeSpacing.m)
        .padding([.vertical, .trailing], ModeSpacing.chatInline)
        .background(ModeColor.tileOrange, in: .rect(cornerRadius: ModeRadius.chatNotice))
        .accessibilityElement(children: .contain)
    }
}

/// Under a reply whose task couldn't finish (board TaskFailedChat): Mo, "Couldn't finish", what Mode can do,
/// then What went wrong and Try again.
public struct ModeFailedCard: View {
    private let title: LocalizedStringResource
    private let detail: LocalizedStringResource
    private let debugTitle: LocalizedStringResource
    private let retryTitle: LocalizedStringResource
    private let onDebug: () -> Void
    private let onRetry: () -> Void

    public init(title: LocalizedStringResource, detail: LocalizedStringResource, debugTitle: LocalizedStringResource, retryTitle: LocalizedStringResource,
                onDebug: @escaping () -> Void, onRetry: @escaping () -> Void) {
        self.title = title
        self.detail = detail
        self.debugTitle = debugTitle
        self.retryTitle = retryTitle
        self.onDebug = onDebug
        self.onRetry = onRetry
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            HStack(spacing: ModeSpacing.m) {
                ModeMo(.stuck, size: ModeSize.moFailed).accessibilityHidden(true)
                VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                    HStack(spacing: ModeSpacing.s) {
                        ModeChatIcon.warning.image.font(ModeIconFont.tag)
                        Text(title)
                    }
                    .modeText(.subheadlineEmphasis, color: ModeColor.danger)
                    .fontWeight(.bold)
                    Text(detail).modeText(.subheadline, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            HStack(spacing: ModeSpacing.s) {
                ModeChatButton(debugTitle, icon: .search, prominent: true, fill: true, action: onDebug)
                ModeChatButton(retryTitle, icon: .runAgain, prominent: false, fill: false, action: onRetry)
            }
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.vertical, ModeSpacing.chatGap)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.chatCard))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.chatCard))
    }
}

/// A capsule button in the chat's cards and bars: ink when [prominent], soft grey otherwise.
/// [height] 42 on a card, 50 at the bottom of What went wrong; [fill] stretches it to share the row.
public struct ModeChatButton: View {
    private let title: LocalizedStringResource
    private let icon: ModeIcon?
    private let prominent: Bool
    private let height: CGFloat
    private let fill: Bool
    private let action: () -> Void

    public init(_ title: LocalizedStringResource, icon: ModeIcon? = nil, prominent: Bool, height: CGFloat = ModeSize.failedButton, fill: Bool,
                action: @escaping () -> Void) {
        self.title = title
        self.icon = icon
        self.prominent = prominent
        self.height = height
        self.fill = fill
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.chatTight) {
                if let icon { icon.image.font(ModeIconFont.chatButton) }
                Text(title).lineLimit(1).minimumScaleFactor(ModeTextScale.title)
            }
            .modeText(height > ModeSize.failedButton ? .calloutEmphasis : .subheadlineEmphasis, color: prominent ? ModeColor.onAccent : ModeColor.textPrimary)
            .fontWeight(prominent && height <= ModeSize.failedButton ? .bold : .semibold)
            .padding(.horizontal, ModeSpacing.l)
            .frame(maxWidth: fill ? .infinity : nil, minHeight: height)
            .background(prominent ? ModeColor.accent : ModeColor.cardRaised, in: .capsule)
            .contentShape(Rectangle().inset(by: min(0, (height - ModeSize.touch) / 2)))
        }
        .buttonStyle(ModePressStyle())
    }
}

/// A tip the user just taught in a chat (board TeachLearned): ringed in Mo orange, the app's tile,
/// "SAVED FOR NEXT TIME", when it applies, the tip, Edit and Undo.
public struct ModeChatLessonCard: View {
    private let heading: LocalizedStringResource
    private let context: String
    private let text: String
    private let editTitle: LocalizedStringResource
    private let undoTitle: LocalizedStringResource
    private let onEdit: () -> Void
    private let onUndo: () -> Void

    public init(heading: LocalizedStringResource, context: String, text: String, editTitle: LocalizedStringResource, undoTitle: LocalizedStringResource,
                onEdit: @escaping () -> Void, onUndo: @escaping () -> Void) {
        self.heading = heading
        self.context = context
        self.text = text
        self.editTitle = editTitle
        self.undoTitle = undoTitle
        self.onEdit = onEdit
        self.onUndo = onUndo
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.chatInline) {
            HStack(alignment: .top, spacing: ModeSpacing.chatInline) {
                ModeIcon.tip.image.font(ModeIconFont.small).foregroundStyle(ModeColor.onInfo)
                    .frame(width: ModeSize.chatTile, height: ModeSize.chatTile)
                    .background(ModeColor.lessonTile, in: .rect(cornerRadius: ModeRadius.chatTile))
                    .accessibilityHidden(true)
                VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                    Text(heading).modeText(.captionEmphasis, color: ModeColor.tint).textCase(.uppercase).kerning(ModeSize.chatKerning)
                    Text(context).modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            Text(text).modeText(.calloutEmphasis).fixedSize(horizontal: false, vertical: true)
            HStack(spacing: ModeSpacing.s) {
                pill(editTitle, action: onEdit)
                pill(undoTitle, action: onUndo)
            }
        }
        .padding(ModeSpacing.l)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.chatCard))
        .overlay(RoundedRectangle(cornerRadius: ModeRadius.chatCard).strokeBorder(ModeColor.brand, lineWidth: ModeSize.chatRing))
    }

    private func pill(_ title: LocalizedStringResource, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).modeText(.subheadlineEmphasis)
                .padding(.horizontal, ModeSpacing.chatGap)
                .frame(minHeight: ModeSize.lessonButton)
                .background(ModeColor.cardRaised, in: .capsule)
                .contentShape(Rectangle().inset(by: (ModeSize.lessonButton - ModeSize.touch) / 2))
        }
        .buttonStyle(ModePressStyle())
    }
}

/// Mode's answer as a white bubble beside Mo (board TaskDebugAsk): a small "✦ ANSWERED WITH …" label, then the answer.
public struct ModeAnswerBubble<Content: View>: View {
    private let label: LocalizedStringResource?
    private let mood: ModeMo.Mood
    private let content: Content

    public init(label: LocalizedStringResource?, mood: ModeMo.Mood, @ViewBuilder content: () -> Content) {
        self.label = label
        self.mood = mood
        self.content = content()
    }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.chatInline) {
            ModeMo(mood, size: ModeSize.moAnswer).accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                if let label {
                    HStack(spacing: ModeSpacing.chatTight) {
                        ModeChatIcon.sparkle.image.font(ModeIconFont.tag)
                        Text(label).textCase(.uppercase).kerning(ModeSize.chatKerning)
                    }
                    .modeText(.captionEmphasis, color: ModeColor.tint)
                }
                content
            }
            .padding(.horizontal, ModeSpacing.l)
            .padding(.vertical, ModeSpacing.chatGap)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(ModeColor.card, in: shape)
            .modeShadow(in: shape)
        }
    }

    private var shape: UnevenRoundedRectangle {
        UnevenRoundedRectangle(topLeadingRadius: ModeRadius.bubbleTail, bottomLeadingRadius: ModeRadius.bubble,
                               bottomTrailingRadius: ModeRadius.bubble, topTrailingRadius: ModeRadius.bubble)
    }
}

/// What went wrong's header (board TaskDebug): a big title and its line, Mo on the right.
public struct ModeDebugHeader: View {
    private let title: LocalizedStringResource
    private let detail: String?
    private let mood: ModeMo.Mood?

    public init(_ title: LocalizedStringResource, detail: String?, mood: ModeMo.Mood?) {
        self.title = title
        self.detail = detail
        self.mood = mood
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.chatGap) {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                Text(title).modeText(.title).accessibilityAddTraits(.isHeader)
                if let detail { Text(detail).modeText(.callout, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let mood { ModeMo(mood, size: ModeSize.moDebugHeader).accessibilityHidden(true) }
        }
        .padding(.horizontal, ModeSpacing.s)
    }
}

/// A white card of rows with hairlines between them (What Mode found, Repaired).
public struct ModeDebugCard<Content: View>: View {
    private let content: Content

    public init(@ViewBuilder content: () -> Content) {
        self.content = content()
    }

    public var body: some View {
        VStack(spacing: ModeSpacing.none) {
            Group(subviews: content) { rows in
                ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                    row
                    if index < rows.count - 1 { Rectangle().fill(ModeColor.separator).frame(height: ModeSize.separator) }
                }
            }
        }
        .padding(.horizontal, ModeSpacing.l)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
    }
}

/// One finding on What went wrong: a tile, what it is and a line under it, and a check when it's fine.
public struct ModeDebugRow: View {
    public enum Tone: Sendable {
        /// What broke: a red tile and a red line with a warning sign.
        case problem
        /// What happened around it: a plain tile, a grey line.
        case neutral
        /// Working: a plain tile, a grey line, a green check.
        case ok
        /// Mended: a green tile, a green line, a green check.
        case repaired
    }

    /// A finding's tile glyph: one of Mode's icons, or an outlined chat glyph.
    public enum Glyph: Hashable, Sendable {
        case icon(ModeIcon)
        case chat(ModeChatIcon)

        var image: Image {
            switch self {
            case let .icon(icon): icon.image
            case let .chat(icon): icon.image
            }
        }
    }

    private let title: String
    private let detail: String
    private let icon: Glyph
    private let tone: Tone

    public init(_ title: String, detail: String, icon: Glyph, tone: Tone) {
        self.title = title
        self.detail = detail
        self.icon = icon
        self.tone = tone
    }

    private var tile: (Color, Color) {
        switch tone {
        case .problem: (ModeColor.tileRed, ModeColor.danger)
        case .repaired: (ModeColor.tagGreen, ModeColor.success)
        case .neutral, .ok: (ModeColor.cardRaised, ModeColor.textPrimary)
        }
    }

    private var detailColor: Color {
        switch tone {
        case .problem: ModeColor.danger
        case .repaired: ModeColor.success
        case .neutral, .ok: ModeColor.textSecondary
        }
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.m) {
            icon.image.font(ModeIconFont.small).foregroundStyle(tile.1)
                .frame(width: ModeSize.chatTile, height: ModeSize.chatTile)
                .background(tile.0, in: .rect(cornerRadius: ModeRadius.chatTile))
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.callout).fixedSize(horizontal: false, vertical: true)
                if !detail.isEmpty {
                    HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.xs) {
                        if tone == .problem { ModeChatIcon.warning.image.font(ModeIconFont.tag) }
                        Text(detail).fixedSize(horizontal: false, vertical: true)
                    }
                    .modeText(.footnote, color: detailColor)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if tone == .ok || tone == .repaired {
                ModeIcon.checkmark.image.font(ModeIconFont.tag).foregroundStyle(ModeColor.success)
                    .frame(width: ModeSize.debugCheck, height: ModeSize.debugCheck)
                    .background(ModeColor.tagGreen, in: .circle)
                    .accessibilityHidden(true)
            }
        }
        .padding(.vertical, ModeSpacing.s)
        .frame(minHeight: ModeSize.debugRow)
        .accessibilityElement(children: .combine)
    }
}

/// Two choices on a soft track, the chosen one a white pill (board TeachStep: Just this time · Every time).
public struct ModePillSegmented<Option: Hashable>: View {
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
                Button { withAnimation(ModeMotion.quick) { selection = option } } label: {
                    Text(title)
                        .modeText(on ? .subheadlineEmphasis : .subheadline, color: on ? ModeColor.textPrimary : ModeColor.textSecondary)
                        .fontWeight(on ? .bold : .medium)
                        .frame(maxWidth: .infinity, minHeight: ModeSize.chatSegment)
                        .background {
                            if on {
                                Capsule().fill(ModeColor.card).modeShadow(in: Capsule())
                            }
                        }
                        .contentShape(Rectangle().inset(by: (ModeSize.chatSegment - ModeSize.touch) / 2))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(on ? .isSelected : [])
            }
        }
        .padding(ModeSpacing.xxs)
        .background(ModeColor.cardRaised, in: .rect(cornerRadius: ModeRadius.chatFixField))
    }
}

/// "What should Mode have done?" (board TeachStep): Mo waiting on you beside the question, the step,
/// the user's words in a soft box, Just this time · Every time, a note, and Save.
public struct ModeFixStepForm: View {
    private let title: LocalizedStringResource
    private let stepLine: String
    private let prompt: LocalizedStringResource
    @Binding private var text: String
    @Binding private var everyTime: Bool
    private let justThisTime: LocalizedStringResource
    private let everyTimeTitle: LocalizedStringResource
    private let note: LocalizedStringResource
    private let saveTitle: LocalizedStringResource
    private let canSave: Bool
    private let onSave: () -> Void

    public init(title: LocalizedStringResource, stepLine: String, prompt: LocalizedStringResource, text: Binding<String>, everyTime: Binding<Bool>,
                justThisTime: LocalizedStringResource, everyTimeTitle: LocalizedStringResource, note: LocalizedStringResource,
                saveTitle: LocalizedStringResource, canSave: Bool, onSave: @escaping () -> Void) {
        self.title = title
        self.stepLine = stepLine
        self.prompt = prompt
        self._text = text
        self._everyTime = everyTime
        self.justThisTime = justThisTime
        self.everyTimeTitle = everyTimeTitle
        self.note = note
        self.saveTitle = saveTitle
        self.canSave = canSave
        self.onSave = onSave
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.chatGap) {
            HStack(spacing: ModeSpacing.m) {
                ModeMo(.yourTurn, size: ModeSize.moChatCard).accessibilityHidden(true)
                Text(title).modeText(.title2).fixedSize(horizontal: false, vertical: true).accessibilityAddTraits(.isHeader)
            }
            Text(stepLine).modeText(.subheadline, color: ModeColor.textSecondary)
            TextField(text: $text, axis: .vertical) { Text(prompt).foregroundStyle(ModeColor.textSecondary) }
                .modeText(.callout)
                .lineLimit(3...6)
                .padding(.horizontal, ModeSpacing.chatGap)
                .padding(.vertical, ModeSpacing.m)
                .frame(minHeight: ModeSize.chatFixField, alignment: .topLeading)
                .background(ModeColor.cardRaised, in: .rect(cornerRadius: ModeRadius.chatFixField))
            ModePillSegmented([(false, justThisTime), (true, everyTimeTitle)], selection: $everyTime)
            Text(note).modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
            ModeButton(saveTitle, fullWidth: true, action: onSave)
                .disabled(!canSave)
        }
        .padding(.horizontal, ModeSpacing.xl)
        .padding(.top, ModeSpacing.xxl)
        .frame(maxHeight: .infinity, alignment: .top)
        .background(ModeColor.card.ignoresSafeArea())
        .presentationBackground(ModeColor.card)
        .presentationCornerRadius(ModeRadius.sheet)
        .presentationDragIndicator(.visible)
    }
}

/// The page fading in behind a bottom bar, so content scrolls softly under it (as on the canvas):
/// from the bar's own bottom to a little above it.
public extension View {
    func modeBarFade() -> some View {
        background(alignment: .bottom) {
            ModeEdgeFade(edge: .bottom)
                .padding(.top, -ModeSize.barFadeOverlap)
                .ignoresSafeArea(edges: .bottom)
                .allowsHitTesting(false)
        }
    }
}
