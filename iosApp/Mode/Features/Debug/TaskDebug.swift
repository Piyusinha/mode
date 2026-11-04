import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI

/// What went wrong (board TaskDebug) and Ask about it (board TaskDebugAsk) for a task that couldn't finish.
@MainActor
@Observable
final class TaskDebugModel {
    struct Item: Identifiable, Equatable {
        let id: Int
        let title: String
        let detail: String
        let icon: ModeIcon
        let status: ModeHealthRow.Status
    }

    struct Turn: Identifiable, Equatable {
        let id: Int
        let question: String
        let answer: String?
        let failed: Bool
    }

    struct State: Equatable {
        var title = ""
        var when = ""
        var problems: [Item] = []
        var now: [Item] = []
        var repaired: [String] = []
        var canRepair = false
        var repairing = false
        var turns: [Turn] = []
        var provider = ""
        var asking: Bool { turns.last.map { $0.answer == nil } ?? false }
    }

    enum Event { case repair, retry, ask(String), openAsk, openHealth }

    private(set) var state = State()
    private let presenter: TaskDebugPresenter
    private let navigate: (Route) -> Void
    private let taskId: String
    private var updates: Updates?

    init(presenter: TaskDebugPresenter, taskId: String, navigate: @escaping (Route) -> Void) {
        self.presenter = presenter
        self.taskId = taskId
        self.navigate = navigate
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .repair: presenter.repair()
        case .retry: _ = presenter.retry()
        case let .ask(q): presenter.ask(question: q)
        case .openAsk: navigate(.taskDebugAsk(taskId: taskId))
        case .openHealth: navigate(.health)
        }
    }

    private static func map(_ s: TaskDebugUiState) -> State {
        let items = s.findings.enumerated().map { i, f in
            Item(id: i, title: f.title, detail: f.detail, icon: icon(f.kind),
                 status: f.ok ? .ok : f.problem ? .warning : .info)
        }
        let live: Set<FindingKind> = [.phoneOk, .aiOk]
        let isLive = { (i: Int) in live.contains(s.findings[i].kind) || (s.findings[i].title == "Phone control" || s.findings[i].title == s.provider) }
        return State(
            title: s.title,
            when: s.startedAtMs.map { Date(timeIntervalSince1970: TimeInterval(truncating: $0) / 1000).formatted(date: .abbreviated, time: .shortened) } ?? "",
            problems: items.filter { !isLive($0.id) }, now: items.filter { isLive($0.id) },
            repaired: s.repaired, canRepair: s.canRepair, repairing: s.repairing,
            turns: s.turns.enumerated().map { Turn(id: $0, question: $1.question, answer: $1.answer, failed: $1.failed) },
            provider: s.provider)
    }

    private static func icon(_ kind: FindingKind) -> ModeIcon {
        switch kind {
        case .noAction, .aiFailed, .aiOk: .sparkle
        case .stuck, .backAndForth: .repeatTask
        case .outOfSteps, .outOfTime: .clock
        case .unconfirmed: .warning
        case .phoneUnreachable, .phoneOk: .vpn
        case .waitedForYou: .person
        case .interrupted, .notStarted: .stopped
        default: .info
        }
    }
}

struct TaskDebugScreen: View {
    let state: TaskDebugModel.State
    let onEvent: (TaskDebugModel.Event) -> Void
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                ModeCard {
                    VStack(alignment: .leading, spacing: ModeSpacing.xs) {
                        Text(state.title).modeText(.bodyEmphasis).lineLimit(3)
                        if !state.when.isEmpty { Text(state.when).modeText(.footnote, color: ModeColor.textSecondary) }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.vertical, ModeSpacing.m)
                }
                ModeSectionHeader(L10n.Debug.found)
                ModeCard {
                    if state.problems.isEmpty {
                        ModeHealthRow(String(localized: L10n.Debug.nothingFound), detail: String(localized: L10n.Debug.nothingFoundDetail), icon: .info, status: .info)
                    }
                    ForEach(state.problems) { ModeHealthRow($0.title, detail: $0.detail, icon: $0.icon, status: $0.status) }
                }
                ModeSectionHeader(L10n.Debug.now)
                ModeCard {
                    ForEach(state.now) { ModeHealthRow($0.title, detail: $0.detail, icon: $0.icon, status: $0.status) }
                    ForEach(state.repaired, id: \.self) { ModeHealthRow($0, detail: String(localized: L10n.Health.repaired), icon: .wrench, status: .repaired) }
                    ModeRow(String(localized: L10n.Health.row), leading: .icon(.wrench), action: { onEvent(.openHealth) }) { ModeChevron() }
                }
                if state.canRepair && state.repaired.isEmpty {
                    ModeButton(state.repairing ? L10n.Health.repairing : L10n.Debug.repair, icon: .wrench, role: .secondary, fullWidth: true) { onEvent(.repair) }
                        .disabled(state.repairing)
                }
                ModeInfoCard(icon: .shield, L10n.Debug.onDevice)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(L10n.Debug.title))
        .navigationBarTitleDisplayMode(.large)
        .safeAreaInset(edge: .bottom) {
            HStack(spacing: ModeSpacing.s) {
                ModeButton(L10n.Debug.ask, icon: .message, role: .secondary, fullWidth: true) { onEvent(.openAsk) }
                ModeButton(L10n.Conversation.tryAgain, icon: .runAgain, fullWidth: true) { onEvent(.retry); dismiss() }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.vertical, ModeSpacing.s)
            .background(ModeColor.page)
        }
    }
}

