import Foundation

extension L10n {
    /// The first run (v2 boards: Welcome, ConnectAI…, FirstTask…).
    enum FirstRun {
        static let welcomeTitle = LocalizedStringResource("firstRun.welcomeTitle", defaultValue: "Mode uses your phone for you")
        static let welcomeBody = LocalizedStringResource("firstRun.welcomeBody", defaultValue: "Say what you need. Mo opens the apps and does it, step by step, and shows you everything.")
        static let welcomeCaveat = LocalizedStringResource("firstRun.welcomeCaveat", defaultValue: "Mo is new. It asks before sending or paying, and you can undo most things.")
        static let getStarted = LocalizedStringResource("firstRun.getStarted", defaultValue: "Get started")
        static let seeCode = LocalizedStringResource("firstRun.seeCode", defaultValue: "See the code on GitHub")
        static let connectTitle = LocalizedStringResource("firstRun.connectTitle", defaultValue: "Connect your AI")
        static let connectBody = LocalizedStringResource("firstRun.connectBody", defaultValue: "Mo thinks with the AI plan you already have.")
        static let chatGPT = "ChatGPT"
        static let recommended = LocalizedStringResource("firstRun.recommended", defaultValue: "Recommended")
        static let chatGPTDetail = String(localized: "firstRun.chatGPTDetail", defaultValue: "Your Plus plan, no cost per task")
        static let connect = LocalizedStringResource("firstRun.connect", defaultValue: "Connect")
        static let connected = String(localized: "firstRun.connected", defaultValue: "Connected")
        static let checking = String(localized: "firstRun.checking", defaultValue: "Checking your plan…")
        static let waiting = String(localized: "firstRun.waiting", defaultValue: "Waiting for ChatGPT…")
        static let waitingDetail = String(localized: "firstRun.waitingDetail", defaultValue: "Finish signing in on the ChatGPT page. Mode never sees your password.")
        static let cancel = LocalizedStringResource("firstRun.cancel", defaultValue: "Cancel")
        static let notEligible = String(localized: "firstRun.notEligible", defaultValue: "Your plan can't be used here")
        static let notEligibleDetail = String(localized: "firstRun.notEligibleDetail", defaultValue: "Mode needs a Plus or Pro plan. You can connect OpenRouter instead, pay as you go.")
        static let failed = String(localized: "firstRun.failed", defaultValue: "Couldn't connect ChatGPT")
        static let useOpenRouter = LocalizedStringResource("firstRun.useOpenRouter", defaultValue: "Use OpenRouter")
        static let tryAgain = LocalizedStringResource("firstRun.tryAgain", defaultValue: "Try ChatGPT again")
        static let moreOptions = LocalizedStringResource("firstRun.moreOptions", defaultValue: "More options")
        static let keychain = LocalizedStringResource("firstRun.keychain", defaultValue: "Sign-ins stay in this iPhone's Keychain.")
        static let `continue` = LocalizedStringResource("firstRun.continue", defaultValue: "Continue")
        static let firstJobTitle = LocalizedStringResource("firstRun.firstJobTitle", defaultValue: "Give Mo its first job")
        static let firstJobBody = LocalizedStringResource("firstRun.firstJobBody", defaultValue: "Something small. Watch it happen.")
        static let ownJob = LocalizedStringResource("firstRun.ownJob", defaultValue: "Or type your own")
        static let start = LocalizedStringResource("firstRun.start", defaultValue: "Start")
        static let permissionsNote = LocalizedStringResource("firstRun.permissionsNote", defaultValue: "As it starts, iOS asks about two things: Mode's VPN, so Mo can use your iPhone, and notifications, so Mo can ask you things.")
        static let starting = LocalizedStringResource("firstRun.starting", defaultValue: "Starting…")
        static let skip = LocalizedStringResource("firstRun.skip", defaultValue: "Skip for now")
        static func step(_ n: Int, of total: Int) -> String { String(localized: "firstRun.step", defaultValue: "Step \(n) of \(total)") }
        static let widgetTitle = LocalizedStringResource("firstRun.widgetTitle", defaultValue: "See Mo on your Home Screen")
        static let widgetResting = LocalizedStringResource("firstRun.widgetResting", defaultValue: "Mo is resting")
        static let widgetDone = LocalizedStringResource("firstRun.widgetDone", defaultValue: "1 thing done today")
        static let addWidget = LocalizedStringResource("firstRun.addWidget", defaultValue: "Add widget")
        static let later = LocalizedStringResource("firstRun.later", defaultValue: "Later")
        static let widgetHow = LocalizedStringResource("firstRun.widgetHow", defaultValue: "Touch and hold the Home Screen, tap Edit, then Add Widget, and pick Mode.")
        static let tryBigger = LocalizedStringResource("firstRun.tryBigger", defaultValue: "Try something bigger")
        static let pickDifferent = LocalizedStringResource("firstRun.pickDifferent", defaultValue: "Pick a different first job")
        /// "Couldn't turn on Do Not Disturb", from the job "Turn on Do Not Disturb".
        static func couldnt(_ job: String) -> String {
            String(localized: "firstRun.couldnt", defaultValue: "Couldn't \(job.prefix(1).lowercased() + job.dropFirst())")
        }
        static let nothingChanged = LocalizedStringResource("firstRun.nothingChanged", defaultValue: "Nothing changed on your phone.")
    }
}
