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
                VStack(alignment: .leading, spacing: ModeSpacing.none) {
                    content
                }
                .padding(.horizontal, ModeSpacing.screenEdge)
                .padding(.bottom, ModeSpacing.xl)
            }
            .scrollBounceBehavior(.basedOnSize)
            .toolbar(.hidden, for: .navigationBar)
            .safeAreaInset(edge: .top, spacing: ModeSpacing.none) { header }
            .safeAreaInset(edge: .bottom, spacing: ModeSpacing.none) { buttons }
            .modeScreen()
            .animation(ModeMotion.standard, value: state.step)
        }
    }

    /// The back button and step dots; none on Welcome or on the result.
    @ViewBuilder
    private var header: some View {
        if state.step != .welcome && state.step != .result {
            ModeSetupHeader(backLabel: L10n.Common.back, step: state.stepNumber.map { (current: $0, total: state.stepTotal, label: state.stepLabel) }) { onEvent(.back) }
                .padding(.bottom, ModeSpacing.xs)
                .background(ModeEdgeFade(edge: .top).ignoresSafeArea(edges: .top))
        }
    }

    @ViewBuilder
    private var content: some View {
        switch state.step {
        case .welcome: WelcomeStep(state: state)
        case .name: NameStep(state: state, callName: $callName, onEvent: onEvent)
        case .key: KeyStep(providers: providers, onProvider: onProvider)
        case .model: ModelStep(state: state, providers: providers, onEvent: onEvent)
        case .permissions: PermissionsStep(state: state, onEvent: onEvent)
        case .voice: VoiceStep(state: state, onEvent: onEvent)
        case .test: TestStep(state: state)
        case .result: ResultStep(state: state, onEvent: onEvent)
        case .diagnose: DiagnoseStep(state: state)
        }
    }

    @ViewBuilder
    private var buttons: some View {
        VStack(spacing: ModeSpacing.xs) {
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
                HStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                    ModeSetupButton(L10n.Setup.sendReport, icon: .share, style: .lifted) { onEvent(.sendReport) }
                    ModeSetupButton(L10n.Setup.checkAgain, style: .ink) { onEvent(.checkAgain) }.disabled(state.checking)
                }
                ModeButton(L10n.Setup.continueAnyway, role: .plain) { onEvent(.finish) }
            default:
                ModeButton(L10n.Common.continue, role: state.canContinue ? .primary : .secondary, fullWidth: true) { onEvent(.next) }
                    .disabled(!state.canContinue)
            }
        }
        .padding(.horizontal, ModeSpacing.screenEdge)
        .padding(.top, ModeSpacing.l)
        .background(ModeEdgeFade(edge: .bottom).ignoresSafeArea(edges: .bottom))
    }
}

/// A setup page's title and the line under it, inset a little from the cards as on the boards.
private struct StepHeader: View {
    let title: LocalizedStringResource
    let message: LocalizedStringResource
    var top: CGFloat = ModeSpacing.setupTop
    var small = false

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.setupTitleGap) {
            Text(title).modeText(.title).accessibilityAddTraits(.isHeader)
            Text(message).modeText(small ? .subheadline : .callout, color: ModeColor.textSecondary)
        }
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, ModeSpacing.s)
        .padding(.top, top)
    }
}

private struct WelcomeStep: View {
    let state: OnboardingViewModel.State

    var body: some View {
        VStack(spacing: ModeSpacing.m + ModeSpacing.xxs) {
            ModeMo(.idle, size: ModeSize.setupMoWelcome)
            ModeSetupText(L10n.Setup.appName, style: .welcomeTitle).padding(.top, ModeSpacing.xxs)
            ModeSetupText(L10n.Setup.tagline, style: .welcomeTagline).multilineTextAlignment(.center)
            VStack(spacing: ModeSpacing.s) {
                HStack(spacing: ModeSpacing.s) {
                    ModePill(L10n.Setup.pillOpenSource)
                    ModePill(L10n.Setup.pillOwnKey)
                }
                ModePill(L10n.Setup.pillOnPhone)
            }
            .padding(.top, ModeSpacing.xs)
            ModeCard {
                ModeWelcomeRow(String(localized: L10n.Setup.thisIPhone), subtitle: String(localized: state.paired ? L10n.Setup.paired : L10n.Setup.notPaired),
                               mark: state.paired ? .done : .warning)
                ModeWelcomeRow(String(localized: L10n.Setup.stepName), subtitle: String(localized: L10n.Setup.stepNameDetail), mark: .number(1))
                ModeWelcomeRow(String(localized: L10n.Setup.stepAI), subtitle: String(localized: L10n.Setup.stepAIDetail), mark: .number(2))
                ModeWelcomeRow(String(localized: L10n.Setup.stepTest), subtitle: String(localized: L10n.Setup.stepTestDetail), mark: .number(3))
            }
            .padding(.top, ModeSpacing.xs)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, ModeSpacing.xxl)
    }
}

