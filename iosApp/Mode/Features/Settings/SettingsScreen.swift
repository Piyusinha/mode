import ModeDesignSystem
import SwiftUI

/// Settings. Draws state, sends events.
struct SettingsScreen: View {
    let state: SettingsViewModel.State
    @Binding var name: String
    @Binding var key: String
    @Binding var query: String
    let costStep: Double
    let costRange: ClosedRange<Double>
    let dailyCapStep: Double
    let dailyCapRange: ClosedRange<Double>
    let canSaveKey: Bool
    let onEvent: (SettingsViewModel.Event) -> Void

    var body: some View {
        List {
            Section {
                ModeTextField(L10n.Settings.name, prompt: L10n.Settings.namePrompt, text: $name)
                    .onSubmit { onEvent(.saveName) }
                ModeTextField(L10n.Settings.key, prompt: L10n.Settings.keyPrompt, text: $key, secure: true)
                Text(state.keyStatus).modeText(.footnote, color: state.hasKey ? ModeColor.textSecondary : ModeColor.warning)
                if canSaveKey { ModeButton(L10n.Settings.saveKey) { onEvent(.saveKey) } }
            } header: { ModeSectionHeader(L10n.Settings.you) }

            Section {
                ForEach(state.picks) { model in modelRow(model, selected: model.isMain) { onEvent(.chooseModel(model.id)) } }
                ModeTextField(L10n.Settings.search, prompt: L10n.Settings.search, text: $query)
                    .onChange(of: query) { onEvent(.search) }
                ForEach(state.results) { model in modelRow(model, selected: model.isMain) { onEvent(.chooseModel(model.id)) } }
            } header: { ModeSectionHeader(L10n.Settings.model) }

            Section {
                ForEach(state.picks) { model in modelRow(model, selected: model.isStuck) { onEvent(.chooseStuck(model.id)) } }
                ModeRow(String(localized: L10n.Settings.never), action: { onEvent(.chooseStuck(nil)) }) {
                    if state.neverStuck { ModeIcon.done.image.foregroundStyle(ModeColor.tint) }
                }
            } header: { ModeSectionHeader(L10n.Settings.whenStuck) }

            Section {
                Stepper(value: Binding(get: { state.maxCost }, set: { onEvent(.maxCost($0)) }), in: costRange, step: costStep) {
                    ModeRow(String(localized: L10n.Settings.maxCost), subtitle: state.maxCostLabel)
                }
                Stepper(value: Binding(get: { state.dailyCap }, set: { onEvent(.dailyCap($0)) }), in: dailyCapRange, step: dailyCapStep) {
                    ModeRow(String(localized: L10n.Settings.dailyCap), subtitle: state.dailyCapLabel)
                }
            } header: { ModeSectionHeader(L10n.Settings.limits) }

            Section {
                ModeToggleRow(L10n.Memory.learn, isOn: Binding(get: { state.learn }, set: { onEvent(.learn($0)) }))
                ModeRow(String(localized: L10n.Developer.title), leading: .icon(.developer)) { onEvent(.openDeveloper) }
            } header: { ModeSectionHeader(L10n.Settings.memory) }
        }
        .listStyle(.insetGrouped)
        .modeScreen()
        .navigationTitle(Text(L10n.Common.settings))
        .onDisappear { onEvent(.saveName) }
    }

    private func modelRow(_ model: SettingsViewModel.Model, selected: Bool, action: @escaping () -> Void) -> some View {
        ModeRow(model.name, subtitle: model.subtitle, action: action) {
            if selected { ModeIcon.done.image.foregroundStyle(ModeColor.tint) }
        }
    }
}

struct SettingsRoute: View {
    @State private var model: SettingsViewModel

    init(app: AppModel, navigate: @escaping (Route) -> Void) {
        _model = State(initialValue: SettingsViewModel(screens: app.screens, navigate: navigate))
    }

    var body: some View {
        SettingsScreen(state: model.state, name: $model.name, key: $model.key, query: $model.query,
                       costStep: model.costStep, costRange: model.costRange, dailyCapStep: model.dailyCapStep,
                       dailyCapRange: model.dailyCapRange, canSaveKey: model.canSaveKey, onEvent: model.send)
    }
}
