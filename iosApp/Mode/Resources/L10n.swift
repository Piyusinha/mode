import Foundation

/// Every string the app shows, in one place. The compiler copies them into
/// Localizable.xcstrings for translation; screens use these, never literals.
enum L10n {
    enum Tab {
        static let home = LocalizedStringResource("tab.home", defaultValue: "Home")
        static let tasks = LocalizedStringResource("tab.tasks", defaultValue: "Tasks")
        static let memory = LocalizedStringResource("tab.memory", defaultValue: "Memory")
        static let ask = LocalizedStringResource("tab.ask", defaultValue: "Ask")
    }

    enum Common {
        static let stop = LocalizedStringResource("common.stop", defaultValue: "Stop")
        static let cancel = LocalizedStringResource("common.cancel", defaultValue: "Cancel")
        static let save = LocalizedStringResource("common.save", defaultValue: "Save")
        static let done = LocalizedStringResource("common.done", defaultValue: "Done")
        static let `continue` = LocalizedStringResource("common.continue", defaultValue: "Continue")
        static let back = LocalizedStringResource("common.back", defaultValue: "Back")
        static let delete = LocalizedStringResource("common.delete", defaultValue: "Delete")
        static let undo = LocalizedStringResource("common.undo", defaultValue: "Undo")
        static let yes = LocalizedStringResource("common.yes", defaultValue: "Yes")
        static let no = LocalizedStringResource("common.no", defaultValue: "No")
        static let settings = LocalizedStringResource("common.settings", defaultValue: "Settings")
        static let comingSoon = LocalizedStringResource("common.comingSoon", defaultValue: "Coming soon")
    }

    enum Day {
        static let today = LocalizedStringResource("day.today", defaultValue: "Today")
        static let yesterday = LocalizedStringResource("day.yesterday", defaultValue: "Yesterday")
    }

    enum Badge {
        static let done = LocalizedStringResource("badge.done", defaultValue: "Done")
        static let gaveUp = LocalizedStringResource("badge.gaveUp", defaultValue: "Couldn't finish")
        static let stopped = LocalizedStringResource("badge.stopped", defaultValue: "Stopped")
        static let failed = LocalizedStringResource("badge.failed", defaultValue: "Failed")
        static let notStarted = LocalizedStringResource("badge.notStarted", defaultValue: "Didn't start")
    }

    enum Question {
        static let title = LocalizedStringResource("question.title", defaultValue: "Mode needs you")
        static let answerPrompt = LocalizedStringResource("question.answerPrompt", defaultValue: "Your answer")
        static let send = LocalizedStringResource("question.send", defaultValue: "Send answer")
        static let stopTask = LocalizedStringResource("question.stopTask", defaultValue: "Stop the task")
        static let allow = LocalizedStringResource("question.allow", defaultValue: "Allow")
        static let dontAllow = LocalizedStringResource("question.dontAllow", defaultValue: "Don't allow")
    }

    enum Placeholder {
        static let title = LocalizedStringResource("placeholder.title", defaultValue: "Being built")
        static let message = LocalizedStringResource("placeholder.message", defaultValue: "This screen arrives in the next update.")
    }

    enum Home {
        static let subtitle = LocalizedStringResource("home.subtitle", defaultValue: "What can I do for you?")
        static let suggested = LocalizedStringResource("home.suggested", defaultValue: "Suggested")
        static let recent = LocalizedStringResource("home.recent", defaultValue: "Recent")
        static let needsKeyTitle = LocalizedStringResource("home.needsKey.title", defaultValue: "Add your AI key")
        static let needsKeyMessage = LocalizedStringResource("home.needsKey.message", defaultValue: "Mode needs an OpenRouter key before it can do anything.")
        static let emptyTitle = LocalizedStringResource("home.empty.title", defaultValue: "Ask Mode anything")
        static let emptyMessage = LocalizedStringResource("home.empty.message", defaultValue: "Tap Ask and say what to do: \u{201C}Turn off dark mode\u{201D}, \u{201C}Text Sonali I'm on my way\u{201D}.")

