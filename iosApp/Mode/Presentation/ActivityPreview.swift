#if DEBUG && targetEnvironment(simulator)
import ModeDesignSystem
import SwiftUI

/// Simulator only: the Live Activity's states drawn in the app to compare with the canvas boards
/// (`MODE_OPEN=activity:<variant>`): the compact island at the top, the expanded island under it, and the
/// Lock Screen card where the boards put it, each inside iOS's 160 pt limit. The real one is checked on the phone.
/// Variants: working, paused, waiting (yes or no), options, reply, yourturn, finished (done), long, gaveup, stopped.
struct ActivityPreview: View {
    let phase: ModeActivityAttributes.ContentState.Phase
    /// Which of the phase's boards to draw.
    static var variant = ""

    static func phase(for variant: String) -> ModeActivityAttributes.ContentState.Phase? {
        self.variant = variant
        switch variant {
        case "working", "paused": return .working
        case "waiting", "options", "two", "reply", "yourturn": return .waiting
        case "finished", "done", "long", "gaveup", "stopped": return .finished
        default: return nil
        }
    }

    /// A question with options, as the agent asks it, for the question sheet's screenshot.
    static let sampleQuestion = RunStatusViewModel.PendingQuestion(
        id: "preview", text: "YUKI (4.5★) has these sushi. Which would you like?", yesNo: false,
        options: ["Ebi Tempura · ₹345 · prawn, most reordered", "Classic California Roll · ₹325 · crab", "Avocado Maki · ₹295 · veg"],
        recommended: "Ebi Tempura · ₹345 · prawn, most reordered"
    )

    private var state: ModeActivityAttributes.ContentState {
        switch Self.variant {
        case "working", "paused":
            .init(phase: .working, step: 4, startedAt: Date().addingTimeInterval(-24), now: "Sending “Leaving in 10” to Sam",
                  paused: Self.variant == "paused",
                  plan: [.init("Open WhatsApp", state: .done), .init("Send message", state: .now), .init("Check it sent", state: .next)])
        case "options":
            .init(phase: .waiting, question: "Which size of Coke Zero?", questionId: "preview", options: ["Small", "Medium", "Large"], recommended: "Medium")
        case "two":
            .init(phase: .waiting, question: "Pay for the Rapido ride with?", questionId: "preview", options: ["Cash", "UPI"], recommended: "UPI")
        case "reply":
            .init(phase: .waiting, question: "Which address should the cab go to?", questionId: "preview")
        case "yourturn":
            .init(phase: .waiting, question: "Enter your UPI PIN in PhonePe. Mode carries on when you’re done.", questionId: "preview", yourTurn: true)
        case "waiting":
            .init(phase: .waiting, question: "Place the Swiggy order for ₹348? It arrives in about 30 min.", questionId: "preview", yesNo: true)
        case "long":
            .init(phase: .finished, outcome: .done, seconds: 48, reply: [.paragraph("3 reminders for today. Next: Biozyme at 14:00")], more: 2)
        case "gaveup":
            .init(phase: .finished, outcome: .gaveUp, reply: [.paragraph("Swiggy asked you to sign in first. Sign in, then try again.")], retryTaskId: "preview")
        case "stopped":
            .init(phase: .finished, outcome: .stopped, reply: [.paragraph("You stopped “Book a Rapido to Bellandur”.")])
        default:
            // As the presenter sends it: one line; the rest is counted for the chat.
            .init(phase: .finished, outcome: .done, seconds: 62, reply: [.paragraph("Sent “Leaving in 10” to Sam on WhatsApp")])
        }
    }

    private var asks: Bool { state.asks(stale: false) }

    var body: some View {
        VStack(spacing: ModeSpacing.xl) {
            // The compact island: Mo, then the clock and ring, or how it ended.
            HStack {
                ModeMo(state.mood(), size: ModeSize.islandCompactMo)
                Spacer()
                ModeIslandTrailing(state: state)
            }
            .padding(.leading, ModeSpacing.xs + ModeSpacing.xxs)
            .padding(.trailing, ModeSpacing.m)
            .frame(width: ModeSize.previewIsland, height: ModeSize.previewIslandHeight)
            .background(.black, in: .capsule)
            // The expanded island.
            VStack(alignment: .leading, spacing: ModeSpacing.activityBarGap) {
                HStack {
                    ModeMo(state.mood(), size: ModeSize.islandExpandedMo)
                    Spacer()
                    ModeActivityTrailing(state: state, stale: false, island: true)
                }
                .frame(height: ModeSize.activityHeader)
                ModeActivityBody(state: state, stale: false, conversationId: nil, island: true)
            }
            .padding(.horizontal, ModeSpacing.memoryBarButton)
            .padding(.top, ModeSpacing.m)
            .padding(.bottom, ModeSpacing.l)
            // What iOS shows: at most 160 pt, the rest cut.
            .frame(maxHeight: ModeSize.activityLimit, alignment: .top)
            .clipped()
            .fixedSize(horizontal: false, vertical: true)
            .background(.black, in: .rect(cornerRadius: ModeRadius.sheet + ModeSpacing.memoryCardGap))
            .padding(.horizontal, ModeSpacing.s + ModeSpacing.xxs)
            Spacer()
            // The Lock Screen card, where the boards put it.
            ModeActivityLockScreen(conversationId: nil, state: state, stale: false)
                // What iOS shows: at most 160 pt, the rest cut.
                .frame(maxHeight: ModeSize.activityLimit, alignment: .top)
                .clipped()
                .fixedSize(horizontal: false, vertical: true)
                .background(asks ? ModeColor.lockScreenAsk : ModeColor.lockScreen, in: .rect(cornerRadius: ModeRadius.card))
                .overlay {
                    if asks { RoundedRectangle(cornerRadius: ModeRadius.card).strokeBorder(ModeColor.lockScreenAskRing, lineWidth: ModeSize.activityAskRing) }
                }
                .padding(.horizontal, ModeSpacing.m)
                .padding(.bottom, ModeSize.activityLimit - ModeSpacing.xl - ModeSpacing.xxl)
        }
        .modeActivityTheme()
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .padding(.top, ModeSpacing.s + ModeSpacing.xxs)
        .background(ModeColor.page)
        .environment(\.colorScheme, .dark)
        .ignoresSafeArea(edges: .bottom)
    }
}
#endif
