import Foundation
import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI
import UIKit

/// First-run setup: turns the presenter's state into what each board shows, and handles its events.
@MainActor
@Observable
final class OnboardingViewModel {
    enum Step: Equatable { case welcome, name, key, model, permissions, test, result, diagnose }

    struct Choice: Identifiable, Equatable {
        let id: String
        let name: String
        let tag: Tag?
        let detail: String
        let leading: ModeRowLeading
        let selected: Bool

        static func == (a: Choice, b: Choice) -> Bool { a.id == b.id && a.selected == b.selected && a.detail == b.detail && a.name == b.name }
    }

    struct Tag: Equatable {
        let text: String
        let background: Color
        let foreground: Color
    }

    struct PermissionRow: Identifiable, Equatable {
        let id: PermissionKind
        let title: LocalizedStringResource
        let why: LocalizedStringResource
        let leading: ModeRowLeading
        let action: ModeStatusAction

        static func == (a: PermissionRow, b: PermissionRow) -> Bool { a.id == b.id && a.action == b.action }
    }

    struct DidRow: Identifiable, Equatable {
        let id: Int
        let title: LocalizedStringResource
        let leading: ModeRowLeading
        let detail: String?

        static func == (a: DidRow, b: DidRow) -> Bool { a.id == b.id && a.leading == b.leading && a.detail == b.detail }
    }

    struct CheckRow: Identifiable, Equatable {
        let id: Int
        let title: LocalizedStringResource
        let leading: ModeRowLeading
        let detail: String?
        let trailing: String?
        /// Failed checks stand out, with their explanation under them.
        var emphasis: ModeRowEmphasis { detail == nil ? .standard : .prominent }

        static func == (a: CheckRow, b: CheckRow) -> Bool { a.id == b.id && a.leading == b.leading && a.detail == b.detail && a.trailing == b.trailing }
    }

    struct State: Equatable {
        var step: Step = .welcome
        var stepNumber: Int?
        var stepTotal = 5
        var stepLabel = ""
        var paired = true
        // Name
        var initials = ""
        var cardName: String?
        // Key
        var connected = false
        var signingIn = false
        var signInError: String?
        var hasSavedKey = false
        var keyHint: String?
        // Model
        var models: [Choice] = []
        var stuck: Choice?
        var stuckChoices: [Choice] = []
        var results: [Choice] = []
        // Permissions
        var permissions: [PermissionRow] = []
        // Test and result
        var testPhases: [ModeTestStrip.Phase] = [.pending, .pending, .pending]
        var testRunning = false
        var testPassed = false
        var did: [DidRow] = []
        var shot: UIImage?
        var summary: String?
        // Diagnose
        var checks: [CheckRow] = []
        var checking = false
        var canContinue = true

        static func == (a: State, b: State) -> Bool {
            a.step == b.step && a.stepNumber == b.stepNumber && a.paired == b.paired && a.initials == b.initials && a.cardName == b.cardName
                && a.connected == b.connected && a.signingIn == b.signingIn && a.signInError == b.signInError && a.hasSavedKey == b.hasSavedKey
                && a.keyHint == b.keyHint && a.models == b.models && a.stuck == b.stuck && a.stuckChoices == b.stuckChoices && a.results == b.results && a.permissions == b.permissions
                && a.testPhases == b.testPhases && a.testRunning == b.testRunning && a.testPassed == b.testPassed && a.did == b.did
                && (a.shot == nil) == (b.shot == nil) && a.summary == b.summary && a.checks == b.checks && a.checking == b.checking
                && a.canContinue == b.canContinue
        }
    }

    enum Event: Equatable {
        case next, back, chooseCard, connect, paste, chooseModel(String), openStuck, chooseStuck(String?), openSearch, allow(PermissionKind)
        case runTest, worked(Bool), checkAgain, sendReport, finish, openCode
    }

