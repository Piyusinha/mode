import AppIntents
import ModeDesignSystem
import SwiftUI

// iOS gives a Live Activity about 160 pt of height on the Lock Screen and in the
// expanded Dynamic Island; anything taller is cut. Every state below is laid out to
// fit: one header row, at most two lines of text, one row of progress or buttons.

/// The Live Activity on the Lock Screen (and in the notification list and Smart Stack).
struct ModeActivityLockScreen: View {
    let conversationId: String?
    let state: ModeActivityAttributes.ContentState
    /// Mode stopped updating it (closed mid-task).
    let stale: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            if stale && state.phase != .finished {
                ModeActivityHeader { ModeBadge(ActivityL10n.stopped, icon: .stopped, tone: .neutral) }
                Text(ActivityL10n.closed).modeText(.callout, color: ModeColor.textSecondary).lineLimit(2)
            } else {
                switch state.phase {
                case .working:
                    ModeActivityWorkingHeader(state: state)
                    ModeActivityWorkingBody(state: state)
                case .waiting:
                    ModeActivityHeader { ModeActivityWaitingBadge(state: state) }
                    ModeActivityWaitingBody(state: state)
                case .finished:
                    ModeActivityHeader { ModeActivityOutcome(state: state) }
                    ModeActivityFinishedBody(state: state, conversationId: conversationId)
                }
            }
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.vertical, ModeSpacing.m)
        .modeActivityTheme()
    }
}

extension View {
    /// Live Activities sit on dark glass or the black island: always the dark palette.
    func modeActivityTheme() -> some View {
        environment(\.colorScheme, .dark)
    }
}

/// The mark and "Mode", with something on the right.
struct ModeActivityHeader<Trailing: View>: View {
    @ViewBuilder let trailing: () -> Trailing

    var body: some View {
        HStack(spacing: ModeSpacing.s) {
            ModeMark(size: ModeSize.activityMark, alwaysLight: true)
            Text(ActivityL10n.mode).modeText(.subheadline).fontWeight(.semibold)
            Spacer(minLength: ModeSpacing.s)
            trailing()
        }
        .lineLimit(1)
    }
}

/// The mark, "Step 4 · 0:24" ticking (or Paused), and Stop.
struct ModeActivityWorkingHeader: View {
    let state: ModeActivityAttributes.ContentState

    var body: some View {
        HStack(spacing: ModeSpacing.s) {
            ModeMark(size: ModeSize.activityMark, alwaysLight: true)
            ModeActivityStepClock(state: state)
            Spacer(minLength: ModeSpacing.s)
            ModeActivityStopButton()
        }
    }
}

/// "Step 4 · 0:24", the time ticking on its own; "Paused" while paused.
struct ModeActivityStepClock: View {
    let state: ModeActivityAttributes.ContentState

    var body: some View {
        HStack(spacing: ModeSpacing.xs) {
            if state.paused {
                Text(ActivityL10n.paused)
            } else if let start = state.startedAt {
                Text(ActivityL10n.step(state.step)).fixedSize()
                Text(timerInterval: start...Date.distantFuture, countsDown: false)
                    .frame(width: ModeSize.islandTimer, alignment: .leading)
            } else {
                Text(ActivityL10n.stepShort(state.step))
            }
        }
        .modeText(.subheadline, color: ModeColor.textSecondary)
        .monospacedDigit()
        .lineLimit(1)
    }
}

