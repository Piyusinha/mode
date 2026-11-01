import ModeKit
import Observation

/// The app's one shared model: the Kotlin graph, and whether setup still has to run.
@MainActor
@Observable
final class AppModel {
    let graph: ModeGraph = IosModeGraphKt.iosModeGraph
    var screens: Screens { graph.screens }
    private(set) var needsOnboarding: Bool

    init() {
        needsOnboarding = IosModeGraphKt.iosModeGraph.screens.needsOnboarding
    }

    func onboardingFinished() {
        needsOnboarding = false
    }
}
