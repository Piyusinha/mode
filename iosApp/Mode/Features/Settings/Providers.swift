import AuthenticationServices
import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI
import UIKit

/// The AI providers, for setup and Settings (boards "Connect your AI" and "AI providers"): several can be
/// connected at once; the one picked is what tasks use. Connecting one opens its own sheet.
@MainActor
@Observable
final class ProvidersViewModel {
    enum Kind: Equatable { case chatGPT, claude, openRouter }

    struct Row: Identifiable, Equatable {
        let id: String
        let kind: Kind
        let title: String
        /// "Plus · connected", "Not connected · pay as you go".
        let status: String
        let connected: Bool
        let inUse: Bool
    }

    struct State: Equatable {
        var rows: [Row] = []
        var activeTitle = ""
        var activeKind: Kind = .chatGPT
        var activeConnected = false
        var summary = ""
        var openRouterSigningIn = false
        var openRouterError: String?
        var claudeAwaitingCode = false
        var claudeSigningIn = false
        var claudeError: String?
    }

    /// The sheet for connecting one provider.
    enum Link: String, Identifiable { case chatGPT, claude, openRouter; var id: String { rawValue } }

    enum Event {
        case select(String)
        case connect(String)
        case connectActive
        case signInOpenRouter
        case pasteKey
        case saveKey
        case startClaude
        case finishClaude
        case cancelClaude
        case disconnect(String)
    }

    private(set) var state = State()
    var link: Link?
    var key = ""
    var claudeCode = ""
    /// OpenRouter's sign-in page, while it is open.
    var signInURL: URL?
    /// OpenRouter's sheet: switch to it once connected (on by default).
    var useOnceConnected = true
    let callbackScheme = "mode"
    /// Setup: picking one that is not connected yet marks it as the one to connect; Settings connects it.
    private let picksUnconnected: Bool
    private let presenter: ProvidersPresenter
    private var updates: Updates?

    init(screens: Screens, picksUnconnected: Bool) {
        presenter = screens.providers()
        self.picksUnconnected = picksUnconnected
        updates = observe(presenter.state) { [weak self] s in
            guard let self else { return }
            let next = Self.map(s)
            // A sheet closes by itself once its provider is connected.
            if let link, next.rows.contains(where: { $0.kind == Self.kind(of: link) && $0.connected }),
               !state.rows.contains(where: { $0.kind == Self.kind(of: link) && $0.connected }) {
                self.link = nil
            }
            state = next
        }
    }

    func send(_ event: Event) {
        switch event {
        case let .select(id):
            guard let row = state.rows.first(where: { $0.id == id }) else { return }
            if row.connected { presenter.select(id: id) } else { send(.connect(id)) }
        case let .connect(id):
            guard let row = state.rows.first(where: { $0.id == id }) else { return }
            if picksUnconnected { presenter.select(id: id) }
            link = switch row.kind {
            case .chatGPT: .chatGPT
            case .claude: .claude
            case .openRouter: .openRouter
            }
        case .connectActive:
            if let row = state.rows.first(where: { $0.inUse }) { send(.connect(row.id)) }
        case .signInOpenRouter:
            signInURL = URL(string: presenter.startOpenRouter(callbackUrl: OnboardingPresenter.companion.SIGN_IN_CALLBACK,
                                                               useWhenConnected: useOnceConnected))
        case .pasteKey:
            if let text = UIPasteboard.general.string { key = text.trimmingCharacters(in: .whitespacesAndNewlines) }
        case .saveKey:
            presenter.saveOpenRouterKey(key: key, useWhenConnected: useOnceConnected)
            key = ""
        case .startClaude:
            claudeCode = ""
            if let address = presenter.startClaude(), let url = URL(string: address) { UIApplication.shared.open(url) }
        case .finishClaude:
            let code = claudeCode
            claudeCode = ""
            presenter.finishClaude(code: code)
        case .cancelClaude:
            claudeCode = ""
            presenter.cancelClaude()
        case let .disconnect(id): presenter.disconnect(id: id)
        }
    }

    /// OpenRouter's page came back (with its callback) or closed.
    func signInFinished(callback: URL?, error: Error?) {
        signInURL = nil
        let cancelled = (error as? ASWebAuthenticationSessionError)?.code == .canceledLogin
        presenter.finishOpenRouter(callbackUrl: callback?.absoluteString, error: cancelled ? nil : error?.localizedDescription)
    }

    isolated deinit {
        presenter.cancelClaude()
        updates?.cancel()
        presenter.close()
    }

    private static func kind(of link: Link) -> Kind {
        switch link {
        case .chatGPT: .chatGPT
        case .claude: .claude
        case .openRouter: .openRouter
        }
    }

