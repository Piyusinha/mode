import AppIntents
import ModeDesignSystem
import SwiftUI

// iOS gives a Live Activity about 160 pt of height on the Lock Screen and in the
// expanded Dynamic Island; anything taller is cut. Every state below is laid out to
// fit: one header row (Mo, then the status), at most two lines of text, one row of
// progress or 36 pt buttons (14 + 30 + 8 + 44 + 8 + 36 + 14 = 154 pt).

/// The Live Activity on the Lock Screen (and in the notification list and Smart Stack): Mode's dark card,
/// or white with a blue ring while Mode asks something.
struct ModeActivityLockScreen: View {
    let conversationId: String?
    let state: ModeActivityAttributes.ContentState
    /// Mode stopped updating it (closed mid-task).
    let stale: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            ModeActivityHeader(state: state, stale: stale, island: false)
            ModeActivityBody(state: state, stale: stale, conversationId: conversationId, island: false)
        }
        .padding(.horizontal, ModeSpacing.activityHorizontal)
        .padding(.vertical, ModeSpacing.activityVertical)
        .frame(maxWidth: .infinity, alignment: .leading)
        .environment(\.colorScheme, state.asks(stale: stale) ? .light : .dark)
    }
}

extension View {
    /// The island is always black: the dark palette.
    func modeActivityTheme() -> some View {
        environment(\.colorScheme, .dark)
    }
}

extension ModeActivityAttributes.ContentState {
    /// Mo's face for this moment.
    func mood(stale: Bool = false) -> ModeMo.Mood {
        if stale && phase != .finished { return .sleep }
        switch phase {
        case .working: return paused ? .sleep : .working
        case .waiting: return .yourTurn
        case .locked: return .idle
        case .finished:
            switch outcome {
            case .done: return .done
            case .stopped, .notStarted: return .sleep
            case .gaveUp, .failed, nil: return .stuck
            }
        }
    }

    /// Mode is asking: the Lock Screen card turns white.
    func asks(stale: Bool) -> Bool { (phase == .waiting || phase == .locked) && !stale }

    /// How much of the plan is done, for the ring beside the camera.
    var planDone: Double {
        guard !plan.isEmpty else { return 0 }
        // Never full until it's done: the step being done counts as begun.
        return Double(plan.filter { $0.state == .done }.count + 1) / Double(plan.count + 2)
    }
}

/// The top row: Mo, then "Working · 0:24" and Stop, or "Mode" and how things stand.
struct ModeActivityHeader: View {
    let state: ModeActivityAttributes.ContentState
    let stale: Bool
    let island: Bool

    var body: some View {
        HStack(spacing: ModeSpacing.s) {
            ModeMo(state.mood(stale: stale), size: ModeSize.activityMo)
            if state.phase == .working && !stale {
                ModeActivityClock(state: state).modeText(.subheadline, color: ModeColor.onDarkSecondary)
            } else if state.phase == .locked {
                Text(ActivityL10n.unlockToStart).modeText(.subheadlineEmphasis)
            } else {
                Text(ActivityL10n.mode).modeText(.subheadlineEmphasis)
            }
            Spacer(minLength: ModeSpacing.s)
            ModeActivityTrailing(state: state, stale: stale, island: island)
        }
        .frame(height: ModeSize.activityHeader)
        .lineLimit(1)
    }
}

/// The right of the header: Stop while working, otherwise how things stand ("Needs you", "Done in 62 s").
struct ModeActivityTrailing: View {
    let state: ModeActivityAttributes.ContentState
    let stale: Bool
    let island: Bool

    var body: some View {
        if stale && state.phase != .finished {
            ModeActivityStatus(ActivityL10n.stopped, glyph: .stopped, color: ModeColor.onDarkSecondary)
        } else {
            switch state.phase {
            case .working: ModeActivityStopButton()
            case .locked: ModeActivityGlyph.locked.image.font(ModeIconFont.chevron).foregroundStyle(island ? ModeColor.onDarkSecondary : ModeColor.textSecondary)
            case .waiting:
                ModeActivityStatus(state.yourTurn ? ActivityL10n.yourTurn : ActivityL10n.needsYou, glyph: state.yourTurn ? .yourTurn : .needsYou,
                                   color: state.yourTurn && island ? ModeColor.warning : ModeColor.infoText)
            case .finished:
                if let outcome = state.outcome { outcomeStatus(outcome) }
            }
        }
    }