    private(set) var state = State()
    var callName = "" { didSet { if callName != oldValue { presenter.setCallName(name: callName) } } }
    var key = "" { didSet { if key != oldValue { presenter.setKey(key: key) } } }
    var query = "" { didSet { if query != oldValue { presenter.search(text: query) } } }
    var showingContactPicker = false
    var showingSearch = false
    var showingStuck = false
    /// The OpenRouter page to sign in on; the screen opens it in a web sign-in sheet.
    var signInURL: URL?
    /// A link the screen should open, or the report to share.
    var openURL: URL?
    var report: String?

    /// Where the code lives.
    let codeURL = URL(string: "https://github.com/Piyusinha/mode")!
    /// The scheme OpenRouter sends the user back to.
    let callbackScheme = "mode"

    private let presenter: OnboardingPresenter
    private let finished: () -> Void
    private var updates: Updates?

    init(screens: Screens, finished: @escaping () -> Void) {
        presenter = screens.onboarding()
        self.finished = finished
        callName = presenter.state.value.callName
        updates = observe(presenter.state) { [weak self] s in self?.apply(s) }
        #if DEBUG && targetEnvironment(simulator)
        jumpForScreenshots()
        #endif
    }

    #if DEBUG && targetEnvironment(simulator)
    /// Simulator only: `MODE_SETUP_STEP=model` walks setup to that step, so each
    /// board can be compared with the app without tapping.
    private func jumpForScreenshots() {
        guard let target = ProcessInfo.processInfo.environment["MODE_SETUP_STEP"] else { return }
        let order = ["welcome", "name", "key", "model", "permissions", "test", "result", "diagnose"]
        guard let index = order.firstIndex(of: target) else { return }
        if index >= 1 { presenter.next() }
        if index >= 2 { cardPicked(given: "Piyush", family: "Sinha"); presenter.next() }
        if index >= 3 { key = "sk-or-v1-simulator-preview-0000"; presenter.next() }
        if index >= 4 { presenter.next() }
        if index >= 5 { presenter.next() }
        if index >= 6 { presenter.next() }
        if index >= 7 {
            _Concurrency.Task { @MainActor [weak self] in
                while self?.state.step != .result { try? await _Concurrency.Task.sleep(for: .milliseconds(200)) }
                self?.presenter.confirmTest(worked: false)
            }
        }
    }
    #endif

    func send(_ event: Event) {
        switch event {
        case .next: presenter.next()
        case .back: presenter.back()
        case .chooseCard: showingContactPicker = true
        case .connect: signInURL = URL(string: presenter.startSignIn(callbackUrl: OnboardingPresenter.companion.SIGN_IN_CALLBACK))
        case .paste: if let text = UIPasteboard.general.string { key = text.trimmingCharacters(in: .whitespacesAndNewlines) }
        case let .chooseModel(id): presenter.chooseModel(id: id)
        case .openStuck: showingStuck = true
        case let .chooseStuck(id):
            presenter.chooseStuck(id: id)
            showingStuck = false
        case .openSearch: showingSearch = true
        case let .allow(kind): presenter.allow(kind: kind)
        case .runTest: presenter.runTest()
        case let .worked(yes): presenter.confirmTest(worked: yes)
        case .checkAgain: presenter.runChecks()
        case .sendReport: report = presenter.report()
        case .finish: presenter.finish()
        case .openCode: openURL = codeURL
        }
    }

    /// The contact picker returned a card.
    func cardPicked(given: String, family: String) {
        presenter.setNameFromCard(given: given, family: family)
        callName = presenter.state.value.callName
    }

    /// The web sign-in sheet finished: with the callback, or an error (including cancelling).
    func signInFinished(callback: URL?, error: Error?) {
        signInURL = nil
        presenter.finishSignIn(callbackUrl: callback?.absoluteString, error: error?.localizedDescription)
    }

