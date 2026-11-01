import ModeDesignSystem
import SwiftUI

/// Home, Tasks and Memory in a Liquid Glass tab bar, Ask as the round button
/// beside it, and the running task above it.
struct MainTabs: View {
    let app: AppModel
    @State private var tab: AppTab = .home
    @State private var run: RunStatusViewModel

    init(app: AppModel) {
        self.app = app
        _run = State(initialValue: RunStatusViewModel(screens: app.screens))
    }

    var body: some View {
        TabView(selection: $tab) {
            Tab(value: AppTab.home) {
                TabRoot(app: app) { navigate in HomeRoute(app: app, navigate: navigate) }
            } label: { Label { Text(L10n.Tab.home) } icon: { ModeIcon.home.image } }
            Tab(value: AppTab.tasks) {
                TabRoot(app: app) { navigate in TasksRoute(app: app, navigate: navigate) }
            } label: { Label { Text(L10n.Tab.tasks) } icon: { ModeIcon.tasks.image } }
            Tab(value: AppTab.memory) {
                TabRoot(app: app) { _ in PlaceholderScreen() }
            } label: { Label { Text(L10n.Tab.memory) } icon: { ModeIcon.memory.image } }
            Tab(value: AppTab.ask, role: .search) {
                TabRoot(app: app) { navigate in AskRoute(app: app, navigate: navigate) }
            } label: { Label { Text(L10n.Tab.ask) } icon: { ModeIcon.ask.image } }
        }
        .tint(ModeColor.tint)
        .tabBarMinimizeBehavior(.onScrollDown)
        .tabViewBottomAccessory(isEnabled: run.state.running != nil) {
            if let running = run.state.running {
                ModeRunningBar(title: running.title, status: running.status, stopLabel: L10n.Common.stop) { run.stop() }
            }
        }
        .sheet(item: Binding(get: { run.state.question }, set: { _ in })) { question in
            QuestionSheet(question: question, onAnswer: run.answer, onStop: run.stopFromQuestion)
        }
    }
}

/// One tab's navigation stack, with every route it can open.
private struct TabRoot<Content: View>: View {
    let app: AppModel
    @ViewBuilder let content: (@escaping (Route) -> Void) -> Content
    @State private var path: [Route] = []

    var body: some View {
        NavigationStack(path: $path) {
            content(navigate)
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case let .conversation(id): ConversationRoute(app: app, conversationId: id, navigate: navigate)
                    case .developer: DeveloperView()
                    case .liveRun: LiveRoute(app: app)
                    case let .runScreens(taskId): RunScreensRoute(app: app, taskId: taskId)
                    case .settings: PlaceholderScreen()
                    }
                }
        }
    }

    private func navigate(_ route: Route) {
        path.append(route)
    }
}

/// Stands in for screens that arrive in the next steps.
struct PlaceholderScreen: View {
    var body: some View {
        ModeEmptyState(icon: .sparkle, title: L10n.Placeholder.title, message: L10n.Placeholder.message)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .modeScreen()
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    NavigationLink(value: Route.developer) { ModeIcon.developer.image }
                        .accessibilityLabel(Text(L10n.Developer.title))
                }
            }
    }
}