    @ViewBuilder private func outcomeStatus(_ outcome: ModeActivityAttributes.ContentState.Outcome) -> some View {
        switch outcome {
        case .done: ModeActivityStatus(ActivityL10n.doneIn(state.seconds), glyph: .done, color: ModeColor.tagGreenText)
        case .gaveUp: ModeActivityStatus(ActivityL10n.gaveUp, glyph: .couldntFinish, color: island ? ModeColor.danger : ModeColor.warning)
        case .failed: ModeActivityStatus(ActivityL10n.failed, glyph: .couldntFinish, color: ModeColor.danger)
        case .stopped: ModeActivityStatus(ActivityL10n.stopped, glyph: .needsYou, color: ModeColor.onDarkSecondary)
        case .notStarted: ModeActivityStatus(ActivityL10n.notStarted, glyph: .needsYou, color: ModeColor.onDarkSecondary)
        }
    }
}

/// A status: a small mark and a word or two, in one colour.
struct ModeActivityStatus: View {
    let text: LocalizedStringResource
    let glyph: ModeActivityGlyph
    let color: Color

    init(_ text: LocalizedStringResource, glyph: ModeActivityGlyph, color: Color) {
        self.text = text
        self.glyph = glyph
        self.color = color
    }

    var body: some View {
        HStack(spacing: ModeSpacing.activityBadgeGap) {
            glyph.image.font(ModeIconFont.chevron)
            Text(text)
        }
        .modeText(.footnoteEmphasis, color: color)
        .fixedSize()
    }
}

/// "Working · 0:24", the time ticking on its own; "Paused" while paused.
struct ModeActivityClock: View {
    let state: ModeActivityAttributes.ContentState

    var body: some View {
        HStack(spacing: ModeSpacing.xs) {
            if state.paused {
                Text(ActivityL10n.paused)
            } else if let start = state.startedAt {
                Text(ActivityL10n.working).fixedSize()
                Text(timerInterval: start...Date.distantFuture, countsDown: false)
                    .frame(width: ModeSize.islandTimer, alignment: .leading)
            } else {
                Text(ActivityL10n.workingNow)
            }
        }
        .monospacedDigit()
        .lineLimit(1)
    }
}