private struct NameStep: View {
    let state: OnboardingViewModel.State
    @Binding var callName: String
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeMo(.yourTurn, size: ModeSize.moLarge)
            ModeSpeechBubble(L10n.Setup.hiMo)
        }
        .padding(.horizontal, ModeSpacing.s)
        .padding(.top, ModeSpacing.xl)
        StepHeader(title: L10n.Setup.nameTitle, message: L10n.Setup.nameMessage, top: ModeSpacing.l + ModeSpacing.xxs)
        VStack(spacing: ModeSpacing.m) {
            if let name = state.cardName {
                ModeContactCard(initials: state.initials, name: name, subtitle: L10n.Setup.fromCard)
            }
            ModeFieldCard(L10n.Setup.callYou, prompt: L10n.Setup.callYouPrompt, text: $callName)
            ModeButton(state.cardName == nil ? L10n.Setup.chooseCard : L10n.Setup.chooseDifferentCard, icon: .contact, role: .secondary, fullWidth: true) {
                onEvent(.chooseCard)
            }
        }
        .padding(.top, ModeSpacing.m)
        ModeNote(icon: .lock, L10n.Setup.contactsNote).padding(.top, ModeSpacing.xxxl)
    }
}

/// "Connect your AI": every provider, ChatGPT picked by default; connect as many as you like.
private struct KeyStep: View {
    let providers: ProvidersViewModel.State
    let onProvider: (ProvidersViewModel.Event) -> Void

    var body: some View {
        StepHeader(title: L10n.Setup.keyTitle, message: L10n.Providers.setupMessage)
        VStack(alignment: .leading, spacing: ModeSpacing.m) {
            ModeCard(lifted: true) {
                ForEach(providers.rows) { row in
                    ModeSetupProviderRow(
                        title: row.title, detail: String(localized: row.kind.setupDetail), logo: row.kind.logo,
                        tileBackground: row.kind.setupTileBackground, tileForeground: row.kind.setupTileForeground,
                        tag: row.kind == .chatGPT ? String(localized: L10n.Providers.recommended) : nil,
                        selected: row.inUse, connected: row.connected,
                        connectTitle: String(localized: L10n.Providers.connect), connectedTitle: String(localized: L10n.Providers.connected),
                        onSelect: { onProvider(.select(row.id)) }, onConnect: { onProvider(.connect(row.id)) }
                    )
                }
            }
            Group {
                if providers.activeConnected {
                    Text(L10n.Setup.keyConnectedHint(providers.activeTitle))
                } else {
                    Text(L10n.Providers.setupHint)
                }
            }
            .modeText(.footnote, color: ModeColor.textSecondary)
            .padding(.horizontal, ModeSpacing.s)
            ModeNote(icon: .lock, L10n.Providers.keychainNote, inset: ModeSpacing.s)
        }
        .padding(.top, ModeSpacing.xxxl)
    }
}

