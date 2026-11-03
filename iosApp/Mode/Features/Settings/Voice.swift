import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI
import UIKit

// Voice (boards "Settings · Voice section", "Mode's voice", "Choose a voice", "Settings · Listening", "Connect ElevenLabs",
// "Start Mode · listening / Mode answers"): who speaks and listens, the voice, keys, and the listening sheet.

/// A speech or hearing service, as the screens name and draw it.
enum VoiceService: String, CaseIterable, Identifiable {
    case apple, fish, elevenlabs, soniox, deepgram
    var id: String { rawValue }

    init?(id: String?) { guard let id, let s = VoiceService(rawValue: id) else { return nil }; self = s }

    var title: String {
        switch self {
        case .apple: String(localized: L10n.Voice.apple)
        case .fish: String(localized: L10n.Voice.fish)
        case .elevenlabs: String(localized: L10n.Voice.eleven)
        case .soniox: String(localized: L10n.Voice.soniox)
        case .deepgram: String(localized: L10n.Voice.deepgram)
        }
    }

    var logo: ModeLogo {
        switch self {
        case .apple: .apple
        case .fish: .fishAudio
        case .elevenlabs: .elevenLabs
        case .soniox: .soniox
        case .deepgram: .deepgram
        }
    }

    /// The brand mark on a black (light) or white (dark) tile.
    var tile: ModeRowLeading { .logoTile(logo, background: ModeColor.textPrimary, foreground: ModeColor.page) }

    /// What it costs, in one line.
    func plan(hearing: Bool) -> String {
        switch self {
        case .apple: String(localized: hearing ? L10n.Voice.appleHearing : L10n.Voice.appleSpeech)
        case .fish: String(localized: L10n.Voice.fishFree)
        case .elevenlabs: String(localized: L10n.Voice.elevenFree)
        case .soniox: String(localized: L10n.Voice.sonioxFree)
        case .deepgram: String(localized: L10n.Voice.deepgramFree)
        }
    }

    var keyPage: URL? {
        switch self {
        case .apple: nil
        case .fish: ModeLinks.fishAudioKeys
        case .elevenlabs: ModeLinks.elevenLabsKeys
        case .soniox: ModeLinks.sonioxKeys
        case .deepgram: ModeLinks.deepgramKeys
        }
    }

    var hearing: Bool { self == .soniox || self == .deepgram }

    /// Mode's recommendation for this provider, if it is one: the most human voice.
    var recommendation: String? { self == .elevenlabs ? String(localized: L10n.Voice.mostHuman) : nil }
}

// MARK: - The provider lists (Mode's voice, Listening)

@MainActor
@Observable
final class VoiceListModel {
    struct Row: Identifiable, Equatable {
        let service: VoiceService
        var id: String { service.id }
        /// The status line under its name: plan, connected key, or why it last failed.
        let status: String
        let ready: Bool
        /// The one the user chose; Mode speaks or listens with it and only it.
        let chosen: Bool
        let connected: Bool
        /// "sk•••••9f"
        let masked: String?
        /// Why it last failed while chosen (VoiceTrouble name).
        let problem: String?
        /// Apple hearing: off and can be turned on here; or the microphone is off in iPhone Settings; or its model downloads.
        let canTurnOn: Bool
        let denied: Bool
        let downloading: Bool
    }

    /// A pasted key being checked with its provider.
    struct KeyCheck: Equatable { let id: String; let state: String; let trouble: String? }

    struct State: Equatable {
        var rows: [Row] = []
        var speaker: VoiceService?
        var voiceName = ""
        var speed = "normal"
        var speakResult = true
        var playing = false
        var keyCheck: KeyCheck?
    }

    enum Event {
        case choose(VoiceService), open(VoiceService), addKey(VoiceService), checkKey(VoiceService, String), keySheetClosed, removeKey(VoiceService)
        case turnOnApple, playSample, speed(String), speakResult(Bool), changeVoice
    }

