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

/// The providers for one direction, as the AI providers are: radio for connected ones, Connect for the rest.
private struct ProviderRows: View {
    let rows: [VoiceListModel.Row]
    let onEvent: (VoiceListModel.Event) -> Void

    var body: some View {
        ForEach(rows) { row in
            ModeProviderRow(title: row.service.title, status: row.status, logo: row.service.logo,
                            tileBackground: ModeColor.textPrimary, tileForeground: ModeColor.page,
                            tag: row.chosen ? String(localized: L10n.Voice.inUse) : row.service.recommendation,
                            selected: row.chosen, connected: row.connected, connectTitle: String(localized: L10n.Voice.connectKey),
                            onSelect: { onEvent(.choose(row.service)) }, onConnect: { onEvent(.addKey(row.service)) },
                            onOpen: row.service == .apple ? nil : { onEvent(.open(row.service)) })
        }
    }
}

/// Mode's voice (board SetupSpeakPicker): who speaks, one chosen, then the chosen one's voices.
struct SpeakPickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    @State private var picker: VoicePickerModel
    @State private var keys: VoiceListModel
    private static let shown = 8

    init(app: AppModel, provider: String) {
        _picker = State(initialValue: VoicePickerModel(screens: app.screens, provider: provider))
        _keys = State(initialValue: VoiceListModel(screens: app.screens, hearing: false, navigate: { _ in }))
    }

    private var chosen: VoiceService { keys.state.rows.first { $0.chosen }?.service ?? .apple }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    ProviderRows(rows: keys.state.rows) { event in
                        if case let .choose(service) = event { picker.send(.service(service)) }
                        keys.send(event)
                    }
                } header: { Text(L10n.Voice.speakSubtitle) }
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
                } header: { Text(L10n.Voice.voicesFrom(picker.state.service.title)) }
            }
            .navigationTitle(Text(L10n.Voice.modesVoice))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { ModeToolbarButton(.close, label: L10n.Voice.close) { dismiss() } } }
            .safeAreaInset(edge: .bottom) {
                if let name = picker.state.selectedName, picker.state.service == chosen {
                    ModeButton(LocalizedStringResource(stringLiteral: L10n.Voice.useVoice(name)), fullWidth: true) { picker.send(.use); dismiss() }
                        .padding(.horizontal, ModeSpacing.screenEdge)
                        .padding(.bottom, ModeSpacing.s)
                }
            }
        }
        .voiceSheets(keys)
        .onChange(of: chosen) { _, service in if picker.state.service != service { picker.send(.service(service)) } }
        .onDisappear { picker.stop() }
    }
}

/// How Mode listens (board SetupListenPicker): Apple, Deepgram or Soniox, one chosen.
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
                    ProviderRows(rows: list.state.rows, onEvent: list.send)
                } header: { Text(L10n.Voice.listenSubtitle) } footer: { Text(L10n.Voice.keysNote) }
            }
            .navigationTitle(Text(L10n.Voice.listenTitle))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { ModeToolbarButton(.done, label: L10n.Common.done) { dismiss() } } }
        }
        .voiceSheets(list)
    }
}
