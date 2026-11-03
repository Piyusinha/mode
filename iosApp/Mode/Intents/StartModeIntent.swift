import AppIntents
import ModeKit

/// "Hey Siri, start Mode": Siri asks what to do, takes down the words, and Mode
/// starts on it. Also in Shortcuts ("Ask Mode to …") and on the Action Button.
/// App Shortcuts need no Siri permission or entitlement, so free accounts get it too.
struct StartModeIntent: AppIntent {
    static let title: LocalizedStringResource = "Ask Mode"
    static let description = IntentDescription("Tell Mode what to do on your iPhone, in your own words.")
    /// Mode opens to start: its keep-awake can't start from the background yet (docs/status.md "Last").
    static let openAppWhenRun = true

    @Parameter(title: "Task", requestValueDialog: IntentDialog("What should Mode do?"))
    var task: String

    static var parameterSummary: some ParameterSummary {
        Summary("Ask Mode to \(\.$task)")
    }

    @MainActor
    func perform() async throws -> some IntentResult & ProvidesDialog {
        let graph = IosModeGraphKt.iosModeGraph
        guard !graph.screens.needsOnboarding else {
            graph.screens.intentRan(listen: false, outcome: "SetupNotDone")
            return .result(dialog: "Open Mode and finish setting it up first.")
        }
        guard let started = graph.inbox.submit(text: task, source: .siri, conversationId: nil, private: false) else {
            graph.screens.intentRan(listen: false, outcome: "Empty")
            return .result(dialog: "Tell me what Mode should do.")
        }
        graph.screens.intentRan(listen: false, outcome: "TaskSent")
        // Mode shows the conversation while it works and when it comes back.
        AppLinks.shared.pending = started.conversationId.map { .conversation(id: $0) }
        return .result(dialog: "On it.")
    }
}

/// The phrases Siri knows without any setup.
/// "Hey Siri, start Mode" opens the app and listens. It does not take the task.
struct StartListeningIntent: AppIntent {
    static let title: LocalizedStringResource = "Start Mode"
    static let description = IntentDescription("Open Mode and tell it what to do.")
    static let openAppWhenRun = true

    @MainActor
    func perform() async throws -> some IntentResult {
        let screens = IosModeGraphKt.iosModeGraph.screens
        guard !screens.needsOnboarding else {
            screens.intentRan(listen: true, outcome: "SetupNotDone")
            return .result(dialog: "Open Mode and finish setting it up first.")
        }
        screens.intentRan(listen: true, outcome: "Listening")
        AppLinks.shared.pending = .listen
        return .result(dialog: "Listening.")
    }
}

struct ModeShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: StartListeningIntent(),
            phrases: ["Start \(.applicationName)"],
            shortTitle: "Start Mode",
            systemImageName: "mic.fill"
        )
        AppShortcut(
            intent: StartModeIntent(),
            phrases: [
                "Ask \(.applicationName)",
                "Tell \(.applicationName)",
                "\(.applicationName), do something",
            ],
            shortTitle: "Ask Mode",
            systemImageName: "text.bubble"
        )
    }
}
