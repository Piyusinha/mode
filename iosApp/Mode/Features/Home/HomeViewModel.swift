import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// Home: maps the presenter's state to what the Home board shows, and handles its events.
@MainActor
@Observable
final class HomeViewModel {
    struct Suggestion: Identifiable, Equatable {
        var id: String { title }
        let title: String
        let subtitle: String
        let icon: ModeIcon
    }

    struct RecentRow: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
        let leading: ModeRowLeading
    }

    /// The "Did you know?" carousel: every open tip, the one showing, and whether it can be closed.
    struct Tips: Equatable {
        let tips: [ModeTipBanner.Tip]
        let index: Int
        let closable: Bool
    }

    struct State: Equatable {
        var greeting = ""
        var suggestions: [Suggestion] = []
        var recent: [RecentRow] = []
        var needsKey = false
        var tips: Tips?
        /// Easy tasks to try, as pills.
        var tryThese: [String] = []
        var isEmpty: Bool { suggestions.isEmpty && recent.isEmpty }
    }

    enum Event {
        case suggestion(String)
        case openConversation(String)
        case addKey
        case openSettings
        case pauseToggle
        case stop
        case openRun
        case tipAction(Int)
        case pickTip(Int)
        case closeTips
        case undoCloseTips
    }

    private(set) var state = State()
    /// "Tips hidden · Undo" for a few seconds after closing them.
    private(set) var showingUndo = false
    private var undoTimer: _Concurrency.Task<Void, Never>?
    /// Moves the carousel on by itself; waits longer after the user swipes.
    private var autoScroll: _Concurrency.Task<Void, Never>?
    private var tips: [SetupTip] = []
    private var showingTip: SetupTip?
    private let presenter: HomePresenter
    private let navigate: (Route) -> Void
    private let run: RunStatusViewModel?
    private let openLive: () -> Void
    private var updates: Updates?

    init(screens: Screens, run: RunStatusViewModel?, navigate: @escaping (Route) -> Void, openLive: @escaping () -> Void) {
        presenter = screens.home()
        self.run = run
        self.navigate = navigate
        self.openLive = openLive
        updates = observe(presenter.state) { [weak self] s in
            self?.tips = s.tips?.tips ?? []
            self?.showingTip = s.tips?.showing
            self?.state = Self.map(s)
        }
        restartAutoScroll(after: Self.autoScrollSeconds)
    }

    func send(_ event: Event) {
        switch event {
        case let .suggestion(text):
            if let conversation = presenter.ask(text: text, private: false) { navigate(.conversation(id: conversation)) }
        case let .openConversation(id): navigate(.conversation(id: id))
        case .addKey, .openSettings: navigate(.settings)
        case .pauseToggle: run?.togglePause()
        case .stop: run?.stop()
        case .openRun: openLive()
        case let .tipAction(i):
            switch tips.indices.contains(i) ? tips[i] : showingTip {
            case .voice: navigate(.voice)
            case .listening: navigate(.listening)
            case .access: navigate(.access)
            case .startWithoutOpening: navigate(.unlockVoice)
            case .name: navigate(.settings)
            case nil: break
            }
        case let .pickTip(i):
            guard tips.indices.contains(i) else { return }
            presenter.showTip(tip: tips[i])
            restartAutoScroll(after: Self.afterSwipeSeconds)
        case .closeTips:
            presenter.hideTips()
            showingUndo = true
            undoTimer?.cancel()
            undoTimer = _Concurrency.Task { [weak self] in
                try? await _Concurrency.Task.sleep(for: .seconds(Self.undoSeconds))
                guard !_Concurrency.Task.isCancelled else { return }
                self?.showingUndo = false
            }
        case .undoCloseTips:
            undoTimer?.cancel()
            showingUndo = false
            presenter.undoHideTips()
        }
    }

    /// How long Undo stays after closing the tips.
    static let undoSeconds = 5
    /// The carousel moves on every few seconds, and waits a little longer after a swipe.
    static let autoScrollSeconds = 6
    static let afterSwipeSeconds = 12

    private func restartAutoScroll(after seconds: Int) {
        autoScroll?.cancel()
        autoScroll = _Concurrency.Task { [weak self] in
            var wait = seconds
            while !_Concurrency.Task.isCancelled {
                try? await _Concurrency.Task.sleep(for: .seconds(wait))
                guard !_Concurrency.Task.isCancelled, let self else { return }
                self.nextTip()
                wait = Self.autoScrollSeconds
            }
        }
    }

    /// The next open tip, back to the first after the last.
    private func nextTip() {
        guard tips.count > 1, let current = showingTip, let i = tips.firstIndex(of: current) else { return }
        presenter.showTip(tip: tips[(i + 1) % tips.count])
    }

    isolated deinit {
        undoTimer?.cancel()
        autoScroll?.cancel()
        updates?.cancel()
        presenter.close()
    }

    static func map(_ s: HomeUiState) -> State {
        State(
            greeting: L10n.Home.greeting(partOfDay(s.partOfDay), name: s.name),
            suggestions: s.suggestions.map { suggestion in
                switch onEnum(of: suggestion.reason) {
                case let .often(o): Suggestion(title: suggestion.text, subtitle: L10n.Home.askedOften(Int(o.times)), icon: .repeatTask)
                case let .learned(l): Suggestion(title: suggestion.text, subtitle: L10n.Home.learned(app: l.app), icon: .skill)
                }
            },
            recent: s.recent.map { c in
                RecentRow(id: c.conversationId, title: c.title, subtitle: when(c), leading: leading(c.badge))
            },
            needsKey: s.needsKey,
            tips: s.tips.map { t in
                Tips(tips: t.tips.map(tip), index: t.tips.firstIndex(of: t.showing) ?? 0, closable: t.closable)
            },
            tryThese: s.tryThese
        )
    }

    /// Each tip's words, Mo's face, its badge and its colour (as on the board).
    nonisolated static func tip(_ tip: SetupTip) -> ModeTipBanner.Tip {
        switch tip {
        case .voice:
            ModeTipBanner.Tip(title: L10n.Tips.voiceTitle, message: L10n.Tips.voiceMessage, action: L10n.Tips.voiceAction, mood: .listening, icon: .speaker, tone: .coral)
        case .listening:
            ModeTipBanner.Tip(title: L10n.Tips.listeningTitle, message: L10n.Tips.listeningMessage, action: L10n.Tips.listeningAction, mood: .idle, icon: .microphone, tone: .blue)
        case .access:
            ModeTipBanner.Tip(title: L10n.Tips.accessTitle, message: L10n.Tips.accessMessage, action: L10n.Tips.accessAction, mood: .done, icon: .shield, tone: .green)
        case .startWithoutOpening:
            ModeTipBanner.Tip(title: L10n.Tips.startTitle, message: L10n.Tips.startMessage, action: L10n.Tips.startAction, mood: .working, icon: .siri, tone: .purple)
        case .name:
            ModeTipBanner.Tip(title: L10n.Tips.nameTitle, message: L10n.Tips.nameMessage, action: L10n.Tips.nameAction, mood: .yourTurn, icon: .profile, tone: .orange)
        }
    }

    /// "Today 8:13 · 62 s", "Yesterday · 37 s".
    private static func when(_ c: RecentConversation) -> String {
        let day = Format.day(c.day)
        var time: String?
        if case .today = onEnum(of: c.day) { time = Format.timeOfDay(epochMs: c.atMs) }
        let duration = c.durationSeconds.map { Format.seconds(Double(truncating: $0)) }
        return [[day, time].compactMap { $0 }.joined(separator: " "), duration].compactMap { $0 }.joined(separator: " · ")
    }

    private static func leading(_ badge: TaskBadge?) -> ModeRowLeading {
        switch badge {
        case .done: .mo(.done)
        case .failed, .gaveUp: .mo(.stuck)
        case .stopped, .notStarted: .mo(.sleep)
        case nil: .mo(.working)
        }
    }

    static func partOfDay(_ p: PartOfDay) -> L10n.PartOfDayName {
        switch p {
        case .morning: .morning
        case .afternoon: .afternoon
        case .evening: .evening
        case .night: .night
        }
    }
}
