import ModeDesignSystem
import AuthenticationServices
import ModeKit
import Observation
import SwiftUI

/// The first run: Welcome, Connect your AI, then a real first job, which runs in its chat like any task.
@MainActor
@Observable
final class FirstRunModel {
    /// `connected` carries the AI that is connected and in use (a ModelProvider id: "chatgpt", "claude_oauth", "openrouter").
    enum Connect: Equatable { case choose, waiting, checking, connected(String), notEligible, failed(String) }
    enum Step: Equatable { case welcome, connect, firstTask }

    struct State: Equatable {
        var step: Step = .welcome
        var connect: Connect = .choose
        var connected = false
        var jobs: [String] = []
        /// Asking iOS for the VPN and notifications, then starting the job.
        var starting = false
        /// The first job is in the queue: its chat ("" when it has none). Setup is over.
        var started: String?
    }

    enum Event { case getStarted, github, connectChatGPT, cancelSignIn, moreOptions, useOpenRouter, next, back, startJob(String), finish }

    private(set) var state = State()
    var showingProviders = false
    /// The ChatGPT page, while it's open.
    var signInURL: URL?
    private let presenter: FirstRunPresenter
    private let loopback = LoopbackServer()
    private var updates: Updates?

    init(screens: Screens) {
        presenter = screens.firstRun()
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .getStarted: presenter.getStarted()
        case .github: if let url = URL(string: Self.repository) { UIApplication.shared.open(url) }
        case .connectChatGPT:
            do {
                try loopback.start(port: UInt16(presenter.loopbackPort)) { [weak self] url in
                    self?.signInURL = nil
                    self?.presenter.chatGptFinished(callbackUrl: url.absoluteString)
                }
                signInURL = URL(string: presenter.connectChatGpt())
            } catch {
                presenter.chatGptCancelled()
            }
        case .cancelSignIn:
            loopback.stop()
            signInURL = nil
            presenter.chatGptCancelled()
        case .moreOptions, .useOpenRouter: showingProviders = true
        case .next: presenter.next()
        case .back: presenter.back()
        case let .startJob(job): presenter.startJob(text: job)
        case .finish: presenter.finish()
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    /// Mode's code (open source).
    static let repository = "https://github.com/Piyusinha/mode"

    /// The ChatGPT page closed: if the sign-in never came back, it was cancelled.
    func pageClosed() {
        guard signInURL != nil else { return }
        signInURL = nil
        loopback.stop()
        presenter.chatGptCancelled()
    }

    static func map(_ s: FirstRunUiState) -> State {
        let step: Step = switch s.step {
        case .connect: .connect
        case .firstTask: .firstTask
        default: .welcome
        }
        let connect: Connect = switch onEnum(of: s.connect) {
        case .choose: .choose
        case .waiting: .waiting
        case .checking: .checking
        case let .connected(c): .connected(c.provider)
        case let .failed(f): f.notEligible ? .notEligible : .failed(f.message)
        }
        return State(step: step, connect: connect, connected: s.connected, jobs: s.firstJobs, starting: s.starting, started: s.started)
    }
}

/// Welcome (its board).
struct WelcomeScreen: View {
    let onEvent: (FirstRunModel.Event) -> Void

