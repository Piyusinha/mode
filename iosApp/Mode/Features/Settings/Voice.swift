import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI
import UIKit

// Voice (boards "Mode's voice", "Listening", a provider's page, the key sheet, "Choose a voice"): who speaks and
// listens, the voice, keys, and the voices to pick from.

/// A speech or hearing service, as the screens name and draw it.
enum VoiceService: String, CaseIterable, Identifiable {
    case apple, speechify, fish, elevenlabs, soniox, deepgram
    var id: String { rawValue }

    init?(id: String?) { guard let id, let s = VoiceService(rawValue: id) else { return nil }; self = s }

    var title: String {
        switch self {
        case .apple: String(localized: L10n.Voice.apple)
        case .speechify: String(localized: L10n.Voice.speechify)
        case .fish: String(localized: L10n.Voice.fish)
        case .elevenlabs: String(localized: L10n.Voice.eleven)
        case .soniox: String(localized: L10n.Voice.soniox)
        case .deepgram: String(localized: L10n.Voice.deepgram)
        }
    }

    var logo: ModeLogo {
        switch self {
        case .apple: .apple
        case .speechify: .speechify
        case .fish: .fishAudio
        case .elevenlabs: .elevenLabs
        case .soniox: .soniox
        case .deepgram: .deepgram
        }
    }

    /// The brand mark on a black (light) or white (dark) tile.
    var tile: ModeRowLeading { .logoTile(logo, background: ModeColor.textPrimary, foreground: ModeColor.page) }

    /// Its square on the voice pages: the logo for who speaks, the first letter for who listens (as on the boards).
    func square(hearing: Bool) -> ModeVoiceTile {
        hearing ? .letter(String(title.prefix(1))) : .logo(logo)
    }

    /// What it costs, in one line.
    func plan(hearing: Bool) -> String {
        switch self {
        case .apple: String(localized: hearing ? L10n.Voice.appleHearing : L10n.Voice.appleSpeech)
        case .speechify: String(localized: L10n.Voice.speechifyFree)
        case .fish: String(localized: L10n.Voice.fishFree)
        case .elevenlabs: String(localized: L10n.Voice.elevenFree)
        case .soniox: String(localized: L10n.Voice.sonioxFree)
        case .deepgram: String(localized: L10n.Voice.deepgramFree)
        }
    }

    /// What it does, on the key sheet.
    var blurb: LocalizedStringResource {
        switch self {
        case .apple: L10n.Voice.blurbApple
        case .speechify, .fish, .elevenlabs: L10n.Voice.blurbSpeech
        case .soniox: L10n.Voice.blurbSoniox
        case .deepgram: L10n.Voice.blurbDeepgram
        }
    }

    var keyPage: URL? {
        switch self {
        case .apple: nil
        case .speechify: ModeLinks.speechifyKeys
        case .fish: ModeLinks.fishAudioKeys
        case .elevenlabs: ModeLinks.elevenLabsKeys
        case .soniox: ModeLinks.sonioxKeys
        case .deepgram: ModeLinks.deepgramKeys
        }
    }

    /// Its website's name ("elevenlabs.io") and its front page, where the account and its usage are.
    var site: (name: String, url: URL)? {
        let name: String? = switch self {
        case .apple: nil
        case .speechify: "speechify.ai"
        case .fish: "fish.audio"
        case .elevenlabs: "elevenlabs.io"
        case .soniox: "soniox.com"
        case .deepgram: "deepgram.com"
        }
        guard let name, let url = URL(string: "https://\(name)") else { return nil }
        return (name, url)
    }

