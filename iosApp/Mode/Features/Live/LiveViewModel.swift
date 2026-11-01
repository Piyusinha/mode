import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// The running task, step by step.
@MainActor
@Observable
final class LiveViewModel {
    struct Step: Identifiable, Equatable {
        let id: Int
        let title: String
        let detail: String
        let state: ModeStepRow.State
    }

    struct Finished: Equatable {
        let badge: Format.BadgeStyle
        let summary: String
        let line: String

        static func == (a: Finished, b: Finished) -> Bool { a.summary == b.summary && a.line == b.line }
    }

    struct State: Equatable {
        var title = ""
        var status = ""
        var isRunning = false
        var steps: [Step] = []
        var finished: Finished?
    }

    enum Event { case stop }

    private(set) var state = State()
    private let presenter: LiveRunPresenter
    private var updates: Updates?

    init(screens: Screens) {
        presenter = screens.liveRun()
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .stop: presenter.stop()
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: LiveRunUiState) -> State {
        let running = s.running != nil
        var steps = s.steps.map { step in
            Step(id: Int(step.index), title: step.reason.isEmpty ? step.action : step.reason, detail: step.action, state: step.worked ? .done : .failed)
        }
        if running, let last = steps.last {
            steps[steps.count - 1] = Step(id: last.id, title: last.title, detail: last.detail, state: .current)
        }
        return State(
            title: s.running?.text ?? "",
            status: s.running?.status ?? "",
            isRunning: running,
            steps: steps,
            finished: s.finished.map {
                Finished(badge: Format.badge($0.badge), summary: $0.summary,
                         line: L10n.Live.summary(steps: Int($0.steps), seconds: Format.seconds(Double($0.seconds)), cost: Format.money($0.costUsd)))
            }
        )
    }
}
