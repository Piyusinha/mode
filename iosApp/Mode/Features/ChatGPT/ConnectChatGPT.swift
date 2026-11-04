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
    /// In setup: the step this page belongs to (its dots). Nil when opened from Settings.
    var step: SetupStep? = nil
    let onSignIn: () -> Void
    let onRecheck: () -> Void
    let onSignOut: () -> Void
    var onBack: () -> Void = {}
    var onContinue: () -> Void = {}
    /// In setup: switches to OpenRouter when the plan can't be used. Nil hides the link.
    var onUseOpenRouter: (() -> Void)? = nil

    private var inSetup: Bool { step != nil }
    private var isConnected: Bool {
        if case .connected = phase { return true }
        return connected && phase == .idle
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                VStack(alignment: .leading, spacing: ModeSpacing.setupTitleGap) {
                    Text(L10n.ChatGPT.title).modeText(.title).accessibilityAddTraits(.isHeader)
                    Text(L10n.ChatGPT.subtitle).modeText(.callout, color: ModeColor.textSecondary)
                }
                .fixedSize(horizontal: false, vertical: true)
                .padding(.horizontal, ModeSpacing.s)
                .padding(.top, ModeSpacing.setupTop)
                .padding(.bottom, ModeSpacing.l)
                status
                if isConnected && !inSetup {
                    HStack(spacing: ModeSpacing.s + ModeSpacing.xxs) {
                        ModeButton(L10n.ChatGPT.check, icon: .runAgain, role: .secondary, fullWidth: true, action: onRecheck)
                        ModeButton(L10n.ChatGPT.signOut, role: .destructive, action: onSignOut)
                    }
                }
                ModeNote(icon: .lock, L10n.ChatGPT.usageNote, inset: ModeSpacing.s).padding(.top, ModeSpacing.xs)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollBounceBehavior(.basedOnSize)
        .toolbar(.hidden, for: .navigationBar)
        .safeAreaInset(edge: .top, spacing: ModeSpacing.none) {
            ModeSetupHeader(backLabel: L10n.Common.back, step: step.map { (current: $0.current, total: $0.total, label: $0.label) }, onBack: onBack)
                .padding(.bottom, ModeSpacing.xs)
                .background(ModeEdgeFade(edge: .top).ignoresSafeArea(edges: .top))
        }
        .safeAreaInset(edge: .bottom, spacing: ModeSpacing.none) { action }
        .modeScreen()
    }

    /// One full-width ink button: sign in; Continue (dimmed while waiting or when the plan can't be used); or,
    /// once connected, Continue in setup and Done from Settings.
    @ViewBuilder private var action: some View {
        Group {
            switch phase {
            case .waiting, .checking:
                ModeButton(L10n.Common.continue, fullWidth: true, action: onContinue).disabled(true)
            case .failed(_, notEligible: true):
                ModeButton(L10n.Common.continue, fullWidth: true, action: onContinue).disabled(true)
            default:
                if isConnected {
                    ModeButton(inSetup ? L10n.Common.continue : L10n.Common.done, fullWidth: true, action: onContinue)
                } else {
                    ModeButton(L10n.ChatGPT.continueWith, fullWidth: true, action: onSignIn)
                }
            }
        }
        .padding(.horizontal, ModeSpacing.screenEdge)
        .padding(.top, ModeSpacing.l)
        .background(ModeEdgeFade(edge: .bottom).ignoresSafeArea(edges: .bottom))
    }

    /// The state as a card with a round icon, as on the board: waiting, checking, connected, or why not.
    @ViewBuilder private var status: some View {
        switch phase {
        case .waiting: card(.progress, ModeColor.tintSoft, ModeColor.brand, L10n.ChatGPT.waiting, String(localized: L10n.ChatGPT.waitingHint))
        case .checking: card(.progress, ModeColor.tintSoft, ModeColor.brand, L10n.ChatGPT.checking, nil)
        case let .connected(models, check):
            card(.icon(.checkmark), ModeColor.tagGreen, ModeColor.success, L10n.ChatGPT.connected, check)
            card(.icon(.model), ModeColor.tintSoft, ModeColor.tint, L10n.ChatGPT.models, models.joined(separator: ", "))
        case let .failed(message, notEligible):
            card(.icon(.warning), ModeColor.tileOrange, ModeColor.tileOrangeText, notEligible ? L10n.ChatGPT.notEligible : L10n.ChatGPT.failed, message,
                 link: notEligible ? onUseOpenRouter.map { (L10n.ChatGPT.useOpenRouter, $0) } : nil)
        case .idle:
            card(connected ? .icon(.checkmark) : .icon(.sparkle), connected ? ModeColor.tagGreen : ModeColor.tintSoft,
                 connected ? ModeColor.success : ModeColor.tint,
                 connected ? L10n.ChatGPT.connected : L10n.ChatGPT.notConnected, String(localized: L10n.ChatGPT.planHint))
        }
    }

    private enum Glyph { case progress, icon(ModeIcon) }

    private func card(_ glyph: Glyph, _ background: Color, _ foreground: Color, _ title: LocalizedStringResource, _ text: String?,
                      link: (LocalizedStringResource, () -> Void)? = nil) -> some View {
        HStack(alignment: .top, spacing: ModeSpacing.m + ModeSpacing.xxs) {
            Group {
                switch glyph {
                case .progress: ProgressView().tint(foreground)
                case let .icon(icon): icon.image.font(ModeIconFont.small.weight(.bold)).foregroundStyle(foreground)
                }
            }
            .frame(width: ModeSize.tile, height: ModeSize.tile)
            .background(background, in: .circle)
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                Text(title).modeText(.calloutEmphasis)
                if let text { Text(text).modeText(.subheadline, color: ModeColor.textSecondary) }
                if let link {
                    Button(action: link.1) {
                        Text(link.0).modeText(.subheadlineEmphasis, color: ModeColor.infoText).frame(minHeight: ModeSize.touch)
                    }
                    .buttonStyle(.plain)
                    .padding(.vertical, -ModeSpacing.s)
                    .padding(.top, ModeSpacing.xs)
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .padding(ModeSpacing.l)
        .background(ModeColor.card, in: .rect(cornerRadius: ModeRadius.card))
        .modeShadow(in: RoundedRectangle(cornerRadius: ModeRadius.card), lifted: false)
        .accessibilityElement(children: .contain)
    }
}

struct ConnectChatGPTRoute: View {
    @State private var model: ConnectChatGPTViewModel
    @Environment(\.webAuthenticationSession) private var webAuthentication
    @Environment(\.dismiss) private var dismiss
    /// Set by setup around its sheets: this page's step, and the way to OpenRouter.
    @Environment(\.setupStep) private var step
    @Environment(\.setupUseOpenRouter) private var useOpenRouter

    init(app: AppModel) {
        _model = State(initialValue: ConnectChatGPTViewModel(screens: app.screens))
    }

    var body: some View {
        ConnectChatGPTScreen(phase: model.phase, connected: model.connected, step: step, onSignIn: model.signIn,
                             onRecheck: model.recheck, onSignOut: model.signOut,
                             onBack: { dismiss() }, onContinue: { dismiss() }, onUseOpenRouter: useOpenRouter)
            // The page answers on the loopback, not a URL scheme: the session is closed once that arrives.
            .task(id: model.signInURL) {
                guard let url = model.signInURL else { return }
                _ = try? await webAuthentication.authenticate(using: url, callbackURLScheme: "mode",
                                                               preferredBrowserSession: .ephemeral)
                model.pageClosed()
            }
    }
}
