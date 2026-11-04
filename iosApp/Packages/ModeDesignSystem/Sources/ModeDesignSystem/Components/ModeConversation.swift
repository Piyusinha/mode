import SwiftUI

/// Your message, as on the Chat board: an ink bubble on the right (22 · 22 · 6 · 22), with an optional
/// label above the text ("Spoken"). [tone] .info is the blue question bubble of Ask about this task.
public struct ModeUserBubble: View {
    public enum Tone: Sendable { case ink, info }

    private let text: String
    private let tag: LocalizedStringResource?
    private let icon: ModeIcon
    private let tone: Tone

    /// [tag]: how it was sent ("Spoken", "Typed", "You, while Mode worked") with its [icon].
    public init(_ text: String, tag: LocalizedStringResource? = nil, icon: ModeIcon = .microphone, tone: Tone = .ink) {
        self.text = text
        self.tag = tag
        self.icon = icon
        self.tone = tone
    }

    private var background: Color { tone == .ink ? ModeColor.accent : ModeColor.info }
    private var foreground: Color { tone == .ink ? ModeColor.onAccent : ModeColor.onInfo }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.xs) {
            if let tag {
                HStack(spacing: ModeSpacing.xs) {
                    icon.image.font(ModeIconFont.tag).accessibilityHidden(true)
                    Text(tag)
                }
                .modeText(.caption, color: foreground.opacity(ModeOpacity.bubbleTag))
            }
            Text(text).modeText(.callout, color: foreground)
        }
        .padding(.horizontal, ModeSpacing.chatGap)
        .padding(.vertical, ModeSpacing.chatInline)
        .background(background, in: UnevenRoundedRectangle(topLeadingRadius: ModeRadius.bubble, bottomLeadingRadius: ModeRadius.bubble,
                                                            bottomTrailingRadius: ModeRadius.bubbleTail, topTrailingRadius: ModeRadius.bubble))
        .frame(maxWidth: ModeSize.bubbleMaxWidth, alignment: .trailing)
        .frame(maxWidth: .infinity, alignment: .trailing)
    }
}

/// Mode's reply in a chat: Mo (its face says how the task went) beside the reply's first lines.
public struct ModeMoReply<Content: View>: View {
    private let mood: ModeMo.Mood
    private let size: CGFloat
    private let content: Content

    public init(mood: ModeMo.Mood, size: CGFloat = ModeSize.moRow, @ViewBuilder content: () -> Content) {
        self.mood = mood
        self.size = size
        self.content = content()
    }

    public var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.chatInline) {
            ModeMo(mood, size: size).accessibilityHidden(true)
            content
                .padding(.top, ModeSpacing.xs)
                .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// Times and what happens then, as in "Here's your day".
public struct ModeTimeTable: View {
    private let rows: [(time: String, text: String)]

    public init(rows: [(time: String, text: String)]) {
        self.rows = rows
    }

    public var body: some View {
        Grid(alignment: .leadingFirstTextBaseline, horizontalSpacing: ModeSpacing.chatGap, verticalSpacing: ModeSpacing.chatTight) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                GridRow {
                    Text(row.time).modeText(.calloutEmphasis).monospacedDigit()
                    Text(row.text).modeText(.callout)
                }
            }
        }
    }
}

/// "See all 11 screens": a white pill with a small stack of screenshots, the count and a chevron.
public struct ModeScreensLink: View {
    private let title: String
    private let thumbnails: [Image]
    private let action: () -> Void

    public init(title: String, thumbnails: [Image], action: @escaping () -> Void) {
        self.title = title
        self.thumbnails = thumbnails
        self.action = action
    }

