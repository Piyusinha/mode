#if DEBUG && targetEnvironment(simulator)
import ModeDesignSystem
import ModeKit
import SwiftUI

/// Simulator only: `MODE_OPEN=sample:<name>` shows a screen with the board's sample state, for screens whose
/// real state needs a signed-in account (the simulator has none). Views are dumb, so the state is all they need.
struct ScreenSample: Identifiable {
    let id: String
    /// "chatgpt-bottom": the same screen, scrolled to its end.
    var bottom: Bool { id.hasSuffix("-bottom") }

    private func talk(_ state: TalkModel.State, level: Double, speaking: Bool, cantHear: Bool = false) -> some View {
        TalkScreen(state: state, level: level, speaking: speaking, typingAnswer: false, typed: .constant(""),
                   onAllowMic: {}, onDone: {}, onType: {}, onSend: {}, onAnswer: { _ in }, onLater: {}, onStop: {}, onClose: {},
                   cantHear: cantHear)
    }

    @ViewBuilder var screen: some View {
        switch id.replacingOccurrences(of: "-bottom", with: "") {
        case "welcome": WelcomeScreen(onEvent: { _ in })
        case "connect": ConnectAIScreen(state: .init(step: .connect, connect: .choose, connected: false, jobs: []), onEvent: { _ in })
        case "connect-claude": ConnectAIScreen(state: .init(step: .connect, connect: .connected("claude_oauth"), connected: true, jobs: []), onEvent: { _ in })
        case "connect-failed": ConnectAIScreen(state: .init(step: .connect, connect: .notEligible, connected: false, jobs: []), onEvent: { _ in })
        case "firsttask": FirstTaskScreen(jobs: ["Turn on Do Not Disturb", "Turn off Wi-Fi", "Set an alarm for 7 am"], starting: false, onEvent: { _ in })
        case "firsttask-starting": FirstTaskScreen(jobs: ["Turn on Do Not Disturb", "Turn off Wi-Fi", "Set an alarm for 7 am"], starting: true, onEvent: { _ in })
        case "setup-voice-apple", "setup-voice-eleven":
            OnboardingScreen(state: Self.voiceSetup(eleven: id == "setup-voice-eleven"), callName: .constant(""),
                             providers: .init(), onProvider: { _ in }, onEvent: { _ in })
        case let setup where setup.hasPrefix("setup-"):
            OnboardingScreen(state: Self.setup(setup), callName: .constant("Alex"),
                             providers: Self.setupProviders(setup), onProvider: { _ in }, onEvent: { _ in })
        case "connect-chatgpt", "connect-chatgpt-failed", "connect-chatgpt-settings":
            NavigationStack {
                ConnectChatGPTScreen(
                    phase: id == "connect-chatgpt" ? .waiting
                        : id == "connect-chatgpt-failed" ? .failed(message: "Continue with ChatGPT needs a Plus or Pro plan. You can connect OpenRouter instead.", notEligible: true)
                        : .connected(models: ["GPT-6.1 Terra", "GPT-6.1 Sol"], check: "Mode uses your plan for tasks."),
                    connected: id == "connect-chatgpt-settings",
                    step: id == "connect-chatgpt-settings" ? nil : SetupStep(current: 2, total: 6, label: L10n.Setup.step(2, of: 6)),
                    onSignIn: {}, onRecheck: {}, onSignOut: {}, onUseOpenRouter: {})
            }
        case "teach-steer": pushed { ConversationScreen(state: Self.teachSteer, thumbnails: [:], draft: .constant(""), canSend: false, onEvent: { _ in }) }
        case "chat": pushed { ConversationScreen(state: Self.chat, thumbnails: [:], draft: .constant(""), canSend: false, onEvent: { _ in }) }
        case "teach-step": pushed {
            RunScreensScreen(state: Self.screens, mode: .constant(.grid), playIndex: .constant(0), onOpen: { _ in }, onShare: {}, onWrong: { _ in },
                             savingFix: false, fixReply: nil, onFixSeen: {})
                .sheet(isPresented: .constant(true)) {
                    ModeFixStepForm(title: L10n.Screens.whatInstead, stepLine: L10n.Screens.stepLine("8", "Scrolled up on Display & Brightness"),
                                    prompt: L10n.Screens.whatInsteadPrompt,
                                    text: .constant("Light and Dark aren’t on this page any more. Search Settings for “Dark Mode”."), everyTime: .constant(true),
                                    justThisTime: L10n.Screens.justThisTime, everyTimeTitle: L10n.Screens.everyTime, note: L10n.Screens.everyTimeNote,
                                    saveTitle: L10n.Common.save, canSave: true) {}
                        .presentationDetents([.height(ModeSize.fixSheet)])
                }
        }
        case "voice-notice": pushed { ConversationScreen(state: Self.voiceNotice, thumbnails: [:], draft: .constant(""), canSend: false, onEvent: { _ in }) }
        case "chat-long": pushed { ConversationScreen(state: Self.teachLearned, thumbnails: [:], draft: .constant("Next time when you search flights, open Flightradar first and check the status for my IndiGo flight on the fourth, then tell me the gate and the delay if there is one."), canSend: true, onEvent: { _ in }) }
        case "teach-learned": pushed { ConversationScreen(state: Self.teachLearned, thumbnails: [:], draft: .constant(""), canSend: false, onEvent: { _ in }) }
        case "talk-mic": talk(.init(phase: .needsMic), level: 0, speaking: false)
        case "talk-conv-working": talk(Self.conversation(.working), level: 0, speaking: false)
        case "talk-conv-long": talk(Self.longConversation(), level: 0, speaking: false)
        case "talk-conversation": talk(Self.conversation(.done), level: 0.4, speaking: false)
        case "talk-conv-partial": talk(Self.conversation(.partial), level: 0.4, speaking: false)
        case "talk-canthear": talk(.init(phase: .listening, greeting: "Hey Alex, good evening. What do you want me to do?"), level: 0, speaking: false, cantHear: true)
        case "talk-listen": talk(.init(phase: .listening, greeting: "Hey Alex, good evening. What do you want me to do?", settled: "Order my usual from", forming: "Swiggy"),
                                 level: 0.7, speaking: false)
        case "talk-start": talk(.init(phase: .starting, task: "Order my usual from Swiggy", reply: "I’ll order your usual from Swiggy.", working: "Opening Swiggy"),
                                level: 0, speaking: true)
        case "talk-question": talk(.init(phase: .answering, forming: "Large", task: "Swiggy · Margherita from Napoli Corner", question: "Medium or Large?",
                                         options: ["Medium · ₹249", "Large · ₹399"], heardOption: "Large · ₹399"), level: 0.5, speaking: false)
        case "talk-notice": talk(.init(phase: .typing, voiceNotice: "Mode couldn’t hear with Deepgram", voiceNoticeHearing: true,
                                       voiceNoticeMessage: "Deepgram didn’t answer, so Mode didn’t start anything."), level: 0, speaking: false)
        case let voice where voice.hasPrefix("voice-"): voiceSample(voice)
        case "talk-answered": talk(.init(phase: .answered, task: "Order my usual from Swiggy", working: "Back in Swiggy", answer: "Large", answerReply: "Large it is. Going back to Swiggy."),
                                   level: 0, speaking: true)
        case "task-failed": pushed { ConversationScreen(state: Self.taskFailed, thumbnails: [:], draft: .constant(""), canSend: false, onEvent: { _ in }) }
        case "task-debug": pushed { TaskDebugScreen(state: Self.taskDebug, onEvent: { _ in }) }
        case "task-debug-ask": pushed { TaskDebugAskScreen(state: Self.taskDebugAsk, onEvent: { _ in }) }
        case "home": shell(.home, running: nil) { HomeScreen(state: Self.home, running: Self.running, onEvent: { _ in }) }
        case "home-tips": shell(.home, running: nil) { HomeScreen(state: Self.homeTips(.voice, closable: false), running: nil, onEvent: { _ in }) }
        case "home-tips-day3": shell(.home, running: nil) { HomeScreen(state: Self.homeTips(.access, closable: true), running: nil, onEvent: { _ in }) }
        case "home-tips-closed": shell(.home, running: nil) {
            HomeScreen(state: HomeViewModel.State(greeting: "Good evening", recent: Self.homeTips(.voice, closable: true).recent), running: nil, showingUndo: true, onEvent: { _ in })
        }
        case "tasks-scheduled": shell(.tasks, running: nil) { TasksScreen(state: Self.tasksScheduled, filter: .constant(.scheduled), onEvent: { _ in }) }
        case "tasks-done": shell(.tasks, running: nil) { TasksScreen(state: Self.tasksDone, filter: .constant(.done), onEvent: { _ in }) }
        case "repeat":
            ModeColor.page.ignoresSafeArea().sheet(isPresented: .constant(true)) {
                RepeatScreen(state: Self.repeatState, dayLabels: Format.shortDays, onEvent: { _ in }, onCancel: {})
            }
        case "run": pushed { SampleRun() }
        case "screens": pushed {
            RunScreensScreen(state: Self.screens, mode: .constant(.grid), playIndex: .constant(0), onOpen: { _ in }, onShare: {}, onWrong: { _ in },
                             savingFix: false, fixReply: nil, onFixSeen: {})
        }
        case "settings": pushed { settings.defaultScrollAnchor(bottom ? .bottom : .top) }
        case "settings-update": pushed {
            SettingsScreen(state: Self.settingsState,
                           update: .init(current: "0.1.3", newer: "0.1.4", notesUrl: URL(string: "https://github.com/heymode/mode/releases")),
                           providers: Self.providers, name: .constant("Alex"), costStep: 0.01, costRange: 0.01...1,
                           dailyCapStep: 0.1, dailyCapRange: 0...10, onEvent: { _ in })
                .defaultScrollAnchor(bottom ? .bottom : .top)
        }
        case "settings-voice", "settings-voice-failed": pushed {
            SettingsScreen(state: Self.settingsVoice(failed: id.hasPrefix("settings-voice-failed")), providers: Self.providers, name: .constant("Alex"),
                           costStep: 0.01, costRange: 0.01...1, dailyCapStep: 0.1, dailyCapRange: 0...10, onEvent: { _ in })
        }
        case "providers": pushed {
            settings.sheet(isPresented: .constant(true)) {
                ProvidersSheetContent(state: Self.providers, onEvent: { _ in }, onDone: {}, onOpen: { _ in })
                    .presentationDetents([.fraction(ModeSheetHeight.providers), .large])
                    .presentationDragIndicator(.visible)
                    .presentationBackground(ModeColor.page)
                    .presentationCornerRadius(ModeRadius.sheet)
            }
        }
        case "link": pushed {
            settings.sheet(isPresented: .constant(true)) {
                OpenRouterLinkSheet(state: Self.providers, key: .constant(""), useOnceConnected: .constant(true), onEvent: { _ in }, onDone: {})
            }
        }
        case "picker": pushed {
            ModelPickerScreen(state: Self.picker, listed: Self.picker.picks, filter: .all, stuck: false, query: .constant(""), onEvent: { _ in })
        }
        case "access-full", "access-ask": pushed {
            AccessScreen(state: .init(fullAccess: id.hasPrefix("access-full"), words: ["auto", "just do it"], readCodes: true, askAbove: "Off"),
                         amounts: [], onEvent: { _ in })
                .defaultScrollAnchor(bottom ? .bottom : .top)
        }
        case "health", "health-ok": pushed {
            HealthScreen(state: Self.health(ok: id.hasPrefix("health-ok")), onEvent: { _ in }).defaultScrollAnchor(bottom ? .bottom : .top)
        }
        case "language": pushed { LanguageScreen(state: Self.language, onEvent: { _ in }) }
        case let backup where backup.hasPrefix("backup"): pushed {
            let stage = backup.replacingOccurrences(of: "-bottom", with: "")
            BackupScreen(state: Self.backup(stage), password: .constant("correcthor"), passwordAgain: .constant("correcthor"),
                         restorePassword: .constant(stage == "backup-wrong" ? "letmein" : ""), onEvent: { _ in })
                .defaultScrollAnchor(bottom ? .bottom : .top)
        }
        case "plan": pushed { PlanModelsScreen(state: Self.plan, onEvent: { _ in }).defaultScrollAnchor(bottom ? .bottom : .top) }
        case "claude": pushed { detail(Self.claude).defaultScrollAnchor(bottom ? .bottom : .top) }
        case "openrouter": pushed { detail(Self.openRouter).defaultScrollAnchor(bottom ? .bottom : .top) }
        default: pushed { detail(Self.chatGPT).defaultScrollAnchor(bottom ? .bottom : .top) }
        }
    }