    private static func map(_ s: ProvidersUiState) -> State {
        func kind(_ id: String) -> Kind { id == "chatgpt" ? .chatGPT : id == "claude_oauth" ? .claude : .openRouter }
        func title(_ k: Kind) -> String {
            switch k {
            case .chatGPT: String(localized: L10n.Providers.chatGPT)
            case .claude: String(localized: L10n.Providers.claude)
            case .openRouter: String(localized: L10n.Providers.openRouter)
            }
        }
        let rows = s.rows.map { r -> Row in
            let k = kind(r.id)
            let detail: String = switch k {
            case .chatGPT: String(localized: L10n.Providers.chatGPTDetail)
            case .claude: String(localized: L10n.Providers.claudeDetail)
            case .openRouter: r.keyEnding.map { L10n.Providers.keyEnding($0) } ?? String(localized: L10n.Providers.openRouterDetail)
            }
            let state = String(localized: r.connected ? L10n.Providers.connected : L10n.Providers.notConnected)
            // Connected first when it is; otherwise what it is: "Not connected · pay as you go".
            return Row(id: r.id, kind: k, title: title(k), status: [state, detail].joined(separator: " · "), connected: r.connected, inUse: r.inUse)
        }
        let active = kind(s.active)
        return State(
            rows: rows, activeTitle: title(active), activeKind: active, activeConnected: s.activeConnected,
            summary: s.activeConnected ? L10n.Providers.connectedCount(Int(s.connectedCount)) : String(localized: L10n.Providers.notConnectedYet),
            openRouterSigningIn: s.openRouterSigningIn, openRouterError: s.openRouterError,
            claudeAwaitingCode: s.claude.awaitingCode, claudeSigningIn: s.claude.signingIn, claudeError: s.claude.error
        )
    }
}

extension ProvidersViewModel.Kind {
    var logo: ModeLogo {
        switch self {
        case .chatGPT: .openAI
        case .claude: .claude
        case .openRouter: .openRouter
        }
    }

    var tileBackground: Color {
        switch self {
        case .chatGPT: ModeColor.textPrimary
        case .claude: ModeColor.tileOrange
        case .openRouter: ModeColor.tintSoft
        }
    }

    var tileForeground: Color {
        switch self {
        case .chatGPT: ModeColor.page
        case .claude: ModeColor.tileOrangeText
        case .openRouter: ModeColor.tint
        }
    }
}

/// Every provider, its state, and the one in use (boards "AI providers" and "Connect your AI").
struct ProviderList: View {
    let rows: [ProvidersViewModel.Row]
    /// Setup: radio first and ChatGPT recommended. Settings: radio last and "In use" on the picked one.
    let setup: Bool
    let onEvent: (ProvidersViewModel.Event) -> Void

    var body: some View {
        ModeCard {
            ForEach(rows) { row in
                ModeProviderRow(
                    title: row.title, status: row.status, logo: row.kind.logo,
                    tileBackground: row.kind.tileBackground, tileForeground: row.kind.tileForeground,
                    tag: setup ? (row.kind == .chatGPT ? String(localized: L10n.Providers.recommended) : nil)
                        : (row.inUse && row.connected ? String(localized: L10n.Providers.inUse) : nil),
                    selected: row.inUse, connected: row.connected, connectTitle: String(localized: L10n.Providers.connect),
                    radio: setup ? .leading : .trailing,
                    onSelect: { onEvent(.select(row.id)) }, onConnect: { onEvent(.connect(row.id)) }
                )
                .contextMenu {
                    if row.connected {
                        Button(role: .destructive) { onEvent(.disconnect(row.id)) } label: { Text(L10n.Providers.disconnect) }
                    }
                }
            }
        }
    }
}

/// Settings' "AI providers" sheet: connect as many as you like, pick the one Mode uses.
struct ProvidersSheet: View {
    let state: ProvidersViewModel.State
    let onEvent: (ProvidersViewModel.Event) -> Void
    let onDone: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                HStack(alignment: .top, spacing: ModeSpacing.m) {
                    VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                        Text(L10n.Providers.title).modeText(.title2)
                        Text(L10n.Providers.sheetMessage).modeText(.subheadline, color: ModeColor.textSecondary)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Button(action: onDone) {
                        Text(L10n.Common.done).modeText(.subheadline).fontWeight(.semibold)
                            .padding(.horizontal, ModeSpacing.l).frame(minHeight: ModeSize.badge)
                    }
                    .buttonStyle(.glass)
                }
                ProviderList(rows: state.rows, setup: false, onEvent: onEvent)
                Text(L10n.Providers.switchNote).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
                ModeNote(icon: .lock, L10n.Providers.keychainNote)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.top, ModeSpacing.xl)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .modeScreen()
        .presentationDetents([.medium, .large])
        .presentationDragIndicator(.visible)
    }
}

/// A sheet's heading with its provider's tile (board "Connect a provider").
struct ProviderSheetHeader: View {
    let kind: ProvidersViewModel.Kind
    let title: LocalizedStringResource
    let message: LocalizedStringResource

    var body: some View {
        HStack(spacing: ModeSpacing.m) {
            ModeLeadingView(.logoTile(kind.logo, background: kind.tileBackground, foreground: kind.tileForeground))
            VStack(alignment: .leading, spacing: ModeSpacing.xxs) {
                Text(title).modeText(.title2)
                Text(message).modeText(.subheadline, color: ModeColor.textSecondary)
            }
        }
    }
}

