import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI

/// One provider's page (boards "ChatGPT / Claude / OpenRouter account"): who is signed in, the plan's or
/// credit's usage, what Mode used, its models, use it or sign out.
@MainActor
@Observable
final class ProviderDetailViewModel {
    struct State: Equatable {
        var kind: ProvidersViewModel.Kind = .chatGPT
        var title = ""
        var status = ""
        var connected = false
        var inUse = false
        var account: [Pair] = []
        // ChatGPT: what Mode used, by day.
        var weekSteps = ""
        var days: [Int] = []
        var today = 0
        var todayLine = ""
        var tasksLine = ""
        var limitLine = ""
        var limitReached = false
        // Claude: the plan's limits.
        var limits: [Limit] = []
        var claudeLoaded = false
        // OpenRouter: credits.
        var creditsValue: String?
        var creditsFraction = 0.0
        var creditsFootnote: String?
        var spentToday = ""
        var spentWeek = ""
        var limitPerTask: String?
        // Models.
        var phoneModel = ""
        var brainModel = ""
    }

    struct Pair: Equatable, Identifiable { var id: String { title }; let title: String; let value: String }
    struct Limit: Equatable, Identifiable { var id: String { title }; let title: String; let value: String; let fraction: Double; let footnote: String? }

    enum Event { case use, disconnect, refresh, openModels(brain: Bool) }

    private(set) var state = State()
    private let presenter: ProviderDetailPresenter
    private let navigate: (Route) -> Void
    private var updates: Updates?

    init(screens: Screens, id: String, navigate: @escaping (Route) -> Void) {
        presenter = screens.providerDetail(id: id)
        self.navigate = navigate
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .use: presenter.use()
        case .disconnect: presenter.disconnect()
        case .refresh: presenter.refresh()
        case let .openModels(brain):
            navigate(state.kind == .openRouter ? .modelPicker(stuck: brain) : .planModels(brain: brain))
        }
    }

    /// Where the provider shows its own usage or credits.
    var link: (title: LocalizedStringResource, url: URL)? {
        switch state.kind {
        case .chatGPT: (L10n.Account.seeChatGPTUsage, ModeLinks.chatGPTUsage)
        case .claude: (L10n.Account.seeClaudeUsage, ModeLinks.claudeUsage)
        case .openRouter: (L10n.Account.addCredits, ModeLinks.openRouterCredits)
        }
    }

    var signOutTitle: LocalizedStringResource {
        switch state.kind {
        case .chatGPT: L10n.Account.signOutChatGPT
        case .claude: L10n.Account.signOutClaude
        case .openRouter: L10n.Account.removeKey
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: ProviderDetailUiState) -> State {
        let kind: ProvidersViewModel.Kind = s.id == "chatgpt" ? .chatGPT : s.id == "claude_oauth" ? .claude : .openRouter
        let title: String = switch kind {
        case .chatGPT: String(localized: L10n.Providers.chatGPT)
        case .claude: String(localized: L10n.Providers.claude)
        case .openRouter: String(localized: L10n.Providers.openRouter)
        }
        let notShared = String(localized: L10n.Account.notShared)
        let account: [Pair] = switch kind {
        case .openRouter: [
            Pair(title: String(localized: L10n.Account.key), value: s.keyEnding.map { "sk-or-…\($0)" } ?? notShared),
            Pair(title: String(localized: L10n.Account.keyName), value: s.keyLabel ?? notShared),
        ]
        default: [
            Pair(title: String(localized: L10n.Account.signedInAs), value: s.email ?? notShared),
            s.name.map { Pair(title: String(localized: L10n.Account.name), value: $0) },
            Pair(title: String(localized: L10n.Account.plan), value: s.plan ?? notShared),
        ].compactMap { $0 }
        }
        func model(_ name: String?, _ effort: String?) -> String {
            [name, effort.map { PlanModelsViewModel.effortName($0) }].compactMap { $0 }.joined(separator: " · ")
        }
        let reached = s.limitReachedAt.map { $0.int64Value }
        return State(
            kind: kind, title: title,
            status: String(localized: !s.connected ? L10n.Providers.notConnected : s.inUse ? L10n.Account.inUse : L10n.Account.notInUse),
            connected: s.connected, inUse: s.inUse, account: account,
            weekSteps: L10n.Account.steps(Int(s.weekSteps)),
            days: s.daySteps.map { Int(truncating: $0) }, today: Int(s.todayIndex),
            todayLine: L10n.Account.stepsAndTasks(Int(s.todaySteps), Int(s.todayTasks)),
            tasksLine: String(Int(s.weekTasks)),
            limitLine: reached.map { L10n.Account.reachedAt(Date(timeIntervalSince1970: Double($0) / 1000).formatted(date: .omitted, time: .shortened)) }
                ?? String(localized: L10n.Account.available),
            limitReached: reached != nil,
            limits: s.limits.map { l in
                Limit(title: String(localized: l.session ? L10n.Account.session : L10n.Account.week),
                      value: L10n.Account.used(Int((l.usedFraction * 100).rounded())), fraction: l.usedFraction,
                      footnote: l.resetsAt.map { L10n.Account.resets(Date(timeIntervalSince1970: Double($0.int64Value) / 1000)
                          .formatted(.dateTime.weekday(.wide).hour().minute())) })
            },
            claudeLoaded: s.loaded,
            creditsValue: s.creditsLeft.map { Format.money($0.doubleValue) },
            creditsFraction: { () -> Double in
                guard let left = s.creditsLeft?.doubleValue, let total = s.creditsTotal?.doubleValue, total > 0 else { return 0 }
                return left / total
            }(),
            creditsFootnote: s.creditsTotal.map { L10n.Account.ofTotal(Format.money($0.doubleValue)) },
            spentToday: [Format.money(s.todayCostUsd), String(Int(s.todayTasks))].joined(separator: " · "),
            spentWeek: Format.money(s.weekCostUsd),
            limitPerTask: s.maxCostPerTaskUsd.map { Format.money($0.doubleValue) },
            phoneModel: model(s.phoneModel, s.phoneEffort), brainModel: model(s.brainModel, s.brainEffort)
        )
    }
}

