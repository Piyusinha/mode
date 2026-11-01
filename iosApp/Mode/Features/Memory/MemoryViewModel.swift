import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// Memory, as on its board: what Mode has learned, by kind; Edit to delete several; a row opens its detail.
@MainActor
@Observable
final class MemoryViewModel {
    struct Entry: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
        let leading: ModeRowLeading
        /// For the detail sheet.
        let text: String
        let learnedFrom: String
        let worked: String?
    }

    struct State: Equatable {
        var learn = true
        var aboutYou: [Entry] = []
        var skills: [Entry] = []
        var tips: [Entry] = []
        var isEmpty: Bool { aboutYou.isEmpty && skills.isEmpty && tips.isEmpty }
    }

    enum Event {
        case learn(Bool)
        case toggleEditing
        case select(String)
        case deleteSelected
        case open(Entry)
        case saveDetail
        case deleteDetail
        case askForget
        case confirmForget
    }

    private(set) var state = State()
    private(set) var editing = false
    private(set) var selected: Set<String> = []
    var detail: Entry?
    var detailText = ""
    var confirmingForget = false
    private let presenter: MemoryPresenter
    private var updates: Updates?

    init(screens: Screens) {
        presenter = screens.memory()
        updates = observe(presenter.state) { [weak self] s in self?.state = Self.map(s) }
    }

    func send(_ event: Event) {
        switch event {
        case let .learn(on): presenter.setLearn(on: on)
        case .toggleEditing:
            editing.toggle()
            selected = []
        case let .select(id): if selected.contains(id) { selected.remove(id) } else { selected.insert(id) }
        case .deleteSelected:
            presenter.deleteMany(ids: Array(selected))
            selected = []
            editing = false
        case let .open(entry):
            if editing { send(.select(entry.id)) } else {
                detail = entry
                detailText = entry.text
            }
        case .saveDetail:
            if let entry = detail, detailText != entry.text { presenter.edit(id: entry.id, text: detailText) }
            detail = nil
        case .deleteDetail:
            if let entry = detail { presenter.delete(id: entry.id) }
            detail = nil
        case .askForget: confirmingForget = true
        case .confirmForget: presenter.forgetEverything()
        }
    }

    /// The row's start: a selection mark while editing, its own icon otherwise.
    func leading(_ entry: Entry) -> ModeRowLeading {
        editing ? (selected.contains(entry.id) ? .success : .pending) : entry.leading
    }

    var deleteTitle: String { L10n.Memory.deleteSelected(selected.count) }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: MemoryUiState) -> State {
        func entry(_ e: MemoryEntry, subtitle: String, leading: ModeRowLeading) -> Entry {
            Entry(id: e.id, title: e.title, subtitle: subtitle, leading: leading, text: e.text,
                  learnedFrom: e.evidence, worked: e.uses > 0 ? L10n.Memory.worked(Int(e.successes), of: Int(e.uses)) : nil)
        }
        return State(
            learn: s.learn,
            aboutYou: s.aboutYou.map { e in entry(e, subtitle: [e.text, L10n.Memory.learned(Format.day(e.learned).lowercased())].joined(separator: " · "), leading: .none) },
            skills: s.skills.map { e in
                entry(e, subtitle: [e.app, e.uses > 0 ? L10n.Memory.worked(Int(e.successes), of: Int(e.uses)) : nil].compactMap { $0 }.joined(separator: " · "), leading: .icon(.skill))
            },
            tips: s.tips.map { e in entry(e, subtitle: e.text, leading: .icon(.tip)) }
        )
    }
}
