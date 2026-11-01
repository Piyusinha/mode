import ModeDesignSystem
import SwiftUI

/// First-run setup, drawn exactly as the canvas boards. Draws state, sends events.
struct OnboardingScreen: View {
    let state: OnboardingViewModel.State
    @Binding var callName: String
    @Binding var key: String
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ModeSpacing.l) {
                    content
                }
                .padding(.horizontal, ModeSpacing.screenEdge)
                .padding(.bottom, ModeSpacing.xxl)
            }
            .scrollEdgeEffectStyle(.soft, for: .all)
            .modeScreen()
            .safeAreaInset(edge: .bottom) { buttons }
            .toolbar {
                if state.step != .welcome && state.step != .result {
                    ToolbarItem(placement: .topBarLeading) { ModeToolbarButton(.back, label: L10n.Common.back) { onEvent(.back) } }
                }
                if let number = state.stepNumber {
                    ToolbarItem(placement: .principal) { ModeStepDots(current: number, total: state.stepTotal, label: state.stepLabel) }
                }
            }
            .animation(ModeMotion.standard, value: state.step)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch state.step {
        case .welcome: WelcomeStep(state: state, onEvent: onEvent)
        case .name: NameStep(state: state, callName: $callName, onEvent: onEvent)
        case .key: KeyStep(state: state, key: $key, onEvent: onEvent)
        case .model: ModelStep(state: state, onEvent: onEvent)
        case .permissions: PermissionsStep(state: state, onEvent: onEvent)
        case .test: TestStep(state: state)
        case .result: ResultStep(state: state, onEvent: onEvent)
        case .diagnose: DiagnoseStep(state: state)
        }
    }

    @ViewBuilder
    private var buttons: some View {
        VStack(spacing: ModeSpacing.m) {
            switch state.step {
            case .welcome:
                ModeButton(L10n.Setup.getStarted, fullWidth: true) { onEvent(.next) }
                ModeButton(L10n.Setup.seeCode, role: .plain) { onEvent(.openCode) }
            case .test:
                ModeButton(L10n.Setup.runTest, icon: .play, fullWidth: true) { onEvent(.runTest) }.disabled(state.testRunning)
            case .result:
                ModeButton(L10n.Setup.runAgain, icon: .runAgain, role: .plain) { onEvent(.runTest) }
            case .diagnose:
                HStack(spacing: ModeSpacing.m) {
                    ModeButton(L10n.Setup.sendReport, icon: .share, role: .secondary, fullWidth: true) { onEvent(.sendReport) }
                    ModeButton(L10n.Setup.checkAgain, fullWidth: true) { onEvent(.checkAgain) }.disabled(state.checking)
                }
                ModeButton(L10n.Setup.continueAnyway, role: .plain) { onEvent(.finish) }
            default:
                ModeButton(L10n.Common.continue, role: state.canContinue ? .primary : .secondary, fullWidth: true) { onEvent(.next) }
                    .disabled(!state.canContinue)
            }
        }
        .padding(.horizontal, ModeSpacing.screenEdge)
        .padding(.bottom, ModeSpacing.s)
    }
}