    /// Board Settings: ChatGPT in use, three providers, full access.
    private var settings: some View {
        SettingsScreen(state: Self.settingsState, providers: Self.providers, name: .constant("Alex"), costStep: 0.01, costRange: 0.01...1,
                       dailyCapStep: 0.1, dailyCapRange: 0...10, onEvent: { _ in })
    }

    /// Boards SettingsVoiceProviders and SettingsVoiceFailed: ElevenLabs speaks with Rachel, Deepgram listens.
    static func settingsVoice(failed: Bool) -> SettingsViewModel.State {
        var state = settingsState
        state.speaker = "elevenlabs"; state.voiceName = "Rachel"; state.voiceValue = "ElevenLabs · Rachel"
        state.speakerLogo = VoiceService.elevenlabs.logo
        state.speakerTag = nil
        state.listener = "deepgram"; state.listenerLabel = VoiceService.deepgram.title; state.listenerLogo = VoiceService.deepgram.logo
        state.speakProblem = failed ? VoiceTroubleText.short("PlanLimit", hearing: false) : nil
        state.voiceNote = failed ? L10n.Voice.sectionNoteFailed : L10n.Voice.sectionNote
        state.modelName = "Claude Haiku 5.5"; state.stuckName = "Claude Sonnet 5.5"
        return state
    }

    static let settingsState = SettingsViewModel.State(
        modelName: "GPT-6.1 Terra · Low", stuckName: "GPT-6.1 Sol · High", showsCost: false, learn: true, fullAccess: true,
        speaker: "apple", voiceName: "Ava", listener: "apple", listenerLabel: String(localized: L10n.Voice.onIPhone), talkBack: true,
        voiceValue: "Ava", showsBackup: true
    )