/// "Connect OpenRouter", as on its board: sign in (no copying keys), or paste a key; use it once connected.
struct OpenRouterLinkSheet: View {
    let state: ProvidersViewModel.State
    @Binding var key: String
    @Binding var useOnceConnected: Bool
    let onEvent: (ProvidersViewModel.Event) -> Void
    let onDone: () -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                ProviderSheetHeader(kind: .openRouter, title: L10n.Providers.connectOpenRouter, message: L10n.Providers.openRouterMessage)
                ModeButton(L10n.Providers.signInOpenRouter, icon: .link, fullWidth: true) { onEvent(.signInOpenRouter) }
                    .disabled(state.openRouterSigningIn)
                Text(L10n.Setup.connectNote).modeText(.footnote, color: ModeColor.textSecondary).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                if let error = state.openRouterError { Text(error).modeText(.footnote, color: ModeColor.danger) }
                ModeOrDivider(L10n.Setup.or)
                ModeFieldCard(L10n.Setup.keyLabel, prompt: L10n.Setup.keyPrompt, text: $key, secure: true, buttonTitle: L10n.Setup.paste) { onEvent(.pasteKey) }
                if !key.isEmpty { ModeButton(L10n.Settings.saveKey, role: .secondary, fullWidth: true) { onEvent(.saveKey) } }
                Button { useOnceConnected.toggle() } label: {
                    HStack(spacing: ModeSpacing.s) {
                        ModeRadio(selected: useOnceConnected)
                        Text(L10n.Providers.useOpenRouterOnceConnected).modeText(.subheadline)
                    }
                }
                .buttonStyle(.plain)
                .padding(.horizontal, ModeSpacing.xs)
                ModeNote(icon: .lock, L10n.Setup.keyNote)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.top, ModeSpacing.xl)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .modeScreen()
        .presentationDetents([.large])
        .presentationDragIndicator(.visible)
    }
}

/// "Connect Claude": sign in on Claude's page, then paste the code it shows.
struct ClaudeLinkSheet: View {
    let state: ProvidersViewModel.State
    @Binding var code: String
    let onEvent: (ProvidersViewModel.Event) -> Void
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: ModeSpacing.l) {
                    Text(L10n.Claude.note).modeText(.callout, color: ModeColor.textSecondary)
                    if state.claudeAwaitingCode {
                        Text(L10n.Claude.instructions).modeText(.footnote, color: ModeColor.textSecondary)
                        ModeFieldCard(L10n.Claude.code, prompt: L10n.Claude.codePrompt, text: $code, secure: true)
                        ModeButton(state.claudeSigningIn ? L10n.Claude.connecting : L10n.Claude.finish, fullWidth: true) { onEvent(.finishClaude) }
                            .disabled(state.claudeSigningIn || code.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    } else {
                        ModeButton(L10n.Claude.connect, icon: .claude, fullWidth: true) { onEvent(.startClaude) }
                    }
                    if let error = state.claudeError { Text(error).modeText(.footnote, color: ModeColor.danger) }
                    ModeNote(icon: .lock, L10n.Providers.keychainNote)
                }
                .padding(.horizontal, ModeSpacing.screenEdge)
                .padding(.bottom, ModeSpacing.xxl)
            }
            .modeScreen()
            .navigationTitle(Text(L10n.Providers.connectClaude))
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button { onEvent(.cancelClaude); onDone() } label: { Text(L10n.Common.cancel) }
                }
            }
        }
        .presentationDetents([.large])
    }
}

/// The sheets for connecting each provider, and OpenRouter's web sign-in, for any screen that lists providers.
struct ProviderLinks: ViewModifier {
    @Bindable var model: ProvidersViewModel
    let app: AppModel
    @Environment(\.webAuthenticationSession) private var webAuthentication

    func body(content: Content) -> some View {
        content
            .sheet(item: $model.link) { link in
                switch link {
                case .chatGPT:
                    NavigationStack {
                        ConnectChatGPTRoute(app: app)
                            .toolbar { ToolbarItem(placement: .confirmationAction) { Button { model.link = nil } label: { Text(L10n.Common.done) } } }
                    }
                case .openRouter:
                    OpenRouterLinkSheet(state: model.state, key: $model.key, useOnceConnected: $model.useOnceConnected, onEvent: model.send) { model.link = nil }
                        .task(id: model.signInURL) {
                            guard let url = model.signInURL else { return }
                            do {
                                let callback = try await webAuthentication.authenticate(using: url, callbackURLScheme: model.callbackScheme)
                                model.signInFinished(callback: callback, error: nil)
                            } catch {
                                model.signInFinished(callback: nil, error: error)
                            }
                        }
                case .claude:
                    ClaudeLinkSheet(state: model.state, code: $model.claudeCode, onEvent: model.send) { model.link = nil }
                }
            }
    }
}