    /// Before the screenshots load (or when there are none), three soft cards stand in.
    private static let placeholders = [ModeColor.cardRaised, ModeColor.ring, ModeColor.tintSoft]

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.chatInline) {
                HStack(spacing: -ModeSpacing.chatInline) {
                    if thumbnails.isEmpty {
                        ForEach(Array(Self.placeholders.enumerated()), id: \.offset) { _, color in thumb { color } }
                    } else {
                        ForEach(Array(thumbnails.prefix(3).enumerated()), id: \.offset) { _, image in thumb { image.resizable().scaledToFill() } }
                    }
                }
                .accessibilityHidden(true)
                Text(title).modeText(.subheadlineEmphasis)
                ModeChevron()
            }
            .padding(.leading, ModeSpacing.s)
            .padding(.trailing, ModeSpacing.chatGap)
            .padding(.vertical, ModeSpacing.chatInline)
            .frame(minHeight: ModeSize.screensLinkHeight)
            .background(ModeColor.card, in: .capsule)
            .modeShadow(in: Capsule())
            .contentShape(.capsule)
        }
        .buttonStyle(ModePressStyle())
    }

    private func thumb<V: View>(@ViewBuilder _ fill: () -> V) -> some View {
        let shape = RoundedRectangle(cornerRadius: ModeRadius.thumb)
        return fill()
            .frame(width: ModeSize.screensThumbWidth, height: ModeSize.screensThumbHeight)
            .clipShape(shape)
            .padding(ModeSize.screensThumbBorder)
            .background(ModeColor.card, in: RoundedRectangle(cornerRadius: ModeRadius.thumb + ModeSize.screensThumbBorder))
    }
}

/// "Remembered: …" with Undo: a soft orange pill, a white Undo pill at its end.
public struct ModeRememberedChip: View {
    private let text: String
    private let undoTitle: LocalizedStringResource
    private let onUndo: () -> Void

    public init(_ text: String, undoTitle: LocalizedStringResource, onUndo: @escaping () -> Void) {
        self.text = text
        self.undoTitle = undoTitle
        self.onUndo = onUndo
    }

    public var body: some View {
        ModeChatPill(background: ModeColor.tintSoft) {
            HStack(spacing: ModeSpacing.s) {
                ModeIcon.memory.image.font(ModeIconFont.tag).accessibilityHidden(true)
                Text(text).lineLimit(2)
            }
            .modeText(.footnote, color: ModeColor.tint)
        } action: {
            ModeChatPillButton(undoTitle, background: ModeColor.card, foreground: ModeColor.textPrimary, action: onUndo)
        }
    }
}

/// A soft pill in the chat with a small button at its end (Remembered … Undo, Also next time? Remember).
public struct ModeChatPill<Label: View, Action: View>: View {
    private let background: Color
    private let label: Label
    private let actionView: Action

    public init(background: Color, @ViewBuilder label: () -> Label, @ViewBuilder action: () -> Action) {
        self.background = background
        self.label = label()
        self.actionView = action()
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            label
            actionView
        }
        .padding(.leading, ModeSpacing.m)
        .padding(.trailing, ModeSpacing.chatTight)
        .padding(.vertical, ModeSpacing.chatTight)
        .background(background, in: .capsule)
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The small button at the end of a chat pill: 30 tall to the eye, a full 44 to the finger.
public struct ModeChatPillButton: View {
    private let title: LocalizedStringResource
    private let background: Color
    private let foreground: Color
    private let height: CGFloat
    private let action: () -> Void

    public init(_ title: LocalizedStringResource, background: Color, foreground: Color, height: CGFloat = ModeSize.chatPillButton, action: @escaping () -> Void) {
        self.title = title
        self.background = background
        self.foreground = foreground
        self.height = height
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            Text(title).modeText(.footnoteEmphasis, color: foreground)
                .lineLimit(1)
                .fixedSize()
                .padding(.horizontal, ModeSpacing.m)
                .frame(minHeight: height)
                .background(background, in: .capsule)
                .contentShape(Rectangle().inset(by: (height - ModeSize.touch) / 2))
        }
        .buttonStyle(ModePressStyle())
    }
}

/// The message bar from the Chat board: a white round + button, then a white capsule with the
/// text, the private switch and the ink Speak (or Send, once there's text).
public struct ModeMessageBar: View {
    public struct Labels {
        let prompt: LocalizedStringResource
        let add: LocalizedStringResource
        let send: LocalizedStringResource
        let speak: LocalizedStringResource
        let privateOn: LocalizedStringResource
        let privateOff: LocalizedStringResource