    var hearing: Bool { self == .soniox || self == .deepgram }
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
        var languageName = ""
    }

    enum Event {
        case choose(VoiceService), open(VoiceService), addKey(VoiceService), checkKey(VoiceService, String), keySheetClosed, removeKey(VoiceService)
        case turnOnApple, playSample, speed(String), speakResult(Bool), changeVoice, openLanguage
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
        case .openLanguage: navigate(.language)
        case let .addKey(service): presenter.clearKeyCheck(); linking = service
        case let .checkKey(service, key): presenter.checkAndSaveKey(id: service.id, key: key)
        case .keySheetClosed: presenter.clearKeyCheck()
        case let .removeKey(service): presenter.remove(id: service.id); linking = nil; opened = nil
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
        // Shown in a fixed order (Speechify first for who speaks); which one is in use is the radio, not the position.
        let order: [VoiceService] = hearing ? [.apple, .deepgram, .soniox] : [.speechify, .apple, .elevenlabs, .fish]
        return State(
            rows: s.rows.sorted { a, b in
                (VoiceService(id: a.id).flatMap(order.firstIndex) ?? order.count) < (VoiceService(id: b.id).flatMap(order.firstIndex) ?? order.count)
            }.compactMap { row in
                guard let service = VoiceService(id: row.id) else { return nil }
                return Self.row(service: service, ready: row.ready, chosen: row.first, masked: row.masked,
                                problem: row.problem, block: row.block, hearing: hearing)
            },
            speaker: speaker,
            voiceName: s.voiceName ?? String(localized: L10n.Voice.defaultVoice),
            speed: s.speed, speakResult: s.speakResult, playing: s.playing,
            keyCheck: s.keyCheck.map { KeyCheck(id: $0.id, state: $0.state, trouble: $0.trouble) },
            languageName: s.languageName
        )
    }

    nonisolated static func row(service: VoiceService, ready: Bool, chosen: Bool, masked: String?, problem: String?, block: String?, hearing: Bool) -> Row {
        let plan = service.plan(hearing: hearing)
        let status: String = if let problem, chosen {
            VoiceTroubleText.short(problem, hearing: hearing)
        } else if let masked {
            L10n.Voice.connectedKey(masked)
        } else if service == .apple {
            plan
        } else {
            L10n.Voice.notConnected(String(localized: service.hearing ? L10n.Voice.freeCreditShort : L10n.Voice.freePlanShort))
        }
        return Row(service: service, status: status, ready: ready, chosen: chosen, connected: service == .apple || masked != nil,
                   masked: masked, problem: chosen ? problem : nil,
                   canTurnOn: service == .apple && hearing && block == "Permission",
                   denied: service == .apple && hearing && block == "Denied",
                   downloading: service == .apple && hearing && block == "Asset")
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
        default: hearing ? L10n.Voice.troubleUnreachableHearLong(provider) : L10n.Voice.troubleUnreachableLong(provider)
        }
    }

    /// A key that didn't check out.
    static func keyFailed(_ trouble: String?, service: VoiceService) -> String {
        let provider = service.title
        switch trouble {
        case "KeyRejected": return L10n.Voice.keyRejected(provider, site: service.site?.name ?? provider)
        case "PlanLimit": return L10n.Voice.keyPlan(provider)
        case "NoKey": return String(localized: L10n.Voice.keyEmpty)
        default: return L10n.Voice.keyUnreachable(provider)
        }
    }
}

/// The big title at the top of a voice page, with what it's for, and Mo beside it on Listening.
private struct VoicePageHeader: View {
    let title: LocalizedStringResource
    let subtitle: LocalizedStringResource?
    var mood: ModeMo.Mood?

    var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.m) {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                Text(title).modeText(.largeTitle).accessibilityAddTraits(.isHeader)
                if let subtitle { Text(subtitle).modeText(.callout, color: ModeColor.textSecondary) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if let mood { ModeMo(mood, size: ModeSize.voiceHeaderMo).padding(.top, -ModeSpacing.s) }
        }
        .padding(.horizontal, ModeSpacing.s)
        .padding(.bottom, ModeSpacing.l)
    }
}

private extension View {
    /// A voice page draws its own big title; the bar keeps only the back button.
    func voicePageChrome(_ title: LocalizedStringResource) -> some View {
        navigationTitle(Text(title))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(removing: .title)
    }
}

/// The providers, one chosen: a radio for connected ones, Connect for the rest. Tapping a connected one opens its page.
struct VoiceProviderPicker: View {
    let rows: [VoiceListModel.Row]
    let hearing: Bool
    let onEvent: (VoiceListModel.Event) -> Void
    /// Listening's page draws who listens as letters; setup's sheet keeps their logos (as on their boards).
    var letters = true

