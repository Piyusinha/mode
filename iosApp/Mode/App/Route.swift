import Foundation

/// Where a tab's navigation can go.
enum Route: Hashable {
    case conversation(id: String)
    case liveRun
    case runScreens(taskId: String)
    case settings
    case developer
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

/// The tabs, plus Ask (the floating button beside them).
enum AppTab: Hashable {
    case home, tasks, memory, ask
}