    var body: some View {
        VStack(spacing: ModeSpacing.l) {
            Spacer()
            VStack(spacing: ModeSpacing.xxl) {
                ModeMo(.idle, size: ModeSize.moWelcome)
                VStack(spacing: ModeSpacing.m) {
                    Text(L10n.FirstRun.welcomeTitle).modeText(.title).accessibilityAddTraits(.isHeader)
                    Text(L10n.FirstRun.welcomeBody).modeText(.body)
                    Text(L10n.FirstRun.welcomeCaveat).modeText(.subheadline, color: ModeColor.textSecondary)
                }
                .multilineTextAlignment(.center)
            }
            Spacer()
            ModeButton(L10n.FirstRun.getStarted, role: .primary, fullWidth: true) { onEvent(.getStarted) }
            ModeButton(L10n.FirstRun.seeCode, role: .plain) { onEvent(.github) }
        }
        .padding(.horizontal, ModeSpacing.xl + ModeSpacing.xs)
        .padding(.bottom, ModeSpacing.l)
        .modeScreen()
    }
}

/// Connect your AI (its boards: choose, waiting, plan can't be used).
struct ConnectAIScreen: View {
    let state: FirstRunModel.State
    let onEvent: (FirstRunModel.Event) -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: ModeSpacing.xl + ModeSpacing.xs) {
            ModeProgressDots(current: 2, total: 3, label: L10n.FirstRun.step(2, of: 3))
            VStack(alignment: .leading, spacing: ModeSpacing.xs + ModeSpacing.xxs) {
                Text(L10n.FirstRun.connectTitle).modeText(.tabTitle).accessibilityAddTraits(.isHeader)
                Text(L10n.FirstRun.connectBody).modeText(.callout)
            }
            VStack(alignment: .leading, spacing: ModeSpacing.s + ModeSpacing.xxs) {
                card
                Button { onEvent(.moreOptions) } label: {
                    HStack {
                        Text(L10n.FirstRun.moreOptions).modeText(.calloutEmphasis)
                        Spacer()
                        ModeChevron()
                    }
                    .padding(.horizontal, ModeSpacing.l + ModeSpacing.xxs)
                    .frame(minHeight: ModeSize.moreOptions)
                    .background(ModeColor.cardRaised, in: .rect(cornerRadius: ModeRadius.firstRunCard))
                }
                .buttonStyle(ModePressStyle())
                Text(L10n.FirstRun.keychain).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.labelInset)
            }
            Spacer()
            ModeButton(L10n.FirstRun.continue, role: .primary, fullWidth: true) { onEvent(.next) }
                .disabled(!state.connected)
        }
        .padding(.horizontal, ModeSpacing.xl)
        .padding(.top, ModeSpacing.s)
        .padding(.bottom, ModeSpacing.l)
        .modeScreen()
    }

    /// The connected AI's logo and name, by its ModelProvider id.
    private static func provider(_ id: String) -> (ModeLogo, String) {
        switch id {
        case "claude_oauth": (.claude, String(localized: L10n.Providers.claude))
        case "openrouter": (.openRouter, String(localized: L10n.Providers.openRouter))
        default: (.openAI, L10n.FirstRun.chatGPT)
        }
    }

    @ViewBuilder private var card: some View {
        switch state.connect {
        case .choose:
            ModeProviderCard(logo: .openAI, title: L10n.FirstRun.chatGPT, tag: L10n.FirstRun.recommended, detail: L10n.FirstRun.chatGPTDetail) {
                ModeButton(L10n.FirstRun.connect, role: .main) { onEvent(.connectChatGPT) }
            }
        case .waiting, .checking:
            ModeProviderCard(logo: .openAI, title: state.connect == .waiting ? L10n.FirstRun.waiting : L10n.FirstRun.checking, tag: nil,
                             detail: L10n.FirstRun.waitingDetail) {
                ModeButton(L10n.FirstRun.cancel, role: .other) { onEvent(.cancelSignIn) }
            }
        case let .connected(provider):
            let (logo, title) = Self.provider(provider)
            ModeProviderCard(logo: logo, title: title, tag: nil, detail: L10n.FirstRun.connected) { EmptyView() }
        case .notEligible:
            ModeProviderCard(logo: .openAI, title: L10n.FirstRun.notEligible, tag: nil, detail: L10n.FirstRun.notEligibleDetail, warning: true) {
                VStack(spacing: ModeSpacing.s) {
                    ModeButton(L10n.FirstRun.useOpenRouter, role: .main) { onEvent(.useOpenRouter) }
                    ModeButton(L10n.FirstRun.tryAgain, role: .other) { onEvent(.connectChatGPT) }
                }
            }
        case let .failed(message):
            ModeProviderCard(logo: .openAI, title: L10n.FirstRun.failed, tag: nil, detail: message) {
                ModeButton(L10n.FirstRun.tryAgain, role: .main) { onEvent(.connectChatGPT) }
            }
        }
    }
}