    var body: some View {
        // Listening's cards float (as on its board); Mode's voice's sit flat.
        ModeCard(lifted: hearing) {
            ForEach(rows) { row in
                VStack(spacing: ModeSpacing.none) {
                    ModeVoiceProviderRow(title: row.service.title, status: row.status, dot: dot(row), tile: row.service.square(hearing: hearing && letters),
                                         tags: tags(row), selected: row.chosen, connected: row.connected,
                                         connectTitle: String(localized: L10n.Voice.connectKey),
                                         onSelect: { onEvent(.choose(row.service)) }, onConnect: { onEvent(.addKey(row.service)) },
                                         onOpen: row.service == .apple ? nil : { onEvent(.open(row.service)) })
                    if row.canTurnOn || row.denied { micBox(row) }
                }
            }
        }
    }

    private func dot(_ row: VoiceListModel.Row) -> Color {
        row.problem != nil ? ModeColor.danger : row.connected ? ModeColor.success : ModeColor.ring
    }

    /// "In use" (green for who speaks, grey for who listens, as on the boards).
    private func tags(_ row: VoiceListModel.Row) -> [ModeVoiceTag] {
        var tags: [ModeVoiceTag] = []
        if row.chosen { tags.append(ModeVoiceTag(String(localized: L10n.Voice.inUse), tone: hearing ? .neutral : .green)) }
        return tags
    }

    private func micBox(_ row: VoiceListModel.Row) -> some View {
        HStack(spacing: ModeSpacing.m) {
            ModeIcon.microphone.image.foregroundStyle(ModeColor.tint).accessibilityHidden(true)
            Text(row.denied ? L10n.Voice.micOff : L10n.Voice.turnOnApple).modeText(.subheadline).frame(maxWidth: .infinity, alignment: .leading)
            if row.denied, let settings = URL(string: UIApplication.openSettingsURLString) {
                Link(destination: settings) {
                    Text(L10n.Voice.openSettings).modeText(.subheadlineEmphasis, color: ModeColor.onAccent)
                        .padding(.horizontal, ModeSpacing.l).frame(minHeight: ModeSize.pillButton).background(ModeColor.accent, in: .capsule)
                }
            } else {
                ModeButton(L10n.Voice.turnOn, role: .pillProminent) { onEvent(.turnOnApple) }
            }
        }
        .padding(ModeSpacing.m)
        .background(ModeColor.tintSoft, in: .rect(cornerRadius: ModeRadius.keyResult))
        .padding(.bottom, ModeSpacing.m)
    }
}

/// A note under a card, inset as on the boards.
struct VoiceNote: View {
    let text: LocalizedStringResource
    var body: some View {
        Text(text).modeText(.footnote, color: ModeColor.textSecondary)
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, ModeSpacing.s)
    }
}

/// "Keys stay in this iPhone's Keychain…", with a lock.
struct VoiceKeysNote: View {
    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.s) {
            ModeIcon.lock.image.font(ModeIconFont.small).foregroundStyle(ModeColor.textSecondary).accessibilityHidden(true)
            Text(L10n.Voice.keysNote).modeText(.footnote, color: ModeColor.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, ModeSpacing.s)
    }
}

struct VoiceScreen: View {
    let state: VoiceListModel.State
    let onEvent: (VoiceListModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                VoicePageHeader(title: L10n.Voice.modesVoice, subtitle: L10n.Voice.voiceSubtitle).padding(.bottom, ModeSpacing.l + ModeSpacing.xs)
                ModeCard {
                    VStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                        ModeVoiceIdentity(name: state.voiceName, detail: (state.speaker ?? .apple).title,
                                          badge: (state.speaker ?? .apple).square(hearing: false))
                        ModeVoiceButton(L10n.Voice.changeVoice, style: .raised, height: ModeSize.secondaryButton - ModeSpacing.xxs) {
                            onEvent(.changeVoice)
                        }
                    }
                    .padding(.top, ModeSpacing.l)
                    .padding(.bottom, ModeSpacing.l)
                }

                ModeSectionHeader(L10n.Voice.whoSpeaks)
                VoiceProviderPicker(rows: state.rows, hearing: false, onEvent: onEvent)
                VoiceNote(text: L10n.Voice.whoSpeaksNote)
                VoiceKeysNote()

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
        .voicePageChrome(L10n.Voice.modesVoice)
    }
}

