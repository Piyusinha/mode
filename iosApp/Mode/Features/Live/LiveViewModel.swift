import Foundation
import ModeDesignSystem
import ModeKit
import Observation
import UIKit

/// The running task, as on the Live run board: the screen Mode sees, and the panel.
@MainActor
@Observable
final class LiveViewModel {
    struct Running: Equatable {
        let badge: String
        let startedAt: Date?
        let paused: Bool
        let task: String
        let now: String
        /// The last few steps as bars with labels: the latest is the one in progress.
        let segments: [ModeSegments.Segment]
    }

    struct Finished: Equatable {
        let badge: Format.BadgeStyle
        let reply: [ModeReplyBlock]
        let line: String

        static func == (a: Finished, b: Finished) -> Bool { a.reply == b.reply && a.line == b.line }
    }

    struct State: Equatable {
        var running: Running?
        var screen: UIImage?
        var finished: Finished?

        static func == (a: State, b: State) -> Bool { a.running == b.running && a.screen === b.screen && a.finished == b.finished }
    }

    enum Event { case stop, togglePause, close }

    private(set) var state = State()
    private let presenter: LiveRunPresenter
    private let close: () -> Void
    private var updates: Updates?
    private var lastScreen: KotlinByteArray?

    init(screens: Screens, close: @escaping () -> Void) {
        presenter = screens.liveRun()
        self.close = close
        updates = observe(presenter.state) { [weak self] s in self?.apply(s) }
    }

    func send(_ event: Event) {
        switch event {
        case .stop: presenter.stop()
        case .togglePause: if state.running?.paused == true { presenter.resume() } else { presenter.pause() }
        case .close: close()
        }
    }

    isolated deinit {
        updates?.cancel()
        presenter.close()
    }

    private func apply(_ s: LiveRunUiState) {
        // Decode the screenshot only when it changed.
        if s.screen !== lastScreen {
            lastScreen = s.screen
            state.screen = s.screen.flatMap { UIImage(data: $0.toData()) }
        }
        state.running = s.running.map { r in
            Running(
                badge: L10n.Run.liveBadge(step: Int(r.steps)),
                startedAt: r.startedAtMs.map { Date(timeIntervalSince1970: TimeInterval(truncating: $0) / 1000) },
                paused: r.paused,
                task: r.text,
                now: r.paused ? String(localized: L10n.Run.paused) : (s.now ?? r.status),
                segments: Self.segments(s.steps.map(\.reason).filter { !$0.isEmpty })
            )
        }
        state.finished = s.finished.map {
            Finished(badge: Format.badge($0.badge), reply: $0.parts.map(ModeReplyBlock.init),
                     line: L10n.Live.summary(steps: Int($0.steps), seconds: Format.seconds(Double($0.seconds))))
        }
    }

    /// The last three steps: the earlier ones done, the latest in progress.
    static func segments(_ steps: [String]) -> [ModeSegments.Segment] {
        let shown = steps.suffix(3)
        return shown.enumerated().map { index, step in .init(step, state: index == shown.count - 1 ? .now : .done) }
    }
}
