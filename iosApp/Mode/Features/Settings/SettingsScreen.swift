import ModeDesignSystem
import ModeKit
import SwiftUI

/// Settings, as on its board: You, AI (the provider in use and its models), Limits, Memory. Draws state, sends events.
struct SettingsScreen: View {
    let state: SettingsViewModel.State
    let providers: ProvidersViewModel.State
    @Binding var name: String
    let costStep: Double
    let costRange: ClosedRange<Double>
    let dailyCapStep: Double
    let dailyCapRange: ClosedRange<Double>
    let onEvent: (SettingsViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                ModeSectionHeader(L10n.Settings.you)
                ModeCard {
                    ModeRow(String(localized: L10n.Settings.name)) {
                        TextField(text: $name) { Text(L10n.Settings.addName) }
                            .multilineTextAlignment(.trailing)
                            .modeText(.body, color: ModeColor.textSecondary)
                            .submitLabel(.done)
                            .onSubmit { onEvent(.saveName) }
                    }
                }

                ModeSectionHeader(L10n.Voice.speech)
                ModeCard {
                    ModeRow(String(localized: L10n.Voice.speechInUse), leading: .icon(.microphone), action: { onEvent(.showSpeech) }) { ModeChevron() }
                }
                ModeSectionHeader(L10n.Voice.hearing)
                ModeCard {
                    ModeRow(String(localized: L10n.Voice.hearingInUse), leading: .icon(.microphone), action: { onEvent(.showHearing) }) { ModeChevron() }
                }

                ModeSectionHeader(L10n.Settings.ai)
                ModeCard {
                    HStack(spacing: ModeSpacing.m) {
                        Button { onEvent(.openProvider) } label: {
                        HStack(spacing: ModeSpacing.m) {
                            ModeLeadingView(.logoTile(providers.activeKind.logo, background: providers.activeKind.tileBackground,
                                                      foreground: providers.activeKind.tileForeground))
                            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                                Text(L10n.Providers.aiInUse).modeText(.caption, color: ModeColor.textSecondary)
                                Text(providers.activeTitle).modeText(.bodyEmphasis)
                                HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.xs) {
                                    Circle().fill(providers.activeConnected ? ModeColor.success : ModeColor.separator)
                                        .frame(width: ModeSize.stepDot, height: ModeSize.stepDot)
                                    Text(providers.summary).modeText(.footnote, color: ModeColor.textSecondary)
                                }
                            }
                            .frame(maxWidth: .infinity, alignment: .leading)
                        }
                        .contentShape(.rect)
                        }
                        .buttonStyle(.plain)
                        Button { onEvent(.showProviders) } label: {
                            Text(L10n.Providers.change).modeText(.subheadline).fontWeight(.semibold)
                                .padding(.horizontal, ModeSpacing.m)
                                .frame(minHeight: ModeSize.badge)
                                .background(ModeColor.fill, in: .capsule)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.vertical, ModeSpacing.m)
                    valueRow(L10n.Settings.mainModel, value: state.modelName) { onEvent(.openPicker(stuck: false)) }
                    valueRow(L10n.Settings.whenStuckShort, value: state.stuckName) { onEvent(.openPicker(stuck: true)) }
                }

                ModeSectionHeader(L10n.Access.section)
                ModeCard {
                    ModeRow(String(localized: L10n.Access.howModeActs), leading: .icon(.shield), action: { onEvent(.openAccess) }) {
                        HStack(spacing: ModeSpacing.s) {
                            Text(state.fullAccess ? L10n.Access.full : L10n.Access.askFirstShort).modeText(.body, color: ModeColor.textSecondary)
                            ModeChevron()
                        }
                    }
                }

                ModeSectionHeader(L10n.Settings.limits)
                ModeCard {
                    if state.showsCost {
                        Stepper(value: Binding(get: { state.maxCost }, set: { onEvent(.maxCost($0)) }), in: costRange, step: costStep) {
                            ModeRow(String(localized: L10n.Settings.maxCost), subtitle: state.maxCostLabel)
                        }
                        Stepper(value: Binding(get: { state.dailyCap }, set: { onEvent(.dailyCap($0)) }), in: dailyCapRange, step: dailyCapStep) {
                            ModeRow(String(localized: L10n.Settings.dailyCap), subtitle: state.dailyCapLabel)
                        }
                    } else {
                        Text(L10n.Providers.planNote).modeText(.footnote, color: ModeColor.textSecondary).padding(.vertical, ModeSpacing.m)
                    }
                }

                ModeSectionHeader(L10n.Settings.memory)
                ModeCard {
                    ModeToggleRow(L10n.Memory.learn, isOn: Binding(get: { state.learn }, set: { onEvent(.learn($0)) }))
                    ModeRow(String(localized: L10n.Tab.memory), action: { onEvent(.openMemory) }) { ModeChevron() }
                }
                ModeCard {
                    ModeRow(String(localized: L10n.Developer.title), action: { onEvent(.openDeveloper) }) { ModeChevron() }
                }
                .padding(.top, ModeSpacing.l)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(L10n.Common.settings))
        .navigationBarTitleDisplayMode(.large)
        .onDisappear { onEvent(.saveName) }
    }

    private func valueRow(_ title: LocalizedStringResource, value: String, action: @escaping () -> Void) -> some View {
        ModeValueRow(String(localized: title), value: value, action: action).frame(minHeight: ModeSize.rowMin)
    }
}

