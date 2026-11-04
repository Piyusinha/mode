import Foundation
import Observation

/// Where a tab's navigation can go.
enum Route: Hashable {
    case conversation(id: String)
    case liveRun
    case runScreens(taskId: String)
    case settings
    case connectChatGPT
    case memory
    case modelPicker(stuck: Bool)
    case planModels(brain: Bool)
    case access
    /// Settings → Voice: Mode's voice, Listening, and choosing a voice from a provider.
    case voice
    case listening
    /// Settings → Listening → Language.
    case language
    /// Settings → Health & repair.
    case health
    /// What went wrong in a task that couldn't finish, and Ask about it.
    case taskDebug(taskId: String)
    case taskDebugAsk(taskId: String)
    case voicePicker(provider: String)
    case providerDetail(id: String)
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
    case listen
    /// A mic in the app: talking mode, adding to [conversationId]'s chat (nil: a new chat).
    case talk(conversationId: String?)

    /// For the trace: which kind of link (never the conversation's id).
    var traceName: String {
        switch self {
        case .conversation: "conversation"
        case .ask: "ask"
        case .open: "open"
        case .listen: "siri"
        case let .talk(id): id == nil ? "ask_mic" : "chat_mic"
        }
    }

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
