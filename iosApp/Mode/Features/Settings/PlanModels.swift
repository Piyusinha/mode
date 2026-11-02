import ModeDesignSystem
import ModeKit
import Observation
import SwiftUI

/// Models on your plan (board "Settings · models on your plan"): phone control or brain, every model the
/// plan offers with its tag and measured speed and accuracy, the chosen one's effort, and measuring.
@MainActor
@Observable
final class PlanModelsViewModel {
    enum Role: Hashable { case phoneControl, brain }

    struct Row: Identifiable, Equatable {
        let id: String
        let name: String
        let leading: ModeRowLeading
        let tag: ModeChoiceRow.Tag?
        let line: String
        let selected: Bool
        let effort: String?
        let efforts: [ModeEffortTable.Row]
    }

    struct State: Equatable {
        var chip = ""
        var role: Role = .phoneControl
        var rows: [Row] = []
        var supportsEffort = false
        var measuring: String?
        var error: String?
    }

    enum Event {
        case role(Role)
        case choose(String)
        case effort(String)
        case measure
    }

    private(set) var state = State()
    private let presenter: PlanModelsPresenter
    private let providerName: (String) -> String
    private var updates: Updates?

    init(screens: Screens, startOnBrain: Bool) {
        presenter = screens.planModels()
        providerName = { id in String(localized: id == "claude_oauth" ? L10n.Providers.claude : L10n.Providers.chatGPT) }
        if startOnBrain { presenter.role(role: .brain) }
        updates = observe(presenter.state) { [weak self] s in
            guard let self else { return }
            state = Self.map(s, provider: providerName(s.provider))
        }
    }

