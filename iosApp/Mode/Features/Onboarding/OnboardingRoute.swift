import AuthenticationServices
import ModeDesignSystem
import SwiftUI

/// Connects setup to its ViewModel and to the iOS sheets it needs: the contact
/// picker, OpenRouter's sign-in, model search, sharing the report, links.
struct OnboardingRoute: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.webAuthenticationSession) private var webAuthentication
    @LazyState private var model: OnboardingViewModel
    @LazyState private var providers: ProvidersViewModel
    let app: AppModel

    init(app: AppModel) {
        self.app = app
        _model = LazyState(wrappedValue: OnboardingViewModel(screens: app.screens, finished: { app.onboardingFinished() }))
        _providers = LazyState(wrappedValue: ProvidersViewModel(screens: app.screens, picksUnconnected: true))
    }

    var body: some View {
        OnboardingScreen(state: model.state, callName: $model.callName, providers: providers.state, onProvider: providers.send, onEvent: model.send)
            .modifier(ProviderLinks(model: providers, app: app))
            // A provider's sign-in page (Continue with ChatGPT) belongs to the Key step: its dots, and the way to OpenRouter.
            .environment(\.setupStep, model.state.stepNumber.map { SetupStep(current: $0, total: model.state.stepTotal, label: model.state.stepLabel) })
            .environment(\.setupUseOpenRouter) { [providers] in
                if let id = providers.state.rows.first(where: { $0.kind == .openRouter })?.id { providers.send(.connect(id)) }
            }
            .onChange(of: model.showingContactPicker) { _, showing in
                guard showing else { return }
                model.showingContactPicker = false
                ContactPicker.present { given, family in model.cardPicked(given: given, family: family) }
            }
            .sheet(isPresented: $model.showingSearch) {
                ModelSearchSheet(results: model.state.results, query: $model.query) { model.send(.chooseModel($0)) }
            }
            .sheet(item: $model.voiceSheet, onDismiss: { model.send(.voiceSheetClosed) }) { sheet in
                switch sheet {
                case .speak: VoiceSettingsSheet(app: app, hearing: false)
                case .listen: VoiceSettingsSheet(app: app, hearing: true)
                case .voices: AllVoicesSheet(app: app, provider: model.state.speaker.id)
                case .elevenLabs: ConnectVoiceSheet(app: app, service: .elevenlabs)
                }
            }
            .sheet(item: $model.picking) { role in
                RolePickerSheet(role: role, state: model.state, onEvent: model.send)
            }
            .sheet(isPresented: $model.showingStuck) {
                StuckSheet(choices: model.state.stuckChoices) { model.send(.chooseStuck($0)) }
            }
            .sheet(item: Binding(get: { model.report.map(SharedText.init) }, set: { _ in model.reportShared() })) { shared in
                ShareSheet(text: shared.text)
            }
            .task(id: model.signInURL) {
                guard let url = model.signInURL else { return }
                do {
                    let callback = try await webAuthentication.authenticate(using: url, callbackURLScheme: model.callbackScheme)
                    model.signInFinished(callback: callback, error: nil)
                } catch {
                    model.signInFinished(callback: nil, error: error)
                }
            }
            .onChange(of: model.openURL) { _, url in
                if let url {
                    openURL(url)
                    model.urlOpened()
                }
            }
    }
}

/// A setup step, for pages setup opens over itself (a provider's sign-in page shows the Key step's dots).
struct SetupStep: Equatable {
    let current: Int
    let total: Int
    let label: String
}

extension EnvironmentValues {
    /// The setup step a page belongs to; nil outside setup.
    @Entry var setupStep: SetupStep? = nil
    /// In setup: switch to OpenRouter (pick it and open its sign-in); nil outside setup.
    @Entry var setupUseOpenRouter: (@MainActor () -> Void)? = nil
}

/// The model Mode steps up to when it gets stuck, or none.
private struct StuckSheet: View {
    @Environment(\.dismiss) private var dismiss
    let choices: [OnboardingViewModel.Choice]
    let onChoose: (String?) -> Void

