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
                status
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

    /// The state as a card with a round icon, as on the board: waiting, checking, connected, or why not.
    @ViewBuilder private var status: some View {
        switch phase {
        case .waiting: card(.progress, ModeColor.tintSoft, L10n.ChatGPT.waiting, String(localized: L10n.ChatGPT.waitingHint))
        case .checking: card(.progress, ModeColor.tintSoft, L10n.ChatGPT.checking, nil)
        case let .connected(models, check):
            card(.icon(.checkmark), ModeColor.tagGreen, L10n.ChatGPT.connected, check)
            card(.icon(.model), ModeColor.tintSoft, L10n.ChatGPT.models, models.joined(separator: ", "))
        case let .failed(message, notEligible):
            card(.icon(.warning), ModeColor.tileOrange, notEligible ? L10n.ChatGPT.notEligible : L10n.ChatGPT.failed, message)
        case .idle:
            card(connected ? .icon(.checkmark) : .icon(.sparkle), connected ? ModeColor.tagGreen : ModeColor.tintSoft,
                 connected ? L10n.ChatGPT.connected : L10n.ChatGPT.notConnected, String(localized: L10n.ChatGPT.planHint))
        }
    }

    private enum Glyph { case progress, icon(ModeIcon) }

    private func card(_ glyph: Glyph, _ background: Color, _ title: LocalizedStringResource, _ text: String?) -> some View {
        HStack(alignment: .top, spacing: ModeSpacing.m) {
            Group {
                switch glyph {
                case .progress: ProgressView().tint(ModeColor.tint)
                case let .icon(icon): icon.image.foregroundStyle(ModeColor.textPrimary)
                }
            }
            .frame(width: ModeSize.tile, height: ModeSize.tile)
            .background(background, in: .circle)
            VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                Text(title).modeText(.bodyEmphasis)
                if let text { Text(text).modeText(.subheadline, color: ModeColor.textSecondary) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(ModeSpacing.l)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.field))
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
