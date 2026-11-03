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
}

// MARK: - The provider lists (Mode's voice, Listening)

@MainActor
@Observable
final class VoiceListModel {
    struct Row: Identifiable, Equatable {
        let service: VoiceService
        var id: String { service.id }
        let status: String
        let ready: Bool
        let first: Bool
        let connected: Bool
        /// Apple hearing: off and can be turned on here; or the microphone is off in iPhone Settings; or its model downloads.
        let canTurnOn: Bool
        let denied: Bool
        let downloading: Bool
    }

    struct State: Equatable {
        var rows: [Row] = []
        var speaker: VoiceService?
        var voiceName = ""
        var speed = "normal"
        var speakResult = true
        var playing = false
    }

    enum Event { case moveTo(String, Int), addKey(VoiceService), saveKey(String), removeKey(VoiceService), turnOnApple, playSample, speed(String), speakResult(Bool), changeVoice }

    private(set) var state = State()
    var linking: VoiceService?
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
        case let .moveTo(id, index): presenter.moveTo(id: id, index: Int32(index))
        case let .addKey(service): linking = service
        case let .saveKey(key):
            if let linking { presenter.saveKey(id: linking.id, key: key) }
            linking = nil
        case let .removeKey(service): presenter.disconnect(id: service.id)
        case .turnOnApple: presenter.prepareApple()
        case .playSample: presenter.playSample()
        case let .speed(id): presenter.setSpeed(id: id)
        case let .speakResult(on): presenter.setSpeakResult(on: on)
        case .changeVoice: navigate(.voicePicker(provider: state.speaker?.id ?? VoiceService.apple.id))
        }
    }

    func stop() { presenter.stopSample() }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: VoiceUiState, hearing: Bool) -> State {
        let speaker = VoiceService(id: s.speaker)
        return State(
            rows: s.rows.compactMap { row in
                guard let service = VoiceService(id: row.id) else { return nil }
                let plan = service.plan(hearing: hearing)
                return Row(service: service, status: row.keyEnding.map { L10n.Voice.withKey(plan, $0) } ?? plan,
                           ready: row.ready, first: row.first, connected: row.keyEnding != nil,
                           canTurnOn: service == .apple && hearing && row.block == "Permission",
                           denied: service == .apple && hearing && row.block == "Denied",
                           downloading: service == .apple && hearing && row.block == "Asset")
            },
            speaker: speaker,
            voiceName: s.voiceName ?? String(localized: L10n.Voice.defaultVoice),
            speed: s.speed, speakResult: s.speakResult, playing: s.playing
        )
    }
}

/// One provider in the try order: its tile, plan line, state on the right, and a handle to drag it.
private struct VoiceProviderRow: View {
    let row: VoiceListModel.Row
    let hearing: Bool
    let onEvent: (VoiceListModel.Event) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            ModeRow(row.service.title, subtitle: row.status, leading: row.service.tile, emphasis: .prominent) {
                HStack(spacing: ModeSpacing.m) {
                    trailing
                    ModeIcon.dragHandle.image.foregroundStyle(ModeColor.textSecondary).accessibilityHidden(true)
                }
            }
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
        .contentShape(.rect)
        .contextMenu {
            if row.connected { Button(role: .destructive) { onEvent(.removeKey(row.service)) } label: { Text(L10n.Voice.removeKey) } }
        }
    }

    @ViewBuilder private var trailing: some View {
        if row.first && row.ready {
            ModeStatusPill(String(localized: L10n.Voice.inUse), tone: .success)
        } else if row.ready {
            ModeStatusPill(String(localized: L10n.Voice.ready), tone: .accent)
        } else if row.service != .apple {
            Button { onEvent(.addKey(row.service)) } label: { ModeStatusPill(String(localized: L10n.Voice.addKey), tone: .accent) }
                .buttonStyle(.plain)
                .frame(minHeight: ModeSize.touch)
        } else if row.downloading {
            ModeStatusPill(String(localized: L10n.Voice.downloading), tone: .neutral)
        } else {
            ModeStatusPill(String(localized: L10n.Voice.off), tone: .neutral)
        }
    }
}

/// The providers in try order; drag one onto another to put it there.
private struct VoiceProviderList: View {
    let rows: [VoiceListModel.Row]
    let hearing: Bool
    let onEvent: (VoiceListModel.Event) -> Void