    func send(_ event: Event) {
        switch event {
        case let .role(r): presenter.role(role: r == .brain ? .brain : .phoneControl)
        case let .choose(id): presenter.choose(id: id)
        case let .effort(e): presenter.effort(effort: e)
        case .measure: presenter.measure(probes: ProbeImages.make())
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    static func effortName(_ e: String) -> String {
        switch e {
        case "low": String(localized: L10n.Plan.low)
        case "medium": String(localized: L10n.Plan.medium)
        default: String(localized: L10n.Plan.high)
        }
    }

    private static func seconds(_ ms: Int64) -> String {
        (Double(ms) / 1000).formatted(.number.precision(.fractionLength(1)))
    }

    private static func map(_ s: PlanModelsUiState, provider: String) -> State {
        let plan = [provider, s.plan].compactMap { $0 }.joined(separator: " ")
        // Phone control is timed per step; the brain per answer (a plan or a decision).
        let speed: (String) -> String = s.role == .brain ? L10n.Plan.perAnswer : L10n.Plan.perStep
        return State(
            chip: L10n.Plan.onPlan(plan, s.models.count),
            role: s.role == .brain ? .brain : .phoneControl,
            rows: s.models.map { m in
                let tag: ModeChoiceRow.Tag? = m.tag.map { t in
                    switch t {
                    case .bestForPhoneControl: .init(String(localized: L10n.Plan.bestForPhone), background: ModeColor.tintSoft, foreground: ModeColor.tint)
                    case .bestForBrain: .init(String(localized: L10n.Plan.bestForBrain), background: ModeColor.tintSoft, foreground: ModeColor.tint)
                    case .fastest: .init(String(localized: L10n.Plan.fastest), background: ModeColor.tagGreen, foreground: ModeColor.tagGreenText)
                    case .mostAccurate: .init(String(localized: L10n.Plan.mostAccurate), background: ModeColor.tintSoft, foreground: ModeColor.tint)
                    case .recommended: .init(String(localized: L10n.Settings.recommended), background: ModeColor.tintSoft, foreground: ModeColor.tint)
                    case .strongest: .init(String(localized: L10n.Settings.strongest), background: ModeColor.tintSoft, foreground: ModeColor.tint)
                    }
                }
                let measured: [String] = if let ms = m.millis?.int64Value, let c = m.correct?.intValue, let t = m.total?.intValue {
                    [speed(seconds(ms)), L10n.Plan.right(c, of: t)]
                } else {
                    [String(localized: L10n.Plan.notMeasured)]
                }
                let effort = m.effort.map { L10n.Plan.effortName(effortName($0)) }
                return Row(
                    id: m.id, name: m.name, leading: ModelTile.leading(for: m.id, name: m.name), tag: tag,
                    line: ([effort].compactMap { $0 } + measured).joined(separator: " · "),
                    selected: m.selected, effort: m.effort,
                    efforts: m.efforts.map { e in
                        ModeEffortTable.Row(
                            label: effortName(e.effort),
                            speed: e.millis.map { speed(seconds($0.int64Value)) } ?? "—",
                            accuracy: (e.correct?.intValue).flatMap { c in (e.total?.intValue).map { L10n.Plan.right(c, of: $0) } } ?? "—",
                            selected: e.selected
                        )
                    }
                )
            },
            supportsEffort: s.supportsEffort,
            measuring: s.measuring.map { L10n.Plan.measuring(Int(truncating: $0.first ?? 0) + 1, of: Int(truncating: $0.second ?? 0)) },
            error: s.error
        )
    }
}

struct PlanModelsScreen: View {
    let state: PlanModelsViewModel.State
    let onEvent: (PlanModelsViewModel.Event) -> Void

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: ModeSpacing.m) {
                Label { Text(state.chip) } icon: { Circle().fill(ModeColor.success).frame(width: ModeSize.stepDot, height: ModeSize.stepDot) }
                    .modeText(.footnote)
                    .fontWeight(.semibold)
                    .padding(.horizontal, ModeSpacing.m)
                    .padding(.vertical, ModeSpacing.xs)
                    .background(ModeColor.card, in: .capsule)
                ModeSegmented([(PlanModelsViewModel.Role.phoneControl, L10n.Plan.phoneControl), (.brain, L10n.Plan.brain)],
                              selection: Binding(get: { state.role }, set: { onEvent(.role($0)) }))
                Text(L10n.Plan.roleHint).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
                ModeCard {
                    ForEach(state.rows) { row in
                        VStack(alignment: .leading, spacing: ModeSpacing.s) {
                            ModeChoiceRow(row.name, tag: row.tag, detail: row.line, leading: row.leading, selected: row.selected) { onEvent(.choose(row.id)) }
                            if row.selected && state.supportsEffort {
                                Text(L10n.Plan.effort).modeText(.footnote, color: ModeColor.textSecondary).fontWeight(.semibold)
                                ModeSegmented([("low", L10n.Plan.low), ("medium", L10n.Plan.medium), ("high", L10n.Plan.high)],
                                              selection: Binding(get: { row.effort ?? "low" }, set: { onEvent(.effort($0)) }))
                                ModeEffortTable(headers: (String(localized: L10n.Plan.effort), String(localized: L10n.Plan.speed),
                                                          String(localized: L10n.Plan.accuracy)), rows: row.efforts)
                                    .padding(.bottom, ModeSpacing.m)
                            }
                        }
                    }
                }
                if let measuring = state.measuring {
                    HStack(spacing: ModeSpacing.s) {
                        ProgressView().tint(ModeColor.tint)
                        Text(measuring).modeText(.subheadline, color: ModeColor.textSecondary)
                    }
                    .frame(maxWidth: .infinity, minHeight: ModeSize.touch)
                } else {
                    ModeButton(L10n.Plan.measure, icon: .clock, role: .secondary, fullWidth: true) { onEvent(.measure) }
                }
                if let error = state.error { Text(error).modeText(.footnote, color: ModeColor.danger) }
                Text(state.role == .brain ? L10n.Plan.brainMeasureNote : L10n.Plan.measureNote).modeText(.footnote, color: ModeColor.textSecondary).padding(.horizontal, ModeSpacing.xs)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .navigationTitle(Text(L10n.Plan.title))
        .navigationBarTitleDisplayMode(.large)
    }
}

struct PlanModelsRoute: View {
    @State private var model: PlanModelsViewModel

    init(app: AppModel, brain: Bool) {
        _model = State(initialValue: PlanModelsViewModel(screens: app.screens, startOnBrain: brain))
    }

    var body: some View {
        PlanModelsScreen(state: model.state, onEvent: model.send)
    }
}