        static func greeting(_ part: PartOfDayName, name: String?) -> String {
            switch (part, name) {
            case let (.morning, name?): String(localized: "home.greeting.morning.named", defaultValue: "Good morning, \(name)")
            case let (.afternoon, name?): String(localized: "home.greeting.afternoon.named", defaultValue: "Good afternoon, \(name)")
            case let (.evening, name?): String(localized: "home.greeting.evening.named", defaultValue: "Good evening, \(name)")
            case let (.night, name?): String(localized: "home.greeting.night.named", defaultValue: "Hello, \(name)")
            case (.morning, nil): String(localized: "home.greeting.morning", defaultValue: "Good morning")
            case (.afternoon, nil): String(localized: "home.greeting.afternoon", defaultValue: "Good afternoon")
            case (.evening, nil): String(localized: "home.greeting.evening", defaultValue: "Good evening")
            case (.night, nil): String(localized: "home.greeting.night", defaultValue: "Hello")
            }
        }

        static func askedOften(_ times: Int) -> String {
            String(localized: "home.suggestion.often", defaultValue: "You've asked \(times) times lately")
        }

        static func learned(app: String?) -> String {
            if let app { String(localized: "home.suggestion.learnedIn", defaultValue: "Mode knows how, in \(app)") }
            else { String(localized: "home.suggestion.learned", defaultValue: "Mode knows how") }
        }
    }

    enum PartOfDayName { case morning, afternoon, evening, night }

    enum Ask {
        static let title = LocalizedStringResource("ask.title", defaultValue: "Ask Mode")
        static let prompt = LocalizedStringResource("ask.prompt", defaultValue: "What should Mode do?")
        static let followUpPrompt = LocalizedStringResource("ask.followUpPrompt", defaultValue: "Ask for more")
        static let send = LocalizedStringResource("ask.send", defaultValue: "Send")
        static let privateOn = LocalizedStringResource("ask.privateOn", defaultValue: "Private: nothing is remembered")
        static let privateOff = LocalizedStringResource("ask.privateOff", defaultValue: "Make this private")
        static let voiceSoon = LocalizedStringResource("ask.voiceSoon", defaultValue: "Speaking a task is coming soon. Type it for now.")
    }

    enum Conversation {
        static let working = LocalizedStringResource("conversation.working", defaultValue: "Working on it")

        static func seeScreens(_ count: Int) -> String {
            String(localized: "conversation.seeScreens", defaultValue: "See all \(count) screens")
        }

        static func remembered(_ text: String) -> String {
            String(localized: "conversation.remembered", defaultValue: "Remembered: \(text)")
        }
    }

    enum Tasks {
        static let now = LocalizedStringResource("tasks.now", defaultValue: "Now")
        static let upNext = LocalizedStringResource("tasks.upNext", defaultValue: "Up next")
        static let repeats = LocalizedStringResource("tasks.repeats", defaultValue: "Repeats")
        static let done = LocalizedStringResource("tasks.done", defaultValue: "Done")
        static let waitingForYou = LocalizedStringResource("tasks.waitingForYou", defaultValue: "Waiting for you")
        static let newRepeat = LocalizedStringResource("tasks.newRepeat", defaultValue: "Repeat a task")
        static let emptyTitle = LocalizedStringResource("tasks.empty.title", defaultValue: "Nothing yet")
        static let emptyMessage = LocalizedStringResource("tasks.empty.message", defaultValue: "Tasks you ask for, and ones that repeat, show up here.")

        static func cancel(_ task: String) -> String {
            String(localized: "tasks.cancel", defaultValue: "Cancel \(task)")
        }

        static func steps(_ count: Int) -> String {
            String(localized: "tasks.steps", defaultValue: "\(count) steps")
        }

        static func nextRun(_ when: String) -> String {
            String(localized: "tasks.nextRun", defaultValue: "Next \(when)")
        }
    }

    enum Source {
        static let app = LocalizedStringResource("source.app", defaultValue: "From Mode")
        static let siri = LocalizedStringResource("source.siri", defaultValue: "From Siri")
        static let watch = LocalizedStringResource("source.watch", defaultValue: "From your Watch")
        static let shortcut = LocalizedStringResource("source.shortcut", defaultValue: "From Shortcuts")
        static let schedule = LocalizedStringResource("source.schedule", defaultValue: "Repeats")
    }

