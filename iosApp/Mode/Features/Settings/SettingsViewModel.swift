import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// Settings (its board): your name; the AI in use (providers in their own sheet) and its models; limits;
/// memory. Also drives the model picker, which searches the provider's models.
@MainActor
@Observable
final class SettingsViewModel {
    struct Model: Identifiable, Equatable {
        let id: String
        let name: String
        let subtitle: String
        let leading: ModeRowLeading
        let isMain: Bool
        let isStuck: Bool
    }

    struct State: Equatable {
        var modelName = ""
        var stuckName = ""
        var neverStuck = false
        var picks: [Model] = []
        var results: [Model] = []
        var makers: [String] = []
        var maker: String?
        var showsCost = true
        var maxCost = 0.05
        var maxCostLabel = ""
        /// The daily limit's stepper value; 0 means off.
        var dailyCap = 0.0
        var dailyCapLabel = ""
        var learn = true
        /// What the picker lists: the search's results, or the picks when nothing is typed or filtered.
        var listed: [Model] { query.isEmpty && maker == nil ? picks : results }
        var query = ""
    }

    enum Event {
        case saveName
        case chooseModel(String)
        case chooseStuck(String?)
        case search
        case filter(String?)
        case maxCost(Double)
        case dailyCap(Double)
        case learn(Bool)
        case openDeveloper
        case openMemory
        case openPicker(stuck: Bool)
        case openAI
    }

    private(set) var state = State()
    var name = ""
    var query = ""
    private let presenter: SettingsPresenter
    private let navigate: (Route) -> Void
    private var updates: Updates?

    /// The steps the cost controls move in, in US dollars.
    let costStep = 0.01
    let costRange = 0.01...1.0
    let dailyCapStep = 0.1
    let dailyCapRange = 0.0...10.0

    init(screens: Screens, navigate: @escaping (Route) -> Void) {
        presenter = screens.settings()
        self.navigate = navigate
        name = presenter.state.value.name
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .saveName: presenter.setName(name: name)
        case let .chooseModel(id): presenter.chooseModel(id: id)
        case let .chooseStuck(id): presenter.chooseStuckModel(id: id)
        case .search: presenter.search(text: query)
        case let .filter(maker): presenter.filterMaker(maker: maker)
        case let .maxCost(usd): presenter.setMaxCostPerTask(usd: usd)
        case let .dailyCap(usd): presenter.setDailyCap(usd: usd > 0 ? KotlinDouble(value: usd) : nil)
        case let .learn(on): presenter.setLearn(on: on)
        case .openDeveloper: navigate(.developer)
        case .openMemory: navigate(.memory)
        case let .openPicker(stuck): navigate(.modelPicker(stuck: stuck))
        case .openAI: navigate(.aiSettings)
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: SettingsUiState) -> State {
        let onPlan = !s.showsCost
        func model(_ c: ModelChoice) -> Model {
            let tag = c.tag.map { tag -> String in
                switch tag {
                case .recommended: String(localized: L10n.Settings.recommended)
                case .cheapest: String(localized: L10n.Settings.cheapest)
                case .strongest: String(localized: L10n.Settings.strongest)
                }
            }
            let cost = onPlan ? String(localized: L10n.Settings.onYourPlan) : L10n.Settings.perTask(Format.money(c.costPerTaskUsd))
            let maker = c.id.split(separator: "/").first.map(String.init) ?? c.name
            return Model(id: c.id, name: c.name, subtitle: [tag, cost, c.note.isEmpty ? nil : c.note].compactMap { $0 }.joined(separator: " · "),
                         leading: .letterTile(String((c.id.contains("/") ? maker : c.name).prefix(1)).uppercased(),
                                              background: ModeColor.tintSoft, foreground: ModeColor.tint),
                         isMain: c.isMain, isStuck: c.isStuck)
        }
        let cap = s.dailyCapUsd?.doubleValue
        return State(
            modelName: s.model?.name ?? "",
            stuckName: s.stuckModel?.name ?? String(localized: L10n.Settings.off),
            neverStuck: s.stuckModel == nil,
            picks: s.picks.map(model),
            results: s.results.map(model),
            makers: s.makers,
            maker: s.maker,
            showsCost: s.showsCost,
            maxCost: s.maxCostPerTaskUsd,
            maxCostLabel: Format.money(s.maxCostPerTaskUsd),
            dailyCap: cap ?? 0,
            dailyCapLabel: cap.map(Format.money) ?? String(localized: L10n.Settings.dailyCapOff),
            learn: s.learn,
            query: s.query
        )
    }
}
