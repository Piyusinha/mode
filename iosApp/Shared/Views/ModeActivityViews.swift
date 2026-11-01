import AppIntents
import ModeDesignSystem
import SwiftUI

/// The Live Activity on the Lock Screen (and the Smart Stack): working, waiting for you, or the answer.
/// Draws the state it is given; its buttons are intents and links.
struct ModeActivityLockScreen: View {
    let conversationId: String?
    let state: ModeActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            switch state.phase {
            case .working:
                ModeActivityWorkingHeader(state: state)
                ModeActivityWorkingBody(state: state)
            case .waiting:
                ModeActivityHeader { ModeBadge(ActivityL10n.needsYou, icon: .waiting, tone: .warning) }
                ModeActivityWaitingBody(state: state, conversationId: conversationId)
            case .finished:
                ModeActivityHeader { ModeActivityOutcome(state: state) }
                ModeActivityFinishedBody(state: state, conversationId: conversationId)
            }
        }
        .padding(ModeSpacing.l)
        .environment(\.colorScheme, .dark)
    }
}

/// The mark and "Mode", with something on the right.
struct ModeActivityHeader<Trailing: View>: View {
    @ViewBuilder let trailing: () -> Trailing

    var body: some View {
        HStack(spacing: ModeSpacing.s) {
            ModeMark(size: ModeSize.activityMark, alwaysLight: true)
            Text(ActivityL10n.mode).modeText(.headline)
            Spacer(minLength: ModeSpacing.s)
            trailing()
        }
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
                Text(ActivityL10n.step(state.step))
                Text(timerInterval: start...Date.distantFuture, countsDown: false)
            } else {
                Text(ActivityL10n.stepShort(state.step))
            }
        }
        .modeText(.subheadline, color: ModeColor.textSecondary)
        .monospacedDigit()
        .lineLimit(1)
    }
}

/// What Mode is doing now, and the plan's progress.
struct ModeActivityWorkingBody: View {
    let state: ModeActivityAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            Text(state.now).modeText(.title2).lineLimit(2).minimumScaleFactor(ModeActivityScale.text)
            if !state.plan.isEmpty { ModeSegments(state.plan) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The question, and Open (it's answered in Mode).
struct ModeActivityWaitingBody: View {
    let state: ModeActivityAttributes.ContentState
    let conversationId: String?

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            Text(state.question ?? "").modeText(.headline).lineLimit(3)
            Link(destination: ModeLinks.open) { ModeActivityButtonLabel(ActivityL10n.open, icon: nil, prominent: true) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// The start of the answer, "+1 more in the chat", Open chat and Another task.
struct ModeActivityFinishedBody: View {
    let state: ModeActivityAttributes.ContentState
    let conversationId: String?

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            ModeReply(state.reply, density: .compact).lineLimit(2)
            if state.more > 0 { Text(ActivityL10n.more(state.more)).modeText(.caption, color: ModeColor.textSecondary) }
            HStack(spacing: ModeSpacing.s) {
                Link(destination: ModeLinks.conversation(conversationId)) { ModeActivityButtonLabel(ActivityL10n.openChat, icon: nil, prominent: false) }
                Link(destination: ModeLinks.ask) { ModeActivityButtonLabel(ActivityL10n.anotherTask, icon: .ask, prominent: true) }
            }
            .padding(.top, ModeSpacing.xs)
        }
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
                .font(ModeTextStyle.subheadline.font)
                .foregroundStyle(ModeColor.stopRed)
                .frame(width: ModeSize.activityStop, height: ModeSize.activityStop)
                .background(ModeColor.stopDisc, in: .circle)
        }
        .buttonStyle(.plain)
        .accessibilityLabel(Text(ActivityL10n.stop))
    }
}

/// A capsule button's look: grey, or the accent for the main one.
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
        .modeText(.subheadline, color: prominent ? ModeColor.onAccent : ModeColor.textPrimary)
        .fontWeight(.semibold)
        .frame(maxWidth: .infinity, minHeight: ModeSize.activityButton)
        .background(prominent ? ModeColor.accent : ModeColor.fill, in: .capsule)
    }
}

/// How far long lines may shrink before they wrap.
enum ModeActivityScale {
    static let text: CGFloat = 0.8
}