    private(set) var state = State()
    /// The key sheet (Connect, Edit key).
    var linking: VoiceService?
    /// A connected provider's page.
    var opened: VoiceService?
    let hearing: Bool
    private let presenter: VoicePresenter
    private let navigate: (Route) -> Void
    private var updates: Updates?

    init(screens: Screens, hearing: Bool, navigate: @escaping (Route) -> Void) {
        self.hearing = hearing
        self.navigate = navigate
        presenter = screens.voice(hearing: hearing)
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s, hearing: hearing) }
    }

    func send(_ event: Event) {
        switch event {
        case let .choose(service): presenter.choose(id: service.id)
        case let .open(service): opened = service
        case let .addKey(service): presenter.clearKeyCheck(); opened = nil; linking = service
        case let .checkKey(service, key): presenter.checkAndSaveKey(id: service.id, key: key)
        case .keySheetClosed: presenter.clearKeyCheck()
        case let .removeKey(service): presenter.remove(id: service.id); opened = nil
        case .turnOnApple: presenter.prepareApple()
        case .playSample: presenter.playSample()
        case let .speed(id): presenter.setSpeed(id: id)
        case let .speakResult(on): presenter.setSpeakResult(on: on)
        case .changeVoice: opened = nil; navigate(.voicePicker(provider: state.speaker?.id ?? VoiceService.apple.id))
        }
    }

    func stop() { presenter.stopSample() }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: VoiceUiState, hearing: Bool) -> State {
        let speaker = VoiceService(id: s.speaker)
        // Shown in a fixed order (Apple, then the others); which one is in use is the radio, not the position.
        let order: [VoiceService] = hearing ? [.apple, .deepgram, .soniox] : [.apple, .elevenlabs, .fish]
        return State(
            rows: s.rows.sorted { a, b in
                (VoiceService(id: a.id).flatMap(order.firstIndex) ?? order.count) < (VoiceService(id: b.id).flatMap(order.firstIndex) ?? order.count)
            }.compactMap { row in
                guard let service = VoiceService(id: row.id) else { return nil }
                let plan = service.plan(hearing: hearing)
                let status: String = if let problem = row.problem, row.first {
                    VoiceTroubleText.short(problem, hearing: hearing)
                } else if let masked = row.masked {
                    L10n.Voice.connectedKey(masked)
                } else if service == .apple {
                    plan
                } else {
                    L10n.Voice.notConnected(String(localized: service.hearing ? L10n.Voice.freeCreditShort : L10n.Voice.freePlanShort))
                }
                return Row(service: service, status: status, ready: row.ready, chosen: row.first, connected: service == .apple || row.masked != nil,
                           masked: row.masked, problem: row.first ? row.problem : nil,
                           canTurnOn: service == .apple && hearing && row.block == "Permission",
                           denied: service == .apple && hearing && row.block == "Denied",
                           downloading: service == .apple && hearing && row.block == "Asset")
            },
            speaker: speaker,
            voiceName: s.voiceName ?? String(localized: L10n.Voice.defaultVoice),
            speed: s.speed, speakResult: s.speakResult, playing: s.playing,
            keyCheck: s.keyCheck.map { KeyCheck(id: $0.id, state: $0.state, trouble: $0.trouble) }
        )
    }
}

/// Words for why a chosen voice or listener failed (Voices.problem's VoiceTrouble names).
enum VoiceTroubleText {
    static func short(_ trouble: String, hearing: Bool) -> String {
        let lead = String(localized: hearing ? L10n.Voice.couldntHear : L10n.Voice.couldntSpeak)
        return "\(lead) · \(reasonShort(trouble, hearing: hearing))"
    }

    static func reasonShort(_ trouble: String, hearing: Bool) -> String {
        switch trouble {
        case "NoKey": String(localized: L10n.Voice.troubleNoKey)
        case "KeyRejected": String(localized: L10n.Voice.troubleKey)
        case "PlanLimit": String(localized: hearing ? L10n.Voice.troublePlanHear : L10n.Voice.troublePlanSpeak)
        case "NotReady": String(localized: L10n.Voice.troubleNotReady)
        default: String(localized: L10n.Voice.troubleUnreachable)
        }
    }

