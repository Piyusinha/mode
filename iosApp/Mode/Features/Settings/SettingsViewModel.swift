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
        // Voice
        var speaker: String?
        var voiceName = ""
        var listener: String?
        var listenerLabel = ""
        /// Why the chosen voice couldn't speak, or the chosen listener couldn't hear (shown on its row, orange).
        var speakProblem: String?
        var listenProblem: String?
        var talkBack = true
        /// Settings → Voice rows: each one's logo, the voice as "ElevenLabs · Rachel" (Apple's by name alone),
        /// the speaker's tag ("Most human"), and the note under them (it changes when one failed).
        var speakerLogo: ModeLogo = .apple
        var listenerLogo: ModeLogo = .apple
        var voiceValue = ""
        var speakerTag: String?
        var voiceNote = L10n.Voice.sectionNote
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
        case openHealth
        case openMemory
        case openPicker(stuck: Bool)
        case showProviders
        case openProvider
        case openAccess
        case openVoice
        case openListening
        case talkBack(Bool)
        case askReset
        case confirmReset
    }

    private(set) var state = State()
    /// The name field. It follows the saved name until the person types, and only what they typed is
    /// saved: saving the field as it was when this screen opened wiped the name from setup.
    var name = "" { didSet { if !syncingName { nameEdited = true } } }
    private var nameEdited = false
    private var syncingName = false
    var query = ""
    var showingProviders = false
    var confirmingReset = false
    private(set) var resetting = false
    private let reset: (() async -> Void)?
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

    init(screens: Screens, navigate: @escaping (Route) -> Void, reset: (() async -> Void)? = nil) {
        presenter = screens.settings()
        self.navigate = navigate
        self.reset = reset
        syncName(presenter.state.value.name)
        updates = observe(presenter.state) { [weak self] s in
            self?.state = Self.map(s)
            self?.syncName(s.name)
        }
    }

    /// Shows the saved name unless the person is mid-edit.
    private func syncName(_ saved: String) {
        guard !nameEdited, name != saved else { return }
        syncingName = true
        name = saved
        syncingName = false
    }

    func send(_ event: Event) {
        switch event {
        case .saveName:
            guard nameEdited else { return }
            nameEdited = false
            if name != presenter.state.value.name { presenter.setName(name: name) }
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
        case .openHealth: navigate(.health)
        case .openMemory: navigate(.memory)
        case let .openPicker(stuck): navigate(state.showsCost ? .modelPicker(stuck: stuck) : .planModels(brain: stuck))
        case .openProvider: navigate(.providerDetail(id: activeProvider))
        case .openAccess: navigate(.access)
        case .showProviders: showingProviders = true
        case .openVoice: navigate(.voice)
        case .openListening: navigate(.listening)
        case let .talkBack(on): presenter.setTalkBack(on: on)
        case .askReset: confirmingReset = true
        case .confirmReset:
            guard !resetting, let reset else { return }
            resetting = true
            _Concurrency.Task { await reset() }
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

    /// Mode's voice as the row shows it: "ElevenLabs · Rachel"; Apple's voice by its name alone ("Ava"); or Off.
    private static func voiceValue(_ s: SettingsUiState) -> String {
        guard let speaker = s.speaker else { return String(localized: L10n.Voice.off) }
        let name = s.voiceName ?? String(localized: L10n.Voice.defaultVoice)
        guard let service = VoiceService(id: speaker), service != .apple else { return name }
        return "\(service.title) · \(name)"
    }

    private static func map(_ s: SettingsUiState) -> State {
        let onPlan = !s.showsCost
        func model(_ c: ModelChoice) -> Model {
            let tag = c.tag.map { tag -> ModeChoiceRow.Tag in
                switch tag {
                case .recommended: .init(String(localized: L10n.Settings.recommended), background: ModeColor.cardRaised, foreground: ModeColor.textPrimary)
                case .cheapest: .init(String(localized: L10n.Settings.cheapest), background: ModeColor.tagGreen, foreground: ModeColor.tagGreenText)
                case .strongest: .init(String(localized: L10n.Settings.strongest), background: ModeColor.cardRaised, foreground: ModeColor.textPrimary)
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
            speaker: s.speaker,
            voiceName: s.voiceName ?? (s.speaker == nil ? String(localized: L10n.Voice.off) : String(localized: L10n.Voice.defaultVoice)),
            listener: s.listener,
            listenerLabel: s.listener == nil ? String(localized: L10n.Voice.off)
                : s.listener == "apple" ? String(localized: L10n.Voice.onIPhone) : (VoiceService(id: s.listener)?.title ?? ""),
            speakProblem: s.speakProblem.map { VoiceTroubleText.short($0, hearing: false) },
            listenProblem: s.listenProblem.map { VoiceTroubleText.short($0, hearing: true) },
            talkBack: s.talkBack,
            speakerLogo: (VoiceService(id: s.speaker) ?? .apple).logo,
            listenerLogo: (VoiceService(id: s.listener) ?? .apple).logo,
            voiceValue: voiceValue(s),
            speakerTag: VoiceService(id: s.speaker)?.recommendation,
            voiceNote: s.speakProblem != nil || s.listenProblem != nil ? L10n.Voice.sectionNoteFailed : L10n.Voice.sectionNote,
            query: s.query,
            modelCount: Int(s.modelCount)
        )
    }
}
