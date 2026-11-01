import ModeKit
import Observation

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
        liveActivity = LiveActivityController(screens: IosModeGraphKt.iosModeGraph.screens)
    }

    func onboardingFinished() {
        needsOnboarding = false
    }
}
