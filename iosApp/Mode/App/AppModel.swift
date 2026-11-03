import ModeKit
import Observation
import UserNotifications

/// The app's one shared model: the Kotlin graph, and whether setup still has to run.
@MainActor
@Observable
final class AppModel {
    let graph: ModeGraph = IosModeGraphKt.iosModeGraph
    var screens: Screens { graph.screens }
    private(set) var needsOnboarding: Bool
    /// The running task on the Dynamic Island and the Lock Screen, for the app's lifetime.
    let liveActivity: LiveActivityController

    init() {
        needsOnboarding = IosModeGraphKt.iosModeGraph.screens.needsOnboarding
        #if DEBUG && targetEnvironment(simulator)
        // Simulator only: MODE_SETUP_STEP shows setup even when it was finished, for comparing with its boards.
        if ProcessInfo.processInfo.environment["MODE_SETUP_STEP"] != nil { needsOnboarding = true }
        #endif
        liveActivity = LiveActivityController(screens: IosModeGraphKt.iosModeGraph.screens)
        // Questions can be answered from their notifications.
        UNUserNotificationCenter.current().delegate = QuestionAnswers.shared
    }

    func onboardingFinished() {
        needsOnboarding = false
    }

    /// Settings → Reset Mode: everything Mode keeps on this iPhone is deleted, and setup starts again.
    func resetEverything() async {
        do { try await graph.resetEverything() } catch { /* What could be deleted was; setup starts either way. */ }
        needsOnboarding = true
    }
}
