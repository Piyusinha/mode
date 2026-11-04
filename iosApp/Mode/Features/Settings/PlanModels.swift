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
        /// An allowance mentioned under the models, not a row to pick or compare.
        var reserve = false
        let effort: String?
        /// The efforts this model takes, as its provider listed them (empty: it takes none).
        let effortOptions: [EffortOption]
        let efforts: [ModeEffortTable.Row]
    }

    struct EffortOption: Equatable, Hashable { let id: String; let title: String }

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

    /// Any effort a provider lists; one Mode has no name for shows as the provider wrote it.
    static func effortName(_ e: String) -> String {
        switch e {
        case "none": String(localized: L10n.Plan.effortNone)
        case "minimal": String(localized: L10n.Plan.minimal)
        case "low": String(localized: L10n.Plan.low)
        case "medium": String(localized: L10n.Plan.medium)
        case "high": String(localized: L10n.Plan.high)
        case "xhigh": String(localized: L10n.Plan.extraHigh)
        case "max": String(localized: L10n.Plan.max)
        default: e.capitalized
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
            chip: L10n.Plan.onPlan(plan, s.models.filter { !$0.reserve }.count),
            role: s.role == .brain ? .brain : .phoneControl,
            rows: s.models.map { m in
                let tag: ModeChoiceRow.Tag? = m.tag.map { t in
                    switch t {
                    case .bestForPhoneControl: .init(String(localized: L10n.Plan.bestForPhone), background: ModeColor.tagGreen, foreground: ModeColor.tagGreenText)
                    case .bestForBrain: .init(String(localized: L10n.Plan.bestForBrain), background: ModeColor.tagGreen, foreground: ModeColor.tagGreenText)
                    case .fastest: .init(String(localized: L10n.Plan.fastest), background: ModeColor.tileOrange, foreground: ModeColor.tileOrangeText)
                    case .mostAccurate: .init(String(localized: L10n.Plan.mostAccurate), background: ModeColor.infoSoft, foreground: ModeColor.infoText)
                    case .recommended: .init(String(localized: L10n.Settings.recommended), background: ModeColor.cardRaised, foreground: ModeColor.textPrimary)
                    case .strongest: .init(String(localized: L10n.Settings.strongest), background: ModeColor.cardRaised, foreground: ModeColor.textPrimary)
                    }
                }
                // A model no call reached (not on the plan, refused) says why, in the provider's words.
                let measured: [String] = if m.reserve {
                    [String(localized: L10n.Plan.reserveNote)]
                } else if let error = m.error {
                    [L10n.Plan.couldNotUse(error)]
                } else if let ms = m.millis?.int64Value, let c = m.correct?.intValue, let t = m.total?.intValue {
                    [speed(seconds(ms)), L10n.Plan.right(c, of: t)]
                } else {
                    [String(localized: L10n.Plan.notMeasured)]
                }
                let effort = m.effort.map { L10n.Plan.effortName(effortName($0)) }
                return Row(
                    id: m.id, name: m.name, leading: ModelTile.leading(for: m.id, name: m.name), tag: m.reserve ? nil : tag,
                    // A model billed apart from the plan says so first: choosing it spends API credits.
                    // Luna Reserve is one line under the list: what it is, with no speed or accuracy.
                    line: m.reserve ? measured[0] : ([m.usesApiCredits ? String(localized: L10n.Plan.usesApiCredits) : nil, effort].compactMap { $0 } + measured).joined(separator: " · "),
                    selected: m.selected && !m.reserve, reserve: m.reserve, effort: m.reserve ? nil : m.effort,
                    effortOptions: m.reserve ? [] : m.efforts.map { EffortOption(id: $0.effort, title: effortName($0.effort)) },
                    efforts: m.reserve ? [] : m.efforts.map { e in
                        ModeEffortTable.Row(
                            label: effortName(e.effort),
                            speed: e.millis.map { speed(seconds($0.int64Value)) } ?? "—",
                            accuracy: e.error != nil ? String(localized: L10n.Plan.failed)
                                : (e.correct?.intValue).flatMap { c in (e.total?.intValue).map { L10n.Plan.right(c, of: $0) } } ?? "—",
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
            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                VStack(alignment: .leading, spacing: ModeSpacing.s) {
                    ModePageTitle(L10n.Plan.title)
                    ModeInfoChip(state.chip).padding(.leading, ModeSpacing.xs)
                }
                .padding(.bottom, ModeSpacing.s)
                ModePillPicker([(PlanModelsViewModel.Role.phoneControl, String(localized: L10n.Plan.phoneControl)),
                                (.brain, String(localized: L10n.Plan.brain))],
                               selection: Binding(get: { state.role }, set: { onEvent(.role($0)) }))
                ModeFootnote(L10n.Plan.roleHint).padding(.bottom, ModeSpacing.xxs)
                ModeCard(lifted: true) {
                    ForEach(state.rows) { row in
                        if row.reserve {
                            Text(row.line)
                                .modeText(.footnote, color: ModeColor.textSecondary)
                                .lineLimit(1)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding(.vertical, ModeSpacing.m)
                        } else {
                            VStack(alignment: .leading, spacing: ModeSpacing.s) {
                                ModeChoiceRow(row.name, tag: row.tag, detail: row.line, leading: row.leading, selected: row.selected) { onEvent(.choose(row.id)) }
                                if row.selected && !row.effortOptions.isEmpty {
                                    Text(L10n.Plan.effort).modeText(.footnoteEmphasis, color: ModeColor.textSecondary)
                                    ModePillPicker(row.effortOptions.map { ($0.id, $0.title) },
                                                   selection: Binding(get: { row.effort ?? row.effortOptions[0].id }, set: { onEvent(.effort($0)) }),
                                                   style: .soft)
                                    ModeEffortTable(headers: (String(localized: L10n.Plan.effort), String(localized: L10n.Plan.speed),
                                                              String(localized: L10n.Plan.accuracy)), rows: row.efforts)
                                        .padding(.bottom, ModeSpacing.l)
                                }
                            }
                        }
                    }
                }
                if let measuring = state.measuring {
                    HStack(spacing: ModeSpacing.s) {
                        ProgressView().tint(ModeColor.tint)
                        Text(measuring).modeText(.subheadline, color: ModeColor.textSecondary)
                    }
                    .frame(maxWidth: .infinity, minHeight: ModeSize.wideButton)
                } else {
                    ModeWideButton(L10n.Plan.measure, mood: .idle) { onEvent(.measure) }
                        .padding(.top, ModeSpacing.xxs)
                }
                if let error = state.error { Text(error).modeText(.footnote, color: ModeColor.danger).padding(.horizontal, ModeSpacing.noteInset) }
                ModeFootnote(state.role == .brain ? L10n.Plan.brainMeasureNote : L10n.Plan.measureNote, style: .caption)
            }
            .padding(.horizontal, ModeSpacing.screenEdge)
            .padding(.bottom, ModeSpacing.xxl)
        }
        .scrollEdgeEffectStyle(.soft, for: .all)
        .modeScreen()
        .modePageChrome(L10n.Plan.title)
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
