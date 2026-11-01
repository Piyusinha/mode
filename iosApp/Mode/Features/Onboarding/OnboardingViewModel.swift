import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// First-run setup: maps each step to what it shows and which buttons it has.
@MainActor
@Observable
final class OnboardingViewModel {
    enum Step: Equatable { case welcome, name, key, model, permissions, testRun, testResult, diagnose }

    struct Button: Equatable {
        let title: LocalizedStringResource
        let event: Event
        let enabled: Bool
        let role: ModeButton.Role

        static func == (a: Button, b: Button) -> Bool { a.title.key == b.title.key && a.enabled == b.enabled }
    }

    struct State: Equatable {
        var step: Step = .welcome
        var icon: ModeIcon = .sparkle
        var title: LocalizedStringResource = L10n.Setup.welcomeTitle
        var message: LocalizedStringResource = L10n.Setup.welcomeMessage
        var models: [SettingsViewModel.Model] = []
        var keyHint: String?
        var busy = false
        var detail: String?
        var log: [String] = []
        var primary: Button?
        var secondary: Button?
        var canGoBack = false

        static func == (a: State, b: State) -> Bool {
            a.step == b.step && a.models == b.models && a.keyHint == b.keyHint && a.busy == b.busy && a.detail == b.detail
                && a.log == b.log && a.primary == b.primary && a.secondary == b.secondary
        }
    }

    enum Event: Equatable {
        case next, back, chooseModel(String), connect, runTest, worked(Bool), diagnose, finish, getKey
    }

    private(set) var state = State()
    var name = "" { didSet { presenter.setName(name: name) } }
    var key = "" { didSet { presenter.setKey(key: key) } }
    /// Where to get an OpenRouter key.
    let keyURL = URL(string: "https://openrouter.ai/keys")!
    /// A link the screen should open now; cleared once opened.
    var openURL: URL?

    func urlOpened() {
        openURL = nil
    }
    private let presenter: OnboardingPresenter
    private let finished: () -> Void
    private var updates: Updates?

    init(screens: Screens, finished: @escaping () -> Void) {
        presenter = screens.onboarding()
        self.finished = finished
        name = presenter.state.value.name
        updates = observe(presenter.state) { [weak self] s in self?.apply(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .next: presenter.next()
        case .back: presenter.back()
        case let .chooseModel(id): presenter.chooseModel(id: id)
        case .connect: presenter.connect()
        case .runTest: presenter.runTest()
        case let .worked(yes): presenter.confirmTest(worked: yes)
        case .diagnose: presenter.diagnose()
        case .finish: presenter.finish()
        case .getKey: openURL = keyURL
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private func apply(_ s: OnboardingUiState) {
        if s.step == .finished {
            finished()
            return
        }
        state = Self.map(s)
    }

    private static func map(_ s: OnboardingUiState) -> State {
        var st = State()
        st.canGoBack = s.step != .welcome && s.step != .testResult
        let next = Button(title: L10n.Common.continue, event: .next, enabled: s.canContinue, role: .primary)
        let skip = Button(title: L10n.Setup.skip, event: .finish, enabled: true, role: .plain)
        switch s.step {
        case .welcome:
            (st.step, st.icon, st.title, st.message) = (.welcome, .sparkle, L10n.Setup.welcomeTitle, L10n.Setup.welcomeMessage)
            st.primary = Button(title: L10n.Setup.getStarted, event: .next, enabled: true, role: .primary)
        case .name:
            (st.step, st.icon, st.title, st.message) = (.name, .person, L10n.Setup.nameTitle, L10n.Setup.nameMessage)
            st.primary = next
        case .key:
            (st.step, st.icon, st.title, st.message) = (.key, .key, L10n.Setup.keyTitle, L10n.Setup.keyMessage)
            st.keyHint = s.key.isEmpty || s.keyLooksRight ? nil : String(localized: L10n.Setup.keyLooksWrong)
            st.primary = next
            st.secondary = Button(title: L10n.Setup.getKey, event: .getKey, enabled: true, role: .plain)
        case .model:
            (st.step, st.icon, st.title, st.message) = (.model, .model, L10n.Setup.modelTitle, L10n.Setup.modelMessage)
            st.models = s.models.map {
                SettingsViewModel.Model(id: $0.id, name: $0.name, subtitle: [$0.note.isEmpty ? nil : $0.note, L10n.Settings.perTask(Format.money($0.costPerTaskUsd))].compactMap { $0 }.joined(separator: " · "),
                                        isMain: $0.isMain, isStuck: false)
            }
            st.primary = next
        case .permissions:
            (st.step, st.icon, st.title, st.message) = (.permissions, .shield, L10n.Setup.permissionsTitle, L10n.Setup.permissionsMessage)
            st.busy = s.connecting
            st.detail = s.connectError
            st.primary = Button(title: L10n.Setup.allow, event: .connect, enabled: !s.connecting, role: .primary)
            st.secondary = skip
        case .testRun:
            (st.step, st.icon, st.title, st.message) = (.testRun, .wifi, L10n.Setup.testTitle, L10n.Setup.testMessage)
            st.busy = s.testRunning
            st.primary = Button(title: L10n.Setup.runTest, event: .runTest, enabled: !s.testRunning, role: .primary)
            st.secondary = skip
        case .testResult:
            (st.step, st.icon, st.title, st.message) = (.testResult, .done, L10n.Setup.resultTitle, L10n.Setup.resultMessage)
            st.detail = s.testSummary
            st.primary = Button(title: L10n.Setup.itWorked, event: .worked(true), enabled: true, role: .primary)
            st.secondary = Button(title: L10n.Setup.itDidnt, event: .worked(false), enabled: true, role: .secondary)
        case .diagnose:
            (st.step, st.icon, st.title, st.message) = (.diagnose, .wrench, L10n.Setup.diagnoseTitle, L10n.Setup.diagnoseMessage)
            st.busy = s.diagnoseBusy
            st.detail = s.diagnoseStatus
            st.log = s.diagnoseLog
            st.primary = Button(title: L10n.Setup.checkAgain, event: .diagnose, enabled: !s.diagnoseBusy, role: .primary)
            st.secondary = Button(title: L10n.Setup.continueAnyway, event: .finish, enabled: true, role: .plain)
        case .finished:
            break
        }
        return st
    }
}