    /// The problem card's sentence on a provider's page.
    static func long(_ trouble: String, provider: String, hearing: Bool) -> String {
        switch trouble {
        case "KeyRejected": L10n.Voice.troubleKeyLong(provider)
        case "PlanLimit": hearing ? L10n.Voice.troublePlanHearLong(provider) : L10n.Voice.troublePlanSpeakLong(provider)
        case "NoKey": L10n.Voice.troubleNoKeyLong(provider)
        case "NotReady": String(localized: L10n.Voice.troubleNotReadyLong)
        default: L10n.Voice.troubleUnreachableLong(provider)
        }
    }

    /// A key that didn't check out.
    static func keyFailed(_ trouble: String?, provider: String) -> String {
        switch trouble {
        case "KeyRejected": L10n.Voice.keyRejected(provider)
        case "PlanLimit": L10n.Voice.keyPlan(provider)
        case "NoKey": String(localized: L10n.Voice.keyEmpty)
        default: L10n.Voice.keyUnreachable(provider)
        }
    }
}

/// The providers, one chosen (as the AI providers are): a radio for connected ones, Connect for the rest.
/// Tapping a connected one opens its page (key, Edit key, Remove).
private struct VoiceProviderPicker: View {
    let rows: [VoiceListModel.Row]
    let hearing: Bool
    let onEvent: (VoiceListModel.Event) -> Void

