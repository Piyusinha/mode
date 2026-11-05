import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI

/// Opens iOS Settings for a guide. Accessibility has no public link, so the root of Settings is the
/// fallback; Mode's own page is never the right place for these steps.
@MainActor
enum SystemSettings {
    /// Mode's own page in Settings (the microphone, notifications).
    static func openModeSettings() {
        if let own = URL(string: UIApplication.openSettingsURLString) { UIApplication.shared.open(own) }
    }

    static func open() {
        let root = URL(string: "App-prefs:")
        if let root, UIApplication.shared.canOpenURL(root) {
            UIApplication.shared.open(root)
        } else if let own = URL(string: UIApplication.openSettingsURLString) {
            UIApplication.shared.open(own)
        }
    }
}

/// Start Mo → Unlock by voice: the guide, "I've set it up", and the check (lock, say the phrase, unlocked).
@MainActor
@Observable
final class UnlockVoiceModel {
    /// What the bottom of the page shows.
    enum Check: Equatable {
        /// Not set up: Open Settings and I've set it up.
        case notSetUp
        /// Set up and not checking: it's on, check again or turn off.
        case on
        case waitingForLock(secondsLeft: Int)
        case waitingForUnlock(secondsLeft: Int)
        case works(seconds: Int)
        case neverLocked
        case stayedLocked
    }

    struct State: Equatable {
        var check: Check = .notSetUp
    }

    enum Event { case openSettings, setUp, check, cancelCheck, turnOff }

    private(set) var state = State()
    private let presenter: UnlockVoicePresenter
    private var updates: Updates?

    init(screens: Screens) {
        presenter = screens.unlockVoice()
        updates = observe(presenter.state) { [weak self] s in self?.state = State(check: Self.check(s)) }
    }