/// One model for the main job, or for when Mode gets stuck, as on the "pick a model" board:
/// a search field, chips (All, Recommended, Cheapest, makers), and the models with their tags.
struct ModelPickerScreen: View {
    let state: SettingsViewModel.State
    let listed: [SettingsViewModel.Model]
    let filter: SettingsViewModel.Filter
    let stuck: Bool
    @Binding var query: String
    let onEvent: (SettingsViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                ModeSearchField(L10n.Settings.searchCount(state.modelCount), text: $query)
                    .onChange(of: query) { onEvent(.search) }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: ModeSpacing.s) {
                        chip(L10n.Settings.all, .all)
                        chip(L10n.Settings.recommended, .recommended)
                        if state.showsCost { chip(L10n.Settings.cheapest, .cheapest) }
                        ForEach(state.makers, id: \.self) { maker in
                            ModeFilterChip(SettingsViewModel.makerName(maker), selected: filter == .maker(maker)) { onEvent(.filter(.maker(maker))) }
                        }
                    }
                }
                .scrollClipDisabled()
                ModeCard {
                    if stuck {
                        ModeChoiceRow(String(localized: L10n.Settings.never), tag: nil, detail: String(localized: L10n.Settings.neverDetail),
                                      leading: .none, selected: state.neverStuck) { onEvent(.chooseStuck(nil)) }
                    }
                    ForEach(listed) { model in
                        ModeChoiceRow(model.name, tag: model.tag, detail: model.subtitle, leading: model.leading,
                                      selected: stuck ? model.isStuck : model.isMain) {
                            onEvent(stuck ? .chooseStuck(model.id) : .chooseModel(model.id))
                        }
                    }
                }
                if listed.isEmpty {
                    Text(L10n.Settings.noModels).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
                }
                Text(state.showsCost ? L10n.Settings.pickerNote : L10n.Settings.pickerPlanNote)
                    .modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .scrollDismissesKeyboard(.interactively)
        .modeScreen()
        .navigationTitle(Text(stuck ? L10n.Settings.whenStuckShort : L10n.Settings.mainModel))
        .navigationBarTitleDisplayMode(.large)
    }

    private func chip(_ title: LocalizedStringResource, _ value: SettingsViewModel.Filter) -> some View {
        ModeFilterChip(String(localized: title), selected: filter == value) { onEvent(.filter(value)) }
    }
}

struct SettingsRoute: View {
    let app: AppModel
    @State private var model: SettingsViewModel
    @State private var providers: ProvidersViewModel

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        self.app = app
        _model = State(initialValue: SettingsViewModel(screens: app.screens, navigate: navigate))
        _providers = State(initialValue: ProvidersViewModel(screens: app.screens, picksUnconnected: false))
    }

    var body: some View {
        SettingsScreen(state: model.state, providers: providers.state, name: $model.name, costStep: model.costStep,
                       costRange: model.costRange, dailyCapStep: model.dailyCapStep, dailyCapRange: model.dailyCapRange, onEvent: model.send)
            .sheet(isPresented: $model.showingProviders) {
                ProvidersSheet(app: app, state: providers.state, onEvent: providers.send) { model.showingProviders = false }
                    .modifier(ProviderLinks(model: providers, app: app))
            }
            .sheet(isPresented: $model.showingSpeech) { VoiceSheet(app: app, hearing: false) }
            .sheet(isPresented: $model.showingHearing) { VoiceSheet(app: app, hearing: true) }
            #if DEBUG && targetEnvironment(simulator)
            // Simulator only: MODE_OPEN=settings:providers opens the providers sheet, for comparing with its board.
            .task { if ProcessInfo.processInfo.environment["MODE_OPEN"] == "settings:providers" { model.send(.showProviders) } }
            #endif
    }
}

struct ModelPickerRoute: View {
    @State private var model: SettingsViewModel
    let stuck: Bool

    init(app: AppModel, stuck: Bool, navigate: @escaping (Route) -> Void) {
        self.stuck = stuck
        _model = State(initialValue: SettingsViewModel(screens: app.screens, navigate: navigate))
    }

    var body: some View {
        ModelPickerScreen(state: model.state, listed: model.listed, filter: model.filter, stuck: stuck, query: $model.query, onEvent: model.send)
    }
}

/// Speech or hearing, in the same shape as the AI provider list: order is the fallback, a key is pasted.
@MainActor
@Observable
final class VoiceListModel {
    struct Row: Identifiable, Equatable {
        let id: String
        let title: String
        let status: String
        let ready: Bool
        let first: Bool
        let needsKey: Bool
    }

    private(set) var rows: [Row] = []
    var key = ""
    var linking: String?
    private let presenter: VoicePresenter
    private var updates: Updates?
    private let hearing: Bool

    init(screens: Screens, hearing: Bool) {
        self.hearing = hearing
        presenter = screens.voice(hearing: hearing)
        updates = observe(presenter.state) { [weak self] s in self?.rows = Self.map(s) }
    }