    var body: some View {
        NavigationStack {
            List {
                ForEach(choices) { choice in ChoiceRow(choice: choice) { onChoose(choice.id) } }
                ModeRow(String(localized: L10n.Setup.stuckOff), action: { onChoose(nil) }) { EmptyView() }
            }
            .navigationTitle(Text(L10n.Setup.whenStuck))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { ModeToolbarButton(.close, label: L10n.Common.cancel) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}

/// Board SetupModelPicker: a white sheet with how hard the role thinks (plan providers), then its model, and Done.
private struct RolePickerSheet: View {
    @Environment(\.dismiss) private var dismiss
    let role: OnboardingViewModel.PickerRole
    let state: OnboardingViewModel.State
    let onEvent: (OnboardingViewModel.Event) -> Void

    private var brain: Bool { role == .brain }
    private var recommended: String { role.recommendedEffort }
    private var effort: String { (brain ? state.brainEffort : state.phoneEffort) ?? recommended }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m + ModeSpacing.xxs) {
                VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                    Text(brain ? L10n.Setup.whenStuck : L10n.Setup.phoneControl).modeText(.title2).accessibilityAddTraits(.isHeader)
                    Text(L10n.Setup.pickerMessage(String(localized: brain ? L10n.Setup.stuckDetail : L10n.Setup.phoneDetail)))
                        .modeText(.subheadline, color: ModeColor.textSecondary)
                }
                if !state.efforts.isEmpty && (!brain || state.brainCard != nil) {
                    label(L10n.Setup.howHard)
                    ModeEffortPicker(options: state.efforts.map { (id: $0, title: PlanModelsViewModel.effortName($0)) }, selection: effort,
                                     recommended: recommended, recommendedLabel: String(localized: L10n.Settings.recommended)) {
                        onEvent(.chooseEffort(role, $0))
                    }
                    Text(L10n.Setup.effortGuide).modeText(.footnote, color: ModeColor.textSecondary).padding(.top, -ModeSpacing.s + ModeSpacing.xxs)
                }
                label(L10n.Setup.modelSection)
                VStack(spacing: ModeSpacing.none) {
                    let choices = brain ? state.stuckChoices : state.phoneChoices
                    let more = brain || !state.onPlan
                    ForEach(Array(choices.enumerated()), id: \.element.id) { index, choice in
                        ChoiceRow(choice: choice, compact: true) { onEvent(brain ? .chooseStuck(choice.id) : .chooseModel(choice.id)) }
                        if index < choices.count - 1 || more { Rectangle().fill(ModeColor.separator).frame(height: ModeSize.separator) }
                    }
                    if brain {
                        ModeRow(String(localized: L10n.Setup.stuckOff), action: { onEvent(.chooseStuck(nil)) }) { ModeRadio(selected: state.brainCard == nil) }
                    }
                    if !state.onPlan {
                        ModeRow(String(localized: L10n.Setup.allModels), action: { dismiss(); onEvent(.openSearch) }) { ModeChevron() }
                    }
                }
            }
            .padding(.horizontal, ModeSpacing.xl)
            .padding(.top, ModeSpacing.xxl + ModeSpacing.xs)
            .padding(.bottom, ModeSpacing.xl)
        }
        .scrollBounceBehavior(.basedOnSize)
        .safeAreaInset(edge: .bottom, spacing: ModeSpacing.none) {
            ModeButton(L10n.Common.done, fullWidth: true) { dismiss() }
                .padding(.horizontal, ModeSpacing.screenEdge)
                .padding(.top, ModeSpacing.l)
                .background(ModeColor.card)
        }
        .presentationBackground(ModeColor.card)
        .presentationCornerRadius(ModeRadius.sheet)
        .presentationDragIndicator(.visible)
        .presentationDetents([.large])
    }

    private func label(_ text: LocalizedStringResource) -> some View {
        Text(text).textCase(.uppercase).modeText(.footnoteEmphasis, color: ModeColor.textSecondary).fontWeight(.bold)
            .accessibilityAddTraits(.isHeader)
    }
}

/// Every model on OpenRouter that can see the screen, searchable.
private struct ModelSearchSheet: View {
    @Environment(\.dismiss) private var dismiss
    let results: [OnboardingViewModel.Choice]
    @Binding var query: String
    let onChoose: (String) -> Void

    var body: some View {
        NavigationStack {
            List {
                ForEach(results) { choice in
                    ChoiceRow(choice: choice) {
                        onChoose(choice.id)
                        dismiss()
                    }
                }
            }
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always))
            .navigationTitle(Text(L10n.Setup.allModels))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { ModeToolbarButton(.close, label: L10n.Common.cancel) { dismiss() } } }
        }
        .presentationDetents([.medium, .large])
    }
}