    func send(_ event: Event) {
        switch event {
        case .openSettings: SystemSettings.open()
        case .setUp: presenter.setUp()
        case .check: presenter.startTest()
        case .cancelCheck: presenter.cancelTest()
        case .turnOff: presenter.turnOff()
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    static func check(_ s: UnlockVoiceUiState) -> Check {
        switch onEnum(of: s.test) {
        case .idle: s.setUp ? .on : .notSetUp
        case let .waitingForLock(w): .waitingForLock(secondsLeft: Int(w.secondsLeft))
        case let .waitingForUnlock(w): .waitingForUnlock(secondsLeft: Int(w.secondsLeft))
        case let .works(w): .works(seconds: Int(w.seconds))
        case .neverLocked: .neverLocked
        case .stayedLocked: .stayedLocked
        }
    }
}

/// Unlock by voice, as on its board, with the check under the guide.
struct UnlockVoiceScreen: View {
    let state: UnlockVoiceModel.State
    let onEvent: (UnlockVoiceModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.groupGap) {
                ModePageHeading(L10n.UnlockVoice.title, subtitle: L10n.UnlockVoice.subtitle)
                ModeLabeledGroup(L10n.UnlockVoice.beforeYouStart) {
                    ModeWarningNotes([
                        .init(.warning, L10n.UnlockVoice.anyone),
                        .init(.microphone, L10n.UnlockVoice.listens),
                        .init(.lockFilled, L10n.UnlockVoice.never),
                    ])
                }
                ModeLabeledGroup(L10n.UnlockVoice.onThisIphone) {
                    ModeStepList([
                        ModeStep(1, title: L10n.UnlockVoice.step1, note: L10n.UnlockVoice.step1Note),
                        ModeStep(2, title: L10n.UnlockVoice.step2, note: L10n.UnlockVoice.step2Note),
                        ModeStep(3, title: L10n.UnlockVoice.step3, note: L10n.UnlockVoice.step3Note),
                        ModeStep(4, title: L10n.UnlockVoice.step4, note: L10n.UnlockVoice.step4Note),
                        ModeStep(5, title: L10n.UnlockVoice.step5, note: L10n.UnlockVoice.step5Note),
                        ModeStep(6, title: L10n.UnlockVoice.step6, note: L10n.UnlockVoice.step6Note),
                    ])
                }
                ModeLabeledGroup(L10n.UnlockVoice.whereToTap) {
                    HStack(spacing: ModeSpacing.l) {
                        ModeKeypadMap(title: L10n.UnlockVoice.enterPasscode, accessibilityLabel: L10n.UnlockVoice.keypadLabel)
                        VStack(alignment: .leading, spacing: ModeSpacing.s + ModeSpacing.xxs) {
                            ModeLetteredHint("a", L10n.UnlockVoice.hintSwipe)
                            ModeLetteredHint("b", L10n.UnlockVoice.hintTap)
                            Text(L10n.UnlockVoice.hintSlower).modeText(.footnote, color: ModeColor.textSecondary)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(ModeSpacing.l)
                    .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.group))
                }
                checkSection
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.top, ModeSpacing.pageTop)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .modeScreen()
        .modePageChrome(L10n.UnlockVoice.title)
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) { ModePillTag(L10n.UnlockVoice.optional) }
                .sharedBackgroundVisibility(.hidden)
        }
        .animation(ModeMotion.standard, value: state.check)
    }

    @ViewBuilder private var checkSection: some View {
        switch state.check {
        case .notSetUp:
            VStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                ModeButton(L10n.UnlockVoice.openSettings, role: .main) { onEvent(.openSettings) }
                ModeButton(L10n.UnlockVoice.setUp, role: .otherOnPage) { onEvent(.setUp) }
            }
        case .on:
            ModeLabeledGroup(L10n.UnlockVoice.check) {
                VStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                    ModeStatusCard(mood: .done, title: String(localized: L10n.UnlockVoice.isOn),
                                   detail: String(localized: L10n.UnlockVoice.isOnDetail), tone: .success)
                    ModeButton(L10n.UnlockVoice.checkAgain, role: .main) { onEvent(.check) }
                    ModeButton(L10n.UnlockVoice.turnOff, role: .otherOnPage) { onEvent(.turnOff) }
                    ModeFootnote(L10n.UnlockVoice.turnOffNote)
                }
            }
        case let .waitingForLock(left):
            checking(mood: .listening, title: String(localized: L10n.UnlockVoice.lockNow),
                     detail: String(localized: L10n.UnlockVoice.lockNowDetail), left: left)
        case let .waitingForUnlock(left):
            checking(mood: .listening, title: String(localized: L10n.UnlockVoice.sayItNow),
                     detail: String(localized: L10n.UnlockVoice.sayItNowDetail), left: left)
        case let .works(seconds):
            ModeLabeledGroup(L10n.UnlockVoice.check) {
                VStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                    ModeStatusCard(mood: .done, title: L10n.UnlockVoice.works(seconds),
                                   detail: String(localized: L10n.UnlockVoice.worksDetail), tone: .success)
                    ModeButton(L10n.UnlockVoice.turnOff, role: .otherOnPage) { onEvent(.turnOff) }
                }
            }
        case .neverLocked:
            failed(String(localized: L10n.UnlockVoice.neverLocked), String(localized: L10n.UnlockVoice.neverLockedDetail))
        case .stayedLocked:
            failed(String(localized: L10n.UnlockVoice.stayedLocked), String(localized: L10n.UnlockVoice.stayedLockedDetail))
        }
    }

    private func checking(mood: ModeMo.Mood, title: String, detail: String, left: Int) -> some View {
        ModeLabeledGroup(L10n.UnlockVoice.check) {
            VStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                ModeStatusCard(mood: mood, title: title, detail: detail, tone: .neutral, trailing: L10n.UnlockVoice.secondsLeft(left))
                ModeButton(L10n.Common.cancel, role: .otherOnPage) { onEvent(.cancelCheck) }
            }
        }
    }

    private func failed(_ title: String, _ detail: String) -> some View {
        ModeLabeledGroup(L10n.UnlockVoice.check) {
            VStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                ModeStatusCard(mood: .stuck, title: title, detail: detail, tone: .warning)
                ModeButton(L10n.UnlockVoice.tryAgain, role: .main) { onEvent(.check) }
                ModeButton(L10n.UnlockVoice.openSettings, role: .otherOnPage) { onEvent(.openSettings) }
            }
        }
    }
}

struct UnlockVoiceRoute: View {
    @State private var model: UnlockVoiceModel

    init(app: AppModel) {
        _model = State(initialValue: UnlockVoiceModel(screens: app.screens))
    }

    var body: some View {
        UnlockVoiceScreen(state: model.state, onEvent: model.send)
    }
}