/// "Pick how Mode thinks" (boards SetupModel, SetupModelClaude): phone control and the brain, each with
/// its model, how hard it thinks, and Mode's recommendation.
private struct ModelStep: View {
    let state: OnboardingViewModel.State
    let providers: ProvidersViewModel.State
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.setupTitleGap) {
            StepHeader(title: L10n.Setup.modelTitle, message: L10n.Setup.modelMessage, top: ModeSpacing.m, small: true)
            if providers.activeConnected {
                ModeConnectedChip(L10n.Setup.providerConnected(providers.activeTitle)).padding(.horizontal, ModeSpacing.s)
            }
        }
        VStack(alignment: .leading, spacing: ModeSpacing.s + ModeSpacing.xxs) {
            RoleCardView(label: L10n.Setup.phoneControl, detail: L10n.Setup.phoneDetail, card: state.phoneCard) { onEvent(.openPicker(.phone)) }
            RoleCardView(label: L10n.Setup.whenStuck, detail: L10n.Setup.stuckDetail, card: state.brainCard) { onEvent(.openPicker(.brain)) }
            if state.onPlan { ModeSetupNote(.mo, lead: String(localized: L10n.Setup.whyLead), text: String(localized: L10n.Setup.whyRest)) }
            Text(state.onPlan ? L10n.Setup.onPlanNote : L10n.Setup.pricesNote).modeText(.footnote, color: ModeColor.textSecondary)
                .padding(.horizontal, ModeSpacing.s).padding(.top, ModeSpacing.xxs)
        }
        .padding(.top, ModeSpacing.l)
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
                Text(label).textCase(.uppercase).modeText(.caption, color: ModeColor.textSecondary).fontWeight(.bold).tracking(ModeSize.separator / 2)
                Text(detail).modeText(.footnote, color: ModeColor.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, ModeSpacing.m + ModeSpacing.xxs)
            .padding(.bottom, ModeSpacing.s + ModeSpacing.xxs)
            Button(action: action) {
                HStack(spacing: ModeSpacing.m) {
                    if let leading = card?.leading { ModeLeadingView(leading) }
                    VStack(alignment: .leading, spacing: ModeSpacing.xs + ModeSpacing.xxs / 2) {
                        Text(card?.name ?? String(localized: L10n.Setup.stuckOff)).modeText(.headline)
                        if let card {
                            if card.effort != nil || card.recommended {
                                HStack(spacing: ModeSpacing.s - ModeSpacing.xxs) {
                                    if let effort = card.effort { ModeTag(effort, icon: .effort, background: ModeColor.cardRaised, foreground: ModeColor.textPrimary, large: true) }
                                    if card.recommended {
                                        ModeTag(String(localized: L10n.Settings.recommended), background: ModeColor.tintSoft, foreground: ModeColor.tint, large: true)
                                    }
                                }
                            }
                            if !card.why.isEmpty { Text(card.why).modeText(.footnote, color: ModeColor.textSecondary) }
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    ModeChevron()
                }
                .padding(.top, ModeSpacing.m)
                .padding(.bottom, ModeSpacing.m + ModeSpacing.xxs)
                .frame(minHeight: ModeSize.setupRoleRow)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)
        }
    }
}

/// A model to choose: maker tile, name and tag, what it's good at and its cost, and the selection mark
/// (board Model; [compact] is the picker sheet's smaller row, board SetupModelPicker).
struct ChoiceRow: View {
    let choice: OnboardingViewModel.Choice
    var compact = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: ModeSpacing.m) {
                ModeLeadingView(choice.leading)
                    .scaleEffect(compact ? ModeSize.setupPickerTile / ModeSize.tile : 1)
                    .frame(width: compact ? ModeSize.setupPickerTile : ModeSize.tile, height: compact ? ModeSize.setupPickerTile : ModeSize.tile)
                VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                    HStack(spacing: ModeSpacing.s - ModeSpacing.xxs) {
                        Text(choice.name).modeText(.calloutEmphasis)
                        if let tag = choice.tag { ModeTag(tag.text, background: tag.background, foreground: tag.foreground, large: compact).fixedSize() }
                    }
                    Text(choice.detail).modeText(.footnote, color: ModeColor.textSecondary)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                ModeRadio(selected: choice.selected)
            }
            .padding(.vertical, ModeSpacing.s)
            .frame(minHeight: compact ? ModeSize.setupPickerRow : ModeSize.setupModelRow)
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
        HStack(alignment: .bottom, spacing: ModeSpacing.m) {
            StepHeader(title: L10n.Setup.permissionsTitle, message: L10n.Setup.permissionsMessage, top: ModeSpacing.xl + ModeSpacing.xs)
            ModeMo(.yourTurn, size: ModeSize.moHero)
        }
        ModeCard(lifted: true) {
            ForEach(state.permissions) { row in
                ModePermissionRow(String(localized: row.title), why: String(localized: row.why), icon: row.icon,
                                  tileBackground: row.tileBackground, tileForeground: row.tileForeground, status: row.action) {
                    onEvent(.allow(row.id))
                }
            }
        }
        .padding(.top, ModeSpacing.xl + ModeSpacing.xs)
    }
}