/// A setup page's title and the line under it.
private struct StepHeader: View {
    let title: LocalizedStringResource
    let message: LocalizedStringResource

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            Text(title).modeText(.largeTitle)
            Text(message).modeText(.callout, color: ModeColor.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct WelcomeStep: View {
    let state: OnboardingViewModel.State
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        VStack(spacing: ModeSpacing.l) {
            ModeMark().padding(.top, ModeSpacing.xxxl)
            Text(L10n.Setup.appName).modeText(.largeTitle)
            Text(L10n.Setup.tagline).modeText(.title2, color: ModeColor.textSecondary).fontWeight(.regular).multilineTextAlignment(.center)
            VStack(spacing: ModeSpacing.s) {
                HStack(spacing: ModeSpacing.s) {
                    ModePill(L10n.Setup.pillOpenSource)
                    ModePill(L10n.Setup.pillOwnKey)
                }
                ModePill(L10n.Setup.pillOnPhone)
            }
            ModeCard {
                ModeRow(String(localized: L10n.Setup.thisIPhone), subtitle: String(localized: state.paired ? L10n.Setup.paired : L10n.Setup.notPaired),
                        leading: state.paired ? .success : .icon(.warning, ModeColor.warning), emphasis: .prominent)
                ModeRow(String(localized: L10n.Setup.stepName), subtitle: String(localized: L10n.Setup.stepNameDetail), leading: .number(1), emphasis: .prominent)
                ModeRow(String(localized: L10n.Setup.stepAI), subtitle: String(localized: L10n.Setup.stepAIDetail), leading: .number(2), emphasis: .prominent)
                ModeRow(String(localized: L10n.Setup.stepTest), subtitle: String(localized: L10n.Setup.stepTestDetail), leading: .number(3), emphasis: .prominent)
            }
        }
        .frame(maxWidth: .infinity)
    }
}

private struct NameStep: View {
    let state: OnboardingViewModel.State
    @Binding var callName: String
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        StepHeader(title: L10n.Setup.nameTitle, message: L10n.Setup.nameMessage)
        if let name = state.cardName {
            ModeContactCard(initials: state.initials, name: name, subtitle: L10n.Setup.fromCard)
        }
        ModeFieldCard(L10n.Setup.callYou, prompt: L10n.Setup.callYouPrompt, text: $callName)
        ModeButton(state.cardName == nil ? L10n.Setup.chooseCard : L10n.Setup.chooseDifferentCard, icon: .contact, role: .secondary, fullWidth: true) {
            onEvent(.chooseCard)
        }
        ModeNote(icon: .lock, L10n.Setup.contactsNote).padding(.top, ModeSpacing.l)
    }
}

private struct KeyStep: View {
    let state: OnboardingViewModel.State
    @Binding var key: String
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        StepHeader(title: L10n.Setup.keyTitle, message: L10n.Setup.keyMessage)
        if state.connected {
            ModeCard { ModeRow(String(localized: L10n.Setup.connected), leading: .success) }
        } else {
            ModeButton(L10n.Setup.connect, icon: .link, fullWidth: true) { onEvent(.connect) }
                .disabled(state.signingIn)
                .padding(.top, ModeSpacing.m)
            Text(L10n.Setup.connectNote).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center).frame(maxWidth: .infinity)
            if let error = state.signInError { Text(error).modeText(.footnote, color: ModeColor.danger) }
            ModeOrDivider(L10n.Setup.or).padding(.vertical, ModeSpacing.s)
            ModeFieldCard(L10n.Setup.keyLabel, prompt: L10n.Setup.keyPrompt, text: $key, secure: true, buttonTitle: L10n.Setup.paste) { onEvent(.paste) }
            if let hint = state.keyHint { Text(hint).modeText(.footnote, color: ModeColor.warning) }
            if state.hasSavedKey { Text(L10n.Setup.savedKey).modeText(.footnote, color: ModeColor.textSecondary) }
        }
        ModeNote(icon: .lock, L10n.Setup.keyNote).padding(.top, ModeSpacing.l)
    }
}

private struct ModelStep: View {
    let state: OnboardingViewModel.State
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        StepHeader(title: L10n.Setup.modelTitle, message: L10n.Setup.modelMessage)
        ModeCard {
            ForEach(state.models) { model in ChoiceRow(choice: model) { onEvent(.chooseModel(model.id)) } }
        }
        ModeSectionHeader(L10n.Setup.whenStuck)
        ModeCard {
            if let stuck = state.stuck {
                ModeRow(stuck.name, subtitle: stuck.detail, leading: stuck.leading, emphasis: .prominent, onTap: { onEvent(.openStuck) })
            }
            ModeRow(String(localized: L10n.Setup.allModels), action: { onEvent(.openSearch) }) {
                HStack(spacing: ModeSpacing.s) {
                    Text(L10n.Setup.search).modeText(.body, color: ModeColor.textSecondary)
                    ModeChevron()
                }
            }
        }
        Text(L10n.Setup.pricesNote).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
    }
}

