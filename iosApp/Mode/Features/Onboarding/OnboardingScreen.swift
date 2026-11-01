import ModeDesignSystem
import SwiftUI

/// One setup step: a heading, the step's own fields, and its buttons. Draws state, sends events.
struct OnboardingScreen: View {
    let state: OnboardingViewModel.State
    @Binding var name: String
    @Binding var key: String
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ModeSpacing.xl) {
                    ModeHero(icon: state.icon, title: state.title, message: state.message)
                    stepContent
                    if state.busy { ProgressView().tint(ModeColor.tint).frame(maxWidth: .infinity) }
                    if let detail = state.detail { Text(detail).modeText(.callout, color: ModeColor.textSecondary) }
                    if !state.log.isEmpty {
                        ModeCard {
                            ForEach(Array(state.log.enumerated()), id: \.offset) { _, line in
                                Text(line).modeText(.caption, color: ModeColor.textSecondary).padding(.vertical, ModeSpacing.xs)
                            }
                        }
                    }
                }
                .padding(ModeSpacing.screenEdge)
                .padding(.top, ModeSpacing.xxl)
            }
            .modeScreen()
            .safeAreaInset(edge: .bottom) { buttons }
            .toolbar {
                if state.canGoBack {
                    ToolbarItem(placement: .topBarLeading) { ModeIconButton(.back, label: L10n.Common.back) { onEvent(.back) } }
                }
            }
            .animation(ModeMotion.standard, value: state.step)
        }
    }

    @ViewBuilder
    private var stepContent: some View {
        switch state.step {
        case .name:
            ModeTextField(L10n.Settings.name, prompt: L10n.Settings.namePrompt, text: $name)
        case .key:
            ModeTextField(L10n.Settings.key, prompt: L10n.Settings.keyPrompt, text: $key, secure: true)
            if let hint = state.keyHint { Text(hint).modeText(.footnote, color: ModeColor.warning) }
        case .model:
            ModeCard {
                ForEach(state.models) { model in
                    ModeRow(model.name, subtitle: model.subtitle, action: { onEvent(.chooseModel(model.id)) }) {
                        if model.isMain { ModeIcon.done.image.foregroundStyle(ModeColor.tint) }
                    }
                }
            }
        default:
            EmptyView()
        }
    }

    private var buttons: some View {
        VStack(spacing: ModeSpacing.s) {
            if let primary = state.primary {
                ModeButton(primary.title, role: primary.role, fullWidth: true) { onEvent(primary.event) }.disabled(!primary.enabled)
            }
            if let secondary = state.secondary {
                ModeButton(secondary.title, role: secondary.role, fullWidth: true) { onEvent(secondary.event) }.disabled(!secondary.enabled)
            }
        }
        .padding(.horizontal, ModeSpacing.screenEdge)
        .padding(.bottom, ModeSpacing.s)
    }
}

struct OnboardingRoute: View {
    @Environment(\.openURL) private var openURL
    @State private var model: OnboardingViewModel

    init(app: AppModel) {
        _model = State(initialValue: OnboardingViewModel(screens: app.screens, finished: { app.onboardingFinished() }))
    }

    var body: some View {
        OnboardingScreen(state: model.state, name: $model.name, key: $model.key, onEvent: model.send)
            .onChange(of: model.openURL) { _, url in
                if let url {
                    openURL(url)
                    model.urlOpened()
                }
            }
    }
}