    var body: some View {
        ModeCard {
            ForEach(rows) { row in
                ModeProviderRow(title: row.service.title, status: row.status, logo: row.service.logo,
                                tileBackground: ModeColor.textPrimary, tileForeground: ModeColor.page,
                                tag: row.chosen ? String(localized: L10n.Voice.inUse) : row.service.recommendation,
                                selected: row.chosen, connected: row.connected, connectTitle: String(localized: L10n.Voice.connectKey),
                                onSelect: { onEvent(.choose(row.service)) }, onConnect: { onEvent(.addKey(row.service)) },
                                onOpen: row.service == .apple ? nil : { onEvent(.open(row.service)) })
                if row.canTurnOn || row.denied {
                    HStack(spacing: ModeSpacing.m) {
                        ModeIcon.microphone.image.foregroundStyle(ModeColor.tint).accessibilityHidden(true)
                        Text(row.denied ? L10n.Voice.micOff : L10n.Voice.turnOnApple).modeText(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
                        if row.denied, let settings = URL(string: UIApplication.openSettingsURLString) {
                            Link(destination: settings) { Text(L10n.Voice.openSettings).modeText(.subheadline, color: ModeColor.onAccent).fontWeight(.semibold) }
                                .padding(.horizontal, ModeSpacing.l).frame(minHeight: ModeSize.touch).background(ModeColor.accent, in: .capsule)
                        } else {
                            ModeButton(L10n.Voice.turnOn, role: .primary) { onEvent(.turnOnApple) }
                        }
                    }
                    .padding(ModeSpacing.m)
                    .background(ModeColor.tintSoft, in: .rect(cornerRadius: ModeRadius.field))
                    .padding(.bottom, ModeSpacing.m)
                }
            }
        }
    }
}

struct VoiceScreen: View {
    let state: VoiceListModel.State
    let onEvent: (VoiceListModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                Text(L10n.Voice.voiceSubtitle).modeText(.body, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs).padding(.bottom, ModeSpacing.s)
                ModeCard {
                    VStack(alignment: .leading, spacing: ModeSpacing.m) {
                        HStack(spacing: ModeSpacing.m) {
                            ModeLeadingView((state.speaker ?? .apple).tile).frame(width: ModeSize.voiceLogo, height: ModeSize.voiceLogo)
                            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                                Text(state.voiceName).modeText(.title2)
                                Text((state.speaker ?? .apple).title).modeText(.subheadline, color: ModeColor.textSecondary)
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        HStack(spacing: ModeSpacing.m) {
                            ModePlayButton(L10n.Voice.play(state.voiceName), playing: state.playing) { onEvent(.playSample) }
                            Text(L10n.Voice.sampleLine).modeText(.callout).frame(maxWidth: .infinity, alignment: .leading)
                            if state.playing { ModeWaveBars() }
                        }
                        .padding(ModeSpacing.m)
                        .background(ModeColor.tintSoft, in: .rect(cornerRadius: ModeRadius.field))
                        ModeButton(L10n.Voice.changeVoice, role: .secondary, fullWidth: true) { onEvent(.changeVoice) }
                    }
                    .padding(.vertical, ModeSpacing.l)
                }

                ModeSectionHeader(L10n.Voice.whoSpeaks)
                VoiceProviderPicker(rows: state.rows, hearing: false, onEvent: onEvent)
                note(L10n.Voice.whoSpeaksNote)
                ModeNote(icon: .lock, L10n.Voice.keysNote).padding(.horizontal, ModeSpacing.xs)

                ModeSectionHeader(L10n.Voice.speaking)
                ModeCard {
                    ModeRow(String(localized: L10n.Voice.speed)) {
                        ModeSegmented([("slower", L10n.Voice.slower), ("normal", L10n.Voice.normal), ("faster", L10n.Voice.faster)],
                                      selection: Binding(get: { state.speed }, set: { onEvent(.speed($0)) }))
                            .fixedSize()
                    }
                    ModeToggleRow(L10n.Voice.readResult, subtitle: L10n.Voice.readResultDetail,
                                  isOn: Binding(get: { state.speakResult }, set: { onEvent(.speakResult($0)) }))
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(L10n.Voice.modesVoice))
        .navigationBarTitleDisplayMode(.large)
    }

    private func note(_ text: LocalizedStringResource) -> some View {
        Text(text).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
    }
}

struct ListeningScreen: View {
    let state: VoiceListModel.State
    let onEvent: (VoiceListModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                Text(L10n.Voice.listeningSubtitle).modeText(.body, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs).padding(.bottom, ModeSpacing.s)
                ModeSectionHeader(L10n.Voice.whoListens)
                VoiceProviderPicker(rows: state.rows, hearing: true, onEvent: onEvent)
                Text(L10n.Voice.listeningNote).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
                ModeNote(icon: .lock, L10n.Voice.keysNote).padding(.horizontal, ModeSpacing.xs)
                ModeSectionHeader(L10n.Voice.privacy)
                ModeCard {
                    VStack(alignment: .leading, spacing: ModeSpacing.m) {
                        privacyLine(.microphone, L10n.Voice.privacyListen)
                        privacyLine(.lock, L10n.Voice.privacyWhere)
                        privacyLine(.keyboard, L10n.Voice.privacyType)
                    }
                    .padding(.vertical, ModeSpacing.l)
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(L10n.Voice.listeningRow))
        .navigationBarTitleDisplayMode(.large)
    }