    static let providers = ProvidersViewModel.State(
        rows: [.init(id: "chatgpt", kind: .chatGPT, title: "ChatGPT", status: "you@example.com · Plus", connected: true, inUse: true),
               .init(id: "claude_oauth", kind: .claude, title: "Claude", status: "you@example.com · Max", connected: true, inUse: false),
               .init(id: "openrouter", kind: .openRouter, title: "OpenRouter", status: "Not connected · pay as you go", connected: false, inUse: false)],
        activeTitle: "ChatGPT", activeKind: .chatGPT, activeConnected: true, summary: L10n.Providers.connectedCount(3),
        planNote: L10n.Providers.planNote("ChatGPT")
    )

    /// Board ModelPicker: OpenRouter's picks for phone control.
    static var picker: SettingsViewModel.State {
        func model(_ id: String, _ name: String, _ line: String, _ tag: ModeChoiceRow.Tag? = nil, main: Bool = false) -> SettingsViewModel.Model {
            .init(id: id, name: name, tag: tag, subtitle: line, leading: ModelTile.leading(for: id, name: name), cost: 0, isMain: main, isStuck: false)
        }
        var state = SettingsViewModel.State()
        state.picks = [
            model("anthropic/claude-haiku-5.5", "Claude Haiku 5.5", "Reliable and fast · about $0.004 a task",
                  .init("Recommended", background: ModeColor.cardRaised, foreground: ModeColor.textPrimary), main: true),
            model("anthropic/claude-sonnet-5.5", "Claude Sonnet 5.5", "For hard steps · about $0.08 a task",
                  .init("Strongest", background: ModeColor.cardRaised, foreground: ModeColor.textPrimary)),
            model("qwen/qwen-3.7-flash", "Qwen 3.7 Flash", "Simple tasks · about $0.001 a task",
                  .init("Cheapest", background: ModeColor.tagGreen, foreground: ModeColor.tagGreenText)),
            model("google/gemini-3.8-flash", "Gemini 3.8 Flash", "about $0.01 a task"),
            model("openai/gpt-6.1-terra", "GPT-6.1 Terra", "about $0.02 a task"),
        ]
        state.makers = ["anthropic", "openai", "google", "qwen"]
        state.modelCount = 342
        state.showsCost = true
        return state
    }

    /// Board Backup's stages: backup, backup-password, backup-restore, backup-wrong, backup-restored.
    static func backup(_ stage: String) -> BackupState {
        func item(_ id: String, _ title: String, _ value: String, _ icon: ModeBackupIcon, _ tone: ModeBackupTone) -> ModeBackupItem {
            .init(id: id, title: title, value: value, mark: .tile(icon, tone))
        }
        func added(_ id: String, _ title: String, _ value: String) -> ModeBackupItem { .init(id: id, title: title, value: value, mark: .added) }
        let contents = [
            item("chats", L10n.Backup.chats, L10n.Backup.chatsCount(42, messages: 318), .chats, .coral),
            item("memory", L10n.Backup.memory, "27", .memory, .lilac),
            item("tasks", L10n.Backup.tasks, "156", .tasks, .blue),
            item("repeats", L10n.Backup.repeats, "3", .repeats, .green),
            item("settings", L10n.Backup.settings, L10n.Backup.all, .settings, .gray),
        ]
        var state = BackupState(contents: contents, signInsDetail: L10n.Backup.signInsDetail("ChatGPT, OpenRouter, ElevenLabs"),
                                includeSignIns: stage == "backup-password", passwordsMatch: true,
                                saveNote: L10n.Backup.saveNote(lastBackup: L10n.Backup.dayAndTime(L10n.Backup.yesterday, "21:40")))
        switch stage {
        case "backup-restore", "backup-wrong":
            state.page = .preview
            state.fileDetail = L10n.Backup.fileDetail("Thu 6 Nov, 16:20", device: "Alex’s iPhone", size: "2.4 MB")
            state.inBackup = contents + [item("signIns", L10n.Backup.signIns, "ChatGPT, OpenRouter", .signIns, .orange)]
            state.locked = true
            state.wrongPassword = stage == "backup-wrong"
            state.canRestore = stage != "backup-wrong"
        case "backup-restored":
            state.page = .restored
            state.restoredLine = L10n.Backup.restoredLine(day: "Thursday")
            state.added = [
                added("chats", L10n.Backup.chats, L10n.Backup.added(38)),
                added("memory", L10n.Backup.memory, L10n.Backup.added(24)),
                added("tasks", L10n.Backup.tasks, L10n.Backup.added(151)),
                added("repeats", L10n.Backup.repeats, L10n.Backup.added(3)),
                added("signIns", L10n.Backup.signIns, "ChatGPT, OpenRouter"),
            ]
            state.skippedNote = L10n.Backup.skipped("4 chats and 3 memories", one: false)
        default: break
        }
        return state
    }

    /// Boards SettingsHealth (two problems) and SettingsHealthOk.
    static func health(ok: Bool) -> HealthModel.State {
        func row(_ id: String, _ group: String, _ title: LocalizedStringResource, _ detail: String, _ icon: ModeHealthIcon,
                 _ status: ModeHealthRow.Status = .ok, repair: Bool = false, opens: String? = nil) -> HealthModel.Row {
            .init(id: id, group: group, title: String(localized: title), detail: detail, icon: icon, status: status, repair: repair, opens: opens)
        }
        return HealthModel.State(rows: [
            row("paired", "phone", L10n.Health.paired, "Pairing file is here", .paired),
            row("vpn", "phone", L10n.Health.vpn, "On", .vpn),
            row("reach", "phone", L10n.Health.reach, "0.9 s", .reach),
            row("screen", "phone", L10n.Health.screen, "Checked just now", .screen),
            row("ai", "ai", L10n.Health.ai, "Signed in · Plus", .ai),
            ok ? row("actions", "ai", L10n.Health.actions, "Test passed just now", .actions)
                : row("actions", "ai", L10n.Health.actions, "Last task: no action 6 times in a row", .actions, .warning, repair: true),
            row("microphone", "voice", L10n.Health.microphone, "Allowed · hears you", .microphone),
            row("listening", "voice", L10n.Health.listening, "Apple · English (India)", .listening),
            ok ? row("speaking", "voice", L10n.Health.speaking, "ElevenLabs · Rachel", .speaking)
                : row("speaking", "voice", L10n.Health.speaking, "ElevenLabs: your plan doesn’t include this voice", .speaking, .warning, opens: "voice"),
            row("notifications", "iphone", L10n.Health.notifications, "On, for questions and results", .notifications),
        ], problems: ok ? 0 : 2, repairable: ok ? 0 : 1,
        attentionTitle: L10n.Health.needAttention(2), attentionDetail: L10n.Health.canRepair("one", rest: "one", restCount: 1))
    }

    /// Board SettingsLanguage.
    static let language = LanguageModel.State(rows: [
        .init(id: "en-IN", name: "English (India)", detail: "On this iPhone", selected: true),
        .init(id: "hi", name: "Hindi", detail: "हिन्दी · download once, 52 MB", selected: false),
        .init(id: "gu", name: "Gujarati", detail: "ગુજરાતી · with Soniox or Deepgram", selected: false),
        .init(id: "auto", name: "Detect it each time", detail: "Mode hears which language you use", selected: false),
    ], replyInSpoken: true)

