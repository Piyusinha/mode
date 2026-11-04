import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// The running task everywhere in the app: the bar above the tabs and the agent's questions.
@MainActor
@Observable
final class RunStatusViewModel {
    struct Running: Equatable {
        let title: String
        /// "Step 9 · Checking the cart", or Paused.
        let status: String
        let paused: Bool
        /// The plan around the current item (done, now, next); empty without a plan.
        var plan: [ModeSegments.Segment] = []

        /// Bars on the running card: one per plan item, the done ones filled.
        var progress: (done: Int, total: Int)? {
            plan.isEmpty ? nil : (plan.filter { $0.state == .done }.count, plan.count)
        }
    }

    struct PendingQuestion: Identifiable, Equatable {
        let id: String
        let text: String
        let yesNo: Bool
        /** Choices to tap; empty for a free answer. */
        var options: [String] = []
        var recommended: String?
        /** Also offers "Yes, and don't ask again". */
        var allowAlways = false
        /** A PIN or password only the user can enter: "I've entered it". */
        var yourTurn = false
        /** Asked out loud in talking mode, which answers it. */
        var spoken = false
    }

    struct State: Equatable {
        var running: Running?
        /// Mode came forward to ask out loud: talking mode is on screen.
        var talking = false
        var question: PendingQuestion?
    }

    private(set) var state = State()
    private let presenter: LiveRunPresenter
    /// Only for the plan, which the running card shows as bars.
    private let activity: LiveActivityPresenter
    private var plan: [ModeSegments.Segment] = []
    private var updates: Updates?
    private var planUpdates: Updates?

    init(screens: Screens) {
        presenter = screens.liveRun()
        activity = screens.liveActivity()
        updates = observe(presenter.state) { [weak self] s in self?.apply(s) }
        planUpdates = observe(activity.state) { [weak self] s in self?.applyPlan(s) }
    }

    private func apply(_ s: LiveRunUiState) {
        var next = Self.map(s)
        next.running?.plan = plan
        state = next
    }

    private func applyPlan(_ s: ActivityUiState) {
        if case let .working(w) = onEnum(of: s) {
            plan = w.plan.map { ModeSegments.Segment($0.label, state: Self.segment($0.state)) }
        } else {
            plan = []
        }
        state.running?.plan = plan
    }

    private static func segment(_ state: PlanStepState) -> ModeSegments.State {
        switch state {
        case .done: .done
        case .now: .now
        case .next: .next
        }
    }

    func stop() { presenter.stop() }

    func togglePause() {
        if state.running?.paused == true { presenter.resume() } else { presenter.pause() }
    }

    func answer(_ text: String) { presenter.answer(text: text) }

    func stopFromQuestion() { presenter.answer(text: nil) }

    /// "Step 9 · Checking the cart"; just the status before the first step.
    static func status(_ r: RunningTask) -> String {
        if r.paused { return String(localized: L10n.Run.paused) }
        return r.steps > 0 ? L10n.Run.stepStatus(Int(r.steps), r.status) : r.status
    }

    private static func map(_ s: LiveRunUiState) -> State {
        guard let running = s.running else { return State() }
        return State(
            running: Running(title: running.text, status: status(running), paused: running.paused),
            talking: running.talking,
            question: running.question.map {
                PendingQuestion(id: running.taskId + $0.text, text: $0.text, yesNo: $0.yesNo, options: $0.options, recommended: $0.recommended,
                                allowAlways: $0.allowAlways, yourTurn: $0.yourTurn, spoken: $0.spoken)
            }
        )
    }
}