    private func privacyLine(_ icon: ModeIcon, _ text: LocalizedStringResource) -> some View {
        HStack(alignment: .top, spacing: ModeSpacing.m) {
            icon.image.foregroundStyle(ModeColor.tint).frame(width: ModeSize.icon).accessibilityHidden(true)
            Text(text).modeText(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

/// A connected provider's page (boards VoiceProviderPage, VoiceProviderFailed): its key, Edit key, Remove,
/// and when it failed while chosen, why and how to fix it.
struct VoiceProviderSheet: View {
    let row: VoiceListModel.Row
    let hearing: Bool
    let onEvent: (VoiceListModel.Event) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ModeSpacing.l) {
                    HStack(spacing: ModeSpacing.m) {
                        ModeLeadingView(row.service.tile).frame(width: ModeSize.contactAvatar, height: ModeSize.contactAvatar)
                        VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                            Text(row.service.title).modeText(.title)
                            if let problem = row.problem {
                                Label { Text(L10n.Voice.inUseFailed(VoiceTroubleText.reasonShort(problem, hearing: hearing))) } icon: { ModeIcon.warning.image }
                                    .modeText(.subheadline, color: ModeColor.warning)
                            } else {
                                Text(row.chosen ? (hearing ? L10n.Voice.inUseHearing : L10n.Voice.inUseSpeaking) : L10n.Voice.connected)
                                    .modeText(.subheadline, color: ModeColor.textSecondary)
                            }
                        }
                    }
                    if let problem = row.problem {
                        VStack(alignment: .leading, spacing: ModeSpacing.s) {
                            Label { Text(hearing ? L10n.Voice.couldntHearTitle(row.service.title) : L10n.Voice.couldntSpeakTitle(row.service.title)) } icon: { ModeIcon.warning.image }
                                .modeText(.headline, color: ModeColor.warning)
                            Text(VoiceTroubleText.long(problem, provider: row.service.title, hearing: hearing)).modeText(.callout)
                        }
                        .padding(ModeSpacing.l)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(ModeColor.tileOrange, in: .rect(cornerRadius: ModeRadius.card))
                        if !hearing && problem == "PlanLimit" {
                            ModeButton(L10n.Voice.chooseAnotherVoice, fullWidth: true) { onEvent(.changeVoice) }
                        }
                        ModeButton(L10n.Voice.updateKey, role: problem == "PlanLimit" && !hearing ? .secondary : .primary, fullWidth: true) { onEvent(.addKey(row.service)) }
                        ModeButton(hearing ? L10n.Voice.useAppleHearing : L10n.Voice.useAppleSpeaking, role: .secondary, fullWidth: true) {
                            onEvent(.choose(.apple)); dismiss()
                        }
                    }
                    ModeSectionHeader(L10n.Voice.keySection)
                    ModeCard {
                        ModeRow(String(localized: L10n.Voice.key)) {
                            Text(row.masked ?? "—").modeText(.body, color: ModeColor.textSecondary).monospaced()
                        }
                    }
                    if row.problem == nil {
                        HStack(spacing: ModeSpacing.m) {
                            ModeButton(L10n.Voice.editKey, role: .secondary, fullWidth: true) { onEvent(.addKey(row.service)) }
                            ModeButton(L10n.Voice.removeKey, role: .destructive, fullWidth: true) { onEvent(.removeKey(row.service)); dismiss() }
                        }
                        if !row.chosen {
                            ModeButton(LocalizedStringResource(stringLiteral: hearing ? L10n.Voice.listenWith(row.service.title) : L10n.Voice.speakWith(row.service.title)), fullWidth: true) {
                                onEvent(.choose(row.service)); dismiss()
                            }
                        }
                        Text(L10n.Voice.removeNote(row.service.title)).modeText(.footnote, color: ModeColor.textSecondary)
                    } else {
                        ModeButton(L10n.Voice.removeKey, role: .destructive, fullWidth: true) { onEvent(.removeKey(row.service)); dismiss() }
                    }
                    ModeNote(icon: .lock, L10n.Voice.keysNote)
                }
                .padding(ModeSpacing.screenEdge)
            }
            .modeScreen()
            .toolbar { ToolbarItem(placement: .cancellationAction) { ModeToolbarButton(.close, label: L10n.Voice.close) { dismiss() } } }
        }
        .presentationDetents([.large])
    }
}

/// Mode's voice or Listening, with the key sheet and a provider's page.
struct VoiceRoute: View {
    @State private var model: VoiceListModel

    init(app: AppModel, hearing: Bool, navigate: @escaping (Route) -> Void) {
        _model = State(initialValue: VoiceListModel(screens: app.screens, hearing: hearing, navigate: navigate))
    }