struct ProviderDetailScreen: View {
    let state: ProviderDetailViewModel.State
    let link: (title: LocalizedStringResource, url: URL)?
    let signOutTitle: LocalizedStringResource
    let onEvent: (ProviderDetailViewModel.Event) -> Void
    let openLink: (URL) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                ModeProviderHeader(logo: state.kind.logo, tileBackground: state.kind.tileBackground, tileForeground: state.kind.tileForeground,
                                   title: state.title, status: state.status, good: state.connected)
                    .padding(.bottom, ModeSpacing.s)

                ModeSectionHeader(L10n.Account.account)
                ModeCard { ForEach(state.account) { ModeValueRow($0.title, value: $0.value) } }

                ModeSectionHeader(state.kind == .openRouter ? L10n.Account.credits : L10n.Account.usage)
                usage
                if let link {
                    ModeButton(link.title, icon: .external, role: .secondary, fullWidth: true) { openLink(link.url) }
                        .padding(.top, ModeSpacing.s)
                }
                if state.connected && !state.inUse {
                    ModeButton(LocalizedStringResource(stringLiteral: L10n.Account.use(state.title)), fullWidth: true) { onEvent(.use) }
                }

                ModeSectionHeader(L10n.Account.models)
                ModeCard {
                    modelRow(L10n.Plan.phoneControl, state.phoneModel) { onEvent(.openModels(brain: false)) }
                    modelRow(L10n.Plan.brain, state.brainModel) { onEvent(.openModels(brain: true)) }
                }
                if state.connected {
                    ModeButton(signOutTitle, role: .destructive) { onEvent(.disconnect) }
                        .frame(maxWidth: .infinity)
                        .padding(.top, ModeSpacing.m)
                }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .refreshable { onEvent(.refresh) }
        .modeScreen()
        .navigationBarTitleDisplayMode(.inline)
    }

    @ViewBuilder private var usage: some View {
        switch state.kind {
        case .chatGPT:
            ModeCard {
                VStack(alignment: .leading, spacing: ModeSpacing.m) {
                    HStack(alignment: .firstTextBaseline) {
                        Text(L10n.Account.thisWeek).modeText(.bodyEmphasis)
                        Spacer(minLength: ModeSpacing.m)
                        Text(state.weekSteps).modeText(.footnote, color: ModeColor.textSecondary)
                    }
                    ModeWeekBars(values: state.days, labels: Calendar.current.veryShortStandaloneWeekdaySymbols.mondayFirst, today: state.today)
                }
                .padding(.vertical, ModeSpacing.m)
                ModeValueRow(String(localized: L10n.Account.today), value: state.todayLine)
                ModeValueRow(String(localized: L10n.Account.tasksThisWeek), value: state.tasksLine)
                ModeValueRow(String(localized: L10n.Account.planLimit), value: state.limitLine,
                             dot: state.limitReached ? ModeColor.warning : ModeColor.success)
            }
            Text(L10n.Account.chatGPTNote).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
        case .claude:
            ModeCard {
                if state.limits.isEmpty {
                    if state.claudeLoaded { Text(L10n.Account.claudeNoUsage).modeText(.footnote, color: ModeColor.textSecondary).padding(.vertical, ModeSpacing.m) }
                    else { ProgressView().tint(ModeColor.tint).frame(maxWidth: .infinity, minHeight: ModeSize.rowMin) }
                }
                ForEach(state.limits) { l in
                    ModeUsageBar(l.title, value: l.value, fraction: l.fraction, tint: l.fraction < 0.7 ? ModeColor.success : ModeColor.warning, footnote: l.footnote)
                }
                ModeValueRow(String(localized: L10n.Account.modeToday), value: state.todayLine)
            }
            Text(L10n.Account.claudeNote).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
        case .openRouter:
            ModeCard {
                if let credits = state.creditsValue {
                    ModeUsageBar(String(localized: L10n.Account.creditsLeft), value: credits, fraction: state.creditsFraction,
                                 tint: ModeColor.accent, footnote: state.creditsFootnote)
                }
                ModeValueRow(String(localized: L10n.Account.spentToday), value: state.spentToday)
                ModeValueRow(String(localized: L10n.Account.week), value: state.spentWeek)
                if let perTask = state.limitPerTask { ModeValueRow(String(localized: L10n.Account.limitPerTask), value: perTask) }
            }
        }
    }

    private func modelRow(_ title: LocalizedStringResource, _ value: String, action: @escaping () -> Void) -> some View {
        ModeValueRow(String(localized: title), value: value, action: action).frame(minHeight: ModeSize.rowMin)
    }
}

private extension Array where Element == String {
    /// Calendar's weekday symbols start on Sunday; the week here starts on Monday.
    var mondayFirst: [String] { count == 7 ? Array(self[1...]) + [self[0]] : self }
}

struct ProviderDetailRoute: View {
    @State private var model: ProviderDetailViewModel
    @Environment(\.openURL) private var openURL

    init(app: AppModel, id: String, navigate: @escaping (Route) -> Void) {
        _model = State(initialValue: ProviderDetailViewModel(screens: app.screens, id: id, navigate: navigate))
    }

    var body: some View {
        ProviderDetailScreen(state: model.state, link: model.link, signOutTitle: model.signOutTitle, onEvent: model.send) { openURL($0) }
    }
}