    func moveUp(_ id: String) { presenter.moveUp(id: id) }
    func save() {
        guard let linking else { return }
        presenter.saveKey(id: linking, key: key)
        key = ""
        self.linking = nil
    }
    func disconnect(_ id: String) { presenter.disconnect(id: id) }

    private static func map(_ s: VoiceUiState) -> [Row] {
        s.rows.map { row in
            let needsKey = row.id != "apple"
            let status: String = if row.first && row.ready {
                String(localized: L10n.Voice.first)
            } else if let ending = row.keyEnding {
                String(localized: L10n.Providers.keyEnding(ending))
            } else {
                switch row.block {
                case "NoKey": String(localized: L10n.Voice.noKey)
                case "Permission": String(localized: L10n.Voice.permission)
                case "Denied": String(localized: L10n.Voice.denied)
                case "Asset": String(localized: L10n.Voice.asset)
                case "Unsupported": String(localized: L10n.Voice.unsupported)
                default: String(localized: row.ready ? L10n.Voice.ready : L10n.Voice.unsupported)
                }
            }
            return Row(id: row.id, title: title(row.id), status: status, ready: row.ready, first: row.first, needsKey: needsKey)
        }
    }

    private static func title(_ id: String) -> String {
        switch id {
        case "apple": String(localized: L10n.Voice.apple)
        case "fish": String(localized: L10n.Voice.fish)
        case "elevenlabs": String(localized: L10n.Voice.eleven)
        case "soniox": String(localized: L10n.Voice.soniox)
        case "deepgram": String(localized: L10n.Voice.deepgram)
        default: id
        }
    }
}

struct VoiceSheet: View {
    let app: AppModel
    let hearing: Bool
    @State private var model: VoiceListModel
    @Environment(\.dismiss) private var dismiss

    init(app: AppModel, hearing: Bool) {
        self.app = app
        self.hearing = hearing
        _model = State(initialValue: VoiceListModel(screens: app.screens, hearing: hearing))
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                Text(hearing ? L10n.Voice.hearing : L10n.Voice.speech).modeText(.title)
                ModeCard {
                    ForEach(model.rows) { row in
                        ModeRow(row.title, subtitle: row.status) {
                            HStack(spacing: ModeSpacing.s) {
                                if row.id != "apple" {
                                    Button(String(localized: L10n.Voice.moveUp)) { model.moveUp(row.id) }
                                        .buttonStyle(.plain)
                                }
                                if row.needsKey {
                                    Button(String(localized: row.ready ? L10n.Providers.disconnect : L10n.Providers.connect)) {
                                        if row.ready { model.disconnect(row.id) } else { model.linking = row.id }
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                }
                if model.linking == "elevenlabs" {
                    Text(L10n.Voice.elevenNote).modeText(.footnote, color: ModeColor.textSecondary)
                }
            }
            .padding(ModeSpacing.screenEdge)
        }
        .modeScreen()
        .presentationDetents([.medium, .large])
        .sheet(isPresented: Binding(get: { model.linking != nil }, set: { if !$0 { model.linking = nil } })) {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                Text(L10n.Voice.key).modeText(.title)
                TextField(text: $model.key) { Text(L10n.Voice.key) }
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                Button(String(localized: L10n.Common.save)) { model.save() }
                    .buttonStyle(.borderedProminent)
            }
            .padding(ModeSpacing.screenEdge)
            .modeScreen()
            .presentationDetents([.medium])
        }
        .task { await AppleVoice.shared.prepareHearing() }
    }
}

struct ListenSheet: View {
    let app: AppModel
    @State private var model: ListenModel
    @State private var typed = ""
    @Environment(\.dismiss) private var dismiss

    init(app: AppModel) {
        self.app = app
        _model = State(initialValue: ListenModel(screens: app.screens))
    }

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            Text(L10n.Voice.listening).modeText(.title)
            Text(model.phase == "typing" ? L10n.Voice.typeInstead : L10n.Voice.listeningHint)
                .modeText(.body, color: ModeColor.textSecondary)
            if !model.transcript.isEmpty {
                Text(model.transcript).modeText(.body)
            }
            if model.phase == "typing" {
                TextField(text: $typed) { Text(L10n.Voice.typeInstead) }
                Button(String(localized: L10n.Voice.send)) { model.submit(typed) }
                    .buttonStyle(.borderedProminent)
            }
        }
        .padding(ModeSpacing.screenEdge)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .modeScreen()
        .task { model.begin() }
        .onDisappear { model.stop() }
    }
}

@MainActor
@Observable
final class ListenModel {
    private(set) var phase = "listening"
    private(set) var transcript = ""
    private let presenter: ListenPresenter
    private var updates: Updates?

    init(screens: Screens) {
        presenter = screens.listen()
        updates = observe(presenter.state) { [weak self] s in
            self?.phase = s.phase
            self?.transcript = s.transcript
        }
    }

    func begin() { presenter.begin() }
    func submit(_ text: String) { presenter.submitTyped(text: text) }
    func stop() { presenter.stop() }
}