    /// A main tab's first screen with the bar at the bottom, as MainTabs draws it.
    private func shell(_ tab: AppTab, running: RunStatusViewModel.Running?, @ViewBuilder content: () -> some View) -> some View {
        NavigationStack { content() }
            .safeAreaInset(edge: .bottom, spacing: ModeSpacing.none) {
                MainBar(tab: .constant(tab), running: running, onSpeak: {}, onPauseToggle: {}, onStop: {}, onOpen: {})
            }
    }

    /// A screen pushed onto a stack, so it has its back button.
    private func pushed(@ViewBuilder content: @escaping () -> some View) -> some View {
        NavigationStack(path: .constant([0])) {
            Color.clear.navigationDestination(for: Int.self) { _ in content() }
        }
    }

    /// Boards Home and Tasks: the running task.
    static let running = RunStatusViewModel.Running(
        title: "Ordering my usual dinner", status: "Step 9 · checking the cart", paused: false,
        plan: [.init("Opened Swiggy", state: .done), .init("Picked the usual", state: .done), .init("Added to cart", state: .done), .init("Checkout", state: .now)])

    /// Board Home.
    static let home = HomeViewModel.State(
        greeting: "Good morning, Alex",
        suggestions: [.init(title: "Read today's calendar", subtitle: "You ask most mornings", icon: .calendar),
                      .init(title: "Text Sam", subtitle: "On WhatsApp", icon: .message),
                      .init(title: "Light mode", subtitle: "Settings", icon: .settings)],
        recent: [.init(id: "1", title: "Text Sam, then today's calendar", subtitle: "Today 8:13 · 62 s", leading: .mo(.done)),
                 .init(id: "2", title: "Turn off dark mode", subtitle: "Yesterday · 37 s", leading: .mo(.done)),
                 .init(id: "3", title: "Set an alarm for 6:30", subtitle: "Yesterday · 21 s", leading: .mo(.done)),
                 .init(id: "4", title: "Turn on Do Not Disturb", subtitle: "Friday · 18 s", leading: .mo(.done))]
    )

    /// Board Home · Did you know: right after setup, with the first job in Recent.
    static func homeTips(_ tip: SetupTip, closable: Bool) -> HomeViewModel.State {
        let all: [SetupTip] = [.voice, .listening, .access, .startWithoutOpening, .name]
        return HomeViewModel.State(
            greeting: "Good evening",
            recent: [.init(id: "1", title: "Turn off Wi-Fi", subtitle: "Today 22:41 · 23 s", leading: .mo(.done))],
            tips: .init(tips: all.map(HomeViewModel.tip), index: all.firstIndex(of: tip) ?? 0, closable: closable),
            tryThese: ["Turn on Do Not Disturb", "Tell me about everything on my calendar today", "Catch me up on today's unread WhatsApp messages"]
        )
    }

    /// Board Tasks · scheduled.
    static let tasksScheduled = TasksViewModel.State(
        scheduled: [.init(id: "o1", title: "Remind Priya about dinner", subtitle: "Today 19:00", icon: .clock, enabled: true),
                    .init(id: "o2", title: "Book a cab to the office", subtitle: "Tomorrow 9:00", icon: .clock, enabled: true),
                    .init(id: "o3", title: "Check where the parcel is", subtitle: "Thu 7 Nov · 10:00", icon: .clock, enabled: true)],
        repeats: [.init(id: "s1", title: "Read today's calendar", subtitle: "Weekdays 8:00", icon: .repeatTask, enabled: true),
                  .init(id: "s2", title: "Book the office cab", subtitle: "Weekdays 8:40", icon: .repeatTask, enabled: true)]
    )

    /// Board Tasks · done: today and yesterday.
    static let tasksDone = TasksViewModel.State(
        doneToday: [.init(id: "d1", title: "Texted Priya", subtitle: "17:42 · took 42 s", leading: .mo(.done), conversationId: nil),
                    .init(id: "d2", title: "Moved the dentist to Thursday", subtitle: "12:10 · took 58 s", leading: .mo(.done), conversationId: nil),
                    .init(id: "d3", title: "Pay electricity bill · Couldn't sign in", subtitle: "10:25 · stopped at 31 s", leading: .mo(.stuck), conversationId: nil),
                    .init(id: "d4", title: "Read your calendar", subtitle: "8:00 · took 20 s", leading: .mo(.done), conversationId: nil)],
        doneYesterday: [.init(id: "d5", title: "Ordered your usual from Swiggy", subtitle: "20:05 · took 1 min 40 s", leading: .mo(.done), conversationId: nil),
                        .init(id: "d6", title: "Booked a cab to the office", subtitle: "8:41 · took 36 s", leading: .mo(.done), conversationId: nil),
                        .init(id: "d7", title: "Read your calendar", subtitle: "8:00 · took 18 s", leading: .mo(.done), conversationId: nil)]
    )

    /// Board Repeat.
    static let repeatState = RepeatViewModel.State(
        text: "Read me today's calendar", time: Calendar.current.date(from: DateComponents(hour: 8, minute: 0)) ?? .now, hour: 8, minute: 0,
        frequency: .weekdays, days: Set(1...5), note: L10n.Repeat.note(time: "8:00", days: String(localized: L10n.Repeat.onWeekdays)), canSave: true)

    /// Board Run: the live run, with a calendar on screen.
    @MainActor static func run() -> LiveViewModel.State {
        var state = LiveViewModel.State()
        state.running = .init(badge: L10n.Run.liveBadge(step: 7), startedAt: Date().addingTimeInterval(-41), paused: false,
                              task: "Text Sam, then calendar", now: "Reading the rest of today",
                              segments: [.init("Opened WhatsApp", state: .done), .init("Sent message", state: .done), .init("Calendar", state: .now)])
        state.screen = SampleCalendar.image
        return state
    }

    /// Board Screens.
    static let screens = RunScreensViewModel.State(
        title: String(localized: L10n.Screens.title), subtitle: "Text Sam, then calendar · 11 steps",
        shots: ["Opened WhatsApp", "Tapped Sam", "Typed the message", "Tapped send", "Message sent", "Opened Calendar",
                "Tapped today", "Scrolled down", "Read the events", "Went back", "Done"].enumerated().map { index, label in
            .init(id: index + 1, number: String(index + 1), label: label, image: nil)
        })

    private func detail(_ s: ProviderDetailViewModel.State) -> some View {
        let links: [ProvidersViewModel.Kind: (LocalizedStringResource, URL)] = [
            .chatGPT: (L10n.Account.seeChatGPTUsage, ModeLinks.chatGPTUsage),
            .claude: (L10n.Account.seeClaudeUsage, ModeLinks.claudeUsage),
            .openRouter: (L10n.Account.addCredits, ModeLinks.openRouterCredits),
        ]
        let signOut: LocalizedStringResource = s.kind == .chatGPT ? L10n.Account.signOutChatGPT : s.kind == .claude ? L10n.Account.signOutClaude : L10n.Account.removeKey
        return ProviderDetailScreen(state: s, link: links[s.kind].map { (title: $0.0, url: $0.1) }, signOutTitle: signOut, onEvent: { _ in }, openLink: { _ in })
    }

