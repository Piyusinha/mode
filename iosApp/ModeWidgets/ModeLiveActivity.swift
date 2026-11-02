import ActivityKit
import ModeDesignSystem
import SwiftUI
import WidgetKit

/// The running task on the Lock Screen and the Dynamic Island.
struct ModeLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ModeActivityAttributes.self) { context in
            ModeActivityLockScreen(conversationId: context.attributes.conversationId, state: context.state, stale: context.isStale)
                .activityBackgroundTint(ModeColor.lockScreen)
                .activitySystemActionForegroundColor(ModeColor.onAccent)
                .widgetURL(ModeLinks.conversation(context.attributes.conversationId))
        } dynamicIsland: { context in
            let state = context.state
            let conversation = context.attributes.conversationId
            return DynamicIsland {
                // The sides beside the camera are narrow: just the mark and Stop (or how it ended).
                DynamicIslandExpandedRegion(.leading) {
                    ModeMark(size: ModeSize.activityMark, alwaysLight: true)
                        .padding(.leading, ModeSpacing.xs)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    Group {
                        switch state.phase {
                        case .working: ModeActivityStopButton()
                        case .waiting: ModeActivityWaitingBadge(state: state)
                        case .finished: ModeActivityOutcome(state: state)
                        }
                    }
                    .padding(.trailing, ModeSpacing.xs)
                    .modeActivityTheme()
                }
                // Everything else gets the full width below.
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: ModeSpacing.s) {
                        switch state.phase {
                        case .working:
                            ModeActivityStepClock(state: state)
                            ModeActivityWorkingBody(state: state)
                        case .waiting:
                            ModeActivityWaitingBody(state: state)
                        case .finished:
                            ModeActivityFinishedBody(state: state, conversationId: conversation)
                        }
                    }
                    .padding(.horizontal, ModeSpacing.xs)
                    .modeActivityTheme()
                }
            } compactLeading: {
                ModeMark(size: ModeSize.islandMark, alwaysLight: true)
            } compactTrailing: {
                ModeIslandTrailing(state: state).modeActivityTheme()
            } minimal: {
                ModeIslandMinimal(state: state).modeActivityTheme()
            }
            .widgetURL(ModeLinks.conversation(conversation))
            .keylineTint(ModeColor.accent)
        }
    }
}

/// Beside the camera: the ticking time while working; how it ended once done.
private struct ModeIslandTrailing: View {
    let state: ModeActivityAttributes.ContentState

    var body: some View {
        switch state.phase {
        case .working:
            if state.paused {
                ModeIcon.pause.image.foregroundStyle(ModeColor.textSecondary)
            } else {
                // The step moves with each action: visible progress even when collapsed.
                Text(ActivityL10n.stepShort(state.step))
                    .modeText(.subheadline, color: ModeColor.tint)
                    .fontWeight(.semibold)
                    .monospacedDigit()
                    .lineLimit(1)
                    .fixedSize()
            }
        case .waiting:
            (state.yourTurn ? ModeIcon.lock : ModeIcon.waiting).image.foregroundStyle(ModeColor.warning)
        case .finished:
            if let badge = state.outcome?.badge(seconds: state.seconds) {
                badge.icon.image.foregroundStyle(badge.tone == .success ? ModeColor.success : ModeColor.warning)
            }
        }
    }
}

/// Sharing the island with another app: the mark, or how it ended.
private struct ModeIslandMinimal: View {
    let state: ModeActivityAttributes.ContentState

    var body: some View {
        if state.phase == .finished, let badge = state.outcome?.badge(seconds: state.seconds) {
            badge.icon.image.foregroundStyle(badge.tone == .success ? ModeColor.success : ModeColor.warning)
        } else {
            ModeMark(size: ModeSize.islandMark, alwaysLight: true)
        }
    }
}
