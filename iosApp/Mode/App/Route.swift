/// Where a tab's navigation can go.
enum Route: Hashable {
    case conversation(id: String)
    case liveRun
    case runScreens(taskId: String)
    case settings
    case developer
}

/// The tabs, plus Ask (the floating button beside them).
enum AppTab: Hashable {
    case home, tasks, memory, ask
}
