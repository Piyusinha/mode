#if DEBUG && targetEnvironment(simulator)
import ModeDesignSystem
import SwiftUI

/// Simulator only: the Live Activity's states drawn in the app, on a Lock Screen-like
/// background with the expanded Dynamic Island above, to compare with the canvas boards
/// (`MODE_OPEN=activity:working|waiting|finished`). The real one is checked on the phone.
struct ActivityPreview: View {
    let phase: ModeActivityAttributes.ContentState.Phase

    private var state: ModeActivityAttributes.ContentState {
        switch phase {
        case .working:
            .init(phase: .working, step: 4, startedAt: Date().addingTimeInterval(-24), now: "Sending “Leaving in 10” to Sonali",
                  plan: [.init("Open WhatsApp", state: .done), .init("Send message", state: .now), .init("Read calendar", state: .next)])
        case .waiting:
            .init(phase: .waiting, step: 6, question: "Two contacts are called Sonali. Which one: Sonali Yadav or Sonali (work)?")
        case .finished:
            .init(phase: .finished, outcome: .done, seconds: 62, reply: [
                .paragraph("Sent **“Leaving in 10”** to Sonali on WhatsApp. Here's your day:"),
                .agenda([ModeAgendaRow(time: "14:00", end: nil, text: "Biozyme with lunch", allDay: false),
                         ModeAgendaRow(time: "21:00", end: nil, text: "Shelcal, fish oil, magnesium", allDay: false)]),
            ], more: 1)
        }
    }

    var body: some View {
        VStack(spacing: ModeSpacing.xl) {
            // The compact island: mark, then the clock or how it ended.
            HStack {
                ModeMark(size: ModeSize.islandMark, alwaysLight: true)
                Spacer()
                ModeActivityStepClock(state: state).modeActivityTheme()
            }
            .padding(.horizontal, ModeSpacing.m)
            .frame(width: ModeSize.previewIsland, height: ModeSize.previewIslandHeight)
            .background(.black, in: .capsule)
            // The expanded island and the Lock Screen card, each in iOS's 160 pt limit (red line).
            ModeActivityLockScreen(conversationId: nil, state: state, stale: false)
                .frame(height: ModeSize.activityLimit, alignment: .top)
                .background(.black, in: .rect(cornerRadius: ModeRadius.sheet + ModeSpacing.l))
                .overlay(alignment: .bottom) { Rectangle().fill(.red).frame(height: ModeSize.separator) }
                .padding(.horizontal, ModeSpacing.s)
            ModeActivityLockScreen(conversationId: nil, state: state, stale: false)
                .frame(height: ModeSize.activityLimit, alignment: .top)
                .background(ModeColor.lockScreen, in: .rect(cornerRadius: ModeRadius.card))
                .overlay(alignment: .bottom) { Rectangle().fill(.red).frame(height: ModeSize.separator) }
                .padding(.horizontal, ModeSpacing.l)
            ModeActivityLockScreen(conversationId: nil, state: state, stale: true)
                .background(ModeColor.lockScreen, in: .rect(cornerRadius: ModeRadius.card))
                .padding(.horizontal, ModeSpacing.l)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .padding(.top, ModeSpacing.s)
        .background(LinearGradient(colors: [.indigo.opacity(0.9), .black, .teal.opacity(0.5)], startPoint: .top, endPoint: .bottom))
        .ignoresSafeArea(edges: .bottom)
    }
}
#endif