    /// Boards Welcome, Key/KeyConnected, SetupModelClaude, TestResult, Diagnose: setup with the board's sample state.
    private static func setup(_ id: String) -> OnboardingViewModel.State {
        var state = OnboardingViewModel.State()
        func step(_ n: Int) { state.stepNumber = n; state.stepLabel = L10n.Setup.step(n, of: state.stepTotal) }
        switch id {
        case "setup-key", "setup-key-connected": state.step = .key; step(2)
        case "setup-model-claude":
            state.step = .model; step(3); state.onPlan = true
            let tile = ModeRowLeading.logoTile(.claude, background: ModeColor.setupClaudeTile, foreground: ModeColor.setupOnClaudeTile)
            state.phoneCard = .init(name: "Claude Haiku 5.5", leading: tile, effort: "Medium", recommended: true, why: String(localized: L10n.Setup.phoneWhy))
            state.brainCard = .init(name: "Claude Sonnet 5.5", leading: tile, effort: "High", recommended: true, why: String(localized: L10n.Setup.brainWhy))
        case "setup-model-chatgpt":
            state.step = .model; step(3); state.onPlan = true
            let tile = ModeRowLeading.logoTile(.openAI, background: ModeColor.accent, foreground: ModeColor.onAccent)
            state.phoneCard = .init(name: "GPT-6.1 Terra", leading: tile, effort: "Medium", recommended: true, why: String(localized: L10n.Setup.phoneWhy))
            state.brainCard = .init(name: "GPT-6.1 Sol", leading: tile, effort: "High", recommended: true, why: String(localized: L10n.Setup.brainWhy))
        case "setup-result":
            state.step = .result; state.testPassed = true
            state.did = [
                .init(id: 0, title: L10n.Setup.didConnect, leading: .check, detail: "0.9 s"),
                .init(id: 1, title: L10n.Setup.didOpenSettings, leading: .check, detail: nil),
                .init(id: 2, title: L10n.Setup.didTapWifi, leading: .check, detail: nil),
                .init(id: 3, title: L10n.Setup.didComeBack, leading: .check, detail: nil),
            ]
        case "setup-diagnose":
            state.step = .diagnose
            let notChecked = String(localized: L10n.Setup.notChecked)
            state.checks = [
                .init(id: 0, title: L10n.Setup.checkPairingFile, leading: .check, detail: nil, trailing: nil),
                .init(id: 1, title: L10n.Setup.checkVpn, leading: .check, detail: nil, trailing: nil),
                .init(id: 2, title: L10n.Setup.checkReach, leading: .check, detail: nil, trailing: "0.9 s"),
                .init(id: 3, title: L10n.Setup.checkPaired, leading: .failure,
                      detail: "This iPhone was unpaired, usually by tapping Stop on the screen-control indicator. Pair it again from your Mac.", trailing: nil),
                .init(id: 4, title: L10n.Setup.checkDeveloperMode, leading: .pending, detail: nil, trailing: notChecked),
                .init(id: 5, title: L10n.Setup.checkDiskImage, leading: .pending, detail: nil, trailing: notChecked),
                .init(id: 6, title: L10n.Setup.checkScreen, leading: .pending, detail: nil, trailing: notChecked),
                .init(id: 7, title: L10n.Setup.checkHands, leading: .pending, detail: nil, trailing: notChecked),
            ]
        default: state.step = .welcome; state.paired = true
        }
        return state
    }

    private static func setupProviders(_ id: String) -> ProvidersViewModel.State {
        let chatGPT = id == "setup-key-connected" || id == "setup-model-chatgpt"
        let claude = id == "setup-model-claude"
        var state = ProvidersViewModel.State()
        state.rows = [
            .init(id: "chatgpt", kind: .chatGPT, title: String(localized: L10n.Providers.chatGPT), status: "", connected: chatGPT, inUse: !claude),
            .init(id: "claude_oauth", kind: .claude, title: String(localized: L10n.Providers.claude), status: "", connected: claude, inUse: claude),
            .init(id: "openrouter", kind: .openRouter, title: String(localized: L10n.Providers.openRouter), status: "", connected: false, inUse: false),
        ]
        state.activeTitle = String(localized: claude ? L10n.Providers.claude : L10n.Providers.chatGPT)
        state.activeKind = claude ? .claude : .chatGPT
        state.activeConnected = chatGPT || claude
        return state
    }

    private static func voiceSetup(eleven: Bool) -> OnboardingViewModel.State {
        var state = OnboardingViewModel.State()
        state.step = .voice; state.stepNumber = 5; state.stepLabel = "Step 5 of 6"
        state.speaker = eleven ? .elevenlabs : .apple
        state.microphoneAllowed = eleven
        state.speakerStatus = eleven ? L10n.Voice.connectedKey("sk•••••9f") : String(localized: L10n.Voice.setupAppleSpeech)
        state.voices = eleven ? [
            .init(id: "rachel", name: "Rachel", traits: "Calm, clear · American", tag: String(localized: L10n.Voice.ourPick)),
            .init(id: "adam", name: "Adam", traits: "Deep, steady · American"),
            .init(id: "aria", name: "Aria", traits: "Warm, friendly · American")
        ] : [
            .init(id: "aaron", name: "Aaron", traits: "Clear · American"),
            .init(id: "eddy", name: "Eddy", traits: "Bright · American"),
            .init(id: "flo", name: "Flo", traits: "Warm · American")
        ]
        state.voice = state.voices.first?.id
        return state
    }

    private static func effort(_ label: String, _ speed: String, _ right: String, _ selected: Bool = false) -> ModeEffortTable.Row {
        .init(label: label, speed: speed, accuracy: right, selected: selected)
    }

    private static func said(_ id: String, _ text: String, spoken: Bool = false, steered: Bool = false) -> ConversationViewModel.Message {
        .init(id: id, fromUser: true, spoken: spoken, steered: steered, text: text, blocks: [], status: nil, screens: nil)
    }
    private static func reply(_ id: String, _ text: String, mood: ModeMo.Mood = .idle) -> ConversationViewModel.Message {
        .init(id: id, fromUser: false, spoken: false, steered: false, text: text, blocks: [.paragraph(text)], status: nil, screens: nil, mood: mood)
    }

    /// Board Chat: a spoken task done, its screens, the day's agenda, and a fact remembered.
    static let chat = ConversationViewModel.State(
        title: "Text Sam, then calendar", when: "Today 8:12",
        messages: [said("1", "Text Sam I'm leaving in 10, then tell me what's on my calendar today", spoken: true),
                   .init(id: "2", fromUser: false, spoken: false, steered: false, text: "Done. I sent “Leaving in 10” to Sam on WhatsApp.",
                         blocks: [.paragraph("Done. I sent “Leaving in 10” to Sam on WhatsApp."), .paragraph("Here's your day:"),
                                  .agenda([.init(time: "14:00", end: nil, text: "Biozyme with lunch", allDay: false),
                                           .init(time: "21:00", end: nil, text: "Shelcal, fish oil, magnesium", allDay: false),
                                           .init(time: "23:00", end: nil, text: "Softovac before bed", allDay: false)])],
                         status: nil, screens: (Self.seeScreens, "t1"), mood: .done)],
        remembered: [.init(id: "r1", text: L10n.Conversation.remembered("Sam is on WhatsApp"))]
    )
    private static let seeScreens = L10n.Conversation.seeScreens(11)

