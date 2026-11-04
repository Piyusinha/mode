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
        /// The quiet line under it ("Learned today · used 3 times"), and its tag ("Worked 3 of 3 times").
        let footer: String?
        let badge: ModeMemoryBadge?
        /// For the editor: the full text as kept, and its notes (where it was learned, how often it worked).
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
        /// Its tile's letter (Mode has no app icons).
        let letter: String
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
        /// Swiped on a tip: open it in the editor, or delete it.
        case edit(Card)
        case remove(Card)
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
        case let .edit(card): openEditor(card.id)
        case let .remove(card): presenter.delete(id: card.id)
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

    /// How Mode records a tip taught in a chat (Teacher.kt): 'Taught by you in chat: "…"'.
    private static let inChat = "in chat:"

    private static func map(_ s: MemoryUiState) -> State {
        /// [marked]: one app's tips: every tip says whose it is (taught by the user, or learned by Mode).
        func card(_ e: MemoryEntry, _ kind: Kind, marked: Bool = false) -> Card {
            let day = Format.day(e.learned)
            // "Learned today", but "Learned 1 Nov" and "Learned Monday".
            let inline = [String(localized: L10n.Day.today), String(localized: L10n.Day.yesterday)].contains(day) ? day.lowercased() : day
            let uses = Int(e.uses), successes = Int(e.successes)
            let footer: String?
            let badge: ModeMemoryBadge?
            switch kind {
            case _ where e.taught && kind == .fact:
                // What the user said about themselves: theirs, not something Mode picked up.
                footer = L10n.Memory.youToldMode
                badge = nil
            case _ where e.taught:
                // Where it was taught: "Today, in chat", "Today, step 8".
                let step = e.evidence.firstMatch(of: /\(step (\d+)\)/).flatMap { Int($0.1) }
                footer = step.map { L10n.Memory.taughtAtStep(day, $0) } ?? (e.evidence.contains(Self.inChat) ? L10n.Memory.taughtInChat(day) : day)
                badge = .taught(L10n.Memory.youTaughtThis)
            case .tip where marked:
                footer = [day, uses > 0 ? L10n.Memory.usedTimes(uses) : nil].compactMap { $0 }.joined(separator: " · ")
                badge = .learned(L10n.Memory.modeLearned)
            case .routine:
                // A routine shows how often it worked; until it's used, when it was learned.
                footer = uses > 0 ? nil : L10n.Memory.learnedOn(inline)
                badge = uses > 0 ? .worked(L10n.Memory.workedBadge(successes, of: uses)) : nil
            case .fact, .tip:
                let source = uses > 0 ? L10n.Memory.usedTimes(uses) : kind == .fact ? e.app.map(L10n.Memory.fromTask) : nil
                footer = [L10n.Memory.learnedOn(inline), source].compactMap { $0 }.joined(separator: " · ")
                badge = nil
            }
            let learnedFrom = e.taught ? L10n.Memory.taughtNote(inline)
                : e.app.map { L10n.Memory.learnedFromTask($0, inline) } ?? L10n.Memory.learnedNote(inline)
            return Card(id: e.id, kind: kind, title: e.title, body: e.body.isEmpty ? nil : e.body, steps: e.steps, footer: footer, badge: badge,
                        text: e.text, learnedFrom: learnedFrom, worked: uses > 0 ? L10n.Memory.usedNote(uses, worked: successes) : nil)
        }
        let tipCount = Int(s.tipCount)
        var details: [String: AppDetail] = [:]
        for group in s.apps + s.routines where details[group.name] == nil {
            let tips = L10n.Memory.tipsCount(group.tips.count)
            details[group.name] = AppDetail(
                name: group.name, letter: String(group.name.prefix(1)).uppercased(), subtitle: L10n.Memory.appTipsSubtitle(tips, app: group.name),
                tips: group.tips.map { card($0, .tip, marked: true) },
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
