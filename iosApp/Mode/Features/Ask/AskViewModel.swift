import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// Ask: the message box for a new task, with the same suggestions as Home.
@MainActor
@Observable
final class AskViewModel {
    struct State: Equatable {
        var suggestions: [HomeViewModel.Row] = []
        var isPrivate = false
    }

    enum Event {
        case send
        case suggestion(String)
        case togglePrivate
    }

    private(set) var state = State()
    var draft = ""
    private let presenter: HomePresenter
    private let navigate: (Route) -> Void
    private var updates: Updates?

    init(screens: Screens, navigate: @escaping (Route) -> Void) {
        presenter = screens.home()
        self.navigate = navigate
        updates = observe(presenter.state) { [weak self] s in
            self?.state.suggestions = s.suggestions.map { HomeViewModel.Row(id: $0.text, title: $0.text, subtitle: $0.text, icon: .sparkle) }
        }
    }

    func send(_ event: Event) {
        switch event {
        case .send:
            ask(draft)
        case let .suggestion(text):
            draft = text
        case .togglePrivate:
            state.isPrivate.toggle()
        }
    }

    private func ask(_ text: String) {
        guard let conversation = presenter.ask(text: text, private: state.isPrivate) else { return }
        draft = ""
        state.isPrivate = false
        navigate(.conversation(id: conversation))
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }
}
