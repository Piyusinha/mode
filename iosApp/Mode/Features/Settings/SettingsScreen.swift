import ModeDesignSystem
import SwiftUI

/// Settings: a short list. Each part opens its own screen. Draws state, sends events.
struct SettingsScreen: View {
    let state: SettingsViewModel.State
    let providers: ProvidersViewModel.State
    @Binding var name: String
    let onEvent: (SettingsViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.l) {
                ModeCard {
                    ModeRow(String(localized: L10n.Settings.name)) {
                        TextField(text: $name) { Text(L10n.Settings.addName) }
                            .multilineTextAlignment(.trailing)
                            .modeText(.body, color: ModeColor.textSecondary)
                            .submitLabel(.done)
                            .onSubmit { onEvent(.saveName) }
                    }
                }
                ModeCard {
                    ModeRow(String(localized: L10n.Settings.ai), subtitle: [providers.activeTitle, state.modelName].filter { !$0.isEmpty }.joined(separator: " · "),
                            leading: .iconTile(providers.activeKind.icon, background: providers.activeKind.tileBackground,
                                               foreground: providers.activeKind.tileForeground),
                            action: { onEvent(.openAI) }) { ModeChevron() }
                }
                ModeCard {
                    ModeToggleRow(L10n.Memory.learn, isOn: Binding(get: { state.learn }, set: { onEvent(.learn($0)) }))
                    ModeRow(String(localized: L10n.Tab.memory), leading: .icon(.memory), action: { onEvent(.openMemory) }) { ModeChevron() }
                }
                ModeCard {
                    ModeRow(String(localized: L10n.Developer.title), leading: .icon(.developer), action: { onEvent(.openDeveloper) }) { ModeChevron() }
                }
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
}

/// AI: which provider (several can be connected), its models, and the cost limits when it bills per task.
struct AISettingsScreen: View {
    let state: SettingsViewModel.State
    let providers: ProvidersViewModel.State
    let costStep: Double
    let costRange: ClosedRange<Double>
    let dailyCapStep: Double
    let dailyCapRange: ClosedRange<Double>
    let onEvent: (SettingsViewModel.Event) -> Void
    let onProvider: (ProvidersViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                ModeSectionHeader(L10n.Providers.provider)
                ProviderList(rows: providers.rows, onEvent: onProvider)
                Text(L10n.Providers.switchNote).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)

                ModeSectionHeader(L10n.Providers.models)
                ModeCard {
                    valueRow(L10n.Settings.mainModel, value: state.modelName) { onEvent(.openPicker(stuck: false)) }
                    valueRow(L10n.Settings.whenStuckShort, value: state.stuckName) { onEvent(.openPicker(stuck: true)) }
                }

                if state.showsCost {
                    ModeSectionHeader(L10n.Settings.limits)
                    ModeCard {
                        Stepper(value: Binding(get: { state.maxCost }, set: { onEvent(.maxCost($0)) }), in: costRange, step: costStep) {
                            ModeRow(String(localized: L10n.Settings.maxCost), subtitle: state.maxCostLabel)
                        }
                        Stepper(value: Binding(get: { state.dailyCap }, set: { onEvent(.dailyCap($0)) }), in: dailyCapRange, step: dailyCapStep) {
                            ModeRow(String(localized: L10n.Settings.dailyCap), subtitle: state.dailyCapLabel)
                        }
                    }
                } else {
                    Text(L10n.Providers.planNote).modeText(.footnote, color: ModeColor.textSecondary)
                        .padding(.horizontal, ModeSpacing.xs).padding(.top, ModeSpacing.s)
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(L10n.Settings.ai))
        .navigationBarTitleDisplayMode(.large)
    }

    private func valueRow(_ title: LocalizedStringResource, value: String, action: @escaping () -> Void) -> some View {
        ModeRow(String(localized: title), action: action) {
            HStack(spacing: ModeSpacing.s) {
                Text(value).modeText(.body, color: ModeColor.textSecondary).lineLimit(1)
                ModeChevron()
            }
        }
    }
}

/// One model for the main job, or for when Mode gets stuck: search, filter by maker, pick.
struct ModelPickerScreen: View {
    let state: SettingsViewModel.State
    let stuck: Bool
    @Binding var query: String
    let onEvent: (SettingsViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                if !state.makers.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: ModeSpacing.s) {
                            ModeFilterChip(String(localized: L10n.Settings.all), selected: state.maker == nil) { onEvent(.filter(nil)) }
                            ForEach(state.makers, id: \.self) { maker in
                                ModeFilterChip(maker.capitalized, selected: state.maker == maker) { onEvent(.filter(maker)) }
                            }
                        }
                    }
                }
                ModeCard {
                    if stuck {
                        ModeRow(String(localized: L10n.Settings.never), action: { onEvent(.chooseStuck(nil)) }) {
                            ModeRadio(selected: state.neverStuck)
                        }
                    }
                    ForEach(state.listed) { model in
                        ModeRow(model.name, subtitle: model.subtitle, leading: model.leading,
                                action: { onEvent(stuck ? .chooseStuck(model.id) : .chooseModel(model.id)) }) {
                            ModeRadio(selected: stuck ? model.isStuck : model.isMain)
                        }
                    }
                }
                if state.listed.isEmpty {
                    Text(L10n.Settings.noModels).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
                }
                Text(state.showsCost ? L10n.Settings.pickerNote : L10n.Settings.pickerPlanNote)
                    .modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .searchable(text: $query, prompt: Text(L10n.Settings.search))
        .onChange(of: query) { onEvent(.search) }
        .navigationTitle(Text(stuck ? L10n.Settings.whenStuckShort : L10n.Settings.mainModel))
        .navigationBarTitleDisplayMode(.large)
    }
}

struct SettingsRoute: View {
    @State private var model: SettingsViewModel
    @State private var providers: ProvidersViewModel

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        _model = State(initialValue: SettingsViewModel(screens: app.screens, navigate: navigate))
        _providers = State(initialValue: ProvidersViewModel(screens: app.screens, picksUnconnected: false))
    }

    var body: some View {
        SettingsScreen(state: model.state, providers: providers.state, name: $model.name, onEvent: model.send)
    }
}

struct AISettingsRoute: View {
    let app: AppModel
    @State private var model: SettingsViewModel
    @State private var providers: ProvidersViewModel

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        self.app = app
        _model = State(initialValue: SettingsViewModel(screens: app.screens, navigate: navigate))
        _providers = State(initialValue: ProvidersViewModel(screens: app.screens, picksUnconnected: false))
    }

    var body: some View {
        AISettingsScreen(state: model.state, providers: providers.state, costStep: model.costStep, costRange: model.costRange,
                         dailyCapStep: model.dailyCapStep, dailyCapRange: model.dailyCapRange, onEvent: model.send, onProvider: providers.send)
            .modifier(ProviderLinks(model: providers, app: app))
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
        ModelPickerScreen(state: model.state, stuck: stuck, query: $model.query, onEvent: model.send)
    }
}
