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
    @LazyState private var run: RunStatusViewModel
    @State private var showingLive = false
    @State private var showingAsk = false
    /// The open voice session, if any: it lives here, so its screen can shrink into the session bar and come back.
    @State private var session: TalkModel?
    /// The voice screen is up (else the session shows as the bar).
    @State private var sessionOpen = false
    /// A mic in the Ask sheet: the session starts once the sheet has closed (its chat and entry).
    @State private var talkAfterAsk: (conversation: String?, entry: String)?
    #if DEBUG && targetEnvironment(simulator)
    @State private var previewActivity: ModeActivityAttributes.ContentState.Phase?
    @State private var previewQuestion: RunStatusViewModel.PendingQuestion?
    #if DEBUG && targetEnvironment(simulator)
    @State private var sample: ScreenSample?
    #endif
    #endif

    init(app: AppModel) {
        self.app = app
        _run = LazyState(wrappedValue: RunStatusViewModel(screens: app.screens))
    }

    var body: some View {
        TabView(selection: $tab) {
            Tab(value: AppTab.home) { stack(.home) { navigate in HomeRoute(app: app, run: run, navigate: navigate, openLive: { showingLive = true }) } }
            Tab(value: AppTab.tasks) { stack(.tasks) { navigate in TasksRoute(app: app, run: run, navigate: navigate) } }
        }
        .environment(\.modeSessionBar, sessionBarInChat)
        .safeAreaInset(edge: .bottom, spacing: ModeSpacing.none) {
            VStack(spacing: ModeSpacing.s) {
            // The session bar: above the tabs here; a chat shows it above its own message bar instead.
            if !inChat, let bar = sessionBar { bar.padding(.horizontal, ModeSpacing.m) }
            if paths[tab, default: []].isEmpty {
                // On Home the running task is the dark card in the page; elsewhere a white bar above the tabs.
                // Settings in the bar opens its own page over the tab you're on; back returns there, same tab.
                MainBar(tab: Binding(get: { tab }, set: { picked in
                            if picked == .settings { paths[tab, default: []].append(.settings) } else { tab = picked }
                        }),
                        running: tab == .home ? nil : run.state.running,
                        onSpeak: { showingAsk = true }, onPauseToggle: run.togglePause, onStop: run.stop, onOpen: { showingLive = true })
            }
            }
            .animation(ModeMotion.standard, value: session != nil && !sessionOpen)
        }
        .fullScreenCover(isPresented: $showingLive) { NavigationStack { LiveRoute(app: app) } }
        // The voice session's screen, and a question in a spoken task when no session is open (Mode came
        // forward to ask it out loud). Swiping it down minimises the session, like ⌄.
        .fullScreenCover(isPresented: Binding(get: { (session != nil && sessionOpen) || (session == nil && run.state.talking) },
                                              set: { if !$0 { sessionOpen = false } })) {
            if let session {
                TalkSessionRoute(model: session, onMinimise: { sessionOpen = false }, onEnded: endSession,
                                 onVoiceSettings: openVoiceSettings, onOpenChat: { id in open(.conversation(id: id)) },
                                 onWhatWentWrong: { taskId in tab = .home; paths[.home] = [.taskDebug(taskId: taskId)] })
            } else {
                TalkRoute(app: app, forQuestion: true, onClose: {}, onVoiceSettings: openVoiceSettings,
                          onOpenChat: { id in open(.conversation(id: id)) },
                          onWhatWentWrong: { taskId in tab = .home; paths[.home] = [.taskDebug(taskId: taskId)] })
            }
        }
        // The session ended while minimised (× on the bar, "that's all", paused too long): the bar goes.
        .onChange(of: session?.state.finished ?? false) { _, finished in if finished { endSession() } }
        // Mode came forward with a question in the session: its screen comes up to ask it.
        .onChange(of: run.state.talking) { _, talking in if talking, session != nil { sessionOpen = true } }
        .sheet(isPresented: $showingAsk, onDismiss: {
            if let pending = talkAfterAsk { talkAfterAsk = nil; startSession(conversation: pending.conversation, entry: pending.entry) }
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
        app.screens.linkOpened(entry: link.traceName)
        switch link {
        case .conversation, .retry, .ask:
            // Whatever is over the tabs (the live run, Ask, talking mode) closes, so the page it opens is seen.
            showingLive = false
            showingAsk = false
            sessionOpen = false
        default: break
        }
        switch link {
        case let .conversation(id):
            showingLive = false
            tab = .home
            paths[.home] = [.conversation(id: id)]
        case .ask: showingAsk = true
        case .open: break
        case .listen: startSession(conversation: nil, entry: "siri")
        case let .talk(id):
            let entry = id == nil ? "ask_mic" : "chat_mic"
            // One full-screen view at a time: from the Ask sheet, the session waits for it to close.
            if showingAsk { talkAfterAsk = (id, entry); showingAsk = false } else { startSession(conversation: id, entry: entry) }
        case let .retry(taskId, conversationId):
            // As What went wrong's Try again: the same task, in the same chat.
            let presenter = app.screens.taskDebug(taskId: taskId)
            _ = presenter.retry()
            presenter.close()
            if let conversationId { open(.conversation(id: conversationId)) }
        }
    }

    /// Opens the voice session: the open one when it's this chat's (or none was named), else a new one.
    private func startSession(conversation: String?, entry: String) {
        if let current = session {
            if conversation == nil || current.state.chat == nil || current.state.chat == conversation {
                sessionOpen = true
                return
            }
            current.endNow()
            current.dismiss()
        }
        session = TalkModel(screens: app.screens, forQuestion: false, conversationId: conversation, entry: entry)
        sessionOpen = true
    }

    /// The session is over (End, ×, or "that's all"): the screen and the bar go.
    private func endSession() {
        sessionOpen = false
        session?.dismiss()
        session = nil
    }

    /// A chat is on screen: it shows the session bar above its message bar.
    private var inChat: Bool {
        if case .conversation = paths[tab, default: []].last { return true }
        return false
    }

    /// The bar, while a session is open and its screen is down.
    private var sessionBar: ModeSessionBar? {
        guard let session, !sessionOpen else { return nil }
        let bar = session.bar
        return ModeSessionBar(bar.kind, title: bar.title, subtitle: bar.subtitle,
                              labels: .init(open: L10n.Talk.barOpenLabel, pause: L10n.Talk.barPause, resume: L10n.Talk.barResume,
                                            end: L10n.Talk.end, stop: L10n.Talk.barStop, answer: L10n.Talk.barAnswer),
                              onOpen: { sessionOpen = true }, onMic: { if session.toggleMic() { sessionOpen = true } }, onEnd: { session.endNow() },
                              onStop: session.stop)
    }

    private var sessionBarInChat: AnyView? { inChat ? sessionBar.map { AnyView($0) } : nil }

    #if DEBUG && targetEnvironment(simulator)
    /// Simulator only: `MODE_OPEN=tasks`, `memory`, `live`, `conversation:<id>` or `screens:<taskId>`
    /// opens that screen, so each board can be compared with the app without tapping.
    private func openForScreenshots() {
        guard let target = ProcessInfo.processInfo.environment["MODE_OPEN"] else { return }
        let parts = target.split(separator: ":", maxSplits: 1).map(String.init)
        switch parts.first {
        case "tasks": tab = .tasks
        case "memory":
            tab = .home
            paths[.home] = [.settings, .memory]
            switch parts.count > 1 ? parts.last : nil {
            case "aboutyou": paths[.home] = [.settings, .memory, .memoryAboutYou]
            case "routines": paths[.home] = [.settings, .memory, .memoryRoutines(app: nil)]
            case "apps": paths[.home] = [.settings, .memory, .memoryApps]
            case let name?: paths[.home] = name.hasPrefix("edit=") ? [.settings, .memory, .memoryEdit(id: String(name.dropFirst(5)))] : [.settings, .memory, .memoryApps, .memoryApp(name: name)]
            default: break
            }
        case "live": showingLive = true
        case "settings": paths[.home] = [.settings]
        case "picker": paths[.home] = [.settings, .modelPicker(stuck: parts.last == "stuck")]
        case "plan": paths[.home] = [.settings, .planModels(brain: parts.last == "brain")]
        case "access": paths[.home] = [.settings, .access]
        case "voice": paths[.home] = [.settings, .voice]
        case "listening": paths[.home] = [.settings, .listening]
        case "language": paths[.home] = [.settings, .listening, .language]
        case "health": paths[.home] = [.settings, .health]
        case let open? where open.hasPrefix("debug:"): paths[.home] = [.taskDebug(taskId: String(open.dropFirst(6)))]
        case "voicepicker": paths[.home] = [.settings, .voice, .voicePicker(provider: parts.count > 1 ? parts[1] : "apple")]
        case "listen": startSession(conversation: nil, entry: "siri")
        case "sessionbar":
            // The session, then its bar a moment later (as if ⌄ was tapped).
            startSession(conversation: nil, entry: "siri")
            DispatchQueue.main.asyncAfter(deadline: .now() + 4) { sessionOpen = false }
        case "provider": paths[.home] = [.settings, .providerDetail(id: parts.last ?? "chatgpt")]
        case "unlockvoice": paths[.home] = [.settings, .unlockVoice]
        case "backup": paths[.home] = [.settings, .backup]
        case "conversation": paths[.home] = [.conversation(id: parts.last ?? "")]
        case "screens": paths[.home] = [.runScreens(taskId: parts.last ?? "")]
        case "activity":
            previewActivity = ActivityPreview.phase(for: parts.last ?? "")
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

/// The bottom of the main screens: the running task (when given) above the tabs and the Speak button,
/// over the page fading out.
struct MainBar: View {
    @Binding var tab: AppTab
    let running: RunStatusViewModel.Running?
    let onSpeak: () -> Void
    let onPauseToggle: () -> Void
    let onStop: () -> Void
    let onOpen: () -> Void

    var body: some View {
        ModeTabBar(
            items: [
                ModeTabItem(AppTab.home, icon: .home, title: L10n.Tab.home),
                ModeTabItem(AppTab.tasks, icon: .tasks, title: L10n.Tab.tasks),
                ModeTabItem(AppTab.settings, icon: .settings, title: L10n.Tab.settings),
            ],
            selection: $tab,
            speakLabel: L10n.Tab.ask,
            onSpeak: onSpeak
        ) {
            if let running {
                ModeMiniPlayer(
                    title: running.title, status: running.status, paused: running.paused, compact: true,
                    pauseLabel: L10n.Run.pause, resumeLabel: L10n.Run.resume, stopLabel: L10n.Common.stop,
                    onPauseToggle: onPauseToggle, onStop: onStop, onOpen: onOpen
                )
            }
        }
        .animation(ModeMotion.standard, value: running)
        // 28 from the bottom of the screen, as on the board (a little into the home indicator's area).
        .padding(.bottom, ModeSpacing.xxs - ModeSpacing.s)
        .background(alignment: .bottom) {
            ModeEdgeFade(edge: .bottom).padding(.top, -ModeSpacing.xxxl).ignoresSafeArea(edges: .bottom).allowsHitTesting(false)
        }
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
                    case .language: LanguageRoute(app: app)
                    case .health: HealthRoute(app: app, navigate: navigate)
                    case let .taskDebug(taskId): TaskDebugRoute(app: app, taskId: taskId, navigate: navigate)
                    case let .taskDebugAsk(taskId): TaskDebugAskRoute(app: app, taskId: taskId, navigate: navigate)
                    case let .voicePicker(provider): VoicePickerRoute(app: app, provider: provider)
                    case let .providerDetail(id): ProviderDetailRoute(app: app, id: id, navigate: navigate)
                    case .memoryAboutYou: MemoryAboutYouRoute(app: app, navigate: navigate)
                    case let .memoryRoutines(appName): MemoryRoutinesRoute(app: app, appName: appName, navigate: navigate)
                    case .memoryApps: MemoryAppsRoute(app: app, navigate: navigate)
                    case let .memoryApp(name): MemoryAppRoute(app: app, name: name, navigate: navigate)
                    case let .memoryEdit(id): MemoryEditRoute(app: app, id: id)
                    case .unlockVoice: UnlockVoiceRoute(app: app)
                    case .backup: BackupRoute(app: app)
                    }
                }
        }
    }

    private func navigate(_ route: Route) {
        path.append(route)
    }
}

/// The session bar a screen with its own bottom bar (a chat) shows above it, set by MainTabs while a voice session
/// is open and minimised. Nil: no bar.
private struct ModeSessionBarKey: EnvironmentKey {
    static let defaultValue: AnyView? = nil
}

extension EnvironmentValues {
    var modeSessionBar: AnyView? {
        get { self[ModeSessionBarKey.self] }
        set { self[ModeSessionBarKey.self] = newValue }
    }
}