/// Under the header: what Mode is doing and the plan, the question and its answers, or the result and what next.
struct ModeActivityBody: View {
    let state: ModeActivityAttributes.ContentState
    let stale: Bool
    let conversationId: String?
    let island: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: island ? ModeSpacing.islandGap : ModeSpacing.s) {
            if stale && state.phase != .finished {
                Text(ActivityL10n.closed).modeText(.callout, color: ModeColor.onDarkSecondary).lineLimit(2)
            } else {
                switch state.phase {
                case .working:
                    if island { ModeActivityClock(state: state).modeText(.footnote, color: ModeColor.onDarkSecondary) }
                    ModeActivityTitle(state.now, island: island)
                    if !state.plan.isEmpty { ModeActivityBars(state.plan) }
                case .waiting:
                    ModeActivityTitle(state.question ?? "", island: island)
                    ModeActivityAnswers(state: state, island: island)
                case .locked:
                    ModeActivityTitle(state.lockedTask ?? "", island: island)
                    ModeActivityUnlock(state: state, island: island)
                case .finished:
                    if let line = state.reply.first.flatMap(\.line) { ModeActivityTitle(line, island: island) }
                    HStack(spacing: ModeSpacing.s) {
                        Link(destination: ModeLinks.conversation(conversationId)) {
                            ModeActivityButtonLabel(state.more > 0 ? ActivityL10n.openChatMore(state.more) : ActivityL10n.openChat,
                                                    style: island ? .islandGrey : .grey)
                        }
                        if let retry = state.retryTaskId {
                            Link(destination: ModeLinks.retry(retry, conversationId: conversationId)) {
                                ModeActivityButtonLabel(ActivityL10n.tryAgain, style: .light)
                            }
                        } else {
                            Link(destination: ModeLinks.ask) { ModeActivityButtonLabel(ActivityL10n.anotherTask, style: .light) }
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// "Unlock, or say your phrase" and Cancel, under the waiting task.
struct ModeActivityUnlock: View {
    let state: ModeActivityAttributes.ContentState
    let island: Bool

    var body: some View {
        HStack(spacing: ModeSpacing.s) {
            VStack(alignment: .leading, spacing: ModeSpacing.none) {
                Text(state.voiceUnlock ? ActivityL10n.unlockOrSay : ActivityL10n.unlock).modeText(.footnoteEmphasis)
                Text(ActivityL10n.startsOnUnlock).modeText(.caption, color: island ? ModeColor.onDarkSecondary : ModeColor.textSecondary)
            }
            .lineLimit(1)
            .minimumScaleFactor(ModeTextScale.title)
            Spacer(minLength: ModeSpacing.xs)
            if let id = state.lockedTaskId {
                Button(intent: CancelWaitingIntent(taskId: id)) {
                    ModeActivityButtonLabel(ActivityL10n.cancel, style: island ? .islandGrey : .askQuiet)
                }.buttonStyle(.plain)
                .fixedSize()
            }
        }
    }
}

/// The question's answers right here: Yes / No, up to three options, I've entered it / Stop, or Reply in Mode
/// (typed in Mode). The app being used stays in front.
struct ModeActivityAnswers: View {
    let state: ModeActivityAttributes.ContentState
    let island: Bool

    private var quiet: ModeActivityButtonLabel.Style { island ? .islandGrey : .askQuiet }

    var body: some View {
        HStack(spacing: ModeSpacing.s) {
            if let id = state.questionId, state.yourTurn {
                Button(intent: StopTaskIntent()) {
                    ModeActivityButtonLabel(ActivityL10n.stop, style: island ? .islandGrey : .stop)
                }.buttonStyle(.plain)
                Button(intent: AnswerIntent(questionId: id, answer: ModeActivityAnswer.done)) {
                    ModeActivityButtonLabel(ActivityL10n.entered, style: .ask)
                }.buttonStyle(.plain)
            } else if let id = state.questionId, state.yesNo {
                Button(intent: AnswerIntent(questionId: id, answer: ModeActivityAnswer.no)) {
                    ModeActivityButtonLabel(ActivityL10n.dontAllow, style: quiet)
                }.buttonStyle(.plain)
                Button(intent: AnswerIntent(questionId: id, answer: ModeActivityAnswer.yes)) {
                    ModeActivityButtonLabel(ActivityL10n.allow, style: .ask)
                }.buttonStyle(.plain)
            } else if let id = state.questionId, !state.options.isEmpty {
                ForEach(Array(state.options.prefix(ModeActivityAnswer.maxOptions).enumerated()), id: \.offset) { _, option in
                    Button(intent: AnswerIntent(questionId: id, answer: option)) {
                        ModeActivityButtonLabel(LocalizedStringResource(stringLiteral: option), style: option == state.recommended ? .ask : quiet)
                    }.buttonStyle(.plain)
                }
            } else {
                Link(destination: ModeLinks.open) { ModeActivityButtonLabel(ActivityL10n.reply, style: .ask) }
            }
        }
    }
}

/// Beside the camera: the time ticking and a ring filling with the plan while working; pause; a mark when
/// Mode needs you; how it ended.
struct ModeIslandTrailing: View {
    let state: ModeActivityAttributes.ContentState

    var body: some View {
        switch state.phase {
        case .working:
            if state.paused {
                ModeActivityGlyph.paused.image.font(ModeIconFont.small).foregroundStyle(ModeColor.heroText)
            } else {
                HStack(spacing: ModeSpacing.activityBarGap) {
                    if !state.plan.isEmpty { ModeIslandRing(fraction: state.planDone) }
                    if let start = state.startedAt {
                        // The time ticks on its own: visible progress even when collapsed.
                        Text(timerInterval: start...Date.distantFuture, countsDown: false)
                            .font(ModeActivityFont.clock)
                            .foregroundStyle(ModeColor.brand)
                            .lineLimit(1)
                            .multilineTextAlignment(.trailing)
                            .frame(width: ModeSize.islandClock)
                    }
                }
            }
        case .locked:
            ModeActivityGlyph.locked.image.font(ModeIconFont.small).foregroundStyle(ModeColor.onDarkSecondary)
        case .waiting:
            (state.yourTurn ? ModeActivityGlyph.yourTurn : ModeActivityGlyph.needsYou).image
                .font(ModeIconFont.small)
                .foregroundStyle(state.yourTurn ? ModeColor.warning : ModeColor.infoText)
        case .finished:
            switch state.outcome {
            case .done: ModeActivityGlyph.done.image.font(ModeIconFont.small.weight(.bold)).foregroundStyle(ModeColor.tagGreenText)
            case .gaveUp, .failed: ModeActivityGlyph.couldntFinish.image.font(ModeIconFont.small).foregroundStyle(ModeColor.danger)
            case .stopped, .notStarted, nil: ModeActivityGlyph.stopped.image.font(ModeIconFont.small).foregroundStyle(ModeColor.onDarkSecondary)
            }
        }
    }
}

/// What the runner expects back, and how many options fit on the Live Activity.
enum ModeActivityAnswer {
    static let yes = "yes"
    static let no = "no"
    static let done = "done"
    static let maxOptions = 3
}

/// The red Stop disc.
struct ModeActivityStopButton: View {
    var body: some View {
        Button(intent: StopTaskIntent()) { ModeActivityStopDisc() }
            .buttonStyle(.plain)
            .accessibilityLabel(Text(ActivityL10n.stop))
    }
}

/// A capsule button's look, 36 pt tall so a whole state fits in 160 pt.
struct ModeActivityButtonLabel: View {
    enum Style {
        /// On the dark Lock Screen card, and on the black island.
        case grey, islandGrey
        /// Paper on dark: Another task.
        case light
        /// The blue answer, and the soft blue one beside it on the white card.
        case ask, askQuiet
        /// Stop on the white card.
        case stop
    }

    let title: LocalizedStringResource
    let style: Style

    init(_ title: LocalizedStringResource, style: Style) {
        self.title = title
        self.style = style
    }

    var body: some View {
        Text(title)
            .modeText(.subheadlineEmphasis, color: foreground)
            .lineLimit(1)
            .padding(.horizontal, ModeSpacing.activityButtonPadding)
            .frame(maxWidth: .infinity, minHeight: ModeSize.activityButton)
            .background(background, in: .capsule)
    }

    private var foreground: Color {
        switch style {
        case .grey, .islandGrey: ModeColor.textPrimary
        case .light: ModeColor.onAccent
        case .ask: ModeColor.onAsk
        case .askQuiet: ModeColor.infoText
        case .stop: ModeColor.danger
        }
    }

    private var background: Color {
        switch style {
        case .grey: ModeColor.onDarkFill
        case .islandGrey: ModeColor.onIslandFill
        case .light: ModeColor.accent
        case .ask: ModeColor.info
        case .askQuiet: ModeColor.infoSoft
        case .stop: ModeColor.tileRed
        }
    }
}

extension ModeReplyBlock {
    /// The text of a paragraph or heading: the Live Activity's one line.
    var line: String? {
        switch self {
        case let .paragraph(text), let .heading(_, text), let .quote(text): text
        default: nil
        }
    }
}

// MARK: - Mode is connected

/// "Mode is connected" on the Lock Screen: the Device Hub session is open with no task running, so iOS keeps
/// the screen on. Mo, how long it has been open, what that means, and Disconnect (it does not unpair).
struct ModeSessionLockScreen: View {
    let state: ModeSessionAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            HStack(spacing: ModeSpacing.s) {
                ModeMo(.idle, size: ModeSize.activityMo)
                ModeSessionClock(since: state.since).modeText(.subheadline, color: ModeColor.onDarkSecondary)
                Spacer(minLength: ModeSpacing.s)
                ModeActivityGlyph.connected.image.font(ModeIconFont.chevron).foregroundStyle(ModeColor.onDarkSecondary)
            }
            .frame(height: ModeSize.activityHeader)
            .lineLimit(1)
            ModeSessionBody(island: false)
        }
        .padding(.horizontal, ModeSpacing.activityHorizontal)
        .padding(.vertical, ModeSpacing.activityVertical)
        .frame(maxWidth: .infinity, alignment: .leading)
        .environment(\.colorScheme, .dark)
    }
}

/// "Connected · 4:12", ticking on its own.
struct ModeSessionClock: View {
    let since: Date

    var body: some View {
        HStack(spacing: ModeSpacing.xs) {
            Text(ActivityL10n.connected).fixedSize()
            Text(timerInterval: since...Date.distantFuture, countsDown: false)
                .frame(width: ModeSize.islandTimer, alignment: .leading)
        }
        .monospacedDigit()
        .lineLimit(1)
    }
}

/// What an open session means, and Disconnect.
struct ModeSessionBody: View {
    let island: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: island ? ModeSpacing.islandGap : ModeSpacing.s) {
            VStack(alignment: .leading, spacing: ModeSpacing.none) {
                Text(ActivityL10n.canControl).modeText(.calloutEmphasis)
                Text(ActivityL10n.screenStaysOn).modeText(.caption, color: ModeColor.onDarkSecondary)
            }
            .lineLimit(1)
            .minimumScaleFactor(ModeTextScale.title)
            ModeSessionDisconnectButton(island: island)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Disconnect: closes the session and Mode's VPN, keeps the pairing.
struct ModeSessionDisconnectButton: View {
    let island: Bool

    var body: some View {
        Button(intent: DisconnectIntent()) {
            ModeActivityButtonLabel(ActivityL10n.disconnect, style: island ? .islandGrey : .light)
        }
        .buttonStyle(.plain)
    }
}