    enum Repeat {
        static let title = LocalizedStringResource("repeat.title", defaultValue: "Repeat")
        static let task = LocalizedStringResource("repeat.task", defaultValue: "Task")
        static let taskPrompt = LocalizedStringResource("repeat.taskPrompt", defaultValue: "What should Mode do?")
        static let time = LocalizedStringResource("repeat.time", defaultValue: "Time")
        static let everyDay = LocalizedStringResource("repeat.everyDay", defaultValue: "Every day")
        static let weekdays = LocalizedStringResource("repeat.weekdays", defaultValue: "Weekdays")
        static let weekends = LocalizedStringResource("repeat.weekends", defaultValue: "Weekends")
        static let once = LocalizedStringResource("repeat.once", defaultValue: "Once")

        static func note(_ first: String) -> String {
            String(localized: "repeat.note", defaultValue: "First run \(first). If Mode isn't open then, you'll get a notification: tap it and Mode does the task.")
        }
    }

    enum Live {
        static let title = LocalizedStringResource("live.title", defaultValue: "Live")
        static let idleTitle = LocalizedStringResource("live.idle.title", defaultValue: "Nothing running")
        static let idleMessage = LocalizedStringResource("live.idle.message", defaultValue: "When Mode works on a task, each step shows here.")

        static func summary(steps: Int, seconds: String, cost: String) -> String {
            String(localized: "live.summary", defaultValue: "\(steps) steps · \(seconds) · \(cost)")
        }
    }

    enum Screens {
        static let title = LocalizedStringResource("screens.title", defaultValue: "All screens")
        static let emptyTitle = LocalizedStringResource("screens.empty.title", defaultValue: "No screens saved")
        static let emptyMessage = LocalizedStringResource("screens.empty.message", defaultValue: "Screens are kept for the latest runs only.")

        static func step(_ n: Int) -> String {
            String(localized: "screens.step", defaultValue: "Step \(n)")
        }
    }

    enum Memory {
        static let subtitle = LocalizedStringResource("memory.subtitle", defaultValue: "What Mode has learned. Kept only on this iPhone.")
        static let learn = LocalizedStringResource("memory.learn", defaultValue: "Learn from my tasks")
        static let learnHint = LocalizedStringResource("memory.learnHint", defaultValue: "Mode still uses what it knows when this is off.")
        static let aboutYou = LocalizedStringResource("memory.aboutYou", defaultValue: "About You")
        static let skills = LocalizedStringResource("memory.skills", defaultValue: "Things Mode Can Do")
        static let tips = LocalizedStringResource("memory.tips", defaultValue: "App Tips")
        static let forgetEverything = LocalizedStringResource("memory.forgetEverything", defaultValue: "Forget Everything")
        static let forgetConfirm = LocalizedStringResource("memory.forgetConfirm", defaultValue: "Forget everything Mode has learned? This can't be undone.")
        static let emptyTitle = LocalizedStringResource("memory.empty.title", defaultValue: "Nothing learned yet")
        static let emptyMessage = LocalizedStringResource("memory.empty.message", defaultValue: "As Mode does tasks, it remembers people, what you like and how your apps work.")

        static func worked(_ successes: Int, of uses: Int) -> String {
            String(localized: "memory.worked", defaultValue: "worked \(successes) of \(uses) times")
        }

        static func learned(_ day: String) -> String {
            String(localized: "memory.learned", defaultValue: "learned \(day)")
        }
    }

    enum Settings {
        static let you = LocalizedStringResource("settings.you", defaultValue: "You")
        static let name = LocalizedStringResource("settings.name", defaultValue: "Your name")
        static let namePrompt = LocalizedStringResource("settings.namePrompt", defaultValue: "What should Mode call you?")
        static let key = LocalizedStringResource("settings.key", defaultValue: "OpenRouter key")
        static let keyPrompt = LocalizedStringResource("settings.keyPrompt", defaultValue: "sk-or-…")
        static let saveKey = LocalizedStringResource("settings.saveKey", defaultValue: "Save key")
        static let noKey = LocalizedStringResource("settings.noKey", defaultValue: "No key yet. Mode can't run tasks without one.")
        static let model = LocalizedStringResource("settings.model", defaultValue: "Model")
        static let whenStuck = LocalizedStringResource("settings.whenStuck", defaultValue: "When Mode is stuck")
        static let never = LocalizedStringResource("settings.never", defaultValue: "Never use a stronger model")
        static let search = LocalizedStringResource("settings.search", defaultValue: "Search all models")
        static let limits = LocalizedStringResource("settings.limits", defaultValue: "Limits")
        static let maxCost = LocalizedStringResource("settings.maxCost", defaultValue: "Most a task may cost")
        static let dailyCap = LocalizedStringResource("settings.dailyCap", defaultValue: "Daily limit")
        static let dailyCapOff = LocalizedStringResource("settings.dailyCapOff", defaultValue: "No daily limit")
        static let memory = LocalizedStringResource("settings.memory", defaultValue: "Memory")
        static let recommended = LocalizedStringResource("settings.tag.recommended", defaultValue: "Recommended")
        static let cheapest = LocalizedStringResource("settings.tag.cheapest", defaultValue: "Cheapest")
        static let strongest = LocalizedStringResource("settings.tag.strongest", defaultValue: "Strongest")

