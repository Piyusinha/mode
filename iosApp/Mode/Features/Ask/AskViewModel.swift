import Foundation
import ModeDesignSystem
import ModeKit
import Observation

/// Ask (the Speak button): a new task, typed for now (speaking comes with voice input).
@MainActor
@Observable
final class AskViewModel {
    struct State: Equatable {
        var suggestions: [HomeViewModel.Suggestion] = []
        var isPrivate = false
    }

    enum Event {
        case send
        case suggestion(String)
        case togglePrivate
        case speak
    }

    private(set) var state = State()
    var draft = ""
    var canSend: Bool { !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    /// Speaking a task isn't built yet; the sheet says so.
    var showingVoiceNote = false
    private let presenter: HomePresenter
    private let onSent: (String) -> Void
    private var updates: Updates?

    init(screens: Screens, onSent: @escaping (String) -> Void) {
        presenter = screens.home()
        self.onSent = onSent
        updates = observe(presenter.state) { [weak self] s in
            self?.state.suggestions = s.suggestions.map { HomeViewModel.Suggestion(title: $0.text, subtitle: $0.text, icon: .sparkle) }
        }
    }

    func send(_ event: Event) {
        switch event {
        case .send:
            guard let conversation = presenter.ask(text: draft, private: state.isPrivate) else { return }
            draft = ""
            state.isPrivate = false
            onSent(conversation)
        case let .suggestion(text): draft = text
        case .togglePrivate: state.isPrivate.toggle()
        case .speak: showingVoiceNote = true
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }
}