struct ListeningScreen: View {
    let state: VoiceListModel.State
    let onEvent: (VoiceListModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                VoicePageHeader(title: L10n.Voice.listeningRow, subtitle: L10n.Voice.listeningSubtitle, mood: .listening)
                    .padding(.bottom, -ModeSpacing.m)
                ModeSectionHeader(L10n.Voice.whoListens)
                VoiceProviderPicker(rows: state.rows, hearing: true, onEvent: onEvent)
                VoiceNote(text: L10n.Voice.listeningNote)
                ModeSectionHeader(L10n.Voice.privacy)
                ModeCard(lifted: true) {
                    privacyLine(L10n.Voice.privacyListen)
                    privacyLine(L10n.Voice.privacyWhere)
                    privacyLine(L10n.Voice.privacyType)
                }
                ModeSectionHeader(L10n.Language.title)
                ModeCard {
                    ModeValueRow(String(localized: L10n.Language.iSpeak), value: state.languageName) { onEvent(.openLanguage) }
                        .frame(minHeight: ModeSize.rowMin)
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .voicePageChrome(L10n.Voice.listeningRow)
    }

    private func privacyLine(_ text: LocalizedStringResource) -> some View {
        HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.s + ModeSpacing.xxs) {
            Text(verbatim: "•").modeText(.small, color: ModeColor.tint).accessibilityHidden(true)
            Text(text).modeText(.small).frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(.vertical, ModeSpacing.m)
    }
}

/// A connected provider's page (boards VoiceProviderPage, VoiceProviderFailed): its key, Edit key and Remove,
/// and when it failed while chosen, why and how to fix it.
struct VoiceProviderPage: View {
    let row: VoiceListModel.Row
    let hearing: Bool
    let onEvent: (VoiceListModel.Event) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                header
                if let problem = row.problem {
                    VStack(spacing: ModeSpacing.s) {
                        ModeVoiceProblem(title: hearing ? L10n.Voice.couldntHearTitle(row.service.title) : L10n.Voice.couldntSpeakTitle(row.service.title),
                                         message: VoiceTroubleText.long(problem, provider: row.service.title, hearing: hearing))
                            .padding(.bottom, ModeSpacing.xxs)
                        if !hearing && problem == "PlanLimit" {
                            ModeVoiceButton(L10n.Voice.chooseAnotherVoice, style: .ink) { onEvent(.changeVoice) }
                        }
                        ModeVoiceButton(L10n.Voice.updateKey, style: !hearing && problem == "PlanLimit" ? .raised : .ink) { onEvent(.addKey(row.service)) }
                        ModeVoiceButton(hearing ? L10n.Voice.useAppleHearing : L10n.Voice.useAppleSpeaking, style: .raised) {
                            onEvent(.choose(.apple)); dismiss()
                        }
                    }
                }
                ModeSectionHeader(L10n.Voice.keySection)
                ModeCard {
                    ModeRow(String(localized: L10n.Voice.key)) {
                        Text(row.masked ?? "—").modeText(.callout, color: ModeColor.textSecondary).monospacedDigit()
                    }
                }
                if row.problem == nil {
                    HStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                        ModeVoiceButton(L10n.Voice.editKey, style: .raised, height: ModeSize.secondaryButton - ModeSpacing.xxs) { onEvent(.addKey(row.service)) }
                        ModeVoiceButton(L10n.Voice.remove, style: .danger, height: ModeSize.secondaryButton - ModeSpacing.xxs) {
                            onEvent(.removeKey(row.service)); dismiss()
                        }
                    }
                    if !row.chosen {
                        ModeVoiceButton(LocalizedStringResource(stringLiteral: hearing ? L10n.Voice.listenWith(row.service.title) : L10n.Voice.speakWith(row.service.title)),
                                        style: .ink) {
                            onEvent(.choose(row.service)); dismiss()
                        }
                    }
                    VoiceNote(text: LocalizedStringResource(stringLiteral: hearing ? L10n.Voice.removeNoteHear(row.service.title) : L10n.Voice.removeNoteSpeak(row.service.title)))
                    if let site = row.service.site {
                        ModeSectionHeader(L10n.Voice.planSection)
                        ModeCard {
                            Link(destination: site.url) {
                                HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
                                    Text(L10n.Voice.seeUsage(site.name)).modeText(.calloutEmphasis)
                                    ModeIcon.external.image.font(ModeIconFont.chevron)
                                }
                                .foregroundStyle(ModeColor.textPrimary)
                                .frame(maxWidth: .infinity, minHeight: ModeSize.rowMin - ModeSpacing.xxs, alignment: .leading)
                            }
                        }
                    }
                }
                VoiceKeysNote().padding(.top, ModeSpacing.xxs)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(row.service.title))
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(removing: .title)
    }

    private var header: some View {
        HStack(spacing: ModeSpacing.m + ModeSpacing.xxs) {
            ModeVoiceTileView(row.service.square(hearing: hearing), size: .page)
            VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                Text(row.service.title).modeText(.pageTitle).lineLimit(1).minimumScaleFactor(ModeTextScale.title)
                    .accessibilityAddTraits(.isHeader)
                if row.problem != nil {
                    statusLine(hearing ? L10n.Voice.inUseCouldntHear : L10n.Voice.inUseCouldntSpeak, dot: ModeColor.danger, color: ModeColor.danger, bold: true)
                } else {
                    statusLine(row.chosen ? (hearing ? L10n.Voice.inUseHearing : L10n.Voice.inUseSpeaking) : L10n.Voice.connected,
                               dot: ModeColor.success, color: ModeColor.textSecondary, bold: false)
                }
            }
        }
        .padding(.horizontal, ModeSpacing.s)
        .padding(.bottom, ModeSpacing.l + ModeSpacing.xs)
    }

    private func statusLine(_ text: LocalizedStringResource, dot: Color, color: Color, bold: Bool) -> some View {
        HStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
            Circle().fill(dot).frame(width: ModeSize.voiceDot, height: ModeSize.voiceDot)
            Text(text).modeText(bold ? .smallEmphasis : .small, color: color)
        }
    }
}