/// Give Mo its first job (its board): three small jobs, or type your own. It runs in its chat like any task;
/// voice, dictation and the rest are set up later.
struct FirstTaskScreen: View {
    let jobs: [String]
    let starting: Bool
    let onEvent: (FirstRunModel.Event) -> Void
    @State private var own = ""

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.xl + ModeSpacing.xs) {
                ModeProgressDots(current: 3, total: 3, label: L10n.FirstRun.step(3, of: 3))
                VStack(spacing: ModeSpacing.l) {
                    ModeMo(starting ? .working : .listening, size: ModeSize.moFirstJob)
                    VStack(spacing: ModeSpacing.xs + ModeSpacing.xxs) {
                        Text(L10n.FirstRun.firstJobTitle).modeText(.tabTitle).accessibilityAddTraits(.isHeader)
                        Text(L10n.FirstRun.firstJobBody).modeText(.callout)
                    }
                    .multilineTextAlignment(.center)
                }
                .frame(maxWidth: .infinity)
                VStack(alignment: .leading, spacing: ModeSpacing.s + ModeSpacing.xxs) {
                    ForEach(jobs, id: \.self) { job in ModeJobButton(job) { onEvent(.startJob(job)) } }
                    ModeJobField(text: $own, prompt: L10n.FirstRun.ownJob, sendLabel: L10n.FirstRun.start) { onEvent(.startJob(own)) }
                    Text(starting ? L10n.FirstRun.starting : L10n.FirstRun.permissionsNote)
                        .modeText(.footnote, color: ModeColor.textSecondary)
                        .padding(.horizontal, ModeSpacing.labelInset)
                }
                .disabled(starting)
                ModeButton(L10n.FirstRun.skip, role: .plain) { onEvent(.finish) }
                    .frame(maxWidth: .infinity)
                    .disabled(starting)
            }
            .padding(.horizontal, ModeSpacing.xl)
            .padding(.top, ModeSpacing.s)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollDismissesKeyboard(.interactively)
        .modeScreen()
    }
}

/// The first run. Once the first job is in the queue (or skipped), setup ends and Mode opens on that job's chat.
struct FirstRunRoute: View {
    let app: AppModel
    @LazyState private var model: FirstRunModel
    @LazyState private var providers: ProvidersViewModel
    @Environment(\.webAuthenticationSession) private var webAuthentication

    init(app: AppModel) {
        self.app = app
        _model = LazyState(wrappedValue: FirstRunModel(screens: app.screens))
        _providers = LazyState(wrappedValue: ProvidersViewModel(screens: app.screens, picksUnconnected: false))
    }

    var body: some View {
        ZStack {
            switch model.state.step {
            case .firstTask: FirstTaskScreen(jobs: model.state.jobs, starting: model.state.starting, onEvent: send)
            case .connect: ConnectAIScreen(state: model.state, onEvent: model.send)
            case .welcome: WelcomeScreen(onEvent: model.send)
            }
        }
        .animation(ModeMotion.standard, value: model.state.step)
        // The page answers on the loopback, not a URL scheme: the session is closed once that arrives.
        .task(id: model.signInURL) {
            guard let url = model.signInURL else { return }
            _ = try? await webAuthentication.authenticate(using: url, callbackURLScheme: "mode", preferredBrowserSession: .ephemeral)
            model.pageClosed()
        }
        .sheet(isPresented: $model.showingProviders) {
            ProvidersSheet(app: app, state: providers.state, onEvent: providers.send) { model.showingProviders = false }
                .modifier(ProviderLinks(model: providers, app: app))
        }
        .onChange(of: model.state.started) { _, conversation in
            guard let conversation else { return }
            if !conversation.isEmpty { AppLinks.shared.pending = .conversation(id: conversation) }
            app.onboardingFinished()
        }
    }

    private func send(_ event: FirstRunModel.Event) {
        model.send(event)
        if case .finish = event { app.onboardingFinished() }
    }
}
