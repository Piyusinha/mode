#if DEBUG && targetEnvironment(simulator)
import ModeDesignSystem
import SwiftUI

/// Simulator only: the Live Activity's states drawn in the app, on a Lock Screen-like
/// background with the expanded Dynamic Island above, to compare with the canvas boards
/// (`MODE_OPEN=activity:working|waiting|finished`). The real one is checked on the phone.
struct ActivityPreview: View {
    let phase: ModeActivityAttributes.ContentState.Phase
    /// `MODE_OPEN=activity:yourturn`: the waiting state for a PIN only the user can enter.
    static var yourTurn = false

    /// A question with options, as the agent asks it, for the question sheet's screenshot.
    static let sampleQuestion = RunStatusViewModel.PendingQuestion(
        id: "preview", text: "YUKI (4.5★) has these sushi. Which would you like?", yesNo: false,
        options: ["Ebi Tempura · ₹345 · prawn, most reordered", "Classic California Roll · ₹325 · crab", "Avocado Maki · ₹295 · veg"],
        recommended: "Ebi Tempura · ₹345 · prawn, most reordered"
    )

    private var state: ModeActivityAttributes.ContentState {
        switch phase {
        case .working:
            .init(phase: .working, step: 4, startedAt: Date().addingTimeInterval(-24), now: "Sending “Leaving in 10” to Asha",
                  plan: [.init("Open WhatsApp", state: .done), .init("Send message", state: .now), .init("Read calendar", state: .next)])
        case .waiting where Self.yourTurn:
            .init(phase: .waiting, step: 6, question: "Enter your UPI PIN in PhonePe. Mode carries on when you’re done.", questionId: "preview", yourTurn: true)
        case .waiting:
            .init(phase: .waiting, step: 6, question: "Mode is about to tap \"Book now\" (Auto to Bellandur, ₹163). Allow it?", questionId: "preview", yesNo: true)
        case .finished:
            // As the presenter sends it: one line; the agenda's rows are counted for the chat.
            .init(phase: .finished, outcome: .done, seconds: 62, reply: [
                .paragraph("Sent **“Leaving in 10”** to Asha on WhatsApp. Here's your day: three things, the next at 14:00."),
            ], more: 3)
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
