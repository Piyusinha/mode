import Foundation
import Observation

/// Where a tab's navigation can go.
enum Route: Hashable {
    case conversation(id: String)
    case liveRun
    case runScreens(taskId: String)
    case settings
    case developer
    case memoryAboutYou
    /// Routines, all of them or one app's.
    case memoryRoutines(app: String?)
    case memoryApps
    case memoryApp(name: String)
    case memoryEdit(id: String)
}

/// A mode:// link from the Live Activity (or anywhere): open a conversation, Ask, or just Mode.
enum DeepLink: Equatable {
    case conversation(id: String)
    case ask
    case open

    init?(_ url: URL) {
        guard url.scheme == ModeLinks.scheme else { return nil }
        switch url.host() {
        case "conversation": self = url.pathComponents.dropFirst().first.map { .conversation(id: $0) } ?? .open
        case "ask": self = .ask
        case "open": self = .open
        default: return nil
        }
    }
}

/// A link for the app to follow from outside a view: Siri started a task in a conversation.
@MainActor
@Observable
final class AppLinks {
    static let shared = AppLinks()
    var pending: DeepLink?
}

/// The tabs, plus Ask (the floating button beside them).
enum AppTab: Hashable {
    case home, tasks, memory, ask
}