/// Setup's Talk to Mode board: Mo listening, compact provider headers and the same voice rows as Settings.
private struct VoiceStep: View {
    let state: OnboardingViewModel.State
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        VStack(spacing: ModeSpacing.xs) {
            ModeMo(.listening, size: ModeSize.setupMoTalk)
            ModeSetupText(L10n.Voice.setupTitle, style: .compactTitle).multilineTextAlignment(.center).padding(.top, ModeSpacing.s)
            Text(L10n.Voice.setupPrompt).modeText(.subheadline, color: ModeColor.textSecondary).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity)

        VStack(alignment: .leading, spacing: ModeSpacing.s) {
            HStack {
                Text(L10n.Voice.whoSpeaks).modeText(.section).accessibilityAddTraits(.isHeader)
                Spacer()
                Button { onEvent(.openVoiceSheet(.voices)) } label: {
                    Text(L10n.Voice.viewMore).modeText(.footnoteEmphasis, color: ModeColor.tint)
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
                        HStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                            ModeLeadingView(VoiceService.elevenlabs.tile)
                                .scaleEffect(ModeSize.iconLarge / ModeSize.tile)
                                .frame(width: ModeSize.iconLarge, height: ModeSize.iconLarge)
                            Text(L10n.Voice.humanVoiceSuggestion).modeText(.subheadlineEmphasis)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            ModeChevron()
                        }
                        .frame(minHeight: ModeSize.touch + ModeSpacing.xs)
                        .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
                }
            }

