import ActivityKit
import ModeDesignSystem
import SwiftUI
import WidgetKit

/// The running task on the Lock Screen and the Dynamic Island.
struct ModeLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ModeActivityAttributes.self) { context in
            ModeActivityLockScreen(conversationId: context.attributes.conversationId, state: context.state)
                .activityBackgroundTint(ModeColor.lockScreen)
                .activitySystemActionForegroundColor(ModeColor.onAccent)
                .widgetURL(ModeLinks.conversation(context.attributes.conversationId))
        } dynamicIsland: { context in
            let state = context.state
            let conversation = context.attributes.conversationId
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: ModeSpacing.s) {
                        ModeMark(size: ModeSize.activityMark, alwaysLight: true)
                        if state.phase == .working { ModeActivityStepClock(state: state) }
                    }
                    .padding(.leading, ModeSpacing.xs)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    switch state.phase {
                    case .working: ModeActivityStopButton()
                    case .waiting: ModeBadge(ActivityL10n.needsYou, icon: .waiting, tone: .warning)
                    case .finished: ModeActivityOutcome(state: state)
                    }
                }
                DynamicIslandExpandedRegion(.bottom) {
                    Group {
                        switch state.phase {
                        case .working: ModeActivityWorkingBody(state: state)
                        case .waiting: ModeActivityWaitingBody(state: state, conversationId: conversation)
                        case .finished: ModeActivityFinishedBody(state: state, conversationId: conversation)
                        }
                    }
                    .padding(.horizontal, ModeSpacing.xs)
                    .padding(.top, ModeSpacing.xs)
                }
            } compactLeading: {
                ModeMark(size: ModeSize.islandMark, alwaysLight: true)
            } compactTrailing: {
                ModeIslandTrailing(state: state)
            } minimal: {
                ModeIslandMinimal(state: state)
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
            if let start = state.startedAt, !state.paused {
                Text(timerInterval: start...Date.distantFuture, countsDown: false)
                    .modeText(.captionEmphasis, color: ModeColor.tint)
                    .monospacedDigit()
                    .frame(width: ModeSize.islandTimer)
            } else {
                ModeIcon.pause.image.foregroundStyle(ModeColor.textSecondary)
            }
        case .waiting:
            ModeIcon.waiting.image.foregroundStyle(ModeColor.warning)
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
