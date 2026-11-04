import ActivityKit
import Foundation
import ModeDesignSystem
import ModeKit
import UIKit

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
    /// The last ActivityKit call, so the next waits for it.
    private var queue: Updates?

    /// How long the answer stays on the Lock Screen.
    private let answerStays: TimeInterval = 30 * 60
    /// If Mode stops updating for this long (it was closed mid-task), iOS shows the activity as stale.
    private let staleAfter: TimeInterval = 3 * 60

    init(screens: Screens) {
        presenter = screens.liveActivity()
        ModeActivityActions.onStop = { [presenter] in presenter.stop() }
        ModeActivityActions.onAnswer = { [presenter] id, answer in presenter.answer(questionId: id, text: answer) }
        // A task from an earlier launch can't still be running.
        for old in Activity<ModeActivityAttributes>.activities {
            Updates { await old.end(nil, dismissalPolicy: .immediate) }
        }
        updates = observe(presenter.state) { [weak self] state in self?.apply(state) }
        // Closed from the app switcher mid-task: take the activity away with it.
        NotificationCenter.default.addObserver(forName: UIApplication.willTerminateNotification, object: nil, queue: .main) { _ in
            Self.endAllNow()
        }
    }

    /// Ends every Mode activity before the app goes, waiting up to a second (termination won't wait longer).
    nonisolated private static func endAllNow() {
        let done = DispatchSemaphore(value: 0)
        Updates.detached {
            for activity in Activity<ModeActivityAttributes>.activities { await activity.end(nil, dismissalPolicy: .immediate) }
            done.signal()
        }
        _ = done.wait(timeout: .now() + 1)
    }

    private func apply(_ state: ActivityUiState) {
        switch onEnum(of: state) {
        case .hidden:
            end()
        case let .ended(e):
            if e.taskId == taskId { end() }
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
            show(taskId: w.taskId, task: w.task, conversationId: w.conversationId, content: Content(
                phase: .waiting, question: w.question, questionId: w.questionId, yesNo: w.yesNo, options: w.options, yourTurn: w.yourTurn,
                recommended: w.recommended
            ))
        case let .finished(f):
            finish(taskId: f.taskId, content: Content(
                phase: .finished,
                outcome: Self.outcome(f.badge),
                seconds: Int(f.seconds),
                reply: f.parts.map(ModeReplyBlock.init),
                more: Int(f.more),
                retryTaskId: f.badge == .gaveUp || f.badge == .failed ? f.taskId : nil
            ))
        }
    }

    private func show(taskId id: String, task: String, conversationId: String?, content: Content) {
        if let activity, taskId == id, activity.activityState == .active {
            guard content != shown else { return }
            shown = content
            let stale = Date().addingTimeInterval(staleAfter)
            let line = "update \(content.phase.rawValue) step \(content.step)"
            send { [presenter] in
                await activity.update(ActivityContent(state: content, staleDate: stale))
                presenter.log(message: line + " (\(activity.activityState))")
            }
            return
        }
        if let previous = activity {
            presenter.log(message: "replacing the activity (\(previous.activityState))")
            send { await previous.end(nil, dismissalPolicy: .immediate) }
        }
        do {
            activity = try Activity.request(
                attributes: ModeActivityAttributes(task: task, conversationId: conversationId),
                content: ActivityContent(state: content, staleDate: Date().addingTimeInterval(staleAfter))
            )
            taskId = id
            shown = content
            presenter.showing(on: true)
            presenter.log(message: "started \(content.phase.rawValue) step \(content.step)")
        } catch {
            // Live Activities are off for Mode: the notification tells the answer instead.
            activity = nil
            presenter.showing(on: false)
            presenter.log(message: "could not start: \(error.localizedDescription)")
        }
    }

    /// Takes the activity away now: the task was stopped, or nothing runs.
    private func end() {
        guard let activity else { return }
        self.activity = nil
        taskId = nil
        shown = nil
        send { [presenter] in
            await activity.end(nil, dismissalPolicy: .immediate)
            presenter.showing(on: false)
            presenter.log(message: "ended")
        }
    }

    private func finish(taskId id: String, content: Content) {
        guard let activity, taskId == id else { return }
        self.activity = nil
        taskId = nil
        shown = nil
        let until = Date().addingTimeInterval(answerStays)
        send { [presenter] in
            await activity.end(ActivityContent(state: content, staleDate: nil), dismissalPolicy: .after(until))
            presenter.showing(on: false)
            presenter.log(message: "finished (\(content.outcome?.rawValue ?? ""))")
        }
    }

    /// ActivityKit calls, one at a time and in order: an older update never lands after a newer one.
    private func send(_ work: @escaping @MainActor () async -> Void) {
        let previous = queue
        queue = Updates { @MainActor in
            await previous?.value
            await work()
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