            Text(L10n.Voice.whoListens).modeText(.section).accessibilityAddTraits(.isHeader)
                .padding(.horizontal, ModeSpacing.xs).padding(.top, ModeSpacing.s - ModeSpacing.xxs)
            ModeCard {
                provider(state.listener, hearing: true)
                microphone
            }
        }
        .padding(.top, ModeSpacing.m)
    }

    private var microphone: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeIcon.microphone.image.font(ModeIconFont.small)
                .foregroundStyle(state.microphoneAllowed ? ModeColor.success : ModeColor.infoText)
                .frame(width: ModeSize.voiceProviderLogo, height: ModeSize.voiceProviderLogo)
                .background(state.microphoneAllowed ? ModeColor.tagGreen : ModeColor.infoSoft, in: .circle)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(L10n.Setup.microphone).modeText(.callout)
                Text(state.microphoneAllowed ? L10n.Voice.setupMicNote : L10n.Voice.setupMicRequestNote)
                    .modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            if state.microphoneAllowed {
                HStack(spacing: ModeSpacing.s - ModeSpacing.xxs) {
                    Circle().fill(ModeColor.success).frame(width: ModeSize.setupDot, height: ModeSize.setupDot)
                    Text(L10n.Setup.allowed).modeText(.subheadlineEmphasis, color: ModeColor.success)
                }
            } else {
                ModeAllowButton(state.microphoneDenied ? L10n.Voice.openSettings : L10n.Setup.allow, quiet: state.microphoneDenied) {
                    onEvent(state.microphoneDenied ? .openMicSettings : .allowMic)
                }
            }
        }
        .padding(.vertical, ModeSpacing.s)
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
                HStack(spacing: ModeSpacing.s - ModeSpacing.xxs) {
                    Circle().fill((hearing ? state.listenerConnected : state.speakerConnected) ? ModeColor.success : ModeColor.separator)
                        .frame(width: ModeSize.setupDot, height: ModeSize.setupDot)
                    Text(hearing ? state.listenerStatus : state.speakerStatus)
                        .modeText(.footnote, color: ModeColor.textSecondary).fixedSize(horizontal: false, vertical: true)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button { onEvent(.openVoiceSheet(hearing ? .listen : .speak)) } label: {
                Text(L10n.Providers.change).modeText(.subheadlineEmphasis)
                    .padding(.horizontal, ModeSize.voiceChangePadding).frame(minHeight: ModeSize.voiceChangeHeight)
                    .background(ModeColor.cardRaised, in: .capsule)
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

    private func providerName(_ service: VoiceService) -> some View { Text(service.title).modeText(.calloutEmphasis) }
    @ViewBuilder private func recommendation(_ service: VoiceService) -> some View {
        if let tag = service.recommendation { ModeTag(tag, background: ModeColor.cardRaised, foreground: ModeColor.textPrimary).fixedSize() }
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
        .padding(.top, ModeSpacing.s)
        ModeSetupNote(.icon(.info), text: String(localized: L10n.Setup.testNote)).padding(.top, ModeSpacing.setupTop)
    }
}

private struct ResultStep: View {
    let state: OnboardingViewModel.State
    let onEvent: (OnboardingViewModel.Event) -> Void

    var body: some View {
        VStack(spacing: ModeSpacing.s) {
            ModeMo(state.testPassed ? .done : .stuck, size: ModeSize.setupMoResult)
            ModeSetupText(state.testPassed ? L10n.Setup.resultTitle : L10n.Setup.resultFailedTitle, style: .compactTitle)
                .multilineTextAlignment(.center)
                .padding(.horizontal, ModeSpacing.s)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, ModeSpacing.xl + ModeSpacing.xs)

        HStack(alignment: .top, spacing: ModeSpacing.m + ModeSpacing.xxs) {
            Group {
                if let shot = state.shot {
                    Image(uiImage: shot).resizable().scaledToFill()
                } else {
                    ModeShotPlaceholder()
                }
            }
            .frame(width: ModeSize.resultShotWidth, height: ModeSize.setupShotHeight)
            .clipShape(.rect(cornerRadius: ModeRadius.setupShot))
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                Text(L10n.Setup.whatModeDid).modeText(.footnote, color: ModeColor.textSecondary)
                ForEach(state.did) { row in
                    ModeDidRow(String(localized: row.title), detail: row.detail, mark: row.leading)
                }
                Spacer(minLength: ModeSpacing.none)
                Text(L10n.Setup.shotCaption).modeText(.caption, color: ModeColor.textSecondary)
            }
            .padding(.top, ModeSpacing.xs)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(ModeSpacing.m)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.card), lifted: false)
        .padding(.top, ModeSpacing.l)

        VStack(spacing: ModeSpacing.m + ModeSpacing.xxs) {
            Text(L10n.Setup.question).modeText(.headline).multilineTextAlignment(.center)
            HStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                ModeSetupButton(L10n.Setup.no, style: .blueQuiet) { onEvent(.worked(false)) }
                ModeSetupButton(L10n.Setup.itWorked, style: .blue) { onEvent(.worked(true)) }
            }
        }
        .padding(.horizontal, ModeSpacing.l)
        .padding(.top, ModeSpacing.l + ModeSpacing.xxs)
        .padding(.bottom, ModeSpacing.l)
        .frame(maxWidth: .infinity)
        .background(ModeColor.infoSoft, in: .rect(cornerRadius: ModeRadius.card))
        .padding(.top, ModeSpacing.m)
    }
}

private struct DiagnoseStep: View {
    let state: OnboardingViewModel.State

    var body: some View {
        HStack(alignment: .top, spacing: ModeSpacing.m) {
            VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                ModeSetupText(L10n.Setup.diagnoseTitle, style: .diagnoseTitle)
                Text(L10n.Setup.diagnoseMessage).modeText(.subheadline, color: ModeColor.textSecondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            ModeMo(.stuck, size: ModeSize.setupMoDiagnose)
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, ModeSpacing.xs)
        .padding(.top, ModeSpacing.s)
        ModeCard {
            ForEach(state.checks) { check in
                ModeCheckRow(String(localized: check.title), state: check.state, detail: check.detail, trailing: check.trailing)
            }
        }
        .padding(.top, ModeSpacing.l)
    }
}