/// What Mode is doing now (two lines at most), and the plan's progress.
struct ModeActivityWorkingBody: View {
    let state: ModeActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            Text(state.now).modeText(.headline).lineLimit(2).minimumScaleFactor(ModeActivityScale.text)
            if !state.plan.isEmpty { ModeSegments(state.plan) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The question (two lines at most) and its answers right here: Allow / Don't allow, up to three
/// options, or Reply (typed in Mode). The app being used stays in front.
struct ModeActivityWaitingBody: View {
    let state: ModeActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            Text(state.question ?? "").modeText(.headline).lineLimit(2).minimumScaleFactor(ModeActivityScale.text)
            HStack(spacing: ModeSpacing.s) {
                if let id = state.questionId, state.yourTurn {
                    ModeActivityStopLabelButton()
                    Button(intent: AnswerIntent(questionId: id, answer: ModeActivityAnswer.done)) {
                        ModeActivityButtonLabel(ActivityL10n.entered, icon: nil, prominent: true)
                    }.buttonStyle(.plain)
                } else if let id = state.questionId, state.yesNo {
                    Button(intent: AnswerIntent(questionId: id, answer: ModeActivityAnswer.no)) {
                        ModeActivityButtonLabel(ActivityL10n.dontAllow, icon: nil, prominent: false)
                    }.buttonStyle(.plain)
                    Button(intent: AnswerIntent(questionId: id, answer: ModeActivityAnswer.yes)) {
                        ModeActivityButtonLabel(ActivityL10n.allow, icon: nil, prominent: true)
                    }.buttonStyle(.plain)
                } else if let id = state.questionId, !state.options.isEmpty {
                    ForEach(Array(state.options.prefix(ModeActivityAnswer.maxOptions).enumerated()), id: \.offset) { index, option in
                        Button(intent: AnswerIntent(questionId: id, answer: option)) {
                            ModeActivityButtonLabel(LocalizedStringResource(stringLiteral: option), icon: nil, prominent: index == 0)
                        }.buttonStyle(.plain)
                    }
                } else {
                    Link(destination: ModeLinks.open) { ModeActivityButtonLabel(ActivityL10n.reply, icon: nil, prominent: true) }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// What the runner expects back, and how many options fit on the Live Activity.
enum ModeActivityAnswer {
    static let yes = "yes"
    static let no = "no"
    static let done = "done"
    static let maxOptions = 3
}

/// One line of the answer (two rows at most), then Open chat (with how much more is there) and Another
/// task: with the header, that fits the 160 pt iOS gives a Live Activity, so nothing is cut.
struct ModeActivityFinishedBody: View {
    let state: ModeActivityAttributes.ContentState
    let conversationId: String?

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            if let line = state.reply.first.flatMap(\.line) {
                ModeInlineText(line, style: .headline).lineLimit(2).minimumScaleFactor(ModeActivityScale.text)
            }
            HStack(spacing: ModeSpacing.s) {
                Link(destination: ModeLinks.conversation(conversationId)) {
                    ModeActivityButtonLabel(state.more > 0 ? ActivityL10n.openChatMore(state.more) : ActivityL10n.openChat, icon: nil, prominent: false)
                }
                Link(destination: ModeLinks.ask) { ModeActivityButtonLabel(ActivityL10n.anotherTask, icon: .ask, prominent: true) }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// "Mode needs you", or "Your turn" when only the user can do the next bit (a PIN, a password).
struct ModeActivityWaitingBadge: View {
    let state: ModeActivityAttributes.ContentState

    var body: some View {
        if state.yourTurn {
            ModeBadge(ActivityL10n.yourTurn, icon: .lock, tone: .warning)
        } else {
            ModeBadge(ActivityL10n.needsYou, icon: .waiting, tone: .warning)
        }
    }
}

/// Stop as a capsule beside another answer (the red disc is for the working state).
struct ModeActivityStopLabelButton: View {
    var body: some View {
        Button(intent: StopTaskIntent()) {
            ModeActivityButtonLabel(ActivityL10n.stop, icon: nil, prominent: false)
        }
        .buttonStyle(.plain)
    }
}

/// "✓ Done in 62 s" or how else it ended.
struct ModeActivityOutcome: View {
    let state: ModeActivityAttributes.ContentState

    var body: some View {
        if let badge = state.outcome?.badge(seconds: state.seconds) {
            ModeBadge(badge.text, icon: badge.icon, tone: badge.tone)
        }
    }
}

/// The red Stop disc.
struct ModeActivityStopButton: View {
    var body: some View {
        Button(intent: StopTaskIntent()) {
            ModeIcon.stop.image
                .font(ModeTextStyle.caption.font)
                .foregroundStyle(ModeColor.stopRed)
                .frame(width: ModeSize.activityStop, height: ModeSize.activityStop)
                .background(ModeColor.stopDisc, in: .circle)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(ActivityL10n.stop))
    }
}

/// A small capsule button's look: grey, or the accent for the main one.
struct ModeActivityButtonLabel: View {
    let title: LocalizedStringResource
    let icon: ModeIcon?
    let prominent: Bool

    init(_ title: LocalizedStringResource, icon: ModeIcon?, prominent: Bool) {
        self.title = title
        self.icon = icon
        self.prominent = prominent
    }

    var body: some View {
        HStack(spacing: ModeSpacing.xs) {
            if let icon { icon.image }
            Text(title)
        }
        .modeText(.footnote, color: prominent ? ModeColor.onAccent : ModeColor.textPrimary)
        .fontWeight(.semibold)
        .lineLimit(1)
        .frame(maxWidth: .infinity, minHeight: ModeSize.activityButton)
        .background(prominent ? ModeColor.accent : ModeColor.fill, in: .capsule)
    }
}

/// How far long lines may shrink before they're cut.
enum ModeActivityScale {
    static let text: CGFloat = 0.85
}

private extension ModeReplyBlock {
    /// The text of a paragraph or heading: the Live Activity's one line.
    var line: String? {
        switch self {
        case let .paragraph(text), let .heading(_, text), let .quote(text): text
        default: nil
        }
    }
}
