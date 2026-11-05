import ActivityKit
import ModeDesignSystem
import SwiftUI
import WidgetKit

/// The running task on the Lock Screen and the Dynamic Island, with Mo showing how it's going.
struct ModeLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ModeActivityAttributes.self) { context in
            let asks = context.state.asks(stale: context.isStale)
            ModeActivityLockScreen(conversationId: context.attributes.conversationId, state: context.state, stale: context.isStale)
                // A question turns the card white, ringed in blue, so it stands out on the Lock Screen.
                .overlay {
                    if asks { ContainerRelativeShape().strokeBorder(ModeColor.lockScreenAskRing, lineWidth: ModeSize.activityAskRing) }
                }
                .activityBackgroundTint(asks ? ModeColor.lockScreenAsk : ModeColor.lockScreen)
                .activitySystemActionForegroundColor(asks ? ModeColor.lockScreenAskRing : ModeColor.heroText)
                .widgetURL(ModeLinks.conversation(context.attributes.conversationId))
        } dynamicIsland: { context in
            let state = context.state
            let conversation = context.attributes.conversationId
            return DynamicIsland {
                // The sides beside the camera are narrow: Mo, and Stop or how things stand.
                DynamicIslandExpandedRegion(.leading) {
                    ModeMo(state.mood(stale: context.isStale), size: ModeSize.islandExpandedMo)
                        .padding(.leading, ModeSpacing.islandHorizontal)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    ModeActivityTrailing(state: state, stale: context.isStale, island: true)
                        .frame(height: ModeSize.islandExpandedMo)
                        .padding(.trailing, ModeSpacing.islandHorizontal)
                        .modeActivityTheme()
                }
                // Everything else gets the full width below.
                DynamicIslandExpandedRegion(.bottom) {
                    ModeActivityBody(state: state, stale: context.isStale, conversationId: conversation, island: true)
                        .padding(.horizontal, ModeSpacing.islandHorizontal)
                        .modeActivityTheme()
                }
            } compactLeading: {
                ModeMo(state.mood(stale: context.isStale), size: ModeSize.islandCompactMo)
            } compactTrailing: {
                ModeIslandTrailing(state: state).modeActivityTheme()
            } minimal: {
                ModeMo(state.mood(stale: context.isStale), size: ModeSize.islandMinimalMo)
            }
            .widgetURL(ModeLinks.conversation(conversation))
            .keylineTint(ModeColor.brand)
        }
    }
}

/// "Mode is connected", with Disconnect: the Device Hub session is open while no task runs.
struct ModeSessionActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: ModeSessionAttributes.self) { context in
            ModeSessionLockScreen(state: context.state)
                .activityBackgroundTint(ModeColor.lockScreen)
                .activitySystemActionForegroundColor(ModeColor.heroText)
                .widgetURL(ModeLinks.open)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    ModeMo(.idle, size: ModeSize.islandExpandedMo)
                        .padding(.leading, ModeSpacing.islandHorizontal)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    ModeActivityStatus(ActivityL10n.connectedShort, glyph: .connected, color: ModeColor.onDarkSecondary)
                        .frame(height: ModeSize.islandExpandedMo)
                        .padding(.trailing, ModeSpacing.islandHorizontal)
                        .modeActivityTheme()
                }
                DynamicIslandExpandedRegion(.bottom) {
                    ModeSessionBody(island: true)
                        .padding(.horizontal, ModeSpacing.islandHorizontal)
                        .modeActivityTheme()
                }
            } compactLeading: {
                ModeMo(.idle, size: ModeSize.islandCompactMo)
            } compactTrailing: {
                ModeActivityGlyph.connected.image.font(ModeIconFont.small).foregroundStyle(ModeColor.onDarkSecondary)
            } minimal: {
                ModeMo(.idle, size: ModeSize.islandMinimalMo)
            }
            .widgetURL(ModeLinks.open)
            .keylineTint(ModeColor.brand)
        }
    }
}
