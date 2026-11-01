import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// Memory: what Mode has learned, by kind.
@MainActor
@Observable
final class MemoryViewModel {
    struct Entry: Identifiable, Equatable {
        let id: String
        let title: String
        let subtitle: String
        let leading: ModeRowLeading
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
        case delete(String)
        case askForget
        case confirmForget
    }

    private(set) var state = State()
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
        case let .delete(id): presenter.delete(id: id)
        case .askForget: confirmingForget = true
        case .confirmForget: presenter.forgetEverything()
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private static func map(_ s: MemoryUiState) -> State {
        func used(_ e: MemoryEntry) -> String? { e.uses > 0 ? L10n.Memory.worked(Int(e.successes), of: Int(e.uses)) : nil }
        return State(
            learn: s.learn,
            aboutYou: s.aboutYou.map { e in
                Entry(id: e.id, title: e.title, subtitle: [e.text, L10n.Memory.learned(Format.day(e.learned))].joined(separator: " · "),
                      leading: .initial(String(e.title.prefix(1)).uppercased()))
            },
            skills: s.skills.map { e in
                Entry(id: e.id, title: e.title, subtitle: [e.app, used(e)].compactMap { $0 }.joined(separator: " · "), leading: .icon(.skill))
            },
            tips: s.tips.map { e in Entry(id: e.id, title: e.title, subtitle: e.text, leading: .icon(.tip)) }
        )
    }
}