        public init(prompt: LocalizedStringResource, add: LocalizedStringResource, send: LocalizedStringResource, speak: LocalizedStringResource,
                    privateOn: LocalizedStringResource, privateOff: LocalizedStringResource) {
            self.prompt = prompt
            self.add = add
            self.send = send
            self.speak = speak
            self.privateOn = privateOn
            self.privateOff = privateOff
        }
    }

    @Binding private var text: String
    private let isPrivate: Bool
    private let canSend: Bool
    private let labels: Labels
    private let addMenu: AnyView
    private let onTogglePrivate: () -> Void
    private let onSend: () -> Void
    private let onSpeak: () -> Void
    private let steering: Bool

    /// [steering]: the chat's task is running, so the bar only steers it (board TeachSteer): just the
    /// field and a round ink Speak beside it, no + and no private switch (they're for a new task).
    public init<Menu: View>(text: Binding<String>, isPrivate: Bool, canSend: Bool, labels: Labels, steering: Bool = false,
                            onTogglePrivate: @escaping () -> Void,
                            onSend: @escaping () -> Void, onSpeak: @escaping () -> Void, @ViewBuilder addMenu: () -> Menu) {
        self.steering = steering
        self._text = text
        self.isPrivate = isPrivate
        self.canSend = canSend
        self.labels = labels
        self.addMenu = AnyView(addMenu())
        self.onTogglePrivate = onTogglePrivate
        self.onSend = onSend
        self.onSpeak = onSpeak
    }

    /// Sends, then clears the field once more on the next update: the keyboard can write a pending
    /// autocorrection back into the field just after it is cleared, so the sent text stayed visible.
    /// (Sending only fails for an empty message, which can't be sent.)
    private func send() {
        onSend()
        DispatchQueue.main.async { text = "" }
    }

    public var body: some View {
        HStack(alignment: .bottom, spacing: ModeSpacing.chatInline) {
            if !steering {
                Menu { addMenu } label: {
                    ModeIcon.add.image.font(ModeIconFont.regular).foregroundStyle(ModeColor.textPrimary)
                        .frame(width: ModeSize.composerHeight, height: ModeSize.composerHeight)
                        .background(ModeColor.card, in: .circle)
                        .modeShadow(in: Circle())
                }
                .accessibilityLabel(Text(labels.add))
            }
            HStack(alignment: .bottom, spacing: ModeSpacing.s) {
                TextField(text: $text, axis: .vertical) { Text(labels.prompt).foregroundStyle(ModeColor.textSecondary) }
                    .modeText(.callout)
                    .lineLimit(1...5)
                    .submitLabel(.send)
                    // A multi-line field gets a new line from the keyboard's Send key: it sends instead.
                    .onChange(of: text) { _, now in
                        guard now.hasSuffix("\n") else { return }
                        text = String(now.dropLast())
                        if canSend { send() }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .frame(minHeight: ModeSize.sendButton)
                    .padding(.vertical, ModeSpacing.xxs)
                if !steering {
                    Button(action: onTogglePrivate) {
                        (isPrivate ? ModeIcon.privateOn : ModeIcon.privateOff).image
                            .font(ModeIconFont.regular)
                            .foregroundStyle(isPrivate ? ModeColor.tint : ModeColor.textSecondary)
                            .frame(width: ModeSize.sendButton, height: ModeSize.sendButton)
                            .contentShape(Rectangle().inset(by: (ModeSize.sendButton - ModeSize.touch) / 2))
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(isPrivate ? labels.privateOn : labels.privateOff))
                    speakOrSend(size: ModeSize.sendButton)
                }
            }
            .padding(.leading, steering ? ModeSpacing.l + ModeSpacing.xxs : ModeSpacing.l)
            .padding(.trailing, ModeSpacing.composerInset)
            .padding(.vertical, ModeSpacing.composerInset)
            .frame(minHeight: ModeSize.composerHeight)
            // A capsule's ends grow with its height and cut into several lines of text; a fixed radius doesn't.
            .background(ModeColor.card, in: .rect(cornerRadius: ModeSize.composerHeight / 2))
            .modeShadow(in: RoundedRectangle(cornerRadius: ModeSize.composerHeight / 2))
            if steering { speakOrSend(size: ModeSize.composerHeight) }
        }
    }

    /// The ink round button: Speak, or Send once there's text.
    private func speakOrSend(size: CGFloat) -> some View {
        Button(action: canSend ? send : onSpeak) {
            (canSend ? ModeIcon.send : ModeIcon.microphone).image
                .font(ModeIconFont.chatSend)
                .foregroundStyle(ModeColor.onAccent)
                .frame(width: size, height: size)
                .background(ModeColor.accent, in: .circle)
                .contentShape(Rectangle().inset(by: min(0, (size - ModeSize.touch) / 2)))
        }
        .buttonStyle(ModePressStyle())
        .accessibilityLabel(Text(canSend ? labels.send : labels.speak))
    }
}

/// "● Live · step 7 · 0:41", the glass label at the top of Live run.
public struct ModeLiveBadge: View {
    private let text: String
    private let since: Date?
    private let paused: Bool