    var body: some View {
        Group {
            if model.hearing { ListeningScreen(state: model.state, onEvent: model.send) }
            else { VoiceScreen(state: model.state, onEvent: model.send) }
        }
        .voiceSheets(model)
        .onDisappear { model.stop() }
    }
}

extension View {
    /// The key sheet and a provider's page for [model], wherever its rows show (Settings, setup).
    func voiceSheets(_ model: VoiceListModel) -> some View {
        @Bindable var model = model
        return self
            .sheet(item: $model.linking, onDismiss: { model.send(.keySheetClosed) }) { service in
                VoiceKeySheet(service: service, masked: model.state.rows.first { $0.service == service }?.masked,
                              check: model.state.keyCheck?.id == service.id ? model.state.keyCheck : nil) { model.send(.checkKey(service, $0)) }
            }
            .sheet(item: $model.opened) { service in
                if let row = model.state.rows.first(where: { $0.service == service }) {
                    VoiceProviderSheet(row: row, hearing: model.hearing, onEvent: model.send)
                }
            }
    }
}

// MARK: - Connect a key

/// Paste a voice service's key (boards VoiceKeyAdd/Checking/Failed/Saved/Edit): what it is, its free plan, where
/// to get a key, the key; Check and save asks the provider first and saves only a key that works.
struct VoiceKeySheet: View {
    let service: VoiceService
    /// The saved key ("sk•••••9f"): this is Edit key.
    let masked: String?
    let check: VoiceListModel.KeyCheck?
    let checkAndSave: (String) -> Void
    @State private var key = ""
    @Environment(\.dismiss) private var dismiss

    private var checking: Bool { check?.state == "checking" }
    private var failed: Bool { check?.state == "failed" }
    private var saved: Bool { check?.state == "saved" }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.l) {
                HStack(spacing: ModeSpacing.m) {
                    ModeLeadingView(service.tile).frame(width: ModeSize.contactAvatar, height: ModeSize.contactAvatar)
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        Text(masked == nil ? L10n.Voice.connect(service.title) : L10n.Voice.editKeyTitle(service.title)).modeText(.title)
                        Text(service.hearing ? L10n.Voice.hearingPurpose : L10n.Voice.speechPurpose).modeText(.subheadline, color: ModeColor.textSecondary)
                    }
                }
                if let masked, !saved {
                    Text(L10n.Voice.inUseNowKey(masked)).modeText(.callout, color: ModeColor.textSecondary)
                } else if masked == nil {
                    ModeStatusPill(service.plan(hearing: service.hearing), tone: .success)
                }
                if saved {
                    Label { Text(L10n.Voice.savedConnected(service.title)) } icon: { ModeIcon.done.image }
                        .modeText(.headline, color: ModeColor.success)
                } else {
                    ModeTextField(LocalizedStringResource(stringLiteral: L10n.Voice.keyField(service.title)), prompt: L10n.Voice.key, text: $key, secure: true)
                        .overlay(RoundedRectangle(cornerRadius: ModeRadius.field).strokeBorder(failed ? ModeColor.danger : .clear, lineWidth: ModeSize.separator * 2))
                        .disabled(checking)
                    if failed {
                        VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                            Text(L10n.Voice.keyDidntWork).modeText(.headline, color: ModeColor.danger)
                            Text(VoiceTroubleText.keyFailed(check?.trouble, provider: service.title)).modeText(.subheadline, color: ModeColor.textSecondary)
                        }
                    }
                    if checking {
                        HStack(spacing: ModeSpacing.s) {
                            ProgressView().tint(ModeColor.tint)
                            Text(L10n.Voice.checkingKey(service.title)).modeText(.subheadline, color: ModeColor.textSecondary)
                        }
                    }
                    if let page = service.keyPage {
                        Link(destination: page) {
                            Label { Text(L10n.Voice.open(service.title)) } icon: { ModeIcon.external.image }.modeText(.body, color: ModeColor.tint).fontWeight(.semibold)
                        }
                    }
                }
                ModeNote(icon: .lock, LocalizedStringResource(stringLiteral: L10n.Voice.keyNote(service.title)))
            }
            .padding(ModeSpacing.screenEdge)
        }
        .safeAreaInset(edge: .bottom) {
            Group {
                if saved {
                    ModeButton(L10n.Common.done, fullWidth: true) { dismiss() }
                } else {
                    ModeButton(checking ? L10n.Voice.checking : failed ? L10n.Voice.checkAgain : L10n.Voice.checkAndSave, fullWidth: true) {
                        checkAndSave(key.trimmingCharacters(in: .whitespacesAndNewlines))
                    }
                    .disabled(checking || key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.s)
        }
        .modeScreen()
        .presentationDetents([.large])
    }
}

