import ModeDesignSystem
import SwiftUI

/// Setup's "Mode's voice" and "How Mode listens" sheets (boards SetupSpeakPicker, SetupListenPicker): the providers
/// with the same key sheets and provider pages as Settings, and for speech the chosen provider's first voices.
struct VoiceSettingsSheet: View {
    let app: AppModel
    let hearing: Bool
    @State private var path: [Route] = []
    @State private var list: VoiceListModel
    @State private var voices: VoicePickerModel?
    @Environment(\.dismiss) private var dismiss

    init(app: AppModel, hearing: Bool) {
        self.app = app
        self.hearing = hearing
        let list = VoiceListModel(screens: app.screens, hearing: hearing, navigate: { _ in })
        _list = State(initialValue: list)
        _voices = State(initialValue: hearing ? nil : VoicePickerModel(screens: app.screens, provider: list.state.speaker?.id ?? VoiceService.apple.id))
    }

    var body: some View {
        NavigationStack(path: $path) {
            VoiceSetupSheetScreen(hearing: hearing, state: list.state, voices: voices?.state, onEvent: list.send,
                                  onVoice: { voices?.send($0) }, onClose: { dismiss() })
                .voiceSheets(list)
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: Route.self) { route in
                    if case let .voicePicker(provider) = route { VoicePickerRoute(app: app, provider: provider) }
                }
        }
        .onChange(of: list.state.speaker) { _, speaker in voices?.send(.service(speaker ?? .apple)) }
        .onDisappear { list.stop(); voices?.stop() }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(ModeRadius.sheet)
        .presentationBackground(ModeColor.page)
    }
}

/// The sheet's page: its title, the providers, and (speech) the voices to pick, with Use or Done at the bottom.
struct VoiceSetupSheetScreen: View {
    let hearing: Bool
    let state: VoiceListModel.State
    let voices: VoicePickerModel.State?
    let onEvent: (VoiceListModel.Event) -> Void
    let onVoice: (VoicePickerModel.Event) -> Void
    let onClose: () -> Void

    private var shown: [VoicePickerModel.Voice] {
        guard let voices, !voices.loading, !voices.needsKey, !voices.failed else { return [] }
        return Array(voices.voices.prefix(3))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                    ModeSheetHeader(String(localized: hearing ? L10n.Voice.listenTitle : L10n.Voice.modesVoice), mood: nil,
                                    closeLabel: L10n.Voice.close, onClose: onClose)
                    Text(hearing ? L10n.Voice.listenSubtitle : L10n.Voice.speakSubtitle).modeText(.small, color: ModeColor.textSecondary)
                }
                .padding(.horizontal, ModeSpacing.xs)
                .padding(.bottom, ModeSpacing.l)
                VoiceProviderPicker(rows: state.rows, hearing: hearing, onEvent: onEvent, letters: false)
                VoiceNote(text: L10n.Voice.tapConnected)
                if hearing {
                    VoiceKeysNote().padding(.top, ModeSpacing.s)
                } else if !shown.isEmpty, let voices {
                    ModeSectionHeader(LocalizedStringResource(stringLiteral: L10n.Voice.voicesFrom(voices.service.title)))
                    ModeCard {
                        ForEach(shown) { v in
                            ModeVoiceChoiceRow(name: v.name, traits: v.traits, tag: v.tag, tagTone: v.tagTone, selected: v.id == voices.selected,
                                               playing: voices.playing == v.id, playLabel: L10n.Voice.play(v.name),
                                               minHeight: ModeSize.voicePickerRow,
                                               onSelect: { onVoice(.select(v.id)) }, onPlay: { onVoice(.play(v.id)) })
                        }
                    }
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.top, ModeSpacing.xl)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaInset(edge: .bottom) {
            Group {
                if !hearing, let name = voices?.selectedName {
                    ModeVoiceButton(LocalizedStringResource(stringLiteral: L10n.Voice.use(name)), style: .ink, height: ModeSize.primaryButton) {
                        onVoice(.use); onClose()
                    }
                } else {
                    ModeVoiceButton(L10n.Common.done, style: .ink, height: ModeSize.primaryButton, action: onClose)
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.top, ModeSpacing.xl)
            .padding(.bottom, ModeSpacing.s)
            .background(ModeEdgeFade(edge: .bottom).ignoresSafeArea())
        }
        .modeScreen()
    }
}

/// The same searchable, untruncated voice catalog used by Settings, presented as a bottom sheet.
struct AllVoicesSheet: View {
    let app: AppModel
    let provider: String
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            VoicePickerRoute(app: app, provider: provider)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        ModeToolbarButton(.close, label: L10n.Voice.close) { dismiss() }
                    }
                }
        }
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
}

/// A recommendation opens the same key check/save UI used when connecting from Settings.
struct ConnectVoiceSheet: View {
    let app: AppModel
    let service: VoiceService
    @State private var model: VoiceListModel

    init(app: AppModel, service: VoiceService) {
        self.app = app
        self.service = service
        _model = State(initialValue: VoiceListModel(screens: app.screens, hearing: service.hearing, navigate: { _ in }))
    }

    var body: some View {
        Group {
            if let row = model.state.rows.first(where: { $0.service == service }) {
                if row.connected {
                    AllVoicesSheet(app: app, provider: service.id)
                } else {
                    VoiceKeySheet(service: service, masked: row.masked,
                                  check: model.state.keyCheck?.id == service.id ? model.state.keyCheck : nil) {
                        model.send(.checkKey(service, $0))
                    }
                    .presentationDetents([.height(ModeSize.keySheet), .large])
                    .presentationDragIndicator(.visible)
                    .presentationCornerRadius(ModeRadius.sheet)
                    .presentationBackground(ModeColor.page)
                }
            } else {
                ProgressView().tint(ModeColor.tint)
            }
        }
        .onDisappear { model.send(.keySheetClosed); model.stop() }
    }
}
