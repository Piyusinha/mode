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
        let tag: ModeChoiceRow.Tag?
        let subtitle: String
        let leading: ModeRowLeading
        let cost: Double
        let isMain: Bool
        let isStuck: Bool
    }

    /// The picker's chips.
    enum Filter: Hashable { case all, recommended, cheapest, maker(String) }

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
        /// Every task runs without asking (Settings → Access).
        var fullAccess = false
        var query = ""
        var modelCount = 0
    }

    enum Event {
        case saveName
        case chooseModel(String)
        case chooseStuck(String?)
        case search
        case filter(Filter)
        case maxCost(Double)
        case dailyCap(Double)
        case learn(Bool)
        case openDeveloper
        case openMemory
        case openPicker(stuck: Bool)
        case showProviders
        case openProvider
        case openAccess
    }

    private(set) var state = State()
    var name = ""
    var query = ""
    var showingProviders = false
    /// The provider in use (ModelProvider id), for its page.
    private var activeProvider: String { presenter.state.value.provider }
    private(set) var filter: Filter = .all
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
        case let .filter(f):
            filter = f
            switch f {
            case .all, .recommended: presenter.filterMaker(maker: nil)
            case .cheapest: presenter.filterMaker(maker: "")
            case let .maker(m): presenter.filterMaker(maker: m)
            }
        case let .maxCost(usd): presenter.setMaxCostPerTask(usd: usd)
        case let .dailyCap(usd): presenter.setDailyCap(usd: usd > 0 ? KotlinDouble(value: usd) : nil)
        case let .learn(on): presenter.setLearn(on: on)
        case .openDeveloper: navigate(.developer)
        case .openMemory: navigate(.memory)
        case let .openPicker(stuck): navigate(state.showsCost ? .modelPicker(stuck: stuck) : .planModels(brain: stuck))
        case .openProvider: navigate(.providerDetail(id: activeProvider))
        case .openAccess: navigate(.access)
        case .showProviders: showingProviders = true
        }
    }

    /// A maker as people write it ("openai" → "OpenAI").
    static func makerName(_ id: String) -> String {
        switch id.lowercased() {
        case "openai": "OpenAI"
        case "x-ai": "xAI"
        case "meta-llama": "Meta"
        case "mistralai": "Mistral"
        case "deepseek": "DeepSeek"
        case "z-ai": "Z.ai"
        default: id.prefix(1).uppercased() + id.dropFirst()
        }
    }

    /// What the picker lists for the chip and the search.
    var listed: [Model] {
        switch filter {
        case .recommended: state.picks
        case .cheapest: state.results.sorted { $0.cost < $1.cost }
        case .maker: state.results
        case .all: state.query.isEmpty ? state.picks : state.results
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: SettingsUiState) -> State {
        let onPlan = !s.showsCost
        func model(_ c: ModelChoice) -> Model {
            let tag = c.tag.map { tag -> ModeChoiceRow.Tag in
                switch tag {
                case .recommended: .init(String(localized: L10n.Settings.recommended), background: ModeColor.tintSoft, foreground: ModeColor.tint)
                case .cheapest: .init(String(localized: L10n.Settings.cheapest), background: ModeColor.tagGreen, foreground: ModeColor.tagGreenText)
                case .strongest: .init(String(localized: L10n.Settings.strongest), background: ModeColor.tintSoft, foreground: ModeColor.tint)
                }
            }
            let cost = onPlan ? String(localized: L10n.Settings.onYourPlan) : L10n.Settings.perTask(Format.money(c.costPerTaskUsd))
            let leading = ModelTile.leading(for: c.id, name: c.name)
            return Model(id: c.id, name: c.name, tag: tag, subtitle: [c.note.isEmpty ? nil : c.note, cost].compactMap { $0 }.joined(separator: " · "),
                         leading: leading, cost: c.costPerTaskUsd, isMain: c.isMain, isStuck: c.isStuck)
        }
        let cap = s.dailyCapUsd?.doubleValue
        return State(
            modelName: [s.model?.name, s.modelEffort.map { PlanModelsViewModel.effortName($0) }].compactMap { $0 }.joined(separator: " · "),
            stuckName: s.stuckModel.map { m in [m.name, s.stuckEffort.map { PlanModelsViewModel.effortName($0) }].compactMap { $0 }.joined(separator: " · ") }
                ?? String(localized: L10n.Settings.off),
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
            fullAccess: s.fullAccess,
            query: s.query,
            modelCount: Int(s.modelCount)
        )
    }
}
