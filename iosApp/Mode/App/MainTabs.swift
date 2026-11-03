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
    @State private var showingListen = false
    /// The chat a mic was tapped in (talking mode adds to it); nil for a new chat.
    @State private var talkConversation: String?
    /// A mic in the Ask sheet: talking mode opens once the sheet has closed.
    @State private var talkAfterAsk = false
    #if DEBUG && targetEnvironment(simulator)
    @State private var previewActivity: ModeActivityAttributes.ContentState.Phase?
    @State private var previewQuestion: RunStatusViewModel.PendingQuestion?
    #if DEBUG && targetEnvironment(simulator)
    @State private var sample: ScreenSample?
    #endif
    #endif

    init(app: AppModel) {
        self.app = app
        _run = State(initialValue: RunStatusViewModel(screens: app.screens))
    }

    var body: some View {
        TabView(selection: $tab) {
            Tab(value: AppTab.home) { stack(.home) { navigate in HomeRoute(app: app, navigate: navigate) } }
            Tab(value: AppTab.tasks) { stack(.tasks) { navigate in TasksRoute(app: app, navigate: navigate, newTask: { showingAsk = true }) } }
            Tab(value: AppTab.memory) { stack(.memory) { navigate in MemoryRoute(app: app, navigate: navigate) } }
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
        // Talking mode: "Start Mode", and a question in a spoken task (Mode came forward to ask it out loud).
        .fullScreenCover(isPresented: Binding(get: { showingListen || run.state.talking },
                                              set: { if !$0 { showingListen = false } })) {
            TalkRoute(app: app, forQuestion: !showingListen, conversationId: talkConversation,
                      onClose: { showingListen = false; talkConversation = nil }, onVoiceSettings: openVoiceSettings)
        }
        .sheet(isPresented: $showingAsk, onDismiss: {
            if talkAfterAsk { talkAfterAsk = false; showingListen = true }
        }) {
            AskRoute(app: app) { conversation in
                showingAsk = false
                tab = .home
                paths[.home, default: []].append(.conversation(id: conversation))
            }
        }
        .sheet(item: Binding(get: { run.state.question.flatMap { $0.spoken ? nil : $0 } }, set: { _ in })) { question in
            QuestionSheet(question: question, onAnswer: run.answer, onStop: run.stopFromQuestion)
        }
        .onOpenURL { url in DeepLink(url).map(open) }
        .onChange(of: AppLinks.shared.pending, initial: true) { _, link in
            guard let link else { return }
            AppLinks.shared.pending = nil
            open(link)
        }
        #if DEBUG && targetEnvironment(simulator)
        .task { openForScreenshots() }
        .fullScreenCover(item: $previewActivity) { ActivityPreview(phase: $0) }
        .sheet(item: $previewQuestion) { QuestionSheet(question: $0, onAnswer: { _ in }, onStop: {}) }
        .fullScreenCover(item: $sample) { ScreenSampleHost(sample: $0) }
        #endif
    }

    /// Follows a link from the Live Activity.
    /// Talking mode's "Update in Settings": Settings → Mode's voice or Listening, from Home.
    private func openVoiceSettings(hearing: Bool) {
        tab = .home
        paths[.home, default: []].append(contentsOf: [Route.settings, hearing ? Route.listening : Route.voice])
    }

    private func open(_ link: DeepLink) {
        switch link {
        case let .conversation(id):
            showingLive = false
            tab = .home
            paths[.home] = [.conversation(id: id)]
        case .ask: showingAsk = true
        case .open: break
        case .listen: talkConversation = nil; showingListen = true
        case let .talk(id):
            talkConversation = id
            // One full-screen view at a time: from the Ask sheet, talking mode waits for it to close.
            if showingAsk { talkAfterAsk = true; showingAsk = false } else { showingListen = true }
        }
    }

    #if DEBUG && targetEnvironment(simulator)
    /// Simulator only: `MODE_OPEN=tasks`, `memory`, `live`, `conversation:<id>` or `screens:<taskId>`
    /// opens that screen, so each board can be compared with the app without tapping.
    private func openForScreenshots() {
        guard let target = ProcessInfo.processInfo.environment["MODE_OPEN"] else { return }
        let parts = target.split(separator: ":", maxSplits: 1).map(String.init)
        switch parts.first {
        case "tasks": tab = .tasks
        case "memory":
            tab = .memory
            switch parts.count > 1 ? parts.last : nil {
            case "aboutyou": paths[.memory] = [.memoryAboutYou]
            case "routines": paths[.memory] = [.memoryRoutines(app: nil)]
            case "apps": paths[.memory] = [.memoryApps]
            case let name?: paths[.memory] = name.hasPrefix("edit=") ? [.memoryEdit(id: String(name.dropFirst(5)))] : [.memoryApps, .memoryApp(name: name)]
            default: break
            }
        case "live": showingLive = true
        case "settings": paths[.home] = [.settings]
        case "picker": paths[.home] = [.settings, .modelPicker(stuck: parts.last == "stuck")]
        case "plan": paths[.home] = [.settings, .planModels(brain: parts.last == "brain")]
        case "access": paths[.home] = [.settings, .access]
        case "voice": paths[.home] = [.settings, .voice]
        case "listening": paths[.home] = [.settings, .listening]
        case "voicepicker": paths[.home] = [.settings, .voice, .voicePicker(provider: parts.count > 1 ? parts[1] : "apple")]
        case "listen": showingListen = true
        case "provider": paths[.home] = [.settings, .providerDetail(id: parts.last ?? "chatgpt")]
        case "conversation": paths[.home] = [.conversation(id: parts.last ?? "")]
        case "screens": paths[.home] = [.runScreens(taskId: parts.last ?? "")]
        case "activity":
            ActivityPreview.yourTurn = parts.last == "yourturn"
            previewActivity = ActivityPreview.yourTurn ? .waiting : ModeActivityAttributes.ContentState.Phase(rawValue: parts.last ?? "")
        case "question": previewQuestion = ActivityPreview.sampleQuestion
        case "sample": sample = ScreenSample(id: parts.last ?? "")
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
                    case .connectChatGPT: ConnectChatGPTRoute(app: app)
                    case .memory: MemoryRoute(app: app, navigate: navigate)
                    case let .modelPicker(stuck): ModelPickerRoute(app: app, stuck: stuck, navigate: navigate)
                    case let .planModels(brain): PlanModelsRoute(app: app, brain: brain)
                    case .access: AccessRoute(app: app)
                    case .voice: VoiceRoute(app: app, hearing: false, navigate: navigate)
                    case .listening: VoiceRoute(app: app, hearing: true, navigate: navigate)
                    case let .voicePicker(provider): VoicePickerRoute(app: app, provider: provider)
                    case let .providerDetail(id): ProviderDetailRoute(app: app, id: id, navigate: navigate)
                    case .memoryAboutYou: MemoryAboutYouRoute(app: app, navigate: navigate)
                    case let .memoryRoutines(appName): MemoryRoutinesRoute(app: app, appName: appName, navigate: navigate)
                    case .memoryApps: MemoryAppsRoute(app: app, navigate: navigate)
                    case let .memoryApp(name): MemoryAppRoute(app: app, name: name, navigate: navigate)
                    case let .memoryEdit(id): MemoryEditRoute(app: app, id: id)
                    }
                }
        }
    }

    private func navigate(_ route: Route) {
        path.append(route)
    }
}