/// Mode's voice or Listening, with the key sheet and a provider's page.
struct VoiceRoute: View {
    @LazyState private var model: VoiceListModel

    init(app: AppModel, hearing: Bool, navigate: @escaping (Route) -> Void) {
        _model = LazyState(wrappedValue: VoiceListModel(screens: app.screens, hearing: hearing, navigate: navigate))
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
            .modeBottomSheet(item: $model.linking, height: ModeSize.keySheet, onDismiss: { model.send(.keySheetClosed) }) { service in
                let masked = model.state.rows.first { $0.service == service }?.masked
                VoiceKeySheet(service: service, masked: masked, editing: masked,
                              check: model.state.keyCheck?.id == service.id ? model.state.keyCheck : nil,
                              checkAndSave: { model.send(.checkKey(service, $0)) },
                              onRemove: masked == nil ? nil : { model.send(.removeKey(service)) })
            }
            .navigationDestination(item: $model.opened) { service in
                if let row = model.state.rows.first(where: { $0.service == service }) {
                    VoiceProviderPage(row: row, hearing: model.hearing, onEvent: model.send)
                }
            }
    }
}

// MARK: - Connect a key

/// Paste a voice service's key (boards VoiceKeyAdd/Checking/Failed/Saved/Edit): what it is, the key, where to get
/// one; Check and save asks the provider first and saves only a key that works.
struct VoiceKeySheet: View {
    let service: VoiceService
    /// The saved key ("sk•••••9f").
    let masked: String?
    let check: VoiceListModel.KeyCheck?
    let checkAndSave: (String) -> Void
    /// Edit key: Remove key, under the field.
    var onRemove: (() -> Void)?
    @State private var key = ""
    /// The key that was saved when the sheet opened: this is Edit key.
    @State private var editing: String?
    @Environment(\.modeSheetDismiss) private var sheetDismiss
    @Environment(\.dismiss) private var systemDismiss

    /// Slides the bottom sheet away; inside a system sheet (setup), closes that.
    private func dismiss() { if let sheetDismiss { sheetDismiss() } else { systemDismiss() } }