    func urlOpened() { openURL = nil }
    func reportShared() { report = nil }

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
        st.step = step(s.step)
        st.stepNumber = s.step.number.map { Int(truncating: $0) }
        st.stepTotal = Int(s.stepCount)
        st.stepLabel = st.stepNumber.map { L10n.Setup.step($0, of: st.stepTotal) } ?? ""
        st.paired = s.paired
        st.cardName = s.cardName
        st.initials = (s.cardName ?? s.callName).split(separator: " ").prefix(2).compactMap(\.first).map(String.init).joined().uppercased()
        st.connected = s.connected
        st.signingIn = s.signingIn
        st.signInError = s.signInError
        st.hasSavedKey = s.hasSavedKey && !s.connected
        st.keyHint = s.key.isEmpty || s.keyLooksRight ? nil : String(localized: L10n.Setup.keyLooksWrong)
        st.models = s.models.map { choice($0, selected: $0.isMain) }
        st.stuck = s.stuckModel.map { m in
            Choice(id: m.id, name: m.name, tag: nil,
                   detail: m.isStuck ? [tagText(m.tag), String(localized: L10n.Setup.stuckDetail)].compactMap { $0 }.joined(separator: " · ") : String(localized: L10n.Setup.stuckOff),
                   leading: maker(m.id), selected: m.isStuck)
        }
        st.results = s.results.map { choice($0, selected: $0.isMain) }
        st.stuckChoices = s.stuckChoices.map { choice($0, selected: $0.isStuck) }
        st.permissions = s.permissions.map(permission)
        st.testRunning = s.testRunning
        let items = s.testItems
        st.testPhases = [TestItemKind.openSettings, .tapWifi, .comeBack].map { kind in
            switch items.first(where: { $0.kind == kind })?.state {
            case .done: .done
            case .running: .running
            case .failed: .failed
            default: .pending
            }
        }
        st.testPassed = items.allSatisfy { $0.state == .done }
        st.did = items.enumerated().map { index, item in
            DidRow(id: index, title: didTitle(item.kind),
                   leading: item.state == .done ? .check : item.state == .failed ? .failure : item.state == .running ? .progress : .pending,
                   detail: item.kind == .connected ? s.connectedMs.map { Format.seconds(Double(truncating: $0) / 1000) } : nil)
        }
        st.shot = s.testShot.flatMap { UIImage(data: $0.toData()) }
        st.summary = s.testSummary
        st.checks = s.checks.enumerated().map { index, check in checkRow(index, check) }
        st.checking = s.checking
        st.canContinue = s.canContinue
        return st
    }

    private static func step(_ s: OnboardingStep) -> Step {
        switch s {
        case .welcome: .welcome
        case .name: .name
        case .key: .key
        case .model: .model
        case .permissions: .permissions
        case .test: .test
        case .result: .result
        case .diagnose, .finished: .diagnose
        }
    }

    private static func choice(_ m: ModelChoice, selected: Bool) -> Choice {
        Choice(id: m.id, name: m.name, tag: tag(m.tag), detail: [m.note.isEmpty ? nil : m.note, L10n.Setup.perTask(Format.money(m.costPerTaskUsd))].compactMap { $0 }.joined(separator: " · "),
               leading: maker(m.id), selected: selected)
    }

    private static func tagText(_ tag: ModelTag?) -> String? {
        switch tag {
        case .recommended: String(localized: L10n.Settings.recommended)
        case .cheapest: String(localized: L10n.Settings.cheapest)
        case .strongest: String(localized: L10n.Settings.strongest)
        default: nil
        }
    }

    private static func tag(_ tag: ModelTag?) -> Tag? {
        guard let text = tagText(tag) else { return nil }
        switch tag {
        case .cheapest: return Tag(text: text, background: ModeColor.tagGreen, foreground: ModeColor.tagGreenText)
        default: return Tag(text: text, background: ModeColor.tintSoft, foreground: ModeColor.tint)
        }
    }

    /// A tile with the model maker's first letter: orange for Anthropic, violet for most, grey for the rest.
    private static func maker(_ id: String) -> ModeRowLeading {
        let maker = id.split(separator: "/").first.map(String.init) ?? id
        let letter = String(maker.prefix(1)).uppercased()
        switch maker {
        case "anthropic": return .letterTile(letter, background: ModeColor.tileOrange, foreground: ModeColor.tileOrangeText)
        case "openai": return .letterTile(letter, background: ModeColor.tileGray, foreground: ModeColor.textPrimary)
        default: return .letterTile(letter, background: ModeColor.tintSoft, foreground: ModeColor.tint)
        }
    }

    private static func permission(_ p: ModeKit.PermissionRow) -> PermissionRow {
        let (title, why, leading): (LocalizedStringResource, LocalizedStringResource, ModeRowLeading) = switch p.kind {
        case .vpn: (L10n.Setup.vpn, L10n.Setup.vpnWhy, .iconTile(.vpn, background: ModeColor.tintSoft, foreground: ModeColor.tint))
        case .notifications: (L10n.Setup.notifications, L10n.Setup.notificationsWhy, .iconTile(.bell, background: ModeColor.tileRed, foreground: ModeColor.danger))
        case .siri: (L10n.Setup.siri, L10n.Setup.siriWhy, .iconTile(.siriWave, background: ModeColor.tileOrange, foreground: ModeColor.tileOrangeText))
        case .microphone: (L10n.Setup.microphone, L10n.Setup.microphoneWhy, .iconTile(.microphone, background: ModeColor.tileGray, foreground: ModeColor.textPrimary))
        }
        let action: ModeStatusAction = switch p.status {
        case .allowed: .done(L10n.Setup.allowed)
        case .denied: .note(String(localized: L10n.Setup.denied))
        case .unavailable: .note(String(localized: L10n.Setup.unavailable))
        case .notAsked: p.later ? .note(String(localized: L10n.Setup.later)) : p.optional ? .quiet(L10n.Setup.allow) : .prominent(L10n.Setup.allow)
        }
        return PermissionRow(id: p.kind, title: title, why: why, leading: leading, action: action)
    }

    private static func didTitle(_ kind: TestItemKind) -> LocalizedStringResource {
        switch kind {
        case .connected: L10n.Setup.didConnect
        case .openSettings: L10n.Setup.didOpenSettings
        case .tapWifi: L10n.Setup.didTapWifi
        case .comeBack: L10n.Setup.didComeBack
        }
    }

    private static func checkRow(_ index: Int, _ check: PhoneCheckResult) -> CheckRow {
        let title: LocalizedStringResource = switch check.id {
        case .pairingFile: L10n.Setup.checkPairingFile
        case .vpn: L10n.Setup.checkVpn
        case .reach: L10n.Setup.checkReach
        case .paired: L10n.Setup.checkPaired
        case .developerMode: L10n.Setup.checkDeveloperMode
        case .diskImage: L10n.Setup.checkDiskImage
        case .screen: L10n.Setup.checkScreen
        case .hands: L10n.Setup.checkHands
        }
        switch onEnum(of: check.state) {
        case .notChecked: return CheckRow(id: index, title: title, leading: .pending, detail: nil, trailing: String(localized: L10n.Setup.notChecked))
        case .running: return CheckRow(id: index, title: title, leading: .progress, detail: nil, trailing: nil)
        case let .passed(p): return CheckRow(id: index, title: title, leading: .check, detail: nil, trailing: p.ms.map { Format.seconds(Double(truncating: $0) / 1000) })
        case let .failed(f): return CheckRow(id: index, title: title, leading: .failure, detail: [f.reason, f.fix].compactMap { $0 }.joined(separator: " "), trailing: nil)
        }
    }
}

extension KotlinByteArray {
    /// The bytes as Data (screenshots from the shared code).
    func toData() -> Data {
        let count = Int(size)
        var bytes = [UInt8](repeating: 0, count: count)
        for i in 0..<count { bytes[i] = UInt8(bitPattern: get(index: Int32(i))) }
        return Data(bytes)
    }
}