    var body: some View {
        ModeCard {
            ForEach(Array(rows.enumerated()), id: \.element.id) { index, row in
                VoiceProviderRow(row: row, hearing: hearing, onEvent: onEvent)
                    .draggable(row.id)
                    .dropDestination(for: String.self) { items, _ in
                        guard let moved = items.first, moved != row.id else { return false }
                        onEvent(.moveTo(moved, index))
                        return true
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
                VoiceProviderList(rows: state.rows, hearing: false, onEvent: onEvent)
                note(L10n.Voice.whoSpeaksNote)

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
                VoiceProviderList(rows: state.rows, hearing: true, onEvent: onEvent)
                Text(L10n.Voice.listeningNote).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
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

/// Mode's voice or Listening, with the key sheet.
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
        .sheet(item: $model.linking) { service in
            VoiceKeySheet(service: service) { model.send(.saveKey($0)) }
        }
        .onDisappear { model.stop() }
    }
}

// MARK: - Connect a key

/// Paste a voice service's key: what it is, its free plan, three steps, the key, Connect.
struct VoiceKeySheet: View {
    let service: VoiceService
    let save: (String) -> Void
    @State private var key = ""
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.l) {
                HStack(spacing: ModeSpacing.m) {
                    ModeLeadingView(service.tile).frame(width: ModeSize.contactAvatar, height: ModeSize.contactAvatar)
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        Text(L10n.Voice.connect(service.title)).modeText(.title)
                        Text(service.hearing ? L10n.Voice.hearingPurpose : L10n.Voice.speechPurpose).modeText(.subheadline, color: ModeColor.textSecondary)
                    }
                }
                ModeStatusPill(service.plan(hearing: service.hearing), tone: .success)
                VStack(alignment: .leading, spacing: ModeSpacing.m) {
                    step(1, L10n.Voice.stepSignIn(service.title))
                    step(2, String(localized: L10n.Voice.stepCreateKey))
                    step(3, String(localized: L10n.Voice.stepPaste))
                }
                if let page = service.keyPage {
                    Link(destination: page) {
                        Label { Text(L10n.Voice.open(service.title)) } icon: { ModeIcon.external.image }
                            .modeText(.body).fontWeight(.semibold)
                            .frame(maxWidth: .infinity, minHeight: ModeSize.touch)
                            .background(ModeColor.fill, in: .capsule)
                    }
                    .buttonStyle(.plain)
                }
                ModeTextField(LocalizedStringResource(stringLiteral: L10n.Voice.keyField(service.title)), prompt: L10n.Voice.key, text: $key, secure: true)
                ModeNote(icon: .lock, LocalizedStringResource(stringLiteral: L10n.Voice.keyNote(service.title)))
            }
            .padding(ModeSpacing.screenEdge)
        }
        .safeAreaInset(edge: .bottom) {
            ModeButton(L10n.Voice.connectButton, fullWidth: true) { save(key); dismiss() }
                .disabled(key.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                .padding(.horizontal, ModeSpacing.screenEdge)
                .padding(.bottom, ModeSpacing.s)
        }
        .modeScreen()
        .presentationDetents([.large])
    }

    private func step(_ n: Int, _ text: String) -> some View {
        HStack(alignment: .top, spacing: ModeSpacing.m) {
            ModeLeadingView(.number(n))
            Text(text).modeText(.callout).frame(maxWidth: .infinity, alignment: .leading)
        }
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
    private let presenter: VoicePickerPresenter
    private let keys: VoicePresenter
    private var updates: Updates?

    init(screens: Screens, provider: String) {
        presenter = screens.voicePicker(provider: provider)
        keys = screens.voice(hearing: false)
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
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
            if let linking { keys.saveKey(id: linking.id, key: key) }
            linking = nil
            presenter.reload()
        }
    }

    func stop() { presenter.stopSample() }

    isolated deinit {
        updates?.cancel()
        presenter.close()
        keys.close()
    }

    private static func map(_ s: VoicePickerUiState) -> State {
        let voices = s.voices.map { v in
            Voice(id: v.id, name: v.name, traits: v.traits,
                  tag: v.tag == Voices.companion.POPULAR ? String(localized: L10n.Voice.popular) : v.tag)
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
                    VStack(spacing: ModeSpacing.xxs) {
                        ForEach(state.voices) { v in
                            ModeVoiceRow(name: v.name, traits: v.traits, tag: v.tag, selected: v.id == state.selected, playing: state.playing == v.id,
                                         playLabel: L10n.Voice.play(v.name), onSelect: { onEvent(.select(v.id)) }, onPlay: { onEvent(.play(v.id)) })
                        }
                    }
                    .padding(.vertical, ModeSpacing.s)
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
            .sheet(item: $model.linking) { service in VoiceKeySheet(service: service) { model.send(.saveKey($0)) } }
            .onDisappear { model.stop() }
    }
}
