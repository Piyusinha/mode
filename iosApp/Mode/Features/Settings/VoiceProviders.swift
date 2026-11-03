import ModeDesignSystem
import SwiftUI

// Who speaks and who listens, with Mode's recommendations: ElevenLabs is the most human voice, Deepgram the
// most accurate listening, Apple the free and private fallback (boards SetupVoiceProviders, SetupSpeakPicker,
// SetupListenPicker, SettingsVoiceProviders).

/// One direction's choice: the provider's mark, "ElevenLabs · Rachel", its tag and line, and a chevron.
struct VoiceChoiceRow<Trailing: View>: View {
    let service: VoiceService
    let title: String
    let detail: String
    let action: () -> Void
    @ViewBuilder var trailing: Trailing

    var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.m) {
                ModeLeadingView(service.tile)
                VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                    HStack(spacing: ModeSpacing.s) {
                        Text(title).modeText(.headline).lineLimit(1)
                        if let tag = service.recommendation { ModeTag(tag, background: ModeColor.tintSoft, foreground: ModeColor.tint) }
                    }
                    Text(detail).modeText(.subheadline, color: ModeColor.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                trailing
            }
            .padding(.vertical, ModeSpacing.m)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }
}

/// "Most accurate: Deepgram · Connect", shown while another provider listens. [compact]: one line, for setup.
struct DeepgramSuggestion: View {
    var compact = false
    let connect: () -> Void

    var body: some View {
        if compact {
            Button(action: connect) {
                HStack(spacing: ModeSpacing.s) {
                    Text(L10n.Voice.suggestDeepgram).modeText(.captionEmphasis, color: ModeColor.tint)
                    Spacer(minLength: 0)
                    Text(L10n.Voice.connectKey).modeText(.captionEmphasis, color: ModeColor.tint)
                    ModeChevron()
                }
                .padding(.horizontal, ModeSpacing.m)
                .padding(.vertical, ModeSpacing.s)
                .background(ModeColor.tintSoft, in: .rect(cornerRadius: ModeRadius.field))
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
        } else {
            full
        }
    }

    private var full: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeLeadingView(VoiceService.deepgram.tile).scaleEffect(0.75).frame(width: ModeSize.badge, height: ModeSize.badge)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(L10n.Voice.suggestDeepgram).modeText(.captionEmphasis, color: ModeColor.tint)
                Text(L10n.Voice.suggestDeepgramDetail).modeText(.caption, color: ModeColor.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            ModeButton(L10n.Voice.connectKey, action: connect)
        }
        .padding(ModeSpacing.m)
        .background(ModeColor.tintSoft, in: .rect(cornerRadius: ModeRadius.field))
    }
}