/// A model to choose: maker tile, name and tag, what it's good at and its cost, and the selection mark.
struct ChoiceRow: View {
    let choice: OnboardingViewModel.Choice
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.m) {
                ModeLeadingView(choice.leading)
                VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                    HStack(spacing: ModeSpacing.s) {
                        Text(choice.name).modeText(.headline)
                        if let tag = choice.tag { ModeTag(tag.text, background: tag.background, foreground: tag.foreground) }
                    }
                    Text(choice.detail).modeText(.subheadline, color: ModeColor.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ModeRadio(selected: choice.selected)
            }
            .padding(.vertical, ModeSpacing.m)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(choice.selected ? .isSelected : [])
    }
}

private struct PermissionsStep: View {
    let state: OnboardingViewModel.State
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        StepHeader(title: L10n.Setup.permissionsTitle, message: L10n.Setup.permissionsMessage)
        ModeCard {
            ForEach(state.permissions) { row in
                ModeRow(String(localized: row.title), subtitle: String(localized: row.why), leading: row.leading, emphasis: .prominent) {
                    ModeStatusView(row.action) { onEvent(.allow(row.id)) }
                }
            }
        }
    }
}

private struct TestStep: View {
    let state: OnboardingViewModel.State

    var body: some View {
        StepHeader(title: L10n.Setup.testTitle, message: L10n.Setup.testMessage)
        ModeTestStrip(
            labels: [String(localized: L10n.Setup.openSettings), String(localized: L10n.Setup.tapWifi), String(localized: L10n.Setup.comeBack)],
            phases: state.testPhases
        )
        .padding(.vertical, ModeSpacing.l)
        ModeInfoCard(icon: .info, L10n.Setup.testNote)
    }
}

private struct ResultStep: View {
    let state: OnboardingViewModel.State
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        VStack(spacing: ModeSpacing.l) {
            ModeResultBadge(success: state.testPassed).padding(.top, ModeSpacing.xxxl)
            Text(state.testPassed ? L10n.Setup.resultTitle : L10n.Setup.resultFailedTitle).modeText(.title).multilineTextAlignment(.center)
            HStack(alignment: .top, spacing: ModeSpacing.l) {
                Group {
                    if let shot = state.shot {
                        Image(uiImage: shot).resizable().scaledToFill()
                    } else {
                        ModeColor.fill
                    }
                }
                .frame(width: ModeSize.resultShotWidth)
                .frame(maxHeight: .infinity)
                .clipShape(.rect(cornerRadius: ModeRadius.field))
                VStack(alignment: .leading, spacing: ModeSpacing.s) {
                    Text(L10n.Setup.whatModeDid).modeText(.footnote, color: ModeColor.textSecondary)
                    ForEach(state.did) { row in
                        HStack(spacing: ModeSpacing.s) {
                            ModeRow(String(localized: row.title), leading: row.leading, action: nil) {
                                if let detail = row.detail { Text(detail).modeText(.subheadline, color: ModeColor.textSecondary) }
                            }
                        }
                    }
                    if state.shot != nil { Text(L10n.Setup.shotCaption).modeText(.caption, color: ModeColor.textSecondary) }
                }
            }
            .padding(ModeSpacing.l)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
            VStack(spacing: ModeSpacing.l) {
                Text(L10n.Setup.question).modeText(.headline).multilineTextAlignment(.center)
                HStack(spacing: ModeSpacing.m) {
                    ModeButton(L10n.Setup.no, role: .secondary, fullWidth: true) { onEvent(.worked(false)) }
                    ModeButton(L10n.Setup.itWorked, fullWidth: true) { onEvent(.worked(true)) }
                }
            }
            .padding(ModeSpacing.l)
            .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
        }
        .frame(maxWidth: .infinity)
    }
}

private struct DiagnoseStep: View {
    let state: OnboardingViewModel.State

    var body: some View {
        StepHeader(title: L10n.Setup.diagnoseTitle, message: L10n.Setup.diagnoseMessage)
        ModeCard {
            ForEach(state.checks) { check in
                ModeRow(String(localized: check.title), subtitle: check.detail, leading: check.leading, emphasis: check.emphasis, action: nil) {
                    if let trailing = check.trailing { Text(trailing).modeText(.subheadline, color: ModeColor.textSecondary) }
                }
            }
        }
    }
}
