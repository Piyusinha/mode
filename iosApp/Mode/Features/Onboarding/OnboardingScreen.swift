import ModeDesignSystem
import SwiftUI

/// First-run setup, drawn exactly as the canvas boards. Draws state, sends events.
struct OnboardingScreen: View {
    let state: OnboardingViewModel.State
    @Binding var callName: String
    let providers: ProvidersViewModel.State
    let onProvider: (ProvidersViewModel.Event) -> Void
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
            .navigationBarTitleDisplayMode(.inline)
            .toolbar(state.step == .voice ? .hidden : .visible, for: .navigationBar)
            .modeScreen()
            .safeAreaInset(edge: .bottom) { buttons.background(ModeColor.page) }
            .toolbar {
                if state.step != .welcome && state.step != .result && state.step != .voice {
                    ToolbarItem(placement: .topBarLeading) { ModeToolbarButton(.back, label: L10n.Common.back) { onEvent(.back) } }
                }
                if let number = state.stepNumber, state.step != .voice {
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
        case .key: KeyStep(providers: providers, onProvider: onProvider)
        case .model: ModelStep(state: state, onEvent: onEvent)
        case .permissions: PermissionsStep(state: state, onEvent: onEvent)
        case .voice: VoiceStep(state: state, onEvent: onEvent)
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
            case .key:
                if providers.activeConnected {
                    ModeButton(L10n.Common.continue, fullWidth: true) { onEvent(.next) }
                } else {
                    ModeButton(LocalizedStringResource(stringLiteral: L10n.Providers.continueWith(providers.activeTitle)), fullWidth: true) {
                        onProvider(.connectActive)
                    }
                }
            case .voice:
                ModeButton(L10n.Common.continue, fullWidth: true) { onEvent(.next) }
                ModeButton(L10n.Voice.notNow, role: .plain) { onEvent(.skipVoice) }
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

/// "Connect your AI": every provider, ChatGPT picked by default; connect as many as you like.
private struct KeyStep: View {
    let providers: ProvidersViewModel.State
    let onProvider: (ProvidersViewModel.Event) -> Void

    var body: some View {
        StepHeader(title: L10n.Setup.keyTitle, message: L10n.Providers.setupMessage)
        ProviderList(rows: providers.rows, setup: true, onEvent: onProvider)
            .padding(.top, ModeSpacing.s)
        Text(providers.activeConnected ? L10n.Providers.setupHintConnected : L10n.Providers.setupHint)
            .modeText(.footnote, color: ModeColor.textSecondary)
            .padding(.horizontal, ModeSpacing.xs)
        ModeNote(icon: .lock, L10n.Providers.keychainNote).padding(.top, ModeSpacing.m)
    }
}

/// "Pick how Mode thinks" (boards SetupModel, SetupModelClaude): phone control and the brain, each with
/// its model, how hard it thinks, and Mode's recommendation.
private struct ModelStep: View {
    let state: OnboardingViewModel.State
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        StepHeader(title: L10n.Setup.modelTitle, message: L10n.Setup.modelMessage)
        RoleCardView(label: L10n.Setup.phoneControl, detail: L10n.Setup.phoneDetail, card: state.phoneCard) { onEvent(.openPicker(.phone)) }
        RoleCardView(label: L10n.Setup.whenStuck, detail: L10n.Setup.stuckDetail, card: state.brainCard) { onEvent(.openPicker(.brain)) }
        if state.onPlan { ModeInfoCard(icon: .info, L10n.Setup.whyThese) }
        Text(state.onPlan ? L10n.Setup.onPlanNote : L10n.Setup.pricesNote).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
    }
}

/// One role on "Pick how Mode thinks": what it does, then its pick; tapping opens the picker.
private struct RoleCardView: View {
    let label: LocalizedStringResource
    let detail: LocalizedStringResource
    let card: OnboardingViewModel.RoleCard?
    let action: () -> Void

    var body: some View {
        ModeCard {
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(label).textCase(.uppercase).modeText(.captionEmphasis, color: ModeColor.textSecondary)
                Text(detail).modeText(.caption, color: ModeColor.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, ModeSpacing.s)
            Button(action: action) {
                HStack(spacing: ModeSpacing.m) {
                    if let leading = card?.leading { ModeLeadingView(leading) }
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                        Text(card?.name ?? String(localized: L10n.Setup.stuckOff)).modeText(.headline)
                        if let card {
                            HStack(spacing: ModeSpacing.s) {
                                if let effort = card.effort { ModeTag(effort, background: ModeColor.fill, foreground: ModeColor.textSecondary) }
                                if card.recommended {
                                    ModeTag(String(localized: L10n.Settings.recommended), background: ModeColor.tintSoft, foreground: ModeColor.tint)
                                }
                            }
                            if !card.why.isEmpty { Text(card.why).modeText(.subheadline, color: ModeColor.textSecondary) }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    ModeChevron()
                }
                .padding(.vertical, ModeSpacing.m)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
        }
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

/// Setup's Talk to Mode board: compact provider headers and the same voice rows as Settings.
private struct VoiceStep: View {
    let state: OnboardingViewModel.State
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            ZStack(alignment: .topLeading) {
                VStack(spacing: ModeSpacing.s) {
                    ModeStepDots(current: state.stepNumber ?? 1, total: state.stepTotal, label: state.stepLabel)
                        .padding(.top, ModeSpacing.l)
                    ModeVoiceOrb(speaking: false, size: ModeSize.voiceLogo, compact: true, ringCount: 1)
                    VStack(spacing: ModeSpacing.none) {
                        Text(L10n.Voice.setupTitle).modeText(.title).multilineTextAlignment(.center)
                        Text(L10n.Voice.setupPrompt).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
                    }
                }
                .frame(maxWidth: .infinity)
                ModeIconButton(.back, label: L10n.Common.back, size: ModeSize.activityButton) { onEvent(.back) }
            }

            HStack {
                Text(L10n.Voice.whoSpeaks).modeText(.headline).accessibilityAddTraits(.isHeader)
                Spacer()
                Button { onEvent(.openVoiceSheet(.voices)) } label: {
                    Text(L10n.Voice.viewMore).modeText(.footnote, color: ModeColor.tint).fontWeight(.semibold)
                        .frame(minHeight: ModeSize.touch)
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, ModeSpacing.xs)
            .frame(height: ModeSize.voiceSectionHeading)
            ModeCard {
                provider(state.speaker, hearing: false)
                ForEach(state.voices) { voice in
                    ModeVoiceChoiceRow(name: voice.name, traits: voice.traits, tag: voice.tag,
                                       selected: state.voice == voice.id, playing: state.voicePlaying == voice.id,
                                       playLabel: L10n.Voice.play(voice.name),
                                       onSelect: { onEvent(.chooseVoice(voice.id)) }, onPlay: { onEvent(.playVoice(voice.id)) })
                }
                if state.speaker == .apple {
                    Button { onEvent(.openVoiceSheet(.elevenLabs)) } label: {
                        HStack(spacing: ModeSpacing.s) {
                            ModeLeadingView(VoiceService.elevenlabs.tile).scaleEffect(0.7).frame(width: ModeSize.badge)
                            Text(L10n.Voice.humanVoiceSuggestion).modeText(.footnote, color: ModeColor.tint).fontWeight(.semibold)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            ModeChevron()
                        }
                        .frame(minHeight: ModeSize.touch)
                        .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
                }
            }

            ModeSectionHeader(L10n.Voice.whoListens)
            ModeCard {
                provider(state.listener, hearing: true)
                microphone
            }
        }
    }

    private var microphone: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeIcon.microphone.image.modeText(.callout, color: ModeColor.tint)
                .frame(width: ModeSize.voiceProviderLogo, height: ModeSize.voiceProviderLogo)
                .background(ModeColor.tintSoft, in: .circle)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(L10n.Setup.microphone).modeText(.callout)
                Text(state.microphoneAllowed ? L10n.Voice.setupMicNote : L10n.Voice.setupMicRequestNote)
                    .modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if state.microphoneAllowed {
                HStack(spacing: ModeSpacing.xs) {
                    Circle().fill(ModeColor.success).frame(width: ModeSize.stepDot, height: ModeSize.stepDot)
                    Text(L10n.Setup.allowed).modeText(.footnote, color: ModeColor.success).fontWeight(.semibold)
                }
            } else {
                Button { onEvent(state.microphoneDenied ? .openMicSettings : .allowMic) } label: {
                    Text(state.microphoneDenied ? L10n.Voice.openSettings : L10n.Setup.allow)
                        .modeText(.footnote, color: state.microphoneDenied ? ModeColor.tint : ModeColor.onAccent).fontWeight(.semibold)
                        .padding(.horizontal, ModeSize.voiceChangePadding).frame(minHeight: ModeSize.voiceChangeHeight)
                        .background(state.microphoneDenied ? ModeColor.fill : ModeColor.accent, in: .capsule)
                        .frame(minHeight: ModeSize.touch).contentShape(.rect)
                }
                .buttonStyle(.plain)
            }
        }
        .frame(minHeight: ModeSize.rowMin)
    }

    private func provider(_ service: VoiceService, hearing: Bool) -> some View {
        HStack(spacing: ModeSpacing.m) {
            ModeLeadingView(service.tile)
                .scaleEffect(ModeSize.voiceProviderLogo / ModeSize.tile)
                .frame(width: ModeSize.voiceProviderLogo, height: ModeSize.voiceProviderLogo)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: ModeSpacing.s) { providerName(service).fixedSize(); recommendation(service) }
                    VStack(alignment: .leading, spacing: ModeSpacing.xxs) { providerName(service); recommendation(service) }
                }
                HStack(alignment: .firstTextBaseline, spacing: ModeSpacing.xs) {
                    Circle().fill((hearing ? state.listenerConnected : state.speakerConnected) ? ModeColor.success : ModeColor.separator)
                        .frame(width: ModeSize.stepDot, height: ModeSize.stepDot)
                    Text(hearing ? state.listenerStatus : state.speakerStatus)
                        .modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button { onEvent(.openVoiceSheet(hearing ? .listen : .speak)) } label: {
                Text(L10n.Providers.change).modeText(.footnote).fontWeight(.semibold)
                    .padding(.horizontal, ModeSize.voiceChangePadding).frame(minHeight: ModeSize.voiceChangeHeight)
                    .background(ModeColor.fill, in: .capsule)
                    .frame(minHeight: ModeSize.touch)
                    .contentShape(.rect)
            }
            .buttonStyle(.plain)
            .fixedSize()
            .layoutPriority(1)
            .accessibilityLabel(Text(hearing ? L10n.Voice.changeListener : L10n.Voice.changeSpeaker))
            .accessibilityIdentifier(hearing ? "setup.voice.listening.provider" : "setup.voice.speaking.provider")
        }
        .padding(.vertical, ModeSpacing.s)
        .frame(minHeight: ModeSize.voiceProviderRow)
    }

    private func providerName(_ service: VoiceService) -> some View { Text(service.title).modeText(.callout).fontWeight(.semibold) }
    @ViewBuilder private func recommendation(_ service: VoiceService) -> some View {
        if let tag = service.recommendation { ModeTag(tag, background: ModeColor.tintSoft, foreground: ModeColor.tint).fixedSize() }
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
