import Foundation
import ModeKit
import Observation
import UIKit

/// Every screen the agent saw for one task, as on the All screens board: a grid, or played back.
@MainActor
@Observable
final class RunScreensViewModel {
    enum Mode: Hashable { case grid, play }

    struct Shot: Identifiable, Equatable {
        static func == (a: Shot, b: Shot) -> Bool { a.id == b.id }

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
    /// The step the user marked wrong (the sheet), Mode working on it, and what Mode said back.
    var fixing: Shot?
    private(set) var savingFix = false
    var fixReply: String?
    private let presenter: RunScreensPresenter
    private var player: Updates?
    private var updates: Updates?

    /// How long each screen shows in Play.
    private let playInterval: Duration = .milliseconds(1200)

    init(screens: Screens, taskId: String) {
        presenter = screens.runScreens(taskId: taskId)
        defer {
            updates = observe(presenter.state) { [weak self] s in
                self?.savingFix = s.fixing
                self?.fixReply = s.fixed
            }
        }
        let s = presenter.state.value
        state = State(
            title: String(localized: L10n.Screens.title),
            subtitle: [s.task, L10n.Tasks.steps(s.shots.count)].joined(separator: " · "),
            shots: s.shots.map { shot in
                Shot(id: Int(shot.step), number: String(shot.step), label: shot.label ?? "",
                     image: presenter.image(shot: shot).flatMap { UIImage(data: $0.toData()) })
            }
        )
    }

    /// This step was wrong: what Mode should have done, now or every time.
    func markWrong(_ shot: Shot) {
        mode = .grid
        fixing = shot
    }

    func saveFix(_ text: String, everyTime: Bool) {
        guard let shot = fixing else { return }
        fixing = nil
        presenter.fixStep(step: Int32(shot.id), label: shot.label, said: text, everyTime: everyTime)
    }

    func fixSeen() {
        fixReply = nil
        presenter.fixedSeen()
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
        updates?.cancel()
        presenter.close()
    }
}
