import ActivityKit
import Foundation
import ModeDesignSystem
import ModeKit

/// Keeps the Live Activity in step with the running task: starts it when a task
/// starts, updates it when something changes, and ends it with the answer.
/// Plumbing between Kotlin's LiveActivityPresenter and ActivityKit; it draws nothing.
@MainActor
final class LiveActivityController {
    private typealias Content = ModeActivityAttributes.ContentState

    private let presenter: LiveActivityPresenter
    private var updates: Updates?
    private var activity: Activity<ModeActivityAttributes>?
    private var taskId: String?
    private var shown: Content?

    /// How long the answer stays on the Lock Screen.
    private let answerStays: TimeInterval = 30 * 60

    init(screens: Screens) {
        presenter = screens.liveActivity()
        ModeActivityActions.onStop = { [presenter] in presenter.stop() }
        // A task from an earlier launch can't still be running.
        for old in Activity<ModeActivityAttributes>.activities {
            Updates { await old.end(nil, dismissalPolicy: .immediate) }
        }
        updates = observe(presenter.state) { [weak self] state in self?.apply(state) }
    }

    private func apply(_ state: ActivityUiState) {
        switch onEnum(of: state) {
        case .hidden:
            break
        case let .working(w):
            show(taskId: w.taskId, task: w.task, conversationId: w.conversationId, content: Content(
                phase: .working,
                step: Int(w.step),
                startedAt: w.startedAtMs.map { Date(timeIntervalSince1970: TimeInterval(truncating: $0) / 1000) },
                now: w.now,
                paused: w.paused,
                plan: w.plan.map { ModeSegments.Segment($0.label, state: Self.segment($0.state)) }
            ))
        case let .waiting(w):
            show(taskId: w.taskId, task: w.task, conversationId: w.conversationId, content: Content(phase: .waiting, question: w.question))
        case let .finished(f):
            finish(taskId: f.taskId, content: Content(
                phase: .finished,
                outcome: Self.outcome(f.badge),
                seconds: Int(f.seconds),
                reply: f.parts.map(ModeReplyBlock.init),
                more: Int(f.more)
            ))
        }
    }

    private func show(taskId id: String, task: String, conversationId: String?, content: Content) {
        if let activity, taskId == id {
            guard content != shown else { return }
            shown = content
            Updates { await activity.update(ActivityContent(state: content, staleDate: nil)) }
            return
        }
        if let previous = activity { Updates { await previous.end(nil, dismissalPolicy: .immediate) } }
        do {
            activity = try Activity.request(
                attributes: ModeActivityAttributes(task: task, conversationId: conversationId),
                content: ActivityContent(state: content, staleDate: nil)
            )
            taskId = id
            shown = content
            presenter.showing(on: true)
        } catch {
            // Live Activities are off for Mode: the notification tells the answer instead.
            activity = nil
            presenter.showing(on: false)
        }
    }

    private func finish(taskId id: String, content: Content) {
        guard let activity, taskId == id else { return }
        self.activity = nil
        taskId = nil
        shown = nil
        let until = Date().addingTimeInterval(answerStays)
        Updates { [presenter] in
            await activity.end(ActivityContent(state: content, staleDate: nil), dismissalPolicy: .after(until))
            presenter.showing(on: false)
        }
    }

    private static func segment(_ state: PlanStepState) -> ModeSegments.State {
        switch state {
        case .done: .done
        case .now: .now
        case .next: .next
        }
    }

    private static func outcome(_ badge: TaskBadge) -> Content.Outcome {
        switch badge {
        case .done: .done
        case .gaveUp: .gaveUp
        case .stopped: .stopped
        case .failed: .failed
        case .notStarted: .notStarted
        }
    }
}
