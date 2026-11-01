import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// Settings: name, key, models picked from the live list, limits.
@MainActor
@Observable
final class SettingsViewModel {
    struct Model: Identifiable, Equatable {
        let id: String
        let name: String
        let subtitle: String
        let isMain: Bool
        let isStuck: Bool
    }

    struct State: Equatable {
        var keyStatus = ""
        var hasKey = false
        var modelName = ""
        var stuckName = ""
        var neverStuck = false
        var picks: [Model] = []
        var results: [Model] = []
        var maxCost = 0.05
        var maxCostLabel = ""
        /// The daily limit's stepper value; 0 means off.
        var dailyCap = 0.0
        var dailyCapLabel = ""
        var learn = true
    }

    enum Event {
        case saveName
        case saveKey
        case chooseModel(String)
        case chooseStuck(String?)
        case search
        case maxCost(Double)
        case dailyCap(Double)
        case learn(Bool)
        case openDeveloper
    }

    private(set) var state = State()
    var name = ""
    var key = ""
    var query = ""
    private let presenter: SettingsPresenter
    private let navigate: (Route) -> Void
    private var updates: Updates?

    /// The steps the cost controls move in, in US dollars.
    let costStep = 0.01
    let costRange = 0.01...1.0
    let dailyCapStep = 0.1
    let dailyCapRange = 0.0...10.0

    /// A new key has been typed and can be saved.
    var canSaveKey: Bool { !key.isEmpty }

    init(screens: Screens, navigate: @escaping (Route) -> Void) {
        presenter = screens.settings()
        self.navigate = navigate
        name = presenter.state.value.name
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .saveName: presenter.setName(name: name)
        case .saveKey:
            presenter.setKey(key: key)
            key = ""
        case let .chooseModel(id): presenter.chooseModel(id: id)
        case let .chooseStuck(id): presenter.chooseStuckModel(id: id)
        case .search: presenter.search(text: query)
        case let .maxCost(usd): presenter.setMaxCostPerTask(usd: usd)
        case let .dailyCap(usd): presenter.setDailyCap(usd: usd > 0 ? KotlinDouble(value: usd) : nil)
        case let .learn(on): presenter.setLearn(on: on)
        case .openDeveloper: navigate(.developer)
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: SettingsUiState) -> State {
        func model(_ c: ModelChoice) -> Model {
            let tag = c.tag.map { tag -> String in
                switch tag {
                case .recommended: String(localized: L10n.Settings.recommended)
                case .cheapest: String(localized: L10n.Settings.cheapest)
                case .strongest: String(localized: L10n.Settings.strongest)
                }
            }
            return Model(id: c.id, name: c.name, subtitle: [tag, L10n.Settings.perTask(Format.money(c.costPerTaskUsd)), c.note.isEmpty ? nil : c.note].compactMap { $0 }.joined(separator: " · "),
                         isMain: c.isMain, isStuck: c.isStuck)
        }
        let cap = s.dailyCapUsd?.doubleValue
        return State(
            keyStatus: s.keyEnding.map { L10n.Settings.keyEnding($0) } ?? String(localized: L10n.Settings.noKey),
            hasKey: s.hasKey,
            modelName: s.model?.name ?? "",
            stuckName: s.stuckModel?.name ?? String(localized: L10n.Settings.never),
            neverStuck: s.stuckModel == nil,
            picks: s.picks.map(model),
            results: s.results.map(model),
            maxCost: s.maxCostPerTaskUsd,
            maxCostLabel: Format.money(s.maxCostPerTaskUsd),
            dailyCap: cap ?? 0,
            dailyCapLabel: cap.map(Format.money) ?? String(localized: L10n.Settings.dailyCapOff),
            learn: s.learn
        )
    }
}
