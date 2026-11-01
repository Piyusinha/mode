import Foundation

/// The Live Activity's words, shared by the app and the widget extension.
enum ActivityL10n {
    static let mode = LocalizedStringResource("activity.mode", defaultValue: "Mode")
    static let stop = LocalizedStringResource("activity.stop", defaultValue: "Stop")
    static let paused = LocalizedStringResource("activity.paused", defaultValue: "Paused")
    static let needsYou = LocalizedStringResource("activity.needsYou", defaultValue: "Mode needs you")
    static let open = LocalizedStringResource("activity.open", defaultValue: "Open")
    static let openChat = LocalizedStringResource("activity.openChat", defaultValue: "Open chat")
    static let anotherTask = LocalizedStringResource("activity.anotherTask", defaultValue: "Another task")
    static let gaveUp = LocalizedStringResource("activity.gaveUp", defaultValue: "Couldn't finish")
    static let stopped = LocalizedStringResource("activity.stopped", defaultValue: "Stopped")
    static let failed = LocalizedStringResource("activity.failed", defaultValue: "Failed")
    static let notStarted = LocalizedStringResource("activity.notStarted", defaultValue: "Didn't start")

    static func step(_ n: Int) -> LocalizedStringResource {
        LocalizedStringResource("activity.step", defaultValue: "Step \(n) ·")
    }

    static func stepShort(_ n: Int) -> LocalizedStringResource {
        LocalizedStringResource("activity.stepShort", defaultValue: "Step \(n)")
    }

    static func doneIn(_ seconds: Int) -> LocalizedStringResource {
        LocalizedStringResource("activity.doneIn", defaultValue: "Done in \(seconds) s")
    }

    static func more(_ n: Int) -> LocalizedStringResource {
        LocalizedStringResource("activity.more", defaultValue: "+\(n) more in the chat")
    }
}