// MARK: - Choose a voice

@MainActor
@Observable
final class VoicePickerModel {
    struct Voice: Identifiable, Equatable { let id: String; let name: String; let traits: String; let tag: String? }

    struct State: Equatable {
        var service: VoiceService = .apple
        var groups: [String] = []
        var group: String?
        var voices: [Voice] = []
        var selected: String?
        var selectedName: String?
        var playing: String?
        var loading = true
        var needsKey = false
        var failed = false
    }

    enum Event { case service(VoiceService), group(String?), play(String), select(String), use, reload, addKey, saveKey(String) }

    private(set) var state = State()
    var query = "" { didSet { if query != oldValue { presenter.search(text: query) } } }
    var linking: VoiceService?
    /// The pasted key being checked with its provider (Connect from the picker).
    private(set) var keyCheck: VoiceListModel.KeyCheck?
    private let presenter: VoicePickerPresenter
    private let keys: VoicePresenter
    private var updates: Updates?
    private var keyUpdates: Updates?

    init(screens: Screens, provider: String) {
        presenter = screens.voicePicker(provider: provider)
        keys = screens.voice(hearing: false)
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
        keyUpdates = observe(keys.state) { [weak self] s in
            guard let self else { return }
            let check = s.keyCheck.map { VoiceListModel.KeyCheck(id: $0.id, state: $0.state, trouble: $0.trouble) }
            if check?.state == "saved" && self.keyCheck?.state != "saved" { self.presenter.reload() }
            self.keyCheck = check
        }
    }

    func send(_ event: Event) {
        switch event {
        case let .service(s): presenter.provider(id: s.id)
        case let .group(g): presenter.filter(groupName: g)
        case let .play(id): presenter.play(id: id)
        case let .select(id): presenter.select(id: id)
        case .use: presenter.use()
        case .reload: presenter.reload()
        case .addKey: linking = state.service
        case let .saveKey(key):
            if let linking { keys.checkAndSaveKey(id: linking.id, key: key) }
        }
    }

    func stop() { presenter.stopSample() }

    func keySheetClosed() { keys.clearKeyCheck() }

    isolated deinit {
        updates?.cancel()
        keyUpdates?.cancel()
        presenter.close()
        keys.close()
    }

    private static func map(_ s: VoicePickerUiState) -> State {
        let voices = s.voices.map { v in
            Voice(id: v.id, name: v.name, traits: v.traits,
                  tag: v.tag == Voices.companion.POPULAR ? String(localized: L10n.Voice.popular)
                    : v.tag == Voices.companion.OUR_PICK ? String(localized: L10n.Voice.ourPick) : v.tag)
        }
        return State(
            service: VoiceService(id: s.provider) ?? .apple, groups: s.groups, group: s.group, voices: voices,
            selected: s.selected, selectedName: voices.first { $0.id == s.selected }?.name,
            playing: s.playing, loading: s.loading,
            needsKey: s.error == .noKey, failed: s.error == .failed
        )
    }
}