    /// [text] such as "Live · step 7"; [since] adds a ticking timer (" · 0:41").
    public init(_ text: String, since: Date?, paused: Bool) {
        self.text = text
        self.since = since
        self.paused = paused
    }

    public var body: some View {
        HStack(spacing: ModeSpacing.s) {
            Circle().fill(paused ? ModeColor.textSecondary : ModeColor.stopRed).frame(width: ModeSize.liveDot, height: ModeSize.liveDot)
            Text(text).modeText(.bodyEmphasis).monospacedDigit()
            if let since {
                Text(since, style: .timer).modeText(.bodyEmphasis).monospacedDigit()
            }
        }
        .padding(.horizontal, ModeSpacing.l)
        .frame(minHeight: ModeSize.touch)
        .glassEffect(.regular, in: .capsule)
    }
}

/// The glass panel at the bottom of Live run: the task, what it's doing now,
/// the steps done so far, Pause and Stop.
public struct ModeLivePanel: View {
    private let task: String
    private let now: String
    private let steps: [String]
    private let paused: Bool
    private let pauseLabel: LocalizedStringResource
    private let resumeLabel: LocalizedStringResource
    private let stopLabel: LocalizedStringResource
    private let onPauseToggle: () -> Void
    private let onStop: () -> Void

    public init(task: String, now: String, steps: [String], paused: Bool, pauseLabel: LocalizedStringResource, resumeLabel: LocalizedStringResource,
                stopLabel: LocalizedStringResource, onPauseToggle: @escaping () -> Void, onStop: @escaping () -> Void) {
        self.task = task
        self.now = now
        self.steps = steps
        self.paused = paused
        self.pauseLabel = pauseLabel
        self.resumeLabel = resumeLabel
        self.stopLabel = stopLabel
        self.onPauseToggle = onPauseToggle
        self.onStop = onStop
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            Capsule().fill(ModeColor.separator).frame(width: ModeSpacing.xxxl, height: ModeSpacing.xs).frame(maxWidth: .infinity)
            Text(task).modeText(.subheadline, color: ModeColor.textSecondary).lineLimit(1)
            Text(now).modeText(.title).lineLimit(2)
            if !steps.isEmpty {
                let shown = steps.suffix(3)
                ModeSegments(shown.enumerated().map { index, step in .init(step, state: index == shown.count - 1 ? .now : .done) })
            }
            HStack(spacing: ModeSpacing.m) {
                Button(action: onPauseToggle) {
                    Text(paused ? resumeLabel : pauseLabel).modeText(.bodyEmphasis).frame(maxWidth: .infinity, minHeight: ModeSize.fieldHeight)
                }
                .buttonStyle(.plain)
                .background(ModeColor.fill, in: .capsule)
                Button(action: onStop) {
                    Text(stopLabel).modeText(.bodyEmphasis, color: ModeColor.onAccent).frame(maxWidth: .infinity, minHeight: ModeSize.fieldHeight)
                }
                .buttonStyle(.plain)
                .glassEffect(.regular.tint(ModeColor.stopRed).interactive(), in: .capsule)
            }
            .padding(.top, ModeSpacing.s)
        }
        .padding(ModeSpacing.xl)
        .glassEffect(.regular, in: .rect(cornerRadius: ModeRadius.sheet))
    }
}
