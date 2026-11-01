import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// Memory, one screen per kind (its boards): the hub, About You, Things Mode Can Do, App Tips, one app,
/// and the editor. Each screen gets its own instance; all of them map the same presenter state.
@MainActor
@Observable
final class MemoryViewModel {
    enum Kind: Equatable { case fact, routine, tip }

    /// One remembered item, as its card shows it, with what the editor needs.
    struct Card: Identifiable, Equatable {
        let id: String
        let kind: Kind
        let title: String
        let body: String?
        let steps: [String]
        let footer: String
        /// For the editor: the full text as kept, and where it was learned.
        let text: String
        let learnedFrom: String
        let worked: String?
    }

    struct Section: Identifiable, Equatable {
        let id: String
        let header: String?
        let cards: [Card]
    }

    struct AppRow: Identifiable, Equatable {
        var id: String { name }
        let name: String
        let initial: String
        let subtitle: String
    }

    struct AppDetail: Equatable {
        let name: String
        let subtitle: String
        let tips: [Card]
        /// The app's routines by title, for the link to them; nil when it has none.
        let routines: String?
    }

    struct State: Equatable {
        var learn = true
        var isEmpty = true
        var aboutYouSummary = ""
        var routinesSummary = ""
        var appsSummary = ""
        var aboutYou: [Card] = []
        var routines: [Section] = []
        var apps: [AppRow] = []
        var appDetails: [String: AppDetail] = [:]

        func card(_ id: String) -> Card? {
            (aboutYou + routines.flatMap(\.cards) + appDetails.values.flatMap(\.tips)).first { $0.id == id }
        }
    }

    enum Event {
        case learn(Bool)
        case askForget
        case confirmForget
        case toggleEditing
        case tap(Card)
        case deleteSelected
        case save
        case delete
    }

    private(set) var state = State()
    private(set) var editing = false
    private(set) var selected: Set<String> = []
    var confirmingForget = false
    /// The editor's fields, while one item is open.
    var draftTitle = ""
    var draftText = ""
    private(set) var draftId: String?
    private let presenter: MemoryPresenter
    private let openEditor: (String) -> Void
    private var updates: Updates?

    init(screens: Screens, openEditor: @escaping (String) -> Void = { _ in }) {
        presenter = screens.memory()
        self.openEditor = openEditor
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case let .learn(on): presenter.setLearn(on: on)
        case .askForget: confirmingForget = true
        case .confirmForget: presenter.forgetEverything()
        case .toggleEditing:
            editing.toggle()
            selected = []
        case let .tap(card):
            if editing {
                if selected.contains(card.id) { selected.remove(card.id) } else { selected.insert(card.id) }
            } else {
                openEditor(card.id)
            }
        case .deleteSelected:
            presenter.deleteMany(ids: Array(selected))
            selected = []
            editing = false
        case .save:
            guard let id = draftId, let card = state.card(id) else { return }
            if draftText != card.text || draftTitle != card.title { presenter.edit(id: id, text: draftText, title: draftTitle) }
        case .delete:
            if let id = draftId { presenter.delete(id: id) }
        }
    }

    /// Opens one item in the editor (its fields start from what Mode keeps).
    func beginEditing(_ id: String) {
        guard draftId != id, let card = state.card(id) else { return }
        draftId = id
        draftTitle = card.title
        draftText = card.text
    }

    func isSelected(_ card: Card) -> Bool? { editing ? selected.contains(card.id) : nil }

    var deleteTitle: String { L10n.Memory.deleteSelected(selected.count) }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: MemoryUiState) -> State {
        func card(_ e: MemoryEntry, _ kind: Kind) -> Card {
            let worked = e.uses > 0 ? L10n.Memory.worked(Int(e.successes), of: Int(e.uses)) : nil
            let footer = [L10n.Memory.learnedOn(Format.day(e.learned).lowercased()), worked].compactMap { $0 }.joined(separator: " · ")
            return Card(id: e.id, kind: kind, title: e.title, body: e.body.isEmpty ? nil : e.body, steps: e.steps, footer: footer,
                        text: e.text, learnedFrom: L10n.Memory.learnedFrom(e.evidence), worked: worked)
        }
        let tipCount = Int(s.tipCount)
        var details: [String: AppDetail] = [:]
        for group in s.apps + s.routines where details[group.name] == nil {
            let tips = L10n.Memory.tipsCount(group.tips.count)
            details[group.name] = AppDetail(
                name: group.name, subtitle: L10n.Memory.appTipsHint(tips), tips: group.tips.map { card($0, .tip) },
                routines: group.routines.isEmpty ? nil : group.routines.map(\.title).joined(separator: ", ")
            )
        }
        return State(
            learn: s.learn,
            isEmpty: s.isEmpty,
            aboutYouSummary: L10n.Memory.things(s.aboutYou.count),
            routinesSummary: L10n.Memory.routinesLearned(Int(s.routineCount)),
            appsSummary: [L10n.Memory.appsCount(s.apps.count), L10n.Memory.tipsCount(tipCount)].joined(separator: " · "),
            aboutYou: s.aboutYou.map { card($0, .fact) },
            routines: s.routines.map { g in Section(id: g.name, header: g.name, cards: g.routines.map { card($0, .routine) }) },
            apps: s.apps.map { g in
                AppRow(name: g.name, initial: String(g.name.prefix(1)).uppercased(), subtitle: [
                    L10n.Memory.tipsCount(g.tips.count), g.routines.isEmpty ? nil : L10n.Memory.routinesCount(g.routines.count),
                ].compactMap { $0 }.joined(separator: " · "))
            },
            appDetails: details
        )
    }
}
