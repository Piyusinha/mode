import ActivityKit
import Foundation
import ModeDesignSystem
import ModeKit
import UIKit

/// Keeps the Live Activity in step with the running task: starts it when a task
/// starts, updates it when something changes, and ends it with the answer.
/// Also shows "Mode is connected", with Disconnect, while the Device Hub session is open and no task runs.
/// Plumbing between Kotlin's LiveActivityPresenter / SessionActivityPresenter and ActivityKit; it draws nothing.
@MainActor
final class LiveActivityController {
    private typealias Content = ModeActivityAttributes.ContentState

    private let presenter: LiveActivityPresenter
    private var updates: Updates?
    private var activity: Activity<ModeActivityAttributes>?
    private var taskId: String?
    private var shown: Content?
    private let sessionPresenter: SessionActivityPresenter
    private var sessionUpdates: Updates?
    private var sessionActivity: Activity<ModeSessionAttributes>?
    /// The last ActivityKit call, so the next waits for it.
    private var queue: Updates?

    /// How long the answer stays on the Lock Screen.
    private let answerStays: TimeInterval = 30 * 60
    /// If Mode stops updating for this long (it was closed mid-task), iOS shows the activity as stale.
    /// No update for this long and iOS may show the activity as stale (Mode looks stuck): long tasks go quiet for minutes.
    private let staleAfter: TimeInterval = 10 * 60

    init(screens: Screens) {
        presenter = screens.liveActivity()
        ModeActivityActions.onStop = { [presenter] in presenter.stop() }
        ModeActivityActions.onAnswer = { [presenter] id, answer in presenter.answer(questionId: id, text: answer) }
        ModeActivityActions.onCancelWaiting = { [presenter] id in presenter.cancelWaiting(taskId: id) }
        sessionPresenter = screens.sessionActivity()
        ModeActivityActions.onDisconnect = { [sessionPresenter] in sessionPresenter.disconnect() }
        // A task or a session from an earlier launch can't still be open.
        for old in Activity<ModeActivityAttributes>.activities {
            Updates { await old.end(nil, dismissalPolicy: .immediate) }
        }
        for old in Activity<ModeSessionAttributes>.activities {
            Updates { await old.end(nil, dismissalPolicy: .immediate) }
        }
        updates = observe(presenter.state) { [weak self] state in self?.apply(state) }
        sessionUpdates = observe(sessionPresenter.state) { [weak self] state in self?.applySession(state) }
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
            for activity in Activity<ModeSessionAttributes>.activities { await activity.end(nil, dismissalPolicy: .immediate) }
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
        case let .waitingForUnlock(w):
            let content = Content(phase: .locked, lockedTaskId: w.taskId, lockedTask: w.task, voiceUnlock: w.voiceUnlock)
            // From the background iOS won't start a new activity, but one already showing (the task before)
            // can change: it becomes "Unlock to start" for this one.
            if let activity, Self.alive(activity), taskId != w.taskId {
                taskId = w.taskId
            }
            show(taskId: w.taskId, task: w.task, conversationId: w.conversationId, content: content)
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
        var content = content
        content.chat = conversationId ?? (taskId == id ? shown?.chat : nil)
        // Stale only means no update came for a while (iOS can mark it so mid-task even as updates arrive): an
        // update brings it back. Seen on the phone (6 Nov): treating stale as dead ended the island 3 min into a
        // task, and from the background iOS refused a new one, so it was simply gone.
        if let activity, taskId == id, Self.alive(activity) {
            guard content != shown || activity.activityState == .stale else { return }
            update(activity, with: content)
            return
        }
        let previous = activity.flatMap { Self.alive($0) ? $0 : nil }
        do {
            // The new one first; the old one ends only once it exists, so the island is never left empty.
            activity = try Activity.request(
                attributes: ModeActivityAttributes(task: task, conversationId: conversationId),
                content: ActivityContent(state: content, staleDate: Date().addingTimeInterval(staleAfter))
            )
            taskId = id
            shown = content
            presenter.showing(on: true)
            presenter.log(message: "started \(content.phase.rawValue) step \(content.step)")
            if let previous {
                presenter.log(message: "replaced the activity (\(previous.activityState))")
                send { await previous.end(nil, dismissalPolicy: .immediate) }
            }
        } catch {
            if let previous {
                // From the background iOS won't start a new one: the one showing carries on for this task
                // (its chat moves with it, in the state).
                activity = previous
                taskId = id
                update(previous, with: content)
                presenter.log(message: "kept the activity for this task (could not start a new one: \(error.localizedDescription))")
            } else {
                // Live Activities are off for Mode: the notification tells the answer instead.
                activity = nil
                presenter.showing(on: false)
                presenter.log(message: "could not start: \(error.localizedDescription)")
            }
        }
    }

    /// Showing: active, or stale (an update revives it). Ended or dismissed ones are gone.
    private static func alive(_ activity: Activity<ModeActivityAttributes>) -> Bool {
        activity.activityState == .active || activity.activityState == .stale
    }

    private func update(_ activity: Activity<ModeActivityAttributes>, with content: Content) {
        shown = content
        let stale = Date().addingTimeInterval(staleAfter)
        let line = "update \(content.phase.rawValue) step \(content.step)"
        send { [presenter] in
            await activity.update(ActivityContent(state: content, staleDate: stale))
            presenter.log(message: line + " (\(activity.activityState))")
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
        var content = content
        content.chat = content.chat ?? shown?.chat
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

    /// "Mode is connected": shown while the session sits open with no task, gone the moment it closes
    /// or a task starts (the task's own activity has Stop). No stale date: it stays as long as the session.
    private func applySession(_ state: SessionActivityUiState) {
        switch onEnum(of: state) {
        case .hidden:
            guard let current = sessionActivity else { return }
            sessionActivity = nil
            send { [sessionPresenter] in
                await current.end(nil, dismissalPolicy: .immediate)
                sessionPresenter.log(message: "session activity ended")
            }
        case let .connected(c):
            if let current = sessionActivity, current.activityState == .active { return }
            let since = Date(timeIntervalSince1970: TimeInterval(c.sinceMs) / 1000)
            do {
                sessionActivity = try Activity.request(
                    attributes: ModeSessionAttributes(),
                    content: ActivityContent(state: ModeSessionAttributes.ContentState(since: since), staleDate: nil)
                )
                sessionPresenter.log(message: "session activity started")
            } catch {
                sessionActivity = nil
                sessionPresenter.log(message: "session activity could not start: \(error.localizedDescription)")
            }
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
