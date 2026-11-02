import ActivityKit
import AppIntents
import Foundation
import ModeDesignSystem

/// The Live Activity for the task Mode is running: on the Dynamic Island, the
/// Lock Screen and the Watch's Smart Stack. Compiled into the app and the widget
/// extension; the extension only draws it.
struct ModeActivityAttributes: ActivityAttributes {
    struct ContentState: Codable, Hashable {
        enum Phase: String, Codable, Hashable, Identifiable {
            case working, waiting, finished
            var id: Self { self }
        }
        enum Outcome: String, Codable, Hashable { case done, gaveUp, stopped, failed, notStarted }

        var phase: Phase
        /// Working: steps so far, when it started (the clock ticks by itself), what it's doing now.
        var step: Int = 0
        var startedAt: Date?
        var now: String = ""
        var paused = false
        var plan: [ModeSegments.Segment] = []
        /// Waiting: what the agent asks, its id (answers carry it), and how it can be answered.
        var question: String?
        var questionId: String?
        var yesNo = false
        var options: [String] = []
        /// Finished: how it ended, how long it took, the start of the answer, how much more is in the chat.
        var outcome: Outcome?
        var seconds = 0
        var reply: [ModeReplyBlock] = []
        var more = 0
    }

    /// What was asked, and where the answer goes.
    var task: String
    var conversationId: String?
}

/// Links from the Live Activity into Mode.
enum ModeLinks {
    static let scheme = "mode"

    static func conversation(_ id: String?) -> URL {
        id.flatMap { URL(string: "\(scheme)://conversation/\($0)") } ?? open
    }

    static let ask = URL(string: "\(scheme)://ask")!
    static let open = URL(string: "\(scheme)://open")!

    /// Where each provider shows its own usage or credits.
    static let chatGPTUsage = URL(string: "https://chatgpt.com/settings/usage")!
    static let claudeUsage = URL(string: "https://claude.ai/settings/usage")!
    static let openRouterCredits = URL(string: "https://openrouter.ai/settings/credits")!
}

/// Stop, from the Dynamic Island or the Lock Screen. A Live Activity intent runs
/// in Mode's own process, where the app has registered what Stop does.
struct StopTaskIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Stop the task"
    static let isDiscoverable = false

    func perform() async throws -> some IntentResult {
        await ModeActivityActions.stop()
        return .result()
    }
}

/// An answer to the agent's question, tapped on the Live Activity (Allow, Don't allow, an option).
struct AnswerIntent: LiveActivityIntent {
    static let title: LocalizedStringResource = "Answer Mode"
    static let isDiscoverable = false

    @Parameter(title: "Question") var questionId: String
    @Parameter(title: "Answer") var answer: String

    init() {}

    init(questionId: String, answer: String) {
        self.questionId = questionId
        self.answer = answer
    }

    func perform() async throws -> some IntentResult {
        await ModeActivityActions.answer(questionId, answer)
        return .result()
    }
}

/// What the Live Activity's buttons do, set by the app at launch (nothing in the extension).
@MainActor
enum ModeActivityActions {
    static var onStop: (() -> Void)?
    static var onAnswer: ((String, String) -> Void)?

    static func stop() {
        onStop?()
    }

    static func answer(_ questionId: String, _ answer: String) {
        onAnswer?(questionId, answer)
    }
}

extension ModeActivityAttributes.ContentState.Outcome {
    /// The tag beside the answer: "✓ Done in 62 s", "Couldn't finish"…
    func badge(seconds: Int) -> (text: LocalizedStringResource, icon: ModeIcon, tone: ModeBadge.Tone) {
        switch self {
        case .done: (ActivityL10n.doneIn(seconds), .checkmark, .success)
        case .gaveUp: (ActivityL10n.gaveUp, .warning, .warning)
        case .stopped: (ActivityL10n.stopped, .stopped, .neutral)
        case .failed: (ActivityL10n.failed, .failed, .danger)
        case .notStarted: (ActivityL10n.notStarted, .info, .neutral)
        }
    }
}