    /// Board VoiceNoticeChat: the chosen voice failed in a spoken chat.
    static let voiceNotice = ConversationViewModel.State(
        title: "What’s on my calendar?", when: "Today 17:58",
        messages: [said("1", "What’s on my calendar today?", spoken: true),
                   reply("2", "Three things: standup at 10:00, lunch with Asha at 13:00, and the dentist at 18:30.")],
        voiceNotice: L10n.Voice.couldntSpeakWith("ElevenLabs")
    )

    /// Board TaskFailedChat: a reply whose task couldn't finish, with What went wrong.
    static let taskFailed = ConversationViewModel.State(
        title: "Order Tres Leches", when: "Today 01:36",
        messages: [said("1", "Order Tres Leches from Magnolia Bakery on Zomato and pay, no need to ask me.", spoken: true),
                   .init(id: "2", fromUser: false, spoken: false, steered: false,
                         text: "I couldn’t confirm the order went through. One Classic Tres Leches is still in your cart at Magnolia Bakery, Indiranagar, for ₹521.69, scheduled for 11:00–11:30.",
                         blocks: [.paragraph("I couldn’t confirm the order went through. One Classic Tres Leches is still in your cart at Magnolia Bakery, Indiranagar, for ₹521.69, scheduled for 11:00–11:30.")],
                         status: Format.badge(.stopped), screens: nil, debugTaskId: "t1", mood: .stuck)]
    )

    /// Board TaskDebug: what Mode found in the run, and what it repaired.
    static let taskDebug = TaskDebugModel.State(
        title: "Order Tres Leches", when: "today 01:36", subtitle: "Order Tres Leches · today 01:36. Checked on this iPhone.",
        found: [.init(id: 0, title: "Place Order didn’t go through", detail: "The cart still had the item after the tap", icon: .chat(.wrench), tone: .problem),
                .init(id: 1, title: "Then it went back and forth", detail: "It couldn’t find Your Orders to check, so it stopped", icon: .icon(.runAgain), tone: .neutral),
                .init(id: 2, title: "Phone control", detail: "Working the whole time", icon: .chat(.phone), tone: .ok),
                .init(id: 3, title: "ChatGPT", detail: "Signed in and answering", icon: .chat(.sparkle), tone: .ok)],
        repaired: [.init(id: 0, title: "Reconnected phone control", detail: "Repaired on this iPhone", icon: .chat(.arrowRight), tone: .repaired),
                   .init(id: 1, title: "Remembered where your orders are", detail: "Zomato › Profile › Your Orders", icon: .chat(.brain), tone: .repaired)],
        provider: "ChatGPT")

    /// Board TaskDebugAsk: a question answered with the user's own plan.
    static let taskDebugAsk = TaskDebugModel.State(
        title: taskDebug.title,
        turns: [.init(id: 0, question: "Why did it stop at checkout?",
                      answer: "The Place Order tap didn’t register: the cart still showed your Tres Leches afterwards. Mode then tried to open Your Orders to check, but kept landing back on search.\n\n**What to do:** tap Try again. Mode now knows Your Orders is under Profile, so it will check there first, then place the order once.",
                      failed: false)],
        provider: "ChatGPT",
        suggestions: [String(localized: L10n.Debug.whatToChange), String(localized: L10n.Debug.willWork)])

    /// Board TeachSteer: the user changes how the running task is done.
    static let teachSteer = ConversationViewModel.State(
        title: "Turn off Dark Mode", when: L10n.Conversation.whenWorking("Today 20:39"),
        messages: [said("1", "Open the Settings app and turn off Dark Mode", spoken: true),
                   said("2", "It’s not there any more. Search for Dark Mode.", steered: true),
                   reply("3", "Okay, I’ll search Settings for Dark Mode instead.", mood: .working)],
        working: "Looking for Appearance in Display & Brightness", workingAfterId: "1", workingSince: Date().addingTimeInterval(-42),
        offerRemember: true, running: true
    )

    /// Board TeachLearned: a lesson saved for next time.
    static let teachLearned = ConversationViewModel.State(
        title: "Turn off Dark Mode", when: "Today 20:39",
        messages: [said("1", "Open the Settings app and turn off Dark Mode", spoken: true),
                   reply("2", "Done. Dark Mode is off; Light is on.", mood: .done),
                   said("3", "Next time use Control Center for this, it’s faster."),
                   reply("4", "Got it. Next time I’ll switch Dark Mode from Control Center.")],
        lessons: [.init(id: "l1", context: "Settings · Next time you ask about Dark Mode", text: "Dark Mode: open Control Center and tap its Dark Mode switch. Don’t go through Display & Brightness.", afterTurnId: "4")]
    )

    static let plan = PlanModelsViewModel.State(
        chip: "ChatGPT Plus · 4 models on your plan",
        role: .phoneControl,
        rows: [
            .init(id: "gpt-6.1-terra", name: "GPT-6.1 Terra", leading: ModelTile.leading(for: "openai/gpt", name: "GPT"),
                  tag: .init("Best for phone control", background: ModeColor.tagGreen, foreground: ModeColor.tagGreenText),
                  line: "Low effort · 1.4 s a step · 9 of 10 right", selected: true, effort: "low",
                  effortOptions: [("low", "Low"), ("medium", "Medium"), ("high", "High")].map { .init(id: $0.0, title: $0.1) },
                  efforts: [effort("Low", "1.4 s a step", "9 of 10 right", true), effort("Medium", "2.6 s a step", "10 of 10 right"),
                            effort("High", "5.2 s a step", "10 of 10 right")]),
            .init(id: "gpt-6-luna", name: "GPT-6 Luna", leading: ModelTile.leading(for: "openai/gpt", name: "GPT"),
                  tag: .init("Fastest", background: ModeColor.tileOrange, foreground: ModeColor.tileOrangeText),
                  line: "0.9 s a step · 7 of 10 right", selected: false, effort: nil, effortOptions: [], efforts: []),
            .init(id: "gpt-6.1-sol", name: "GPT-6.1 Sol", leading: ModelTile.leading(for: "openai/gpt", name: "GPT"),
                  tag: .init("Most accurate", background: ModeColor.infoSoft, foreground: ModeColor.infoText),
                  line: "3.8 s a step · 10 of 10 right", selected: false, effort: nil, effortOptions: [], efforts: []),
            .init(id: "gpt-6.1-terra-mini", name: "GPT-6.1 Terra mini", leading: ModelTile.leading(for: "openai/gpt", name: "GPT"),
                  tag: nil, line: "Not measured yet", selected: false, effort: nil, effortOptions: [], efforts: []),
        ],
        supportsEffort: true
    )

    static let chatGPT = ProviderDetailViewModel.State(
        kind: .chatGPT, title: "ChatGPT", status: "Connected · in use", connected: true, inUse: true,
        account: [.init(title: "Signed in as", value: "you@example.com"), .init(title: "Name", value: "Your name"), .init(title: "Plan", value: "Plus")],
        weekSteps: "186 steps", days: [32, 41, 18, 47, 12, 24, 12], today: 6, todayLine: "12 steps · 3 tasks", tasksLine: "41",
        limitLine: "Available", phoneModel: "GPT-6.1 Terra · Low", brainModel: "GPT-6.1 Sol · High"
    )

