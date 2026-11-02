import Foundation
import ModeKit
import Observation

/// The running task everywhere in the app: the bar above the tabs and the agent's questions.
@MainActor
@Observable
final class RunStatusViewModel {
    struct Running: Equatable {
        let title: String
        let status: String
        let paused: Bool
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
    }

    struct State: Equatable {
        var running: Running?
        var question: PendingQuestion?
    }

    private(set) var state = State()
    private let presenter: LiveRunPresenter
    private var updates: Updates?

    init(screens: Screens) {
        presenter = screens.liveRun()
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func stop() { presenter.stop() }

    func togglePause() {
        if state.running?.paused == true { presenter.resume() } else { presenter.pause() }
    }

    func answer(_ text: String) { presenter.answer(text: text) }

    func stopFromQuestion() { presenter.answer(text: nil) }

    private static func map(_ s: LiveRunUiState) -> State {
        guard let running = s.running else { return State() }
        return State(
            running: Running(title: running.text, status: running.paused ? String(localized: L10n.Run.paused) : running.status, paused: running.paused),
            question: running.question.map {
                PendingQuestion(id: running.taskId + $0.text, text: $0.text, yesNo: $0.yesNo, options: $0.options, recommended: $0.recommended,
                                allowAlways: $0.allowAlways, yourTurn: $0.yourTurn)
            }
        )
    }
}
