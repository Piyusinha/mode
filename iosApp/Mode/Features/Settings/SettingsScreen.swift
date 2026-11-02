import ModeDesignSystem
import SwiftUI

/// Settings. Draws state, sends events.
struct SettingsScreen: View {
    let state: SettingsViewModel.State
    @Binding var name: String
    @Binding var key: String
    @Binding var query: String
    @Binding var claudeCode: String
    let costStep: Double
    let costRange: ClosedRange<Double>
    let dailyCapStep: Double
    let dailyCapRange: ClosedRange<Double>
    let canSaveKey: Bool
    let onEvent: (SettingsViewModel.Event) -> Void

    var body: some View {
        List {
            Section {
                ModeToggleRow(L10n.Claude.use, isOn: Binding(get: { state.useClaude }, set: { onEvent(.useClaude($0)) }))
                Text(state.claudeConnected ? L10n.Claude.connected : L10n.Claude.notConnected).modeText(.footnote, color: ModeColor.textSecondary)
                Text(L10n.Claude.note).modeText(.footnote, color: ModeColor.textSecondary)
                if state.claudeAwaitingCode {
                    Text(L10n.Claude.instructions).modeText(.footnote, color: ModeColor.textSecondary)
                    ModeTextField(L10n.Claude.code, prompt: L10n.Claude.codePrompt, text: $claudeCode, secure: true)
                    ModeButton(state.claudeSigningIn ? L10n.Claude.connecting : L10n.Claude.finish) { onEvent(.finishClaude) }
                        .disabled(state.claudeSigningIn || claudeCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    ModeButton(L10n.Claude.cancel) { onEvent(.cancelClaude) }
                } else {
                    ModeButton(state.claudeConnected ? L10n.Claude.reconnect : L10n.Claude.connect) { onEvent(.connectClaude) }
                }
                if let error = state.claudeError { Text(error).modeText(.footnote, color: ModeColor.warning) }
                if state.claudeConnected {
                    ModeButton(state.claudeChecking ? L10n.Claude.checking : L10n.Claude.check) { onEvent(.checkClaude) }
                        .disabled(state.claudeChecking)
                }
                if let check = state.claudeCheck { Text(check).modeText(.footnote, color: ModeColor.textSecondary) }
                if state.claudeConnected { ModeButton(L10n.Claude.disconnect) { onEvent(.disconnectClaude) } }
            } header: { ModeSectionHeader(L10n.Claude.title) }

            Section {
                ModeTextField(L10n.Settings.name, prompt: L10n.Settings.namePrompt, text: $name)
                    .onSubmit { onEvent(.saveName) }
                ModeTextField(L10n.Settings.key, prompt: L10n.Settings.keyPrompt, text: $key, secure: true)
                Text(state.keyStatus).modeText(.footnote, color: state.hasKey ? ModeColor.textSecondary : ModeColor.warning)
                if canSaveKey { ModeButton(L10n.Settings.saveKey) { onEvent(.saveKey) } }
            } header: { ModeSectionHeader(L10n.Settings.you) }

            Section {
                ModeRow(String(localized: L10n.ChatGPT.continueWith), subtitle: String(localized: L10n.ChatGPT.planHint), leading: .icon(.sparkle),
                        action: { onEvent(.openChatGPT) }) { ModeIcon.chevron.image.foregroundStyle(ModeColor.textSecondary) }
            } header: { ModeSectionHeader(L10n.ChatGPT.title) }

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
                if state.useClaude {
                    Text(L10n.Claude.quota).modeText(.footnote, color: ModeColor.textSecondary)
                } else {
                    Stepper(value: Binding(get: { state.maxCost }, set: { onEvent(.maxCost($0)) }), in: costRange, step: costStep) {
                        ModeRow(String(localized: L10n.Settings.maxCost), subtitle: state.maxCostLabel)
                    }
                    Stepper(value: Binding(get: { state.dailyCap }, set: { onEvent(.dailyCap($0)) }), in: dailyCapRange, step: dailyCapStep) {
                        ModeRow(String(localized: L10n.Settings.dailyCap), subtitle: state.dailyCapLabel)
                    }
                }
            } header: { ModeSectionHeader(L10n.Settings.limits) }

            Section {
                ModeToggleRow(L10n.Memory.learn, isOn: Binding(get: { state.learn }, set: { onEvent(.learn($0)) }))
                ModeRow(String(localized: L10n.Developer.title), leading: .icon(.developer), onTap: { onEvent(.openDeveloper) })
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
        SettingsScreen(state: model.state, name: $model.name, key: $model.key, query: $model.query, claudeCode: $model.claudeCode,
                       costStep: model.costStep, costRange: model.costRange, dailyCapStep: model.dailyCapStep,
                       dailyCapRange: model.dailyCapRange, canSaveKey: model.canSaveKey, onEvent: model.send)
    }
}