    static let claude = ProviderDetailViewModel.State(
        kind: .claude, title: "Claude", status: "Connected · in use", connected: true, inUse: true,
        account: [.init(title: "Signed in as", value: "you@example.com"), .init(title: "Plan", value: "Max")],
        todayLine: "14 steps · 2 tasks",
        limits: [.init(title: "Current session · 5 hours", value: "34% used", fraction: 0.34, footnote: "Resets at 15:40"),
                 .init(title: "This week", value: "12% used", fraction: 0.12, footnote: "Resets Monday 09:00")],
        usageUpdated: "11:32", phoneModel: "Haiku 5.5", brainModel: "Sonnet 5.5"
    )

    static let openRouter = ProviderDetailViewModel.State(
        kind: .openRouter, title: "OpenRouter", status: "Connected · not in use", connected: true,
        account: [.init(title: "Key", value: "sk-or-…1234"), .init(title: "Key name", value: "Mode")],
        creditsValue: "$4.21", creditsFraction: 0.421, creditsFootnote: "of $10.00 · from OpenRouter",
        spentToday: "$0.31 · 9 tasks", spentWeek: "$1.84", limitPerTask: "$0.10",
        phoneModel: "Claude Haiku 5.5", brainModel: "Claude Sonnet 5.5"
    )
}

/// Board Run, built on the main actor (it draws its sample screen).
private struct SampleRun: View {
    var body: some View { LiveScreen(state: ScreenSample.run(), onEvent: { _ in }) }
}

/// A phone screen with a calendar on it, for the live run's sample.
@MainActor
enum SampleCalendar {
    static var image: UIImage? {
        let renderer = ImageRenderer(content: screen)
        renderer.scale = 2
        return renderer.uiImage
    }

    private static var screen: some View {
        let width: CGFloat = 393, height: CGFloat = 852
        return VStack(alignment: .leading, spacing: ModeSpacing.m) {
            Text(verbatim: "November").modeText(.title2)
            HStack {
                ForEach(1...7, id: \.self) { day in
                    Text(verbatim: "\(day)").modeText(.footnoteEmphasis, color: day == 1 ? ModeColor.onAccent : ModeColor.textSecondary)
                        .frame(maxWidth: .infinity, minHeight: ModeSize.badge)
                        .background(day == 1 ? ModeColor.danger : .clear, in: .capsule)
                }
            }
            ForEach([("All day", "Daylight saving time ends"), ("14:00", "Biozyme with lunch"), ("21:00", "Shelcal, fish oil, magnesium")], id: \.1) { when, what in
                VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                    Text(verbatim: when).modeText(.caption, color: ModeColor.textSecondary)
                    Text(verbatim: what).modeText(.subheadlineEmphasis)
                }
                .padding(ModeSpacing.m)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(ModeColor.cardRaised, in: .rect(cornerRadius: ModeRadius.chip))
            }
            Spacer()
        }
        .padding(.horizontal, ModeSpacing.xl)
        .padding(.top, ModeSpacing.xxxl * 2)
        .frame(width: width, height: height)
        .background(ModeColor.card)
    }
}

/// The sample, pushed onto a Settings stack as the real screen is.
struct ScreenSampleHost: View {
    let sample: ScreenSample
    @State private var path = [0]

    var body: some View {
        if sample.id.hasPrefix("talk") || sample.id.hasPrefix("setup-") || sample.id.hasPrefix("connect-") { sample.screen } else { stacked }
    }

    private var stacked: some View {
        NavigationStack(path: $path) {
            Color.clear.navigationTitle(Text(L10n.Common.settings))
                .navigationDestination(for: Int.self) { _ in
                    sample.screen.defaultScrollAnchor(sample.bottom ? .bottom : .top)
                }
        }
    }
}

// MARK: - Voice boards (Voice, VoiceListening, VoiceProvider*, VoiceKey*, VoicePicker*)

extension ScreenSample {
    @ViewBuilder func voiceSample(_ id: String) -> some View {
        switch id {
        case "voice-listening": pushed { ListeningScreen(state: Self.listening, onEvent: { _ in }) }
        case "voice-page": pushed { VoiceProviderPage(row: Self.speakers.rows[1], hearing: false, onEvent: { _ in }) }
        case "voice-page-failed": pushed { VoiceProviderPage(row: Self.failedEleven, hearing: false, onEvent: { _ in }) }
        case "voice-key-add": pushed { VoiceScreen(state: Self.speakers, onEvent: { _ in }).modeBottomSheet(item: .constant(VoiceService.fish), height: ModeSize.keySheet) { _ in Self.keySheet(.fish, masked: nil, check: nil) } }
        case "voice-key-edit": pushed { VoiceScreen(state: Self.speakers, onEvent: { _ in }).modeBottomSheet(item: .constant(VoiceService.elevenlabs), height: ModeSize.keySheet) { _ in Self.keySheet(.elevenlabs, masked: "sk•••••9f", check: nil) } }
        case "voice-key-checking": pushed {
            ListeningScreen(state: Self.listening, onEvent: { _ in }).modeBottomSheet(item: .constant(VoiceService.soniox), height: ModeSize.keySheet) { _ in
                Self.keySheet(.soniox, masked: nil, check: .init(id: "soniox", state: "checking", trouble: nil), typed: "sk-live-0b81d6c1f2a947e5917c2e")
            }
        }
        case "voice-key-failed": pushed {
            ListeningScreen(state: Self.listening, onEvent: { _ in }).modeBottomSheet(item: .constant(VoiceService.deepgram), height: ModeSize.keySheet) { _ in
                Self.keySheet(.deepgram, masked: nil, check: .init(id: "deepgram", state: "failed", trouble: "KeyRejected"), typed: "4f19c0a2be77d3e1f58aa1x")
            }
        }
        case "voice-key-saved": pushed {
            ListeningScreen(state: Self.listening, onEvent: { _ in }).modeBottomSheet(item: .constant(VoiceService.deepgram), height: ModeSize.keySheet) { _ in
                VoiceKeySheet(service: .deepgram, masked: "4f•••••a1", check: .init(id: "deepgram", state: "saved", trouble: nil), checkAndSave: { _ in })
            }
        }
        case "voice-picker", "voice-picker-eleven", "voice-picker-fish": pushed {
            VoicePickerScreen(state: Self.picker(id), query: .constant(""), onEvent: { _ in }, done: {})
        }
        default: pushed { VoiceScreen(state: Self.speakers, onEvent: { _ in }) }
        }
    }

    private static func keySheet(_ service: VoiceService, masked: String?, check: VoiceListModel.KeyCheck?, typed: String = "") -> some View {
        VoiceKeySheet(service: service, masked: masked, editing: masked, check: check, checkAndSave: { _ in }, onRemove: masked == nil ? nil : {}, typed: typed)
    }

    /// Board Voice: ElevenLabs connected and in use, with Rachel.
    static var speakers: VoiceListModel.State {
        var state = VoiceListModel.State()
        state.rows = [
            VoiceListModel.row(service: .apple, ready: true, chosen: false, masked: nil, problem: nil, block: nil, hearing: false),
            VoiceListModel.row(service: .elevenlabs, ready: true, chosen: true, masked: "sk•••••9f", problem: nil, block: nil, hearing: false),
            VoiceListModel.row(service: .fish, ready: false, chosen: false, masked: nil, problem: nil, block: nil, hearing: false),
        ]
        state.speaker = .elevenlabs
        state.voiceName = "Rachel"
        return state
    }