struct VoicePickerScreen: View {
    let state: VoicePickerModel.State
    @Binding var query: String
    let onEvent: (VoicePickerModel.Event) -> Void
    let done: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                ModeSegmented([(VoiceService.apple, L10n.Voice.apple), (.elevenlabs, L10n.Voice.eleven), (.fish, L10n.Voice.fish)],
                              selection: Binding(get: { state.service }, set: { onEvent(.service($0)) }))
                HStack(alignment: .top, spacing: ModeSpacing.m) {
                    ModeLeadingView(state.service.tile)
                    Text(banner).modeText(.footnote, color: ModeColor.textSecondary).frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(ModeSpacing.m)
                .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.field))
                content
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .safeAreaInset(edge: .bottom) {
            VStack(spacing: ModeSpacing.m) {
                if let playing = state.voices.first(where: { $0.id == state.playing }) {
                    ModeNowPlaying(name: playing.name, title: L10n.Voice.isSpeaking(playing.name), line: String(localized: L10n.Voice.sampleLine))
                        .transition(.move(edge: .bottom).combined(with: .opacity))
                }
                if let name = state.selectedName {
                    ModeButton(LocalizedStringResource(stringLiteral: L10n.Voice.use(name)), fullWidth: true) { onEvent(.use); done() }
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.s)
            .animation(ModeMotion.standard, value: state.playing)
        }
        .modeScreen()
        .navigationTitle(Text(L10n.Voice.chooseVoice))
        .navigationBarTitleDisplayMode(.large)
    }

    private var banner: LocalizedStringResource {
        switch state.service {
        case .elevenlabs: L10n.Voice.elevenBanner
        case .fish: L10n.Voice.fishBanner
        default: L10n.Voice.appleBanner
        }
    }

    @ViewBuilder private var content: some View {
        if state.needsKey {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                Text(L10n.Voice.needsKey(state.service.title)).modeText(.callout)
                ModeButton(L10n.Voice.addKey, fullWidth: true) { onEvent(.addKey) }
            }
            .padding(ModeSpacing.l)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
        } else if state.failed {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                Text(L10n.Voice.couldNotLoad).modeText(.callout)
                ModeButton(L10n.Voice.tryAgain, role: .secondary) { onEvent(.reload) }
            }
            .padding(ModeSpacing.l)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
        } else if state.loading {
            ProgressView().tint(ModeColor.tint).frame(maxWidth: .infinity, minHeight: ModeSize.rowMin)
        } else {
            ModeSearchField(String(localized: L10n.Voice.searchVoices), text: $query)
            if !state.groups.isEmpty {
                ScrollView(.horizontal) {
                    HStack(spacing: ModeSpacing.s) {
                        ModeFilterChip(String(localized: L10n.Voice.all), selected: state.group == nil) { onEvent(.group(nil)) }
                        ForEach(state.groups, id: \.self) { g in ModeFilterChip(g, selected: state.group == g) { onEvent(.group(g)) } }
                    }
                }
                .scrollIndicators(.hidden)
            }
            if state.voices.isEmpty {
                Text(L10n.Voice.noMatch).modeText(.callout, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
            } else {
                ModeCard {
                    ForEach(state.voices) { v in
                        ModeVoiceChoiceRow(name: v.name, traits: v.traits, tag: v.tag, selected: v.id == state.selected, playing: state.playing == v.id,
                                           playLabel: L10n.Voice.play(v.name), onSelect: { onEvent(.select(v.id)) }, onPlay: { onEvent(.play(v.id)) })
                    }
                }
                Text(L10n.Voice.pickerNote).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
            }
        }
    }
}

struct VoicePickerRoute: View {
    @State private var model: VoicePickerModel
    @Environment(\.dismiss) private var dismiss

    init(app: AppModel, provider: String) {
        _model = State(initialValue: VoicePickerModel(screens: app.screens, provider: provider))
    }

    var body: some View {
        VoicePickerScreen(state: model.state, query: $model.query, onEvent: model.send) { dismiss() }
            .sheet(item: $model.linking, onDismiss: { model.keySheetClosed() }) { service in
                VoiceKeySheet(service: service, masked: nil, check: model.keyCheck) { model.send(.saveKey($0)) }
            }
            .onDisappear { model.stop() }
    }
}
