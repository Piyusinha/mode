import AuthenticationServices
import ModeDesignSystem
import SwiftUI

/// Connects setup to its ViewModel and to the iOS sheets it needs: the contact
/// picker, OpenRouter's sign-in, model search, sharing the report, links.
struct OnboardingRoute: View {
    @Environment(\.openURL) private var openURL
    @Environment(\.webAuthenticationSession) private var webAuthentication
    @State private var model: OnboardingViewModel
    @State private var providers: ProvidersViewModel
    let app: AppModel

    init(app: AppModel) {
        self.app = app
        _model = State(initialValue: OnboardingViewModel(screens: app.screens, finished: { app.onboardingFinished() }))
        _providers = State(initialValue: ProvidersViewModel(screens: app.screens, picksUnconnected: true))
    }

    var body: some View {
        OnboardingScreen(state: model.state, callName: $model.callName, providers: providers.state, onProvider: providers.send, onEvent: model.send)
            .modifier(ProviderLinks(model: providers, app: app))
            .onChange(of: model.showingContactPicker) { _, showing in
                guard showing else { return }
                model.showingContactPicker = false
                ContactPicker.present { given, family in model.cardPicked(given: given, family: family) }
            }
            .sheet(isPresented: $model.showingSearch) {
                ModelSearchSheet(results: model.state.results, query: $model.query) { model.send(.chooseModel($0)) }
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
