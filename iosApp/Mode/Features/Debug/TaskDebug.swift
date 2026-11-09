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
        let icon: ModeDebugRow.Glyph
        let tone: ModeDebugRow.Tone
    }

    struct Turn: Identifiable, Equatable {
        let id: Int
        let question: String
        let answer: String?
        let failed: Bool

        /// The answer's paragraphs (blank lines between), each with inline Markdown.
        var paragraphs: [String] {
            (answer ?? "").components(separatedBy: "\n\n").map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }.filter { !$0.isEmpty }
        }
    }

    struct State: Equatable {
        var title = ""
        var when = ""
        /// "Order Tres Leches · today 01:36. Checked on this iPhone."
        var subtitle = ""
        /// What broke, what happened around it, and what's working (with a check).
        var found: [Item] = []
        var repaired: [Item] = []
        var canRepair = false
        var repairing = false
        var turns: [Turn] = []
        var provider = ""
        /// Questions to tap, without the ones already asked.
        var suggestions: [String] = []
        var asking: Bool { turns.last.map { $0.answer == nil } ?? false }
        var showRepair: Bool { canRepair && repaired.isEmpty }
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
        let found = s.findings.enumerated().map { i, f in
            Item(id: i, title: f.title, detail: f.detail, icon: icon(f.kind), tone: f.ok ? .ok : f.problem ? .problem : .neutral)
        }
        let when = s.startedAtMs.map { Format.timeOfDay(epochMs: Int64(truncating: $0)) } ?? ""
        let day = s.startedAtMs.map { Date(timeIntervalSince1970: TimeInterval(truncating: $0) / 1000) }
        let whenLine = day.map { Calendar.current.isDateInToday($0) ? String(localized: L10n.Debug.today(when)) : $0.formatted(date: .abbreviated, time: .shortened) }
        let asked = Set(s.turns.map(\.question))
        return State(
            title: s.title, when: whenLine ?? "",
            subtitle: subtitleLine(title: s.title, when: whenLine),
            found: found,
            repaired: s.repaired.enumerated().map { Item(id: $0, title: $1, detail: String(localized: L10n.Health.repaired), icon: .chat(.arrowRight), tone: .repaired) },
            canRepair: s.canRepair, repairing: s.repairing,
            turns: s.turns.enumerated().map { Turn(id: $0, question: $1.question, answer: $1.answer, failed: $1.failed) },
            provider: s.provider,
            suggestions: [L10n.Debug.whyStop, L10n.Debug.whatToChange, L10n.Debug.willWork].map { String(localized: $0) }.filter { !asked.contains($0) })
    }

    private static func subtitleLine(title: String, when: String?) -> String {
        String(localized: L10n.Debug.subtitle([title, when].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")))
    }

    private static func icon(_ kind: FindingKind) -> ModeDebugRow.Glyph {
        switch kind {
        case .noAction, .aiFailed, .aiOk: .chat(.sparkle)
        case .stuck, .backAndForth: .icon(.runAgain)
        case .outOfSteps, .outOfTime: .icon(.clock)
        case .unconfirmed: .chat(.wrench)
        case .phoneUnreachable, .phoneOk: .chat(.phone)
        case .waitedForYou: .icon(.profile)
        case .interrupted, .notStarted: .icon(.stopped)
        default: .icon(.info)
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
                ModeDebugHeader(L10n.Debug.title, detail: state.subtitle, mood: .stuck)
                ModeSectionHeader(L10n.Debug.found)
                ModeDebugCard {
                    if state.found.isEmpty {
                        ModeDebugRow(String(localized: L10n.Debug.nothingFound), detail: String(localized: L10n.Debug.nothingFoundDetail), icon: .icon(.info), tone: .neutral)
                    }
                    ForEach(state.found) { ModeDebugRow($0.title, detail: $0.detail, icon: $0.icon, tone: $0.tone) }
                }
                if state.showRepair {
                    ModeChatButton(state.repairing ? L10n.Health.repairing : L10n.Debug.repair, icon: .wrench, prominent: false,
                                   height: ModeSize.secondaryButton, fill: true) { onEvent(.repair) }
                        .disabled(state.repairing)
                        .padding(.top, ModeSpacing.xs)
                }
                if !state.repaired.isEmpty {
                    ModeSectionHeader(L10n.Debug.repaired)
                    ModeDebugCard {
                        ForEach(state.repaired) { ModeDebugRow($0.title, detail: $0.detail, icon: $0.icon, tone: $0.tone) }
                    }
                }
                Button { onEvent(.openHealth) } label: {
                    HStack(spacing: ModeSpacing.xs) {
                        Text(L10n.Health.row).modeText(.footnoteEmphasis, color: ModeColor.textSecondary)
                        ModeChevron()
                    }
                    .frame(minHeight: ModeSize.touch)
                    .contentShape(.rect)
                }
                .buttonStyle(ModePressStyle())
                .padding(.horizontal, ModeSpacing.s)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectHidden(true, for: .all)
        .modeEdgeFade(.top)
        .modeScreen()
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            HStack(spacing: ModeSpacing.chatInline) {
                ModeChatButton(L10n.Debug.ask, prominent: false, height: ModeSize.secondaryButton, fill: true) { onEvent(.openAsk) }
                ModeChatButton(L10n.Conversation.tryAgain, prominent: true, height: ModeSize.secondaryButton, fill: true) { onEvent(.retry); dismiss() }
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.s)
            .modeBarFade()
        }
    }
}

struct TaskDebugAskScreen: View {
    let state: TaskDebugModel.State
    let onEvent: (TaskDebugModel.Event) -> Void
    @State private var text = ""

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                VStack(alignment: .leading, spacing: ModeSpacing.l) {
                    Text(L10n.Debug.askTitle).modeText(.title)
                        .padding(.horizontal, ModeSpacing.s)
                        .accessibilityAddTraits(.isHeader)
                    if state.turns.isEmpty {
                        Text(L10n.Debug.askIntro).modeText(.callout, color: ModeColor.textSecondary)
                            .padding(.horizontal, ModeSpacing.s)
                    }
                    ForEach(state.turns) { turn in
                        VStack(alignment: .leading, spacing: ModeSpacing.l) {
                            ModeUserBubble(turn.question, tone: .info)
                            ModeAnswerBubble(label: turn.answer == nil || turn.failed ? nil : L10n.Debug.answeredWith(state.provider),
                                             mood: turn.answer == nil ? .working : .stuck) {
                                if turn.answer == nil {
                                    Text(L10n.Debug.thinking).modeText(.callout, color: ModeColor.textSecondary).modeShimmer()
                                } else {
                                    ForEach(turn.paragraphs, id: \.self) { paragraph in
                                        ModeInlineText(paragraph, style: .callout, color: turn.failed ? ModeColor.danger : ModeColor.textPrimary)
                                    }
                                }
                            }
                            .textSelection(.enabled)
                        }
                        .id(turn.id)
                    }
                }
                .padding(.horizontal, ModeSpacing.screenEdge)
                .padding(.bottom, ModeSpacing.xxl)
            }
            .onChange(of: state.turns.count) { if let last = state.turns.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } } }
        }
        .scrollEdgeEffectHidden(true, for: .all)
        .modeEdgeFade(.top)
        .modeScreen()
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            VStack(alignment: .leading, spacing: ModeSpacing.chatInline) {
                if !state.suggestions.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: ModeSpacing.s) {
                            ForEach(state.suggestions, id: \.self) { s in ModeSuggestionChip(s) { onEvent(.ask(s)) }.disabled(state.asking) }
                        }
                        .padding(.horizontal, ModeSpacing.screenEdge)
                    }
                    .scrollClipDisabled()
                    .padding(.horizontal, -ModeSpacing.screenEdge)
                }
                ModeAskBar(text: $text, prompt: L10n.Debug.askPrompt, send: L10n.Ask.send,
                           canSend: !state.asking && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty) {
                    onEvent(.ask(text))
                    text = ""
                }
                Text(L10n.Debug.whatIsSent(state.provider)).modeText(.caption, color: ModeColor.textSecondary)
                    .padding(.horizontal, ModeSpacing.s)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.s)
            .modeBarFade()
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
    @LazyState private var model: TaskDebugModel

    init(app: AppModel, taskId: String, navigate: @escaping (Route) -> Void) {
        _model = LazyState(wrappedValue: TaskDebugModels.model(app: app, taskId: taskId, navigate: navigate))
    }

    var body: some View { TaskDebugScreen(state: model.state, onEvent: model.send) }
}

struct TaskDebugAskRoute: View {
    @LazyState private var model: TaskDebugModel

    init(app: AppModel, taskId: String, navigate: @escaping (Route) -> Void = { _ in }) {
        _model = LazyState(wrappedValue: TaskDebugModels.model(app: app, taskId: taskId, navigate: navigate))
    }

    var body: some View { TaskDebugAskScreen(state: model.state, onEvent: model.send) }
}
