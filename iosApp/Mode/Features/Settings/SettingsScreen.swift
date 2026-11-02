import ModeDesignSystem
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

                ModeSectionHeader(L10n.Settings.ai)
                ModeCard {
                    Button { onEvent(.showProviders) } label: {
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
                            Text(L10n.Providers.change).modeText(.subheadline).fontWeight(.semibold)
                                .padding(.horizontal, ModeSpacing.m)
                                .frame(minHeight: ModeSize.badge)
                                .background(ModeColor.fill, in: .capsule)
                        }
                        .padding(.vertical, ModeSpacing.m)
                        .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
                    valueRow(L10n.Settings.mainModel, value: state.modelName) { onEvent(.openPicker(stuck: false)) }
                    valueRow(L10n.Settings.whenStuckShort, value: state.stuckName) { onEvent(.openPicker(stuck: true)) }
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
        ModeRow(String(localized: title), action: action) {
            HStack(spacing: ModeSpacing.s) {
                Text(value).modeText(.body, color: ModeColor.textSecondary).lineLimit(1)
                ModeChevron()
            }
        }
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
                ProvidersSheet(state: providers.state, onEvent: providers.send) { model.showingProviders = false }
                    .modifier(ProviderLinks(model: providers, app: app))
            }
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