    /// Board VoiceProviderFailed: ElevenLabs' plan doesn't include Rachel.
    static let failedEleven = VoiceListModel.row(service: .elevenlabs, ready: true, chosen: true, masked: "sk•••••9f", problem: "PlanLimit", block: nil, hearing: false)

    /// Board VoiceListening: Deepgram connected and in use.
    static var listening: VoiceListModel.State {
        var state = VoiceListModel.State()
        state.rows = [
            VoiceListModel.row(service: .apple, ready: true, chosen: false, masked: nil, problem: nil, block: nil, hearing: true),
            VoiceListModel.row(service: .deepgram, ready: true, chosen: true, masked: "4f•••••a1", problem: nil, block: nil, hearing: true),
            VoiceListModel.row(service: .soniox, ready: false, chosen: false, masked: nil, problem: nil, block: nil, hearing: true),
        ]
        state.languageName = "English (India)"
        return state
    }

    /// Boards VoicePicker, VoicePickerEleven, VoicePickerFish.
    static func picker(_ id: String) -> VoicePickerModel.State {
        typealias V = VoicePickerModel.Voice
        var state = VoicePickerModel.State()
        state.loading = false
        switch id {
        case "voice-picker-eleven":
            state.service = .elevenlabs
            state.groups = ["Conversational", "Narration", "British"]
            state.group = "Conversational"
            state.voices = [V(id: "sarah", name: "Sarah", traits: "Young woman · American · soft and clear", tag: nil),
                            V(id: "roger", name: "Roger", traits: "Man · American · easy, casual", tag: nil),
                            V(id: "charlotte", name: "Charlotte", traits: "Woman · Swedish English · warm", tag: nil),
                            V(id: "george", name: "George", traits: "Man · British · warm narrator", tag: nil),
                            V(id: "jessica", name: "Jessica", traits: "Young woman · American · expressive", tag: nil)]
            state.selected = "sarah"; state.selectedName = "Sarah"; state.playing = "roger"
        case "voice-picker-fish":
            state.service = .fish
            state.groups = ["English", "Hinglish", "Hindi"]
            state.voices = [V(id: "calm", name: "Calm Guide", traits: "Male · English · 1.2M uses", tag: String(localized: L10n.Voice.popular), tagTone: .amber),
                            V(id: "bright", name: "Bright Assistant", traits: "Female · English · 860K uses", tag: nil),
                            V(id: "mumbai", name: "Mumbai Friend", traits: "Male · Hinglish · 210K uses", tag: nil),
                            V(id: "soft", name: "Soft Narrator", traits: "Female · English · 540K uses", tag: nil),
                            V(id: "didi", name: "Desi Didi", traits: "Female · Hindi · 98K uses", tag: nil)]
            state.selected = "calm"; state.selectedName = "Calm Guide"; state.playing = "bright"
        default:
            state.groups = ["India", "US", "UK", "Hindi"]
            state.voices = [V(id: "ava", name: "Ava", traits: "Female · English (US) · Premium", tag: nil),
                            V(id: "rishi", name: "Rishi", traits: "Male · English (India) · Enhanced", tag: nil),
                            V(id: "veena", name: "Veena", traits: "Female · English (India)", tag: "Free download", tagTone: .green),
                            V(id: "lekha", name: "Lekha", traits: "Female · Hindi", tag: nil),
                            V(id: "daniel", name: "Daniel", traits: "Male · English (UK) · Enhanced", tag: nil)]
            state.selected = "ava"; state.selectedName = "Ava"; state.playing = "ava"
        }
        return state
    }
}

extension ScreenSample {
    /// The board "Talking · one conversation": a request, its card working, done or partly done, and what's next.
    static func conversation(_ status: ModeTalkTaskCard.Status) -> TalkModel.State {
        let started = Date().addingTimeInterval(-72)
        var steps = [
            ModeTalkTaskCard.Step(id: 0, text: "Opening your chat with Sam.", time: "10:03"),
            ModeTalkTaskCard.Step(id: 1, text: "Sent Sam “testing my agent”.", time: "10:04"),
            ModeTalkTaskCard.Step(id: 2, text: "Checking today in Google Calendar.", time: "10:04"),
        ]
        if status == .done { steps.append(.init(id: 3, text: "Found the DoorDash sync at 11 pm.", time: "10:05")) }
        if status == .partial { steps.append(.init(id: 3, text: "Nothing called DoorDash today in Google Calendar.", time: "10:05")) }
        let rows: [ModeTalkTaskCard.Row] = status == .partial ? [
            .init(id: 0, title: "Replied to Sam", detail: "WhatsApp · “testing my agent”", done: true),
            .init(id: 1, title: "DoorDash meeting not found", detail: "Nothing today in your Google Calendar", done: false),
        ] : [
            .init(id: 0, title: "Replied to Sam", detail: "WhatsApp · “testing my agent”", done: true),
            .init(id: 1, title: "DoorDash sync", detail: "Today 23:00–23:30 · Google Meet", done: true),
        ]
        let card = TalkModel.Card(
            taskId: "sample", conversationId: "sample", status: status, title: "Sam, then today’s DoorDash meeting",
            startedAt: started, seconds: status == .partial ? 130 : 108, steps: steps, doneParts: 1, parts: 3,
            headline: status == .partial ? "Sam has your reply; I couldn’t find the DoorDash meeting" : "Sam has your reply, and you have one DoorDash meeting today",
            rows: rows
        )
        var turns: [TalkModel.Turn] = [
            .init(id: 0, mine: false, kind: .said, text: "Good afternoon, Alex. What would you like me to take care of?"),
            .init(id: 1, mine: true, kind: .said, text: "Reply to Sam that I’m testing my agent, then check my DoorDash meeting today"),
            .init(id: 2, mine: false, kind: .task, text: "", card: card),
        ]
        if status == .done { turns.append(.init(id: 3, mine: false, kind: .next, text: "Anything else, Alex? The DoorDash sync is at 11.")) }
        if status == .partial {
            turns.append(.init(id: 3, mine: false, kind: .next, text: "Want me to look in your work calendar too, or skip it?", yes: "Yes, look there", no: "Skip it"))
        }
        return TalkModel.State(phase: status == .working ? .starting : .listening, turns: turns, hasDone: status != .working)
    }
}
extension ScreenSample {
    /// A finished task, Mo's "Anything else?", then a second task working: the newest line must be in view.
    static func longConversation() -> TalkModel.State {
        var first = conversation(.done)
        let next = conversation(.working).turns.compactMap(\.card).first.map { card in
            TalkModel.Card(taskId: "second", conversationId: "sample", status: .working, title: "Turn on Do Not Disturb until 6", startedAt: Date().addingTimeInterval(-20),
                           seconds: 0, steps: [.init(id: 0, text: "Opening Focus in Control Centre.", time: "10:07"), .init(id: 1, text: "Do Not Disturb is on; setting it to end at 6.", time: "10:07")],
                           doneParts: 0, parts: 0, headline: "", rows: [])
        }
        first.turns += [
            .init(id: 10, mine: true, kind: .said, text: "Turn on Do Not Disturb until 6"),
            .init(id: 11, mine: false, kind: .said, text: "Sure, turning on Do Not Disturb until 6."),
            .init(id: 12, mine: false, kind: .task, text: "", card: next),
        ]
        first.phase = .starting
        return first
    }
}
#endif
