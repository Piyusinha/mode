import SwiftUI

/// Your message: an accent bubble on the right, with an optional label above the text ("Spoken").
public struct ModeUserBubble: View {
    private let text: String
    private let tag: LocalizedStringResource?
    private let icon: ModeIcon

    /// [tag]: how it was sent ("Spoken", "Typed", "You, while Mode worked") with its [icon].
    public init(_ text: String, tag: LocalizedStringResource? = nil, icon: ModeIcon = .microphone) {
        self.text = text
        self.tag = tag
        self.icon = icon
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.xs) {
            if let tag {
                Label { Text(tag) } icon: { icon.image }
                    .modeText(.footnote, color: ModeColor.onAccent.opacity(0.85))
            }
            Text(text).modeText(.body, color: ModeColor.onAccent)
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.vertical, ModeSpacing.m)
        .background(ModeColor.accent, in: UnevenRoundedRectangle(topLeadingRadius: ModeRadius.card, bottomLeadingRadius: ModeRadius.card, bottomTrailingRadius: ModeSpacing.xs, topTrailingRadius: ModeRadius.card))
        .frame(maxWidth: .infinity, alignment: .trailing)
        .padding(.leading, ModeSpacing.xxxl * 3)
    }
}

/// Times and what happens then, as in "Here's your day".
public struct ModeTimeTable: View {
    private let rows: [(time: String, text: String)]

    public init(rows: [(time: String, text: String)]) {
        self.rows = rows
    }

    public var body: some View {
        Grid(alignment: .leadingFirstTextBaseline, horizontalSpacing: ModeSpacing.l, verticalSpacing: ModeSpacing.s) {
            ForEach(Array(rows.enumerated()), id: \.offset) { _, row in
                GridRow {
                    Text(row.time).modeText(.bodyEmphasis).monospacedDigit()
                    Text(row.text).modeText(.body)
                }
            }
        }
    }
}

/// "See all 11 screens": a small stack of screenshots and the count.
public struct ModeScreensLink: View {
    private let title: String
    private let thumbnails: [Image]
    private let action: () -> Void

    public init(title: String, thumbnails: [Image], action: @escaping () -> Void) {
        self.title = title
        self.thumbnails = thumbnails
        self.action = action
    }

    public var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.m) {
                ZStack(alignment: .leading) {
                    ForEach(Array(thumbnails.prefix(3).enumerated()), id: \.offset) { index, image in
                        image.resizable().scaledToFill()
                            .frame(width: ModeSize.tile * 0.7, height: ModeSize.tile)
                            .clipShape(.rect(cornerRadius: ModeSpacing.xs))
                            .offset(x: CGFloat(index) * ModeSpacing.l)
                    }
                    if thumbnails.isEmpty {
                        RoundedRectangle(cornerRadius: ModeSpacing.xs).fill(ModeColor.tintSoft).frame(width: ModeSize.tile, height: ModeSize.tile)
                    }
                }
                .frame(width: ModeSize.tile * 1.4, alignment: .leading)
                Text(title).modeText(.bodyEmphasis)
                ModeChevron()
            }
            .padding(ModeSpacing.m)
            .padding(.trailing, ModeSpacing.s)
            .background(ModeColor.card, in: .capsule)
        }
        .buttonStyle(.plain)
    }
}

/// "Remembered: …" with Undo, in a soft tint.
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
        HStack(spacing: ModeSpacing.s) {
            ModeIcon.memory.image.foregroundStyle(ModeColor.tint).accessibilityHidden(true)
            Text(text).modeText(.subheadline, color: ModeColor.tint).lineLimit(2)
            Button(action: onUndo) {
                Text(undoTitle).modeText(.bodyEmphasis, color: ModeColor.tint)
                    .padding(.horizontal, ModeSpacing.m)
                    .frame(minHeight: ModeSize.touch - ModeSpacing.s)
                    .background(ModeColor.card, in: .capsule)
            }
            .buttonStyle(.plain)
        }
        .padding(.leading, ModeSpacing.l)
        .padding(.trailing, ModeSpacing.xs)
        .padding(.vertical, ModeSpacing.xs)
        .background(ModeColor.tintSoft, in: .capsule)
    }
}

/// The message bar from the boards: a round + button, then a capsule with the
/// text, the private switch and the accent Speak (or Send, once there's text).
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

    public init<Menu: View>(text: Binding<String>, isPrivate: Bool, canSend: Bool, labels: Labels, onTogglePrivate: @escaping () -> Void,
                            onSend: @escaping () -> Void, onSpeak: @escaping () -> Void, @ViewBuilder addMenu: () -> Menu) {
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
        GlassEffectContainer(spacing: ModeSpacing.s) {
            HStack(alignment: .bottom, spacing: ModeSpacing.s) {
                Menu { addMenu } label: {
                    ModeIcon.add.image.font(ModeTextStyle.title2.font).foregroundStyle(ModeColor.textPrimary)
                        .frame(width: ModeSize.composerHeight, height: ModeSize.composerHeight)
                }
                .glassEffect(.regular.interactive(), in: .circle)
                .accessibilityLabel(Text(labels.add))
                HStack(alignment: .bottom, spacing: ModeSpacing.xs) {
                    TextField(text: $text, axis: .vertical) { Text(labels.prompt) }
                        .modeText(.body)
                        .lineLimit(1...5)
                        .submitLabel(.send)
                        // A multi-line field gets a new line from the keyboard's Send key: it sends instead.
                        .onChange(of: text) { _, now in
                            guard now.hasSuffix("\n") else { return }
                            text = String(now.dropLast())
                            if canSend { send() }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.vertical, ModeSpacing.s)
                        .padding(.leading, ModeSpacing.l)
                    Button(action: onTogglePrivate) {
                        (isPrivate ? ModeIcon.privateOn : ModeIcon.privateOff).image
                            .foregroundStyle(isPrivate ? ModeColor.tint : ModeColor.textSecondary)
                            .frame(width: ModeSize.touch, height: ModeSize.touch)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(isPrivate ? labels.privateOn : labels.privateOff))
                    Button(action: canSend ? send : onSpeak) {
                        (canSend ? ModeIcon.send : ModeIcon.ask).image
                            .font(ModeTextStyle.bodyEmphasis.font)
                            .foregroundStyle(ModeColor.onAccent)
                            .frame(width: ModeSize.touch, height: ModeSize.touch)
                            .background(ModeColor.accent, in: .circle)
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(Text(canSend ? labels.send : labels.speak))
                }
                .padding(ModeSpacing.xs)
                .frame(minHeight: ModeSize.composerHeight)
                // A capsule's ends grow with its height and cut into several lines of text; a fixed radius doesn't.
                .glassEffect(.regular.interactive(), in: .rect(cornerRadius: ModeSize.composerHeight / 2))
            }
        }
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
