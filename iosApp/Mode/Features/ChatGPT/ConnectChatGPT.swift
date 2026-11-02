import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI

/// "Continue with ChatGPT", as on its board: sign in, then the plan is checked with one call.
@MainActor
@Observable
final class ConnectChatGPTViewModel {
    enum Phase: Equatable {
        case idle, waiting, checking
        case connected(models: [String], check: String)
        case failed(message: String, notEligible: Bool)
    }

    private(set) var phase: Phase = .idle
    private(set) var connected = false
    /// The ChatGPT page to show, while signing in.
    var signInURL: URL?
    private let presenter: ConnectChatGptPresenter
    private let loopback = LoopbackServer()
    private var updates: Updates?

    init(screens: Screens) {
        presenter = screens.connectChatGpt()
        updates = observe(presenter.state) { [weak self] s in
            self?.connected = s.connected
            self?.phase = switch onEnum(of: s.phase) {
            case .idle: .idle
            case .waiting: .waiting
            case .checking: .checking
            case let .connected(c): .connected(models: c.models, check: c.check)
            case let .failed(f): .failed(message: f.message, notEligible: f.notEligible)
            }
        }
    }

    func signIn() {
        do {
            try loopback.start(port: UInt16(presenter.loopbackPort)) { [weak self] url in
                self?.signInURL = nil
                self?.presenter.finish(callbackUrl: url.absoluteString)
            }
            signInURL = URL(string: presenter.start())
        } catch {
            phase = .failed(message: error.localizedDescription, notEligible: false)
        }
    }

    /// The sign-in page closed without coming back.
    func pageClosed() {
        guard signInURL != nil else { return }
        signInURL = nil
        loopback.stop()
        presenter.cancelled()
    }

    func recheck() { presenter.recheck() }
    func signOut() { presenter.signOut() }

    isolated deinit {
        loopback.stop()
        updates?.cancel()
        presenter.close()
    }
}

struct ConnectChatGPTScreen: View {
    let phase: ConnectChatGPTViewModel.Phase
    let connected: Bool
    let onSignIn: () -> Void
    let onRecheck: () -> Void
    let onSignOut: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.l) {
                Text(L10n.ChatGPT.subtitle).modeText(.callout, color: ModeColor.textSecondary)
                ModeCard { status }
                switch phase {
                case .waiting, .checking: EmptyView()
                default:
                    if connected {
                        ModeButton(L10n.ChatGPT.check, icon: .runAgain, role: .secondary, fullWidth: true, action: onRecheck)
                        ModeButton(L10n.ChatGPT.signOut, role: .destructive, fullWidth: true, action: onSignOut)
                    } else {
                        ModeButton(L10n.ChatGPT.continueWith, icon: .sparkle, role: .primary, fullWidth: true, action: onSignIn)
                    }
                }
                ModeNote(icon: .lock, L10n.ChatGPT.usageNote)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .modeScreen()
        .navigationTitle(Text(L10n.ChatGPT.title))
        .navigationBarTitleDisplayMode(.large)
    }

    @ViewBuilder private var status: some View {
        switch phase {
        case .waiting: ModeRow(String(localized: L10n.ChatGPT.waiting), subtitle: String(localized: L10n.ChatGPT.waitingHint), leading: .progress) { EmptyView() }
        case .checking: ModeRow(String(localized: L10n.ChatGPT.checking), leading: .progress) { EmptyView() }
        case let .connected(models, check):
            ModeRow(String(localized: L10n.ChatGPT.connected), subtitle: check, leading: .success) { EmptyView() }
            ModeRow(String(localized: L10n.ChatGPT.models), subtitle: models.joined(separator: ", "), leading: .icon(.model)) { EmptyView() }
        case let .failed(message, notEligible):
            ModeRow(String(localized: notEligible ? L10n.ChatGPT.notEligible : L10n.ChatGPT.failed), subtitle: message,
                    leading: .symbol(.warning, ModeColor.warning)) { EmptyView() }
        case .idle:
            ModeRow(String(localized: connected ? L10n.ChatGPT.connected : L10n.ChatGPT.notConnected),
                    subtitle: String(localized: L10n.ChatGPT.planHint), leading: connected ? .success : .icon(.sparkle)) { EmptyView() }
        }
    }
}

struct ConnectChatGPTRoute: View {
    @State private var model: ConnectChatGPTViewModel
    @Environment(\.webAuthenticationSession) private var webAuthentication

    init(app: AppModel) {
        _model = State(initialValue: ConnectChatGPTViewModel(screens: app.screens))
    }

    var body: some View {
        ConnectChatGPTScreen(phase: model.phase, connected: model.connected, onSignIn: model.signIn,
                             onRecheck: model.recheck, onSignOut: model.signOut)
            // The page answers on the loopback, not a URL scheme: the session is closed once that arrives.
            .task(id: model.signInURL) {
                guard let url = model.signInURL else { return }
                _ = try? await webAuthentication.authenticate(using: url, callbackURLScheme: "mode",
                                                               preferredBrowserSession: .ephemeral)
                model.pageClosed()
            }
    }
}