/// Mode's voice: the speaking providers (with Connect where a key is missing), then the chosen one's voices.
struct SpeakPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var picker: VoicePickerModel
    @State private var keys: VoiceListModel
    private static let shown = 8

    init(app: AppModel, provider: String) {
        _picker = State(initialValue: VoicePickerModel(screens: app.screens, provider: provider))
        _keys = State(initialValue: VoiceListModel(screens: app.screens, hearing: false, navigate: { _ in }))
    }

    private func connected(_ s: VoiceService) -> Bool { s == .apple || keys.state.rows.first { $0.service == s }?.connected == true }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach([VoiceService.apple, .elevenlabs, .fish]) { service in
                        VoiceChoiceRow(service: service, title: service.title,
                                       detail: connected(service) ? [service.plan(hearing: false), service == .apple ? nil : String(localized: L10n.Voice.connected)].compactMap { $0 }.joined(separator: " · ")
                                                                  : [service.plan(hearing: false), String(localized: L10n.Voice.needsKey)].joined(separator: " · "),
                                       action: { picker.send(.service(service)) }) {
                            if connected(service) {
                                ModeRadio(selected: picker.state.service == service)
                            } else {
                                ModeButton(L10n.Voice.connectKey, role: .secondary) { picker.send(.service(service)); picker.send(.addKey) }
                            }
                        }
                    }
                } header: { Text(L10n.Voice.speakSubtitle) }
                if connected(picker.state.service) {
                    Section {
                        ForEach(picker.state.voices.prefix(Self.shown)) { voice in
                            Button { picker.send(.select(voice.id)) } label: {
                                HStack(spacing: ModeSpacing.m) {
                                    ModePlayButton(L10n.Voice.play(voice.name), playing: picker.state.playing == voice.id, size: ModeSize.badge) { picker.send(.play(voice.id)) }
                                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                                        HStack(spacing: ModeSpacing.s) {
                                            Text(voice.name).modeText(.headline)
                                            if let tag = voice.tag { ModeTag(tag, background: ModeColor.tintSoft, foreground: ModeColor.tint) }
                                        }
                                        if !voice.traits.isEmpty { Text(voice.traits).modeText(.subheadline, color: ModeColor.textSecondary).lineLimit(1) }
                                    }
                                    .frame(maxWidth: .infinity, alignment: .leading)
                                    ModeRadio(selected: picker.state.selected == voice.id)
                                }
                                .contentShape(.rect)
                            }
                            .buttonStyle(.plain)
                        }
                    } header: { Text(L10n.Voice.voicesFrom(picker.state.service.title)) } footer: {
                        if picker.state.service != .apple { Text(L10n.Voice.speakFallback(picker.state.service.title)) }
                    }
                }
            }
            .navigationTitle(Text(L10n.Voice.modesVoice))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { ModeToolbarButton(.close, label: L10n.Voice.close) { dismiss() } } }
            .safeAreaInset(edge: .bottom) {
                if let name = picker.state.selectedName, connected(picker.state.service) {
                    ModeButton(LocalizedStringResource(stringLiteral: L10n.Voice.useVoice(name)), fullWidth: true) { picker.send(.use); dismiss() }
                        .padding(.horizontal, ModeSpacing.screenEdge)
                        .padding(.bottom, ModeSpacing.s)
                }
            }
        }
        .sheet(item: $picker.linking) { service in VoiceKeySheet(service: service) { picker.send(.saveKey($0)) } }
        .onDisappear { picker.stop() }
    }
}

/// How Mode listens: Apple, Deepgram (most accurate) and Soniox; choosing one puts it first.
struct ListenPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var list: VoiceListModel

    init(app: AppModel) {
        _list = State(initialValue: VoiceListModel(screens: app.screens, hearing: true, navigate: { _ in }))
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ForEach([VoiceService.apple, .deepgram, .soniox]) { service in
                        let row = list.state.rows.first { $0.service == service }
                        VoiceChoiceRow(service: service, title: service.title, detail: row?.status ?? service.plan(hearing: true),
                                       action: { choose(service, row: row) }) {
                            if row?.ready == true {
                                ModeRadio(selected: row?.first == true)
                            } else if service == .apple, row?.canTurnOn == true {
                                ModeButton(L10n.Voice.allowMic, role: .secondary) { list.send(.turnOnApple) }
                            } else if service != .apple {
                                ModeButton(L10n.Voice.connectKey, role: .secondary) { list.send(.addKey(service)) }
                            }
                        }
                    }
                } header: { Text(L10n.Voice.listenSubtitle) }
                Section {
                    Text(L10n.Voice.whyDeepgram).modeText(.subheadline, color: ModeColor.textSecondary)
                } header: { Text(L10n.Voice.whyDeepgramTitle) }
            }
            .navigationTitle(Text(L10n.Voice.listenTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { ModeToolbarButton(.done, label: L10n.Common.done) { dismiss() } } }
        }
        .sheet(item: $list.linking) { service in
            VoiceKeySheet(service: service) { key in
                list.send(.saveKey(key))
                list.send(.moveTo(service.id, 0))
            }
        }
    }

    private func choose(_ service: VoiceService, row: VoiceListModel.Row?) {
        if row?.ready == true { list.send(.moveTo(service.id, 0)) }
        else if service != .apple { list.send(.addKey(service)) }
    }
}
