#if DEBUG && targetEnvironment(simulator)
import ModeDesignSystem
import SwiftUI

/// Simulator only: `MODE_OPEN=sample:<name>` shows a screen with the board's sample state, for screens whose
/// real state needs a signed-in account (the simulator has none). Views are dumb, so the state is all they need.
struct ScreenSample: Identifiable {
    let id: String
    /// "chatgpt-bottom": the same screen, scrolled to its end.
    var bottom: Bool { id.hasSuffix("-bottom") }

    @ViewBuilder var screen: some View {
        switch id.replacingOccurrences(of: "-bottom", with: "") {
        case "plan": PlanModelsScreen(state: Self.plan, onEvent: { _ in })
        case "claude": detail(Self.claude)
        case "openrouter": detail(Self.openRouter)
        default: detail(Self.chatGPT)
        }
    }

    private func detail(_ s: ProviderDetailViewModel.State) -> some View {
        let links: [ProvidersViewModel.Kind: (LocalizedStringResource, URL)] = [
            .chatGPT: (L10n.Account.seeChatGPTUsage, ModeLinks.chatGPTUsage),
            .claude: (L10n.Account.seeClaudeUsage, ModeLinks.claudeUsage),
            .openRouter: (L10n.Account.addCredits, ModeLinks.openRouterCredits),
        ]
        let signOut: LocalizedStringResource = s.kind == .chatGPT ? L10n.Account.signOutChatGPT : s.kind == .claude ? L10n.Account.signOutClaude : L10n.Account.removeKey
        return ProviderDetailScreen(state: s, link: links[s.kind].map { (title: $0.0, url: $0.1) }, signOutTitle: signOut, onEvent: { _ in }, openLink: { _ in })
    }

    private static func effort(_ label: String, _ speed: String, _ right: String, _ selected: Bool = false) -> ModeEffortTable.Row {
        .init(label: label, speed: speed, accuracy: right, selected: selected)
    }

    static let plan = PlanModelsViewModel.State(
        chip: "ChatGPT Plus · 4 models on your plan",
        role: .phoneControl,
        rows: [
            .init(id: "gpt-6.1-terra", name: "GPT-6.1 Terra", leading: ModelTile.leading(for: "openai/gpt", name: "GPT"),
                  tag: .init("Best for phone control", background: ModeColor.tintSoft, foreground: ModeColor.tint),
                  line: "Low effort · 1.4 s a step · 9 of 10 right", selected: true, effort: "low",
                  effortOptions: [("low", "Low"), ("medium", "Medium"), ("high", "High"), ("xhigh", "Extra"), ("max", "Max")].map { .init(id: $0.0, title: $0.1) },
                  efforts: [effort("Low", "1.4 s a step", "9 of 10 right", true), effort("Medium", "2.6 s a step", "10 of 10 right"),
                            effort("High", "5.2 s a step", "10 of 10 right"), effort("Extra", "7.9 s a step", "10 of 10 right"),
                            effort("Max", "12.4 s a step", "10 of 10 right")]),
            .init(id: "gpt-6-luna", name: "GPT-6 Luna", leading: ModelTile.leading(for: "openai/gpt", name: "GPT"),
                  tag: .init("Fastest", background: ModeColor.tagGreen, foreground: ModeColor.tagGreenText),
                  line: "0.9 s a step · 7 of 10 right", selected: false, effort: nil, effortOptions: [], efforts: []),
            .init(id: "gpt-6.1-sol", name: "GPT-6.1 Sol", leading: ModelTile.leading(for: "openai/gpt", name: "GPT"),
                  tag: .init("Most accurate", background: ModeColor.tintSoft, foreground: ModeColor.tint),
                  line: "3.8 s a step · 10 of 10 right", selected: false, effort: nil, effortOptions: [], efforts: []),
            .init(id: "gpt-6.1-terra-mini", name: "GPT-6.1 Terra mini", leading: ModelTile.leading(for: "openai/gpt", name: "GPT"),
                  tag: nil, line: "Not measured yet", selected: false, effort: nil, effortOptions: [], efforts: []),
        ],
        supportsEffort: true
    )

    static let chatGPT = ProviderDetailViewModel.State(
        kind: .chatGPT, title: "ChatGPT", status: "Connected · in use", connected: true, inUse: true,
        account: [.init(title: "Signed in as", value: "you@example.com"), .init(title: "Name", value: "Your name"), .init(title: "Plan", value: "Plus")],
        weekSteps: "186 steps", days: [32, 41, 18, 47, 12, 24, 12], today: 6, todayLine: "12 steps · 3 tasks", tasksLine: "41",
        limitLine: "Available", phoneModel: "GPT-6.1 Terra · Low", brainModel: "GPT-6.1 Sol · High"
    )

    static let claude = ProviderDetailViewModel.State(
        kind: .claude, title: "Claude", status: "Connected · not in use", connected: true,
        account: [.init(title: "Signed in as", value: "you@example.com"), .init(title: "Plan", value: "Max")],
        todayLine: "14 steps · 2 tasks",
        limits: [.init(title: "Current session · 5 hours", value: "34% used", fraction: 0.34, footnote: "Resets at 15:40"),
                 .init(title: "This week", value: "12% used", fraction: 0.12, footnote: "Resets Monday 09:00")],
        usageUpdated: "11:32", phoneModel: "Haiku 5.5", brainModel: "Sonnet 5.5"
    )

    static let openRouter = ProviderDetailViewModel.State(
        kind: .openRouter, title: "OpenRouter", status: "Connected · not in use", connected: true,
        account: [.init(title: "Key", value: "sk-or-…1234"), .init(title: "Key name", value: "Mode")],
        creditsValue: "$4.21", creditsFraction: 0.421, creditsFootnote: "of $10.00 · from OpenRouter",
        spentToday: "$0.31 · 9 tasks", spentWeek: "$1.84", limitPerTask: "$0.10",
        phoneModel: "Claude Haiku 5.5", brainModel: "Claude Sonnet 5.5"
    )
}

/// The sample, pushed onto a Settings stack as the real screen is.
struct ScreenSampleHost: View {
    let sample: ScreenSample
    @State private var path = [0]

    var body: some View {
        NavigationStack(path: $path) {
            Color.clear.navigationTitle(Text(L10n.Common.settings))
                .navigationDestination(for: Int.self) { _ in
                    sample.screen.defaultScrollAnchor(sample.bottom ? .bottom : .top)
                }
        }
    }
}
#endif