    /// [editing]: the saved key when this is Edit key. [typed]: what's in the field to start with.
    init(service: VoiceService, masked: String?, editing: String? = nil, check: VoiceListModel.KeyCheck?, checkAndSave: @escaping (String) -> Void,
         onRemove: (() -> Void)? = nil, typed: String = "") {
        self.service = service
        self.masked = masked
        self.check = check
        self.checkAndSave = checkAndSave
        self.onRemove = onRemove
        _editing = State(initialValue: editing)
        _key = State(initialValue: typed)
    }

    private var phase: ModeKeyField.Phase {
        switch check?.state {
        case "checking": .checking
        case "failed": .failed
        case "saved": .saved
        default: .editing
        }
    }

    private var title: String { editing == nil ? L10n.Voice.connect(service.title) : L10n.Voice.editKeyTitle(service.title) }

    /// Mo sits by the title until there's a result card for it.
    private var mood: ModeMo.Mood? {
        switch phase {
        case .editing: .idle
        case .checking: .working
        case .failed, .saved: nil
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                ModeSheetHeader(title, mood: mood, closeLabel: L10n.Voice.close) { dismiss() }
                    .padding(.horizontal, ModeSpacing.xs)
                    .padding(.bottom, mood == nil ? ModeSpacing.l : ModeSpacing.xxl + ModeSpacing.xs)
                HStack(spacing: ModeSpacing.m) {
                    ModeVoiceTileView(service.square(hearing: service.hearing), size: .sheet)
                    Group {
                        if let editing, phase != .saved { Text(L10n.Voice.inUseNowKey(editing)) } else { Text(service.blurb) }
                    }
                    .modeText(.small, color: ModeColor.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .padding(.horizontal, ModeSpacing.xs)
                .padding(.bottom, ModeSpacing.xs + ModeSpacing.xxs)
                ModeKeyField(editing == nil || phase == .saved ? L10n.Voice.keyField(service.title) : L10n.Voice.newKeyField(service.title),
                             prompt: L10n.Voice.pasteHere, text: $key, shown: phase == .saved ? masked : nil, phase: phase,
                             pasteLabel: L10n.Voice.paste) { key = UIPasteboard.general.string ?? key }
                result
                VoiceKeysNote().padding(.top, ModeSpacing.xxs)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.top, ModeSpacing.xxl)
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaInset(edge: .bottom) {
            Group {
                if phase == .saved {
                    ModeVoiceButton(L10n.Common.done, style: .ink, height: ModeSize.primaryButton) { dismiss() }
                } else {
                    ModeVoiceButton(phase == .checking ? L10n.Voice.checking : phase == .failed ? L10n.Voice.checkAgain : L10n.Voice.checkAndSave,
                                    style: .ink, height: ModeSize.primaryButton) {
                        checkAndSave(key.trimmingCharacters(in: .whitespacesAndNewlines))
                    }
                    .disabled(phase == .checking || key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.top, ModeSpacing.xl)
            .padding(.bottom, ModeSpacing.s)
            .background(ModeEdgeFade(edge: .bottom).ignoresSafeArea())
        }
    }

    @ViewBuilder private var result: some View {
        switch phase {
        case .checking:
            Text(L10n.Voice.checkingKey(service.title)).modeText(.smallEmphasis, color: ModeColor.tint)
                .padding(.horizontal, ModeSpacing.xs + ModeSpacing.xxs)
        case .failed:
            ModeKeyResult(.failed, title: String(localized: L10n.Voice.keyDidntWork), message: VoiceTroubleText.keyFailed(check?.trouble, service: service),
                          link: service.site.map { (L10n.Voice.openSite($0.name), $0.url) })
        case .saved:
            ModeKeyResult(.saved, title: L10n.Voice.savedConnected(service.title),
                          message: service.hearing ? L10n.Voice.nowListens(service.title) : L10n.Voice.nowSpeaks(service.title))
        case .editing:
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                if let page = service.keyPage {
                    ModeVoiceLink(editing == nil ? L10n.Voice.getFreeKey(service.site?.name ?? service.title) : L10n.Voice.openKeys(service.title), url: page)
                }
                if editing != nil, let onRemove {
                    Button { onRemove() } label: {
                        Text(L10n.Voice.removeKey).modeText(.subheadlineEmphasis, color: ModeColor.danger)
                            .frame(minHeight: ModeSize.touch - ModeSpacing.l)
                    }
                    .buttonStyle(ModePressStyle())
                }
            }
            .padding(.horizontal, ModeSpacing.xs + ModeSpacing.xxs)
        }
    }
}

// MARK: - Choose a voice

@MainActor
@Observable
final class VoicePickerModel {
    struct Voice: Identifiable, Equatable { let id: String; let name: String; let traits: String; let tag: String?; var tagTone: ModeVoiceTag.Tone = .orange }

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
                    : v.tag == Voices.companion.OUR_PICK ? String(localized: L10n.Voice.ourPick) : v.tag,
                  tagTone: v.tag == Voices.companion.POPULAR ? .amber : v.tag == Voices.companion.OUR_PICK ? .orange : .green)
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
                VoicePageHeader(title: L10n.Voice.chooseVoice, subtitle: nil).padding(.bottom, -ModeSpacing.l + ModeSpacing.s)
                ModeVoiceSwitch([(VoiceService.speechify, L10n.Voice.speechify), (.apple, L10n.Voice.apple), (.elevenlabs, L10n.Voice.eleven), (.fish, L10n.Voice.fish)],
                                selection: Binding(get: { state.service }, set: { onEvent(.service($0)) }))
                Text(banner).modeText(.footnote, color: ModeColor.textSecondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, ModeSpacing.m + ModeSpacing.xxs)
                    .padding(.vertical, ModeSpacing.m)
                    .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.bubble))
                    .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.bubble))
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
                    ModeVoiceButton(LocalizedStringResource(stringLiteral: L10n.Voice.use(name)), style: .ink, height: ModeSize.primaryButton) {
                        onEvent(.use); done()
                    }
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.s)
            .animation(ModeMotion.standard, value: state.playing)
        }
        .modeScreen()
        .voicePageChrome(L10n.Voice.chooseVoice)
    }

    private var banner: LocalizedStringResource {
        switch state.service {
        case .speechify: L10n.Voice.speechifyBanner
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
            ProgressView().tint(ModeColor.brand).frame(maxWidth: .infinity, minHeight: ModeSize.rowMin)
        } else {
            ModeSearchField(String(localized: L10n.Voice.searchVoices), text: $query)
            if !state.groups.isEmpty {
                ScrollView(.horizontal) {
                    HStack(spacing: ModeSpacing.s) {
                        ModeFilterChip(String(localized: L10n.Voice.all), selected: state.group == nil) { onEvent(.group(nil)) }
                        ForEach(state.groups, id: \.self) { g in ModeFilterChip(g, selected: state.group == g) { onEvent(.group(g)) } }
                    }
                    .padding(.horizontal, ModeSpacing.screenEdge)
                }
                .scrollIndicators(.hidden)
                .padding(.horizontal, -ModeSpacing.screenEdge)
            }
            if state.voices.isEmpty {
                Text(L10n.Voice.noMatch).modeText(.callout, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
            } else {
                ModeCard(lifted: true) {
                    ForEach(state.voices) { v in
                        ModeVoiceChoiceRow(name: v.name, traits: v.traits, tag: v.tag, tagTone: v.tagTone, selected: v.id == state.selected,
                                           playing: state.playing == v.id, playLabel: L10n.Voice.play(v.name), barColor: ModeColor.onAccent,
                                           minHeight: ModeSize.voicePickerRow,
                                           onSelect: { onEvent(.select(v.id)) }, onPlay: { onEvent(.play(v.id)) })
                    }
                }
                VoiceNote(text: L10n.Voice.pickerNote)
            }
        }
    }
}

struct VoicePickerRoute: View {
    @LazyState private var model: VoicePickerModel
    @Environment(\.dismiss) private var dismiss

    init(app: AppModel, provider: String) {
        _model = LazyState(wrappedValue: VoicePickerModel(screens: app.screens, provider: provider))
    }

    var body: some View {
        VoicePickerScreen(state: model.state, query: $model.query, onEvent: model.send) { dismiss() }
            .modeBottomSheet(item: $model.linking, height: ModeSize.keySheet, onDismiss: { model.keySheetClosed() }) { service in
                VoiceKeySheet(service: service, masked: nil, check: model.keyCheck) { model.send(.saveKey($0)) }
            }
            .onDisappear { model.stop() }
    }
}
