import Foundation

/// The Live Activity's words, shared by the app and the widget extension.
enum ActivityL10n {
    static let mode = LocalizedStringResource("activity.mode", defaultValue: "Mode")
    static let unlockToStart = LocalizedStringResource("activity.unlockToStart", defaultValue: "Unlock to start")
    static let unlockOrSay = LocalizedStringResource("activity.unlockOrSay", defaultValue: "Unlock, or say your phrase")
    static let unlock = LocalizedStringResource("activity.unlock", defaultValue: "Unlock your iPhone")
    static let startsOnUnlock = LocalizedStringResource("activity.startsOnUnlock", defaultValue: "Mo starts the moment your iPhone unlocks.")
    static let cancel = LocalizedStringResource("activity.cancel", defaultValue: "Cancel")
    static let stop = LocalizedStringResource("activity.stop", defaultValue: "Stop")
    static let connected = LocalizedStringResource("activity.connected", defaultValue: "Connected ·")
    static let connectedShort = LocalizedStringResource("activity.connectedShort", defaultValue: "Connected")
    static let canControl = LocalizedStringResource("activity.canControl", defaultValue: "Mode can see and control this iPhone")
    static let screenStaysOn = LocalizedStringResource("activity.screenStaysOn", defaultValue: "The screen stays on until you disconnect.")
    static let disconnect = LocalizedStringResource("activity.disconnect", defaultValue: "Disconnect")
    static let paused = LocalizedStringResource("activity.paused", defaultValue: "Paused")
    static let needsYou = LocalizedStringResource("activity.needsYou", defaultValue: "Needs you")
    static let open = LocalizedStringResource("activity.open", defaultValue: "Open")
    static let allow = LocalizedStringResource("activity.allow", defaultValue: "Yes")
    static let dontAllow = LocalizedStringResource("activity.dontAllow", defaultValue: "No")
    static let reply = LocalizedStringResource("activity.reply", defaultValue: "Reply in Mode")
    static let yourTurn = LocalizedStringResource("activity.yourTurn", defaultValue: "Your turn")
    static let entered = LocalizedStringResource("activity.entered", defaultValue: "I've entered it")
    static let openChat = LocalizedStringResource("activity.openChat", defaultValue: "Open chat")
    static let anotherTask = LocalizedStringResource("activity.anotherTask", defaultValue: "Another task")
    static let tryAgain = LocalizedStringResource("activity.tryAgain", defaultValue: "Try again")
    static let gaveUp = LocalizedStringResource("activity.gaveUp", defaultValue: "Couldn't finish")
    static let stopped = LocalizedStringResource("activity.stopped", defaultValue: "Stopped")
    static let failed = LocalizedStringResource("activity.failed", defaultValue: "Failed")
    static let notStarted = LocalizedStringResource("activity.notStarted", defaultValue: "Didn't start")
    static let closed = LocalizedStringResource("activity.closed", defaultValue: "Mode was closed before it finished. Open it to try again.")

    static let working = LocalizedStringResource("activity.working", defaultValue: "Working ·")
    static let workingNow = LocalizedStringResource("activity.workingNow", defaultValue: "Working")


    static func doneIn(_ seconds: Int) -> LocalizedStringResource {
        LocalizedStringResource("activity.doneIn", defaultValue: "Done in \(seconds) s")
    }

    static func openChatMore(_ n: Int) -> LocalizedStringResource {
        LocalizedStringResource("activity.openChatMore", defaultValue: "Open chat · \(n) more")
    }
}
