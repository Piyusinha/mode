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
        let steps: [String]
    }

    struct Finished: Equatable {
        let badge: Format.BadgeStyle
        let summary: String
        let line: String

        static func == (a: Finished, b: Finished) -> Bool { a.summary == b.summary && a.line == b.line }
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
                steps: s.steps.map { Self.short($0.action) }
            )
        }
        state.finished = s.finished.map {
            Finished(badge: Format.badge($0.badge), summary: $0.summary,
                     line: L10n.Live.summary(steps: Int($0.steps), seconds: Format.seconds(Double($0.seconds)), cost: Format.money($0.costUsd)))
        }
    }

    /// "open WhatsApp" → "Open WhatsApp", for the step labels.
    private static func short(_ action: String) -> String {
        action.prefix(1).uppercased() + action.dropFirst()
    }
}