struct TaskDebugAskScreen: View {
    let state: TaskDebugModel.State
    let onEvent: (TaskDebugModel.Event) -> Void
    @State private var text = ""

    private let suggestions = [L10n.Debug.whyStop, L10n.Debug.whatToChange, L10n.Debug.willWork]

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: ModeSpacing.m) {
                    Text(L10n.Debug.answeredWith(state.provider)).modeText(.captionEmphasis, color: ModeColor.textSecondary).textCase(.uppercase)
                    if state.turns.isEmpty {
                        Text(L10n.Debug.askIntro).modeText(.body, color: ModeColor.textSecondary)
                        ModeChipFlow {
                            ForEach(suggestions, id: \.key) { s in ModeSuggestionChip(String(localized: s)) { onEvent(.ask(String(localized: s))) } }
                        }
                    }
                    ForEach(state.turns) { turn in
                        VStack(alignment: .leading, spacing: ModeSpacing.s) {
                            ModeUserBubble(turn.question, icon: .keyboard)
                            if let answer = turn.answer {
                                Text(answer).modeText(.body, color: turn.failed ? ModeColor.tileOrangeText : ModeColor.textPrimary)
                                    .textSelection(.enabled)
                            } else {
                                HStack(spacing: ModeSpacing.s) { ProgressView(); Text(L10n.Debug.thinking).modeText(.footnote, color: ModeColor.textSecondary) }
                            }
                        }
                        .id(turn.id)
                    }
                    ModeInfoCard(icon: .shield, L10n.Debug.whatIsSent(state.provider))
                }
                .padding(.horizontal, ModeSpacing.screenEdge)
                .padding(.bottom, ModeSpacing.xxl)
            }
            .onChange(of: state.turns.count) { if let last = state.turns.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } } }
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(L10n.Debug.askTitle))
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            ModeAskBar(text: $text, prompt: L10n.Debug.askPrompt, send: L10n.Ask.send,
                       canSend: !state.asking && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                onEvent(.ask(text))
                text = ""
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.s)
        }
    }
}

/// One presenter per task, shared by What went wrong and Ask about it, so questions carry over.
@MainActor
enum TaskDebugModels {
    private static var cache: [String: TaskDebugModel] = [:]

    static func model(app: AppModel, taskId: String, navigate: @escaping (Route) -> Void) -> TaskDebugModel {
        if let m = cache[taskId] { return m }
        let m = TaskDebugModel(presenter: app.screens.taskDebug(taskId: taskId), taskId: taskId, navigate: navigate)
        cache = [taskId: m]
        return m
    }
}

struct TaskDebugRoute: View {
    @State private var model: TaskDebugModel

    init(app: AppModel, taskId: String, navigate: @escaping (Route) -> Void) {
        _model = State(initialValue: TaskDebugModels.model(app: app, taskId: taskId, navigate: navigate))
    }

    var body: some View { TaskDebugScreen(state: model.state, onEvent: model.send) }
}

struct TaskDebugAskRoute: View {
    @State private var model: TaskDebugModel

    init(app: AppModel, taskId: String, navigate: @escaping (Route) -> Void = { _ in }) {
        _model = State(initialValue: TaskDebugModels.model(app: app, taskId: taskId, navigate: navigate))
    }

    var body: some View { TaskDebugAskScreen(state: model.state, onEvent: model.send) }
}
