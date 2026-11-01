import Foundation
import ModeKit
import Observation
import UIKit

/// Every screen the agent saw for one task, as on the All screens board: a grid, or played back.
@MainActor
@Observable
final class RunScreensViewModel {
    enum Mode: Hashable { case grid, play }

    struct Shot: Identifiable {
        let id: Int
        let number: String
        let label: String
        let image: UIImage?
    }

    struct State {
        var title = ""
        var subtitle = ""
        var shots: [Shot] = []
    }

    private(set) var state = State()
    var mode: Mode = .grid { didSet { mode == .play ? startPlaying() : stopPlaying() } }
    /// The screen showing in Play.
    var playIndex = 0
    var sharing: SharedText?
    private let presenter: RunScreensPresenter
    private var player: Updates?

    /// How long each screen shows in Play.
    private let playInterval: Duration = .milliseconds(1200)

    init(screens: Screens, taskId: String) {
        presenter = screens.runScreens(taskId: taskId)
        let s = presenter.state.value
        state = State(
            title: String(localized: L10n.Screens.title),
            subtitle: [s.task, L10n.Tasks.steps(s.shots.count)].joined(separator: " · "),
            shots: s.shots.map { shot in
                Shot(id: Int(shot.step), number: String(shot.step), label: shot.action.map { $0.prefix(1).uppercased() + $0.dropFirst() } ?? "",
                     image: presenter.image(shot: shot).flatMap { UIImage(data: $0.toData()) })
            }
        )
    }

    func share() {
        sharing = SharedText(text: ([state.subtitle] + state.shots.map { "\($0.number). \($0.label)" }).joined(separator: "\n"))
    }

    func open(_ shot: Shot) {
        playIndex = state.shots.firstIndex { $0.id == shot.id } ?? 0
        mode = .play
    }

    private func startPlaying() {
        player?.cancel()
        player = Updates { @MainActor [weak self] in
            while !_Concurrency.Task.isCancelled {
                try? await _Concurrency.Task.sleep(for: self?.playInterval ?? .seconds(1))
                guard let self, !self.state.shots.isEmpty else { return }
                self.playIndex = (self.playIndex + 1) % self.state.shots.count
            }
        }
    }

    private func stopPlaying() {
        player?.cancel()
        player = nil
    }

    isolated deinit {
        player?.cancel()
        presenter.close()
    }
}
