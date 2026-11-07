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
    var resetting = false
    let onEvent: (SettingsViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                ModePageTitle(L10n.Common.settings)
                ModeSectionHeader(L10n.Settings.you)
                ModeCard {
                    ModeRow(String(localized: L10n.Settings.name)) {
                        TextField(text: $name) { Text(L10n.Settings.addName) }
                            .multilineTextAlignment(.trailing)
                            .modeText(.callout, color: ModeColor.textSecondary)
                            .submitLabel(.done)
                            .onSubmit { onEvent(.saveName) }
                    }
                    ModeRow(String(localized: L10n.Settings.whatModeKnows), leading: .moBadge(.idle, .brain), action: { onEvent(.openMemory) }) {
                        ModeChevron()
                    }
                }

                ModeSectionHeader(L10n.Voice.section)
                ModeCard {
                    ModeLogoValueRow(String(localized: L10n.Voice.modesVoice), value: state.voiceValue, logo: state.speakerLogo,
                                     tag: state.speakerTag, problem: state.speakProblem) { onEvent(.openVoice) }
                    ModeLogoValueRow(String(localized: L10n.Voice.listeningRow), value: state.listenerLabel, logo: state.listenerLogo,
                                     problem: state.listenProblem) { onEvent(.openListening) }
                    ModeToggleRow(L10n.Voice.talkBack, subtitle: L10n.Voice.talkBackDetail, isOn: Binding(get: { state.talkBack }, set: { onEvent(.talkBack($0)) }))
                }
                ModeFootnote(state.voiceNote).padding(.top, ModeSpacing.xxs)

                ModeSectionHeader(L10n.Settings.ai)
                ModeCard {
                    aiInUse
                    valueRow(L10n.Settings.mainModel, value: state.modelName) { onEvent(.openPicker(stuck: false)) }
                    valueRow(L10n.Settings.whenStuckShort, value: state.stuckName) { onEvent(.openPicker(stuck: true)) }
                }

                ModeSectionHeader(L10n.Access.section)
                ModeCard {
                    ModeRow(String(localized: L10n.Access.howModeActs), leading: .mo(.idle), action: { onEvent(.openAccess) }) {
                        HStack(spacing: ModeSpacing.m) {
                            Text(state.fullAccess ? L10n.Access.full : L10n.Access.askFirstShort).modeText(.callout, color: ModeColor.textSecondary)
                            ModeChevron()
                        }
                    }
                    ModeRow(String(localized: L10n.UnlockVoice.title), leading: .icon(.lock), action: { onEvent(.openUnlockVoice) }) { ModeChevron() }
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
                        Text(providers.planNote).modeText(.footnote, color: ModeColor.textSecondary)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.top, ModeSpacing.m)
                            .padding(.bottom, ModeSpacing.headerGap)
                    }
                }

                ModeCard {
                    ModeRow(String(localized: L10n.Health.row), leading: .icon(.wrench), action: { onEvent(.openHealth) }) { ModeChevron() }
                    ModeRow(String(localized: L10n.Developer.title), action: { onEvent(.openDeveloper) }) { ModeChevron() }
                }
                .padding(.top, ModeSpacing.xl)

                if state.showsBackup {
                    ModeSectionHeader(L10n.Backup.section)
                    ModeCard {
                        ModeRow(String(localized: L10n.Backup.title), leading: .icon(.backup), action: { onEvent(.openBackup) }) { ModeChevron() }
                    }
                }

                ModeButton(resetting ? L10n.Settings.resetting : L10n.Settings.resetMode, role: .destructive, fullWidth: true) {
                    onEvent(.askReset)
                }
                .disabled(resetting)
                .padding(.top, ModeSpacing.m)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .modePageChrome(L10n.Common.settings)
        .onDisappear { onEvent(.saveName) }
    }

    /// The AI in use: its tile, "AI in use", its name and how it's connected (opens its page), and Change.
    private var aiInUse: some View {
        HStack(spacing: ModeSpacing.m) {
            Button { onEvent(.openProvider) } label: {
                HStack(spacing: ModeSpacing.m) {
                    ModeLeadingView(providers.activeKind.inUseTile)
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        Text(L10n.Providers.aiInUse).modeText(.footnote, color: ModeColor.textSecondary)
                        Text(providers.activeTitle).modeText(.headline)
                        ModeStatusLine(providers.summary, dot: providers.activeConnected ? ModeColor.success : ModeColor.ring)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
            ModeButton(L10n.Providers.change, role: .pill) { onEvent(.showProviders) }
                .frame(minHeight: ModeSize.touch)
        }
        .padding(.vertical, ModeSpacing.m)
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
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                ModePageTitle(stuck ? L10n.Settings.whenStuckShort : L10n.Settings.mainModel)
                    .padding(.bottom, ModeSpacing.s)
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
                    .padding(.horizontal, ModeSpacing.screenEdge)
                }
                .padding(.horizontal, -ModeSpacing.screenEdge)
                .scrollClipDisabled()
                ModeCard(lifted: true) {
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
                if listed.isEmpty { ModeFootnote(L10n.Settings.noModels) }
                ModeFootnote(state.showsCost ? L10n.Settings.pickerNote : L10n.Settings.pickerPlanNote, style: .caption)
                    .padding(.top, ModeSpacing.xxs)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .scrollDismissesKeyboard(.interactively)
        .modeScreen()
        .modePageChrome(stuck ? L10n.Settings.whenStuckShort : L10n.Settings.mainModel)
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
        _model = State(initialValue: SettingsViewModel(screens: app.screens, navigate: navigate, reset: { [app] in await app.resetEverything() }))
        _providers = State(initialValue: ProvidersViewModel(screens: app.screens, picksUnconnected: false))
    }

    var body: some View {
        SettingsScreen(state: model.state, providers: providers.state, name: $model.name, costStep: model.costStep,
                       costRange: model.costRange, dailyCapStep: model.dailyCapStep, dailyCapRange: model.dailyCapRange,
                       resetting: model.resetting, onEvent: model.send)
            .alert(Text(L10n.Settings.resetConfirm), isPresented: $model.confirmingReset) {
                Button(role: .cancel) {} label: { Text(L10n.Common.cancel) }
                Button(role: .destructive) { model.send(.confirmReset) } label: { Text(L10n.Settings.resetMode) }
            } message: {
                Text(L10n.Settings.resetDetail)
            }
            .sheet(isPresented: $model.showingProviders) {
                ProvidersSheet(app: app, state: providers.state, onEvent: providers.send) { model.showingProviders = false }
                    .modifier(ProviderLinks(model: providers, app: app))
            }
            #if DEBUG && targetEnvironment(simulator)
            // Simulator only: MODE_OPEN=settings:providers opens the providers sheet, settings:reset the Reset
            // confirmation and settings:resetnow resets, for comparing with boards and checking the reset.
            .task {
                switch ProcessInfo.processInfo.environment["MODE_OPEN"] {
                case "settings:providers": model.send(.showProviders)
                case "settings:reset": model.send(.askReset)
                case "settings:resetnow": model.send(.confirmReset)
                default: break
                }
            }
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
