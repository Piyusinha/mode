import ModeDesignSystem
import SwiftUI

/// The main screens as on the Home board: the content, and at the bottom one
/// Liquid Glass group with the running task, the tabs and the Speak button.
/// The bar shows on each tab's first screen; deeper screens have their own
/// bottom (the conversation's message bar).
struct MainTabs: View {
    let app: AppModel
    @State private var tab: AppTab = .home
    @State private var paths: [AppTab: [Route]] = [:]
    @State private var run: RunStatusViewModel
    @State private var showingLive = false
    @State private var showingAsk = false

    init(app: AppModel) {
        self.app = app
        _run = State(initialValue: RunStatusViewModel(screens: app.screens))
    }

    var body: some View {
        TabView(selection: $tab) {
            Tab(value: AppTab.home) { stack(.home) { navigate in HomeRoute(app: app, navigate: navigate) } }
            Tab(value: AppTab.tasks) { stack(.tasks) { navigate in TasksRoute(app: app, navigate: navigate, newTask: { showingAsk = true }) } }
            Tab(value: AppTab.memory) { stack(.memory) { _ in MemoryRoute(app: app) } }
        }
        .safeAreaInset(edge: .bottom, spacing: ModeSpacing.none) {
            if paths[tab, default: []].isEmpty {
                ModeTabBar(
                    items: [
                        ModeTabItem(AppTab.home, icon: .home, title: L10n.Tab.home),
                        ModeTabItem(AppTab.tasks, icon: .tasks, title: L10n.Tab.tasks),
                        ModeTabItem(AppTab.memory, icon: .memory, title: L10n.Tab.memory),
                    ],
                    selection: $tab,
                    speakLabel: L10n.Tab.ask,
                    onSpeak: { showingAsk = true }
                ) {
                    if let running = run.state.running {
                        ModeMiniPlayer(
                            title: running.title, status: running.status, paused: running.paused,
                            pauseLabel: L10n.Run.pause, resumeLabel: L10n.Run.resume, stopLabel: L10n.Common.stop,
                            onPauseToggle: run.togglePause, onStop: run.stop, onOpen: { showingLive = true }
                        )
                    }
                }
                .animation(ModeMotion.standard, value: run.state.running)
                .padding(.bottom, ModeSpacing.xs)
            }
        }
        .sheet(isPresented: $showingLive) { NavigationStack { LiveRoute(app: app) } }
        .sheet(isPresented: $showingAsk) {
            AskRoute(app: app) { conversation in
                showingAsk = false
                tab = .home
                paths[.home, default: []].append(.conversation(id: conversation))
            }
        }
        .sheet(item: Binding(get: { run.state.question }, set: { _ in })) { question in
            QuestionSheet(question: question, onAnswer: run.answer, onStop: run.stopFromQuestion)
        }
        #if DEBUG && targetEnvironment(simulator)
        .task { openForScreenshots() }
        #endif
    }

    #if DEBUG && targetEnvironment(simulator)
    /// Simulator only: `MODE_OPEN=tasks`, `memory`, `live`, `conversation:<id>` or `screens:<taskId>`
    /// opens that screen, so each board can be compared with the app without tapping.
    private func openForScreenshots() {
        guard let target = ProcessInfo.processInfo.environment["MODE_OPEN"] else { return }
        let parts = target.split(separator: ":", maxSplits: 1).map(String.init)
        switch parts.first {
        case "tasks": tab = .tasks
        case "memory": tab = .memory
        case "live": showingLive = true
        case "conversation": paths[.home] = [.conversation(id: parts.last ?? "")]
        case "screens": paths[.home] = [.runScreens(taskId: parts.last ?? "")]
        default: break
        }
    }
    #endif

    private func stack<Content: View>(_ tab: AppTab, @ViewBuilder content: @escaping (@escaping (Route) -> Void) -> Content) -> some View {
        TabRoot(app: app, path: Binding(get: { paths[tab, default: []] }, set: { paths[tab] = $0 }), content: content)
    }
}

/// One tab's navigation stack, with every route it can open.
private struct TabRoot<Content: View>: View {
    let app: AppModel
    @Binding var path: [Route]
    @ViewBuilder let content: (@escaping (Route) -> Void) -> Content

    var body: some View {
        NavigationStack(path: $path) {
            content(navigate)
                // Our own glass bar replaces iOS's; hidden inside each tab, where iOS looks for it.
                .toolbar(.hidden, for: .tabBar)
                .navigationDestination(for: Route.self) { route in
                    switch route {
                    case let .conversation(id): ConversationRoute(app: app, conversationId: id, navigate: navigate)
                    case .developer: DeveloperView()
                    case .liveRun: LiveRoute(app: app)
                    case let .runScreens(taskId): RunScreensRoute(app: app, taskId: taskId)
                    case .settings: SettingsRoute(app: app, navigate: navigate)
                    }
                }
        }
    }

    private func navigate(_ route: Route) {
        path.append(route)
    }
}
