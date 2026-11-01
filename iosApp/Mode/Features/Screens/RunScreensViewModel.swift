import Foundation
import ModeKit
import Observation
import UIKit

/// Every screen the agent saw for one task.
@MainActor
@Observable
final class RunScreensViewModel {
    struct Shot: Identifiable {
        let id: Int
        let title: String
        let action: String
        let image: UIImage?
    }

    struct State {
        var task = ""
        var shots: [Shot] = []
    }

    private(set) var state = State()
    private let presenter: RunScreensPresenter

    init(screens: Screens, taskId: String) {
        presenter = screens.runScreens(taskId: taskId)
        let s = presenter.state.value
        state = State(
            task: s.task,
            shots: s.shots.map { shot in
                Shot(id: Int(shot.step), title: L10n.Screens.step(Int(shot.step)), action: shot.action ?? "",
                     image: presenter.image(shot: shot).flatMap { UIImage(data: $0.toData()) })
            }
        )
    }

    isolated deinit {
        presenter.close()
    }
}

private extension KotlinByteArray {
    func toData() -> Data {
        let count = Int(size)
        var bytes = [UInt8](repeating: 0, count: count)
        for i in 0..<count { bytes[i] = UInt8(bitPattern: get(index: Int32(i))) }
        return Data(bytes)
    }
}