        static func keyEnding(_ ending: String) -> String {
            String(localized: "settings.keyEnding", defaultValue: "Saved in the Keychain · ends in \(ending)")
        }

        static func perTask(_ cost: String) -> String {
            String(localized: "settings.perTask", defaultValue: "about \(cost) a task")
        }
    }

    enum Setup {
        static let welcomeTitle = LocalizedStringResource("setup.welcome.title", defaultValue: "Your iPhone, done for you")
        static let welcomeMessage = LocalizedStringResource("setup.welcome.message", defaultValue: "Tell Mode what you want and it uses your apps to do it, right here on your iPhone. Setup takes about two minutes.")
        static let getStarted = LocalizedStringResource("setup.getStarted", defaultValue: "Get Started")
        static let nameTitle = LocalizedStringResource("setup.name.title", defaultValue: "What should Mode call you?")
        static let nameMessage = LocalizedStringResource("setup.name.message", defaultValue: "Used in greetings and when Mode signs a message for you.")
        static let keyTitle = LocalizedStringResource("setup.key.title", defaultValue: "Your AI key")
        static let keyMessage = LocalizedStringResource("setup.key.message", defaultValue: "Mode thinks with a model you choose on OpenRouter, and you pay it directly: usually under a cent a task. The key stays in this iPhone's Keychain.")
        static let getKey = LocalizedStringResource("setup.key.get", defaultValue: "Get a key at openrouter.ai")
        static let keyLooksWrong = LocalizedStringResource("setup.key.looksWrong", defaultValue: "OpenRouter keys start with sk-or-")
        static let modelTitle = LocalizedStringResource("setup.model.title", defaultValue: "Pick a model")
        static let modelMessage = LocalizedStringResource("setup.model.message", defaultValue: "You can change it any time in Settings.")
        static let permissionsTitle = LocalizedStringResource("setup.permissions.title", defaultValue: "Let Mode see and tap")
        static let permissionsMessage = LocalizedStringResource("setup.permissions.message", defaultValue: "iOS will ask to add a VPN. It never leaves your iPhone: Mode uses it to reach the phone's own developer services, so it can see the screen and tap for you.")
        static let allow = LocalizedStringResource("setup.permissions.allow", defaultValue: "Allow and Connect")
        static let testTitle = LocalizedStringResource("setup.test.title", defaultValue: "A quick test")
        static let testMessage = LocalizedStringResource("setup.test.message", defaultValue: "Mode will open Settings, tap Wi-Fi and come back here. Watch it happen.")
        static let runTest = LocalizedStringResource("setup.test.run", defaultValue: "Run the Test")
        static let resultTitle = LocalizedStringResource("setup.result.title", defaultValue: "Did it work?")
        static let resultMessage = LocalizedStringResource("setup.result.message", defaultValue: "Did you see Settings open and Wi-Fi get tapped?")
        static let itWorked = LocalizedStringResource("setup.result.worked", defaultValue: "Yes, It Worked")
        static let itDidnt = LocalizedStringResource("setup.result.didnt", defaultValue: "No")
        static let diagnoseTitle = LocalizedStringResource("setup.diagnose.title", defaultValue: "Let's find the problem")
        static let diagnoseMessage = LocalizedStringResource("setup.diagnose.message", defaultValue: "Mode checks each part on its own: the VPN, the phone's services, seeing the screen.")
        static let checkAgain = LocalizedStringResource("setup.diagnose.check", defaultValue: "Check Again")
        static let continueAnyway = LocalizedStringResource("setup.continueAnyway", defaultValue: "Continue Anyway")
        static let skip = LocalizedStringResource("setup.skip", defaultValue: "Skip for Now")
    }

    enum Developer {
        static let title = LocalizedStringResource("developer.title", defaultValue: "Developer")
    }
}
