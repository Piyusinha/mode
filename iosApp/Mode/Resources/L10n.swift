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
        static let recommended = LocalizedStringResource("question.recommended", defaultValue: "Mode's pick")
        static let somethingElse = LocalizedStringResource("question.somethingElse", defaultValue: "Or type something else")
        static let yes = LocalizedStringResource("question.yes", defaultValue: "Yes")
        static let no = LocalizedStringResource("question.no", defaultValue: "No")
        static let always = LocalizedStringResource("question.always", defaultValue: "Yes, and don't ask again")
        static let alwaysNote = LocalizedStringResource("question.alwaysNote", defaultValue: "“Don't ask again” gives Mode full access for every task. You can change it in Settings → Access.")
        static let yourTurn = LocalizedStringResource("question.yourTurn", defaultValue: "Your turn")
        static let yourTurnNote = LocalizedStringResource("question.yourTurnNote", defaultValue: "Mode can't see or type this. When you're done, Mode carries on by itself.")
        static let entered = LocalizedStringResource("question.entered", defaultValue: "I've entered it")
    }

    enum Run {
        static let pause = LocalizedStringResource("run.pause", defaultValue: "Pause")
        static let resume = LocalizedStringResource("run.resume", defaultValue: "Resume")
        static let paused = LocalizedStringResource("run.paused", defaultValue: "Paused")
        static let shrink = LocalizedStringResource("run.shrink", defaultValue: "Shrink to the Dynamic Island")

        static func liveBadge(step: Int) -> String {
            String(localized: "run.liveBadge", defaultValue: "Live · step \(step) ·")
        }
    }

    enum Placeholder {
        static let title = LocalizedStringResource("placeholder.title", defaultValue: "Being built")
        static let message = LocalizedStringResource("placeholder.message", defaultValue: "This screen arrives in the next update.")
    }

    enum Home {
        static let subtitle = LocalizedStringResource("home.subtitle", defaultValue: "What can I do for you?")
        static let profile = LocalizedStringResource("home.profile", defaultValue: "Profile and settings")
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
        static let add = LocalizedStringResource("ask.add", defaultValue: "More")
        static let privateOn = LocalizedStringResource("ask.privateOn", defaultValue: "Private: nothing is remembered")
        static let privateOff = LocalizedStringResource("ask.privateOff", defaultValue: "Make this private")
        static let voiceSoon = LocalizedStringResource("ask.voiceSoon", defaultValue: "Speaking a task is coming soon. Type it for now.")
    }

    enum Conversation {
        static let savedForNextTime = LocalizedStringResource("conversation.savedForNextTime", defaultValue: "SAVED FOR NEXT TIME")
        static let edit = LocalizedStringResource("conversation.edit", defaultValue: "Edit")
        static let alsoNextTime = LocalizedStringResource("conversation.alsoNextTime", defaultValue: "Also do it this way next time?")
        static let remember = LocalizedStringResource("conversation.remember", defaultValue: "Remember")
        static let saving = LocalizedStringResource("conversation.saving", defaultValue: "Saving it for next time…")
        static let steerPrompt = LocalizedStringResource("conversation.steerPrompt", defaultValue: "Tell Mode what to do differently…")
        static let teachPrompt = LocalizedStringResource("conversation.teachPrompt", defaultValue: "Ask for more, or teach Mode")
        static let editLesson = LocalizedStringResource("conversation.editLesson", defaultValue: "Edit the tip")
        static let editLessonPrompt = LocalizedStringResource("conversation.editLessonPrompt", defaultValue: "What Mode should do next time")
        static let working = LocalizedStringResource("conversation.working", defaultValue: "Working on it")
        static let spoken = LocalizedStringResource("conversation.spoken", defaultValue: "Spoken")
        static let allDay = LocalizedStringResource("conversation.allDay", defaultValue: "All day")
        static let copy = LocalizedStringResource("conversation.copy", defaultValue: "Copy")
        static let tryAgain = LocalizedStringResource("conversation.tryAgain", defaultValue: "Try again")
        static let shareReply = LocalizedStringResource("conversation.shareReply", defaultValue: "Share reply")
        static let share = LocalizedStringResource("conversation.share", defaultValue: "Share")
        static let more = LocalizedStringResource("conversation.more", defaultValue: "More")
        static let repeatTask = LocalizedStringResource("conversation.repeat", defaultValue: "Repeat this task…")
        static let delete = LocalizedStringResource("conversation.delete", defaultValue: "Delete conversation")
        static let deleteConfirm = LocalizedStringResource("conversation.deleteConfirm", defaultValue: "Delete this conversation? Its tasks stay in Tasks.")

        static func seeScreens(_ count: Int) -> String {
            String(localized: "conversation.seeScreens", defaultValue: "See all \(count) screens")
        }

        static func remembered(_ text: String) -> String {
            String(localized: "conversation.remembered", defaultValue: "Remembered: \(text)")
        }
    }

    enum Tasks {
        static let now = LocalizedStringResource("tasks.now", defaultValue: "Now")
        static let scheduled = LocalizedStringResource("tasks.scheduled", defaultValue: "Scheduled")
        static let running = LocalizedStringResource("tasks.running", defaultValue: "Running")
        static let open = LocalizedStringResource("tasks.open", defaultValue: "Open")
        static let newTask = LocalizedStringResource("tasks.newTask", defaultValue: "New task")
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

        static func summary(steps: Int, seconds: String) -> String {
            String(localized: "live.summary", defaultValue: "\(seconds) · \(steps) steps")
        }
    }

    enum Screens {
        static let wrongStep = LocalizedStringResource("screens.wrongStep", defaultValue: "This step was wrong")
        static let whatInstead = LocalizedStringResource("screens.whatInstead", defaultValue: "What should Mode have done?")
        static let whatInsteadPrompt = LocalizedStringResource("screens.whatInsteadPrompt", defaultValue: "Search Settings for “Dark Mode” instead")
        static let justThisTime = LocalizedStringResource("screens.justThisTime", defaultValue: "Just this time")
        static let everyTime = LocalizedStringResource("screens.everyTime", defaultValue: "Every time")
        static let everyTimeNote = LocalizedStringResource("screens.everyTimeNote", defaultValue: "Mode keeps it as a tip for that app and uses it on the next task.")
        static let justThisTimeNote = LocalizedStringResource("screens.justThisTimeNote", defaultValue: "Mode does it that way now: in the task if it's still running, or by running it again.")
        static let savingFix = LocalizedStringResource("screens.savingFix", defaultValue: "Saving it…")
        static let ok = LocalizedStringResource("screens.ok", defaultValue: "OK")
        static func stepLine(_ number: String, _ label: String) -> String {
            String(localized: "screens.stepLine", defaultValue: "Step \(number) · \(label)")
        }
        static let title = LocalizedStringResource("screens.title", defaultValue: "All screens")
        static let grid = LocalizedStringResource("screens.grid", defaultValue: "Grid")
        static let play = LocalizedStringResource("screens.play", defaultValue: "Play")
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
        static let edit = LocalizedStringResource("memory.edit", defaultValue: "Edit")
        static let whatModeKnows = LocalizedStringResource("memory.whatModeKnows", defaultValue: "What Mode knows")

        static func deleteSelected(_ count: Int) -> String {
            String(localized: "memory.deleteSelected", defaultValue: "Delete \(count)")
        }

        static func learnedFrom(_ evidence: String) -> String {
            String(localized: "memory.learnedFrom", defaultValue: "Learned from: \(evidence)")
        }
        static let forgetConfirm = LocalizedStringResource("memory.forgetConfirm", defaultValue: "Forget everything Mode has learned? This can't be undone.")
        static let emptyTitle = LocalizedStringResource("memory.empty.title", defaultValue: "Nothing learned yet")
        static let emptyMessage = LocalizedStringResource("memory.empty.message", defaultValue: "As Mode does tasks, it remembers people, what you like and how your apps work.")

        static func worked(_ successes: Int, of uses: Int) -> String {
            String(localized: "memory.worked", defaultValue: "worked \(successes) of \(uses) times")
        }

        static func learned(_ day: String) -> String {
            String(localized: "memory.learned", defaultValue: "learned \(day)")
        }

        // One screen per kind of memory.
        static let hubHint = LocalizedStringResource("memory.hubHint", defaultValue: "Mode reads these before every task, so it gets things right the first time and takes fewer steps.")
        static let aboutYouHint = LocalizedStringResource("memory.aboutYouHint", defaultValue: "People, places and preferences Mode keeps in mind.")
        static let routinesHint = LocalizedStringResource("memory.routinesHint", defaultValue: "Routines from tasks that worked. Mode follows them next time.")
        static let appsHint = LocalizedStringResource("memory.appsHint", defaultValue: "What Mode learned in each app. It uses these on every task there.")
        static let routinesInApp = LocalizedStringResource("memory.routinesInApp", defaultValue: "Routines in this app")
        static let titleLabel = LocalizedStringResource("memory.titleLabel", defaultValue: "Title")
        static let stepsLabel = LocalizedStringResource("memory.stepsLabel", defaultValue: "Steps, one per line")
        static let editFact = LocalizedStringResource("memory.editFact", defaultValue: "About You")
        static let editRoutine = LocalizedStringResource("memory.editRoutine", defaultValue: "Routine")
        static let editTip = LocalizedStringResource("memory.editTip", defaultValue: "Tip")
        static let nothingHere = LocalizedStringResource("memory.nothingHere", defaultValue: "Nothing here yet")
        static let nothingHereMessage = LocalizedStringResource("memory.nothingHereMessage", defaultValue: "Mode adds to this as it does tasks for you.")

        static func things(_ n: Int) -> String {
            n == 1 ? String(localized: "memory.things.one", defaultValue: "1 thing · people, places, preferences")
                : String(localized: "memory.things", defaultValue: "\(n) things · people, places, preferences")
        }

        static func routinesLearned(_ n: Int) -> String {
            n == 1 ? String(localized: "memory.routinesLearned.one", defaultValue: "1 routine learned from a task that worked")
                : String(localized: "memory.routinesLearned", defaultValue: "\(n) routines learned from tasks that worked")
        }

        static func tipsCount(_ n: Int) -> String {
            n == 1 ? String(localized: "memory.tipsCount.one", defaultValue: "1 tip") : String(localized: "memory.tipsCount", defaultValue: "\(n) tips")
        }

        static func routinesCount(_ n: Int) -> String {
            n == 1 ? String(localized: "memory.routinesCount.one", defaultValue: "1 routine") : String(localized: "memory.routinesCount", defaultValue: "\(n) routines")
        }

        static func appsCount(_ n: Int) -> String {
            n == 1 ? String(localized: "memory.appsCount.one", defaultValue: "1 app") : String(localized: "memory.appsCount", defaultValue: "\(n) apps")
        }

        static func appTipsHint(_ tips: String) -> String {
            String(localized: "memory.appTipsHint", defaultValue: "\(tips) Mode uses on every task here.")
        }

        static func learnedOn(_ day: String) -> String {
            String(localized: "memory.learnedOn", defaultValue: "Learned \(day)")
        }

        static func taughtOn(_ day: String) -> String {
            String(localized: "memory.taughtOn", defaultValue: "You taught this \(day)")
        }
    }

    enum Providers {
        static let title = LocalizedStringResource("providers.title", defaultValue: "AI providers")
        static let sheetMessage = LocalizedStringResource("providers.sheetMessage", defaultValue: "Connect as many as you like. Mode uses the one you pick.")
        static let chatGPT = LocalizedStringResource("providers.chatgpt", defaultValue: "ChatGPT")
        static let claude = LocalizedStringResource("providers.claude", defaultValue: "Claude")
        static let openRouter = LocalizedStringResource("providers.openrouter", defaultValue: "OpenRouter")
        static let chatGPTDetail = LocalizedStringResource("providers.chatgptDetail", defaultValue: "your Plus or Pro plan")
        static let claudeDetail = LocalizedStringResource("providers.claudeDetail", defaultValue: "experimental")
        static let openRouterDetail = LocalizedStringResource("providers.openrouterDetail", defaultValue: "pay as you go")
        static let connected = LocalizedStringResource("providers.connected", defaultValue: "Connected")
        static let connect = LocalizedStringResource("providers.connect", defaultValue: "Connect")
        static let notConnected = LocalizedStringResource("providers.notConnected", defaultValue: "Not connected")
        static let useOpenRouterOnceConnected = LocalizedStringResource("providers.useOnceConnected", defaultValue: "Use OpenRouter once connected")
        static let recommended = LocalizedStringResource("providers.recommended", defaultValue: "Recommended")
        static let inUse = LocalizedStringResource("providers.inUse", defaultValue: "In use")
        static let change = LocalizedStringResource("providers.change", defaultValue: "Change")
        static let aiInUse = LocalizedStringResource("providers.aiInUse", defaultValue: "AI in use")
        static let notConnectedYet = LocalizedStringResource("providers.notConnectedYet", defaultValue: "Not connected yet")
        static let switchNote = LocalizedStringResource("providers.switchNote", defaultValue: "Switching keeps each provider's models. Tap a connected provider for its models, usage, or to disconnect.")
        static let provider = LocalizedStringResource("providers.provider", defaultValue: "Provider")
        static let models = LocalizedStringResource("providers.models", defaultValue: "Models")
        static let connectedHeader = LocalizedStringResource("providers.connectedHeader", defaultValue: "Connected")
        static let disconnect = LocalizedStringResource("providers.disconnect", defaultValue: "Disconnect")
        static let keychainNote = LocalizedStringResource("providers.keychainNote", defaultValue: "Sign-ins and keys stay in this iPhone's Keychain. They only go to that provider, never to us.")
        static let setupMessage = LocalizedStringResource("providers.setupMessage", defaultValue: "Mode is open source. Use the AI plan you already pay for, or pay as you go.")
        static let setupHint = LocalizedStringResource("providers.setupHint", defaultValue: "Pick the one Mode should use; connect as many as you like. You can change this anytime in Settings.")
        static let setupHintConnected = LocalizedStringResource("providers.setupHintConnected", defaultValue: "Connected and picked. You can connect the others now or later in Settings.")
        static let connectOpenRouter = LocalizedStringResource("providers.connectOpenRouter", defaultValue: "Connect OpenRouter")
        static let openRouterMessage = LocalizedStringResource("providers.openRouterMessage", defaultValue: "Pay as you go, any model. Usually under a cent a task.")
        static let signInOpenRouter = LocalizedStringResource("providers.signInOpenRouter", defaultValue: "Sign in with OpenRouter")
        static let connectClaude = LocalizedStringResource("providers.connectClaude", defaultValue: "Connect Claude")
        static let planNote = LocalizedStringResource("providers.planNote", defaultValue: "Tasks run on your plan, so there is no cost per task. Set how much of your plan Mode may use in that provider's own settings.")

        static func connectedCount(_ n: Int) -> String {
            n == 1 ? String(localized: "providers.connectedCount.one", defaultValue: "Connected")
                : String(localized: "providers.connectedCount", defaultValue: "Connected · \(n) providers connected")
        }

        static func keyEnding(_ ending: String) -> String {
            String(localized: "providers.keyEnding", defaultValue: "key …\(ending)")
        }

        static func continueWith(_ name: String) -> String {
            String(localized: "providers.continueWith", defaultValue: "Continue with \(name)")
        }
    }

    enum Access {
        static let title = LocalizedStringResource("access.title", defaultValue: "Access")
        static let subtitle = LocalizedStringResource("access.subtitle", defaultValue: "How much Mode does on its own.")
        static let section = LocalizedStringResource("access.section", defaultValue: "Access")
        static let howModeActs = LocalizedStringResource("access.howModeActs", defaultValue: "How Mode acts")
        static let askFirst = LocalizedStringResource("access.askFirst", defaultValue: "Ask before paying or sending")
        static let askFirstShort = LocalizedStringResource("access.askFirstShort", defaultValue: "Ask first")
        static let askFirstDetail = LocalizedStringResource("access.askFirstDetail", defaultValue: "Mode stops right before it pays, books, orders, sends or deletes, and asks you.")
        static let full = LocalizedStringResource("access.full", defaultValue: "Full access")
        static let fullDetail = LocalizedStringResource("access.fullDetail", defaultValue: "Mode does the whole task without asking. It stops only where iPhone hides the screen or you must type: a UPI PIN, a password, Face ID.")
        static let spending = LocalizedStringResource("access.spending", defaultValue: "Spending")
        static let askAbove = LocalizedStringResource("access.askAbove", defaultValue: "Ask before paying more than")
        static let askAboveOffNote = LocalizedStringResource("access.askAboveOffNote", defaultValue: "Off: Mode pays any amount a task needs. Set an amount to be asked above it.")
        static let askAboveOnNote = LocalizedStringResource("access.askAboveOnNote", defaultValue: "Mode asks you before paying more than this. Smaller payments go ahead.")
        static let oneTask = LocalizedStringResource("access.oneTask", defaultValue: "Full access for one task")
        static let addWord = LocalizedStringResource("access.addWord", defaultValue: "Add a word")
        static let addWordTitle = LocalizedStringResource("access.addWordTitle", defaultValue: "Add a word")
        static let addWordPrompt = LocalizedStringResource("access.addWordPrompt", defaultValue: "no questions")
        static let addWordMessage = LocalizedStringResource("access.addWordMessage", defaultValue: "Start or end a task with it to run that task with full access.")
        static let add = LocalizedStringResource("access.add", defaultValue: "Add")
        static let codes = LocalizedStringResource("access.codes", defaultValue: "One-time codes")
        static let readCodes = LocalizedStringResource("access.readCodes", defaultValue: "Read OTPs for my tasks")
        static let readCodesNote = LocalizedStringResource("access.readCodesNote", defaultValue: "When a task needs a code, Mode reads it from Messages or the notification and types it in. Codes are never saved: not in memory, the task’s screens or its log.")
        static let askCodesNote = LocalizedStringResource("access.askCodesNote", defaultValue: "Off: when a task needs a code, Mode asks you for it.")
        static let alwaysAsks = LocalizedStringResource("access.alwaysAsks", defaultValue: "Mode always asks you for")
        static let pin = LocalizedStringResource("access.pin", defaultValue: "UPI PIN and bank screens")
        static let pinDetail = LocalizedStringResource("access.pinDetail", defaultValue: "iPhone hides these screens from Mode")
        static let passwords = LocalizedStringResource("access.passwords", defaultValue: "Passwords and passcodes")
        static let passwordsDetail = LocalizedStringResource("access.passwordsDetail", defaultValue: "Mode never types them")
        static let faceID = LocalizedStringResource("access.faceID", defaultValue: "Face ID")
        static let faceIDDetail = LocalizedStringResource("access.faceIDDetail", defaultValue: "Only you can look at the phone")
        static let alwaysAsksNote = LocalizedStringResource("access.alwaysAsksNote", defaultValue: "Mode waits on that screen for you. Enter it, and Mode carries on from there.")

        static func remove(_ word: String) -> String {
            String(localized: "access.remove", defaultValue: "Remove “\(word)”")
        }

        static func oneTaskNote(_ word: String) -> String {
            String(localized: "access.oneTaskNote", defaultValue: "Start or end a task with one of these, like “\(word), order my usual from Swiggy”, and that task runs with full access. Others still ask.")
        }
    }

    enum Plan {
        static let title = LocalizedStringResource("plan.title", defaultValue: "Models")
        static let phoneControl = LocalizedStringResource("plan.phoneControl", defaultValue: "Phone control")
        static let brain = LocalizedStringResource("plan.brain", defaultValue: "Brain")
        static let roleHint = LocalizedStringResource("plan.roleHint", defaultValue: "Phone control reads the screen and taps, every step: pick fast. The brain plans, thinks through hard steps and checks the result: pick accurate.")
        static let effort = LocalizedStringResource("plan.effort", defaultValue: "Effort")
        static let low = LocalizedStringResource("plan.low", defaultValue: "Low")
        static let medium = LocalizedStringResource("plan.medium", defaultValue: "Medium")
        static let high = LocalizedStringResource("plan.high", defaultValue: "High")
        static let effortNone = LocalizedStringResource("plan.effortNone", defaultValue: "Off")
        static let minimal = LocalizedStringResource("plan.minimal", defaultValue: "Minimal")
        static let extraHigh = LocalizedStringResource("plan.extraHigh", defaultValue: "Extra")
        static let max = LocalizedStringResource("plan.max", defaultValue: "Max")
        static let usesApiCredits = LocalizedStringResource("plan.usesApiCredits", defaultValue: "Uses API credits, not your plan")
        static let failed = LocalizedStringResource("plan.failed", defaultValue: "Didn't run")
        static let reserveNote = LocalizedStringResource("plan.reserveNote", defaultValue: "Luna Reserve: extra usage after your plan runs out.")

        static func couldNotUse(_ reason: String) -> String {
            String(localized: "plan.couldNotUse", defaultValue: "Couldn't use it: \(reason)")
        }
        static let speed = LocalizedStringResource("plan.speed", defaultValue: "Speed")
        static let accuracy = LocalizedStringResource("plan.accuracy", defaultValue: "Accuracy")
        static let notMeasured = LocalizedStringResource("plan.notMeasured", defaultValue: "Not measured yet")
        static let measure = LocalizedStringResource("plan.measure", defaultValue: "Measure on this iPhone")
        static let measureNote = LocalizedStringResource("plan.measureNote", defaultValue: "Measured by Mode on this iPhone: the same short screen test for each model and effort. Your plan pays, so there is no cost per task.")
        static let brainMeasureNote = LocalizedStringResource("plan.brainMeasureNote", defaultValue: "The brain's own test, run on this iPhone: made-up moments where Mode must plan or decide, like skipping a sponsored place for the best-rated one, typing instead of tapping a field again, or knowing a task is done. Your plan pays, so there is no cost per task.")
        static let bestForPhone = LocalizedStringResource("plan.tag.bestForPhone", defaultValue: "Best for phone control")
        static let bestForBrain = LocalizedStringResource("plan.tag.bestForBrain", defaultValue: "Best for brain")
        static let fastest = LocalizedStringResource("plan.tag.fastest", defaultValue: "Fastest")
        static let mostAccurate = LocalizedStringResource("plan.tag.mostAccurate", defaultValue: "Most accurate")

        static func perStep(_ seconds: String) -> String {
            String(localized: "plan.perStep", defaultValue: "\(seconds) s a step")
        }

        static func perAnswer(_ seconds: String) -> String {
            String(localized: "plan.perAnswer", defaultValue: "\(seconds) s an answer")
        }

        static func right(_ correct: Int, of total: Int) -> String {
            String(localized: "plan.right", defaultValue: "\(correct) of \(total) right")
        }

        static func effortName(_ effort: String) -> String {
            String(localized: "plan.effortName", defaultValue: "\(effort) effort")
        }

        static func measuring(_ done: Int, of total: Int) -> String {
            String(localized: "plan.measuring", defaultValue: "Measuring \(done) of \(total)…")
        }

        static func onPlan(_ provider: String, _ count: Int) -> String {
            count == 1 ? String(localized: "plan.onPlan.one", defaultValue: "\(provider) · 1 model on your plan")
                : String(localized: "plan.onPlan", defaultValue: "\(provider) · \(count) models on your plan")
        }
    }

    enum Account {
        static let account = LocalizedStringResource("account.account", defaultValue: "Account")
        static let usage = LocalizedStringResource("account.usage", defaultValue: "Usage")
        static let credits = LocalizedStringResource("account.credits", defaultValue: "Credits")
        static let models = LocalizedStringResource("account.models", defaultValue: "Models")
        static let signedInAs = LocalizedStringResource("account.signedInAs", defaultValue: "Signed in as")
        static let name = LocalizedStringResource("account.name", defaultValue: "Name")
        static let plan = LocalizedStringResource("account.plan", defaultValue: "Plan")
        static let key = LocalizedStringResource("account.key", defaultValue: "Key")
        static let keyName = LocalizedStringResource("account.keyName", defaultValue: "Key name")
        static let notShared = LocalizedStringResource("account.notShared", defaultValue: "Not shared")
        static let thisWeek = LocalizedStringResource("account.thisWeek", defaultValue: "Mode on your plan · this week")
        static let today = LocalizedStringResource("account.today", defaultValue: "Today")
        static let tasksThisWeek = LocalizedStringResource("account.tasksThisWeek", defaultValue: "Tasks this week")
        static let planLimit = LocalizedStringResource("account.planLimit", defaultValue: "Plan limit")
        static let available = LocalizedStringResource("account.available", defaultValue: "Available")
        static let chatGPTNote = LocalizedStringResource("account.chatGPTNote", defaultValue: "Plus shares one 5-hour limit across every app that uses your plan. OpenAI doesn't tell apps what is left: Mode shows what it used, and says so when the limit is reached.")
        static let claudeNote = LocalizedStringResource("account.claudeNote", defaultValue: "From Claude, for your whole plan (Claude apps and Mode together).")
        static let updated = LocalizedStringResource("account.updated", defaultValue: "Updated")
        static let updating = LocalizedStringResource("account.updating", defaultValue: "Updating…")
        static let notYet = LocalizedStringResource("account.notYet", defaultValue: "Not yet")
        static let session = LocalizedStringResource("account.session", defaultValue: "Current session · 5 hours")
        static let week = LocalizedStringResource("account.week", defaultValue: "This week")
        static let modeToday = LocalizedStringResource("account.modeToday", defaultValue: "Mode today")
        static let creditsLeft = LocalizedStringResource("account.creditsLeft", defaultValue: "Credits left")
        static let spentToday = LocalizedStringResource("account.spentToday", defaultValue: "Spent by Mode today")
        static let limitPerTask = LocalizedStringResource("account.limitPerTask", defaultValue: "Limit per task")
        static let seeChatGPTUsage = LocalizedStringResource("account.seeChatGPTUsage", defaultValue: "See usage in ChatGPT")
        static let seeClaudeUsage = LocalizedStringResource("account.seeClaudeUsage", defaultValue: "See usage in Claude")
        static let addCredits = LocalizedStringResource("account.addCredits", defaultValue: "Add credits on OpenRouter")
        static let signOutChatGPT = LocalizedStringResource("account.signOutChatGPT", defaultValue: "Sign out of ChatGPT")
        static let signOutClaude = LocalizedStringResource("account.signOutClaude", defaultValue: "Sign out of Claude")
        static let removeKey = LocalizedStringResource("account.removeKey", defaultValue: "Remove key")
        static let inUse = LocalizedStringResource("account.inUse", defaultValue: "Connected · in use")
        static let notInUse = LocalizedStringResource("account.notInUse", defaultValue: "Connected · not in use")

        static func steps(_ n: Int) -> String {
            n == 1 ? String(localized: "account.steps.one", defaultValue: "1 step") : String(localized: "account.steps", defaultValue: "\(n) steps")
        }

        static func stepsAndTasks(_ steps: Int, _ tasks: Int) -> String {
            String(localized: "account.stepsAndTasks", defaultValue: "\(Account.steps(steps)) · \(tasks) tasks")
        }

        static func use(_ provider: String) -> String {
            String(localized: "account.use", defaultValue: "Use \(provider)")
        }

        static func reachedAt(_ time: String) -> String {
            String(localized: "account.reachedAt", defaultValue: "Reached at \(time)")
        }

        static func used(_ percent: Int) -> String {
            String(localized: "account.used", defaultValue: "\(percent)% used")
        }

        static func resets(_ when: String) -> String {
            String(localized: "account.resets", defaultValue: "Resets \(when)")
        }

        static func ofTotal(_ total: String) -> String {
            String(localized: "account.ofTotal", defaultValue: "of \(total) · from OpenRouter")
        }
    }

    enum ChatGPT {
        static let title = LocalizedStringResource("chatgpt.title", defaultValue: "Continue with ChatGPT")
        static let subtitle = LocalizedStringResource("chatgpt.subtitle", defaultValue: "Sign in with your ChatGPT account and allow Mode to use your plan. You come straight back here.")
        static let continueWith = LocalizedStringResource("chatgpt.continue", defaultValue: "Continue with ChatGPT")
        static let waiting = LocalizedStringResource("chatgpt.waiting", defaultValue: "Waiting for ChatGPT…")
        static let waitingHint = LocalizedStringResource("chatgpt.waitingHint", defaultValue: "Finish signing in on the ChatGPT page. Mode never sees your password.")
        static let checking = LocalizedStringResource("chatgpt.checking", defaultValue: "Checking your plan…")
        static let connected = LocalizedStringResource("chatgpt.connected", defaultValue: "Connected to ChatGPT")
        static let notConnected = LocalizedStringResource("chatgpt.notConnected", defaultValue: "Not connected")
        static let planHint = LocalizedStringResource("chatgpt.planHint", defaultValue: "Uses your ChatGPT Plus or Pro plan. No extra cost per task.")
        static let models = LocalizedStringResource("chatgpt.models", defaultValue: "Models on your plan")
        static let notEligible = LocalizedStringResource("chatgpt.notEligible", defaultValue: "Your plan can't be used here")
        static let failed = LocalizedStringResource("chatgpt.failed", defaultValue: "Couldn't connect")
        static let check = LocalizedStringResource("chatgpt.check", defaultValue: "Check again")
        static let signOut = LocalizedStringResource("chatgpt.signOut", defaultValue: "Sign out of ChatGPT")
        static let usageNote = LocalizedStringResource("chatgpt.usageNote", defaultValue: "Change how much of your plan Mode may use in ChatGPT → Settings → Usage.")
    }

    enum Claude {
        static let check = LocalizedStringResource("claude.check", defaultValue: "Test Claude connection")
        static let checking = LocalizedStringResource("claude.checking", defaultValue: "Checking image and action…")
        static let title = LocalizedStringResource("claude.title", defaultValue: "Claude account · Experimental")
        static let use = LocalizedStringResource("claude.use", defaultValue: "Use Claude OAuth")
        static let connect = LocalizedStringResource("claude.connect", defaultValue: "Sign in with Claude")
        static let reconnect = LocalizedStringResource("claude.reconnect", defaultValue: "Sign in again")
        static let disconnect = LocalizedStringResource("claude.disconnect", defaultValue: "Disconnect Claude")
        static let connected = LocalizedStringResource("claude.connected", defaultValue: "Claude account connected")
        static let notConnected = LocalizedStringResource("claude.notConnected", defaultValue: "Claude account not connected")
        static let note = LocalizedStringResource("claude.note", defaultValue: "Connects directly from this iPhone. This unofficial integration may be refused or billed as extra usage by Claude. Mode cannot verify the billing allowance.")
        static let code = LocalizedStringResource("claude.code", defaultValue: "Authorization code")
        static let codePrompt = LocalizedStringResource("claude.codePrompt", defaultValue: "Paste the complete code, including #…")
        static let instructions = LocalizedStringResource("claude.instructions", defaultValue: "Finish signing in in your browser, copy the code Claude shows, then return here and paste it. The code expires after 10 minutes.")
        static let finish = LocalizedStringResource("claude.finish", defaultValue: "Connect account")
        static let cancel = LocalizedStringResource("claude.cancel", defaultValue: "Cancel sign-in")
        static let connecting = LocalizedStringResource("claude.connecting", defaultValue: "Connecting…")
        static let quota = LocalizedStringResource("claude.quota", defaultValue: "Claude manages your quota. Dollar costs are not reported; Mode's dollar limits apply only to OpenRouter. Step and time limits still apply.")
        static let modelDetail = LocalizedStringResource("claude.modelDetail", defaultValue: "Claude account · availability depends on your plan")
    }

    enum Talk {
        static let listening = LocalizedStringResource("talk.listening", defaultValue: "Listening")
        static let speaking = LocalizedStringResource("talk.speaking", defaultValue: "Mode is speaking")
        static let working = LocalizedStringResource("talk.working", defaultValue: "Working")
        static let needsYou = LocalizedStringResource("talk.needsYou", defaultValue: "Mode needs you")
        static let gotIt = LocalizedStringResource("talk.gotIt", defaultValue: "Got it")
        static let startMode = LocalizedStringResource("talk.startMode", defaultValue: "Start Mode")
        static let typeIt = LocalizedStringResource("talk.typeIt", defaultValue: "Type it")
        static let hearingLive = LocalizedStringResource("talk.hearingLive", defaultValue: "Hearing you live")
        static let sayIt = LocalizedStringResource("talk.sayIt", defaultValue: "Say what you want done")
        static let wordsAppear = LocalizedStringResource("talk.wordsAppear", defaultValue: "Your words appear as you speak. Mode stops when you pause.")
        static let micOpensAfter = LocalizedStringResource("talk.micOpensAfter", defaultValue: "The mic opens as soon as Mode stops talking.")
        static let type = LocalizedStringResource("talk.type", defaultValue: "Type")
        static let cancel = LocalizedStringResource("talk.cancel", defaultValue: "Cancel")
        static let done = LocalizedStringResource("talk.done", defaultValue: "Done")
        static let later = LocalizedStringResource("talk.later", defaultValue: "Later")
        static let speak = LocalizedStringResource("talk.speak", defaultValue: "Speak")
        static let stop = LocalizedStringResource("talk.stop", defaultValue: "Stop")
        static let close = LocalizedStringResource("talk.close", defaultValue: "Close")
        static let workingOnIt = LocalizedStringResource("talk.workingOnIt", defaultValue: "Working on it")
        static let inBackground = LocalizedStringResource("talk.inBackground", defaultValue: "Mode works in the background")
        static let ifNeeded = LocalizedStringResource("talk.ifNeeded", defaultValue: "If Mode needs you, it comes back here and asks out loud. You answer by voice and it carries on.")
        static let sayOrTap = LocalizedStringResource("talk.sayOrTap", defaultValue: "Say your answer, or tap one.")
        static let sayAnswer = LocalizedStringResource("talk.sayAnswer", defaultValue: "Say your answer.")
        static let leavesSoon = LocalizedStringResource("talk.leavesSoon", defaultValue: "Mode goes back to the app by itself in a moment.")
        static let modeLabel = LocalizedStringResource("talk.modeLabel", defaultValue: "MODE")
        static let modeSpeaking = LocalizedStringResource("talk.modeSpeaking", defaultValue: "MODE · SPEAKING")
        static let micTitle = LocalizedStringResource("talk.micTitle", defaultValue: "Let Mode hear you")
        static let micMessage = LocalizedStringResource("talk.micMessage", defaultValue: "Allow the microphone once. Mode listens only after you say “Start Mode” or tap the mic, and Apple turns your words into text on this iPhone.")
        static let micModel = LocalizedStringResource("talk.micModel", defaultValue: "First time only: Apple downloads its speech model.")
        static let micDenied = LocalizedStringResource("talk.micDenied", defaultValue: "The microphone is off for Mode. Turn it on in iPhone Settings → Mode.")
        static let allowMic = LocalizedStringResource("talk.allowMic", defaultValue: "Allow microphone")
        static let openSettings = LocalizedStringResource("talk.openSettings", defaultValue: "Open Settings")
        static let typeInstead = LocalizedStringResource("talk.typeInstead", defaultValue: "Type instead")
        static let typeTask = LocalizedStringResource("talk.typeTask", defaultValue: "Type what you want done")
        static let typeAnswer = LocalizedStringResource("talk.typeAnswer", defaultValue: "Type your answer")
        static let send = LocalizedStringResource("talk.send", defaultValue: "Send")
    }

    enum Voice {
        static let speech = LocalizedStringResource("voice.speech", defaultValue: "Speech")
        static let hearing = LocalizedStringResource("voice.hearing", defaultValue: "Hearing")
        static let speechInUse = LocalizedStringResource("voice.speechInUse", defaultValue: "Speech in use")
        static let hearingInUse = LocalizedStringResource("voice.hearingInUse", defaultValue: "Hearing in use")
        static let apple = LocalizedStringResource("voice.apple", defaultValue: "Apple")
        static let fish = LocalizedStringResource("voice.fish", defaultValue: "Fish Audio")
        static let eleven = LocalizedStringResource("voice.eleven", defaultValue: "ElevenLabs")
        static let soniox = LocalizedStringResource("voice.soniox", defaultValue: "Soniox")
        static let deepgram = LocalizedStringResource("voice.deepgram", defaultValue: "Deepgram")
        static let ready = LocalizedStringResource("voice.ready", defaultValue: "Ready")
        static let first = LocalizedStringResource("voice.first", defaultValue: "First")
        static let noKey = LocalizedStringResource("voice.noKey", defaultValue: "Add a key")
        static let permission = LocalizedStringResource("voice.permission", defaultValue: "Microphone permission needed")
        static let denied = LocalizedStringResource("voice.denied", defaultValue: "Microphone is off")
        static let unsupported = LocalizedStringResource("voice.unsupported", defaultValue: "Not available on this iPhone")
        static let asset = LocalizedStringResource("voice.asset", defaultValue: "Downloading the speech model")
        static let moveUp = LocalizedStringResource("voice.moveUp", defaultValue: "Move up")
        static let turnOn = LocalizedStringResource("voice.turnOn", defaultValue: "Turn on")
        static let openSettings = LocalizedStringResource("voice.openSettings", defaultValue: "Open Settings")
        static let key = LocalizedStringResource("voice.key", defaultValue: "API key")
        static let elevenNote = LocalizedStringResource("voice.elevenNote", defaultValue: "The free plan is for trying Mode on your own phone. A shipped app needs ElevenLabs' paid plan.")
        static let listening = LocalizedStringResource("voice.listening", defaultValue: "Listening")
        static let listeningHint = LocalizedStringResource("voice.listeningHint", defaultValue: "Tell Mode what to do. It will say what it will do, then start.")
        static let typeInstead = LocalizedStringResource("voice.typeInstead", defaultValue: "Type it instead")
        static let send = LocalizedStringResource("voice.send", defaultValue: "Send")
        // Settings → Voice
        static let section = LocalizedStringResource("voice.section", defaultValue: "Voice")
        static let modesVoice = LocalizedStringResource("voice.modesVoice", defaultValue: "Mode's voice")
        static let listeningRow = LocalizedStringResource("voice.listeningRow", defaultValue: "Listening")
        static let onIPhone = LocalizedStringResource("voice.onIPhone", defaultValue: "On iPhone")
        static let off = LocalizedStringResource("voice.off", defaultValue: "Off")
        static let talkBack = LocalizedStringResource("voice.talkBack", defaultValue: "Talk back")
        static let talkBackDetail = LocalizedStringResource("voice.talkBackDetail", defaultValue: "On tasks you start by voice")
        static let sectionNote = LocalizedStringResource("voice.sectionNote", defaultValue: "Mode picks the most human voice and the most accurate listening it can reach. Apple’s are free, private and always there to fall back on.")
        static let defaultVoice = LocalizedStringResource("voice.defaultVoice", defaultValue: "Default voice")
        // Mode's voice
        static let voiceSubtitle = LocalizedStringResource("voice.voiceSubtitle", defaultValue: "What Mode sounds like when it talks back.")
        static let sampleLine = LocalizedStringResource("voice.sampleLine", defaultValue: "“I’ll turn on Dark Mode.”")
        static let changeVoice = LocalizedStringResource("voice.changeVoice", defaultValue: "Change voice")
        static let whoSpeaks = LocalizedStringResource("voice.whoSpeaks", defaultValue: "Who speaks first")
        static let whoSpeaksNote = LocalizedStringResource("voice.whoSpeaksNote", defaultValue: "Mode speaks with the one you pick, and only that one. If it can’t, Mode tells you and shows how to fix it. Tap a connected one to edit or remove its key.")
        static let speaking = LocalizedStringResource("voice.speaking", defaultValue: "Speaking")
        static let speed = LocalizedStringResource("voice.speed", defaultValue: "Speed")
        static let slower = LocalizedStringResource("voice.slower", defaultValue: "Slower")
        static let normal = LocalizedStringResource("voice.normal", defaultValue: "Normal")
        static let faster = LocalizedStringResource("voice.faster", defaultValue: "Faster")
        static let readResult = LocalizedStringResource("voice.readResult", defaultValue: "Read the result aloud")
        static let readResultDetail = LocalizedStringResource("voice.readResultDetail", defaultValue: "A short summary; the full answer stays in the chat")
        static let inUse = LocalizedStringResource("voice.inUse", defaultValue: "In use")
        static let addKey = LocalizedStringResource("voice.addKey", defaultValue: "Add key")
        static let removeKey = LocalizedStringResource("voice.removeKey", defaultValue: "Remove key")
        static let downloading = LocalizedStringResource("voice.downloading", defaultValue: "Downloading")
        static let appleSpeech = LocalizedStringResource("voice.appleSpeech", defaultValue: "Free · on this iPhone · works offline")
        static let elevenFree = LocalizedStringResource("voice.elevenFree", defaultValue: "Free plan: 10,000 credits a month")
        static let fishFree = LocalizedStringResource("voice.fishFree", defaultValue: "Free plan: about 7 minutes of speech a month")
        static let appleHearing = LocalizedStringResource("voice.appleHearing", defaultValue: "On this iPhone · private · free")
        static let sonioxFree = LocalizedStringResource("voice.sonioxFree", defaultValue: "Free credit to start · strong with Hinglish")
        static let deepgramFree = LocalizedStringResource("voice.deepgramFree", defaultValue: "$200 free credit to start")
        static func withKey(_ line: String, _ ending: String) -> String {
            String(localized: "voice.withKey", defaultValue: "\(line) · key …\(ending)")
        }
        // Listening
        static let listeningSubtitle = LocalizedStringResource("voice.listeningSubtitle", defaultValue: "How Mode turns what you say into a task.")
        static let turnOnApple = LocalizedStringResource("voice.turnOnApple", defaultValue: "Allow the microphone and Mode downloads Apple’s speech model.")
        static let micOff = LocalizedStringResource("voice.micOff", defaultValue: "The microphone is off for Mode in iPhone Settings.")
        static let listeningNote = LocalizedStringResource("voice.listeningNote", defaultValue: "Mode listens with the one you pick, and only that one. Apple’s words show while you speak either way.")
        static let privacy = LocalizedStringResource("voice.privacy", defaultValue: "Your privacy")
        static let privacyListen = LocalizedStringResource("voice.privacyListen", defaultValue: "Mode listens only after you say “Start Mode” or tap the mic, and stops when you stop talking.")
        static let privacyWhere = LocalizedStringResource("voice.privacyWhere", defaultValue: "With Apple, nothing leaves your iPhone. With Soniox or Deepgram, the clip goes to them only.")
        static let privacyType = LocalizedStringResource("voice.privacyType", defaultValue: "If nothing can listen, you type the task instead.")
        // Choose a voice
        static let chooseVoice = LocalizedStringResource("voice.chooseVoice", defaultValue: "Choose a voice")
        static let searchVoices = LocalizedStringResource("voice.searchVoices", defaultValue: "Search voices")
        static let all = LocalizedStringResource("voice.all", defaultValue: "All")
        static let popular = LocalizedStringResource("voice.popular", defaultValue: "Popular")
        static let appleBanner = LocalizedStringResource("voice.appleBanner", defaultValue: "Free and private. These voices run on your iPhone, even offline. Get more in iPhone Settings → Accessibility → Spoken Content → Voices.")
        static let elevenBanner = LocalizedStringResource("voice.elevenBanner", defaultValue: "Free plan: 10,000 credits a month, about 10 minutes of Mode talking. For personal use. Voices from your ElevenLabs library.")
        static let fishBanner = LocalizedStringResource("voice.fishBanner", defaultValue: "Free plan: 8,000 credits a month, about 7 minutes of Mode talking. Voices shared by the Fish Audio community, including Hindi.")
        static func needsKey(_ provider: String) -> String {
            String(localized: "voice.needsKey", defaultValue: "Add your \(provider) key to see its voices. The free plan is enough.")
        }
        static let couldNotLoad = LocalizedStringResource("voice.couldNotLoad", defaultValue: "Couldn’t load the voices. Check the key, then try again.")
        static let tryAgain = LocalizedStringResource("voice.tryAgain", defaultValue: "Try again")
        static let noMatch = LocalizedStringResource("voice.noMatch", defaultValue: "No voice matches. Try another word.")
        static func use(_ name: String) -> String { String(localized: "voice.use", defaultValue: "Use \(name)") }
        static func isSpeaking(_ name: String) -> String { String(localized: "voice.isSpeaking", defaultValue: "\(name) is speaking") }
        static let pickerNote = LocalizedStringResource("voice.pickerNote", defaultValue: "Tap a voice to choose it. Tap ▶ to hear it; tap again to stop.")
        static func play(_ name: String) -> String { String(localized: "voice.play", defaultValue: "Play \(name)") }
        // Connect a key
        static func connect(_ provider: String) -> String { String(localized: "voice.connect", defaultValue: "Connect \(provider)") }
        static let speechPurpose = LocalizedStringResource("voice.speechPurpose", defaultValue: "Lifelike voices for Mode’s replies")
        static let hearingPurpose = LocalizedStringResource("voice.hearingPurpose", defaultValue: "Turns what you say into a task")
        static func stepSignIn(_ provider: String) -> String {
            String(localized: "voice.stepSignIn", defaultValue: "Sign in to \(provider). The free plan is enough.")
        }
        static let stepCreateKey = LocalizedStringResource("voice.stepCreateKey", defaultValue: "Open its API keys page and create a key.")
        static let stepPaste = LocalizedStringResource("voice.stepPaste", defaultValue: "Copy it and paste it here.")
        static func open(_ provider: String) -> String { String(localized: "voice.open", defaultValue: "Open \(provider)") }
        static func keyField(_ provider: String) -> String { String(localized: "voice.keyField", defaultValue: "\(provider) API key") }
        static func keyNote(_ provider: String) -> String {
            String(localized: "voice.keyNote", defaultValue: "The key stays in this iPhone’s Keychain and only goes to \(provider).")
        }
        static let connectButton = LocalizedStringResource("voice.connectButton", defaultValue: "Connect")
        // Start Mode
        static let gotIt = LocalizedStringResource("voice.gotIt", defaultValue: "Got it")
        static let sayIt = LocalizedStringResource("voice.sayIt", defaultValue: "Say what you want done")
        static let stopsWhenQuiet = LocalizedStringResource("voice.stopsWhenQuiet", defaultValue: "Mode stops listening when you stop talking.")
        static let done = LocalizedStringResource("voice.done", defaultValue: "Done")
        static let stop = LocalizedStringResource("voice.stop", defaultValue: "Stop")
        static let close = LocalizedStringResource("voice.close", defaultValue: "Close")
        static let carriesOn = LocalizedStringResource("voice.carriesOn", defaultValue: "Mode carries on in the background. Follow it on the Lock Screen.")
        static let typeTask = LocalizedStringResource("voice.typeTask", defaultValue: "Type what you want done")
        static func listeningWith(_ provider: String) -> String { String(localized: "voice.listeningWith", defaultValue: "\(provider) · listening") }
        static func speakingAs(_ name: String) -> String { String(localized: "voice.speakingAs", defaultValue: "Speaking as \(name)") }
        // Setup
        static let setupTitle = LocalizedStringResource("voice.setupTitle", defaultValue: "Talk to Mode")
        static let setupMessage = LocalizedStringResource("voice.setupMessage", defaultValue: "Say “Start Mode, order my usual”. Mode answers out loud and asks what it needs to know.")
        static let setupMore = LocalizedStringResource("voice.setupMore", defaultValue: "More voices, free with ElevenLabs or Fish Audio, in Settings → Voice.")
        static let listeningWithApple = LocalizedStringResource("voice.listeningWithApple", defaultValue: "Listening with Apple")
        static let freeAndPrivate = LocalizedStringResource("voice.freeAndPrivate", defaultValue: "On this iPhone, free and private")
        // Who speaks and who listens, with Mode's recommendations (boards SetupVoiceProviders, SetupSpeakPicker, SetupListenPicker)
        static let mostHuman = LocalizedStringResource("voice.mostHuman", defaultValue: "Most human")
        static let freePrivate = LocalizedStringResource("voice.freePrivate", defaultValue: "Free and private")
        // One chosen provider, keys checked before saving, failures with a fix (boards Voice, VoiceListening, VoiceProviderPage, VoiceKey*, VoiceProviderFailed)
        static func connectedKey(_ masked: String) -> String { String(localized: "voice.connectedKey", defaultValue: "Connected · \(masked)") }
        static func notConnected(_ plan: String) -> String { String(localized: "voice.notConnected", defaultValue: "Not connected · \(plan)") }
        static let whoListens = LocalizedStringResource("voice.whoListens", defaultValue: "Who listens")
        static let keysNote = LocalizedStringResource("voice.keysNote", defaultValue: "Keys stay in this iPhone’s Keychain. They only go to that provider, never to us.")
        static let couldntSpeak = LocalizedStringResource("voice.couldntSpeak", defaultValue: "Couldn’t speak")
        static let couldntHear = LocalizedStringResource("voice.couldntHear", defaultValue: "Couldn’t hear")
        static let troubleNoKey = LocalizedStringResource("voice.troubleNoKey", defaultValue: "no key yet")
        static let troubleKey = LocalizedStringResource("voice.troubleKey", defaultValue: "the key was refused")
        static let troublePlanSpeak = LocalizedStringResource("voice.troublePlanSpeak", defaultValue: "your plan doesn’t include this voice")
        static let troublePlanHear = LocalizedStringResource("voice.troublePlanHear", defaultValue: "your plan’s allowance ran out")
        static let troubleNotReady = LocalizedStringResource("voice.troubleNotReady", defaultValue: "not ready on this iPhone")
        static let troubleUnreachable = LocalizedStringResource("voice.troubleUnreachable", defaultValue: "no answer")
        static func troubleKeyLong(_ p: String) -> String { String(localized: "voice.troubleKeyLong", defaultValue: "\(p) refused the key, so Mode stayed quiet. Update the key, or use Apple.") }
        static func troublePlanSpeakLong(_ p: String) -> String { String(localized: "voice.troublePlanSpeakLong", defaultValue: "Your \(p) plan doesn’t include this voice, so Mode stayed quiet. Pick a voice your plan has, update the key, or speak with Apple.") }
        static func troublePlanHearLong(_ p: String) -> String { String(localized: "voice.troublePlanHearLong", defaultValue: "Your \(p) allowance ran out, so Mode didn’t start anything. Update the key, or listen with Apple.") }
        static func troubleNoKeyLong(_ p: String) -> String { String(localized: "voice.troubleNoKeyLong", defaultValue: "There’s no \(p) key on this iPhone. Add one, or use Apple.") }
        static let troubleNotReadyLong = LocalizedStringResource("voice.troubleNotReadyLong", defaultValue: "Apple’s voice or listening isn’t ready on this iPhone yet.")
        static func troubleUnreachableLong(_ p: String) -> String { String(localized: "voice.troubleUnreachableLong", defaultValue: "\(p) didn’t answer, so Mode stayed quiet. Check the internet, update the key, or use Apple.") }
        static func keyRejected(_ p: String) -> String { String(localized: "voice.keyRejected", defaultValue: "\(p) said the key is not valid. Copy it again — keys are long and easy to cut short.") }
        static func keyPlan(_ p: String) -> String { String(localized: "voice.keyPlan", defaultValue: "\(p) knows the key, but its plan doesn’t allow this. Check your plan on \(p).") }
        static let keyEmpty = LocalizedStringResource("voice.keyEmpty", defaultValue: "Paste the key first.")
        static func keyUnreachable(_ p: String) -> String { String(localized: "voice.keyUnreachable", defaultValue: "Couldn’t reach \(p) to check the key. Check the internet and try again.") }
        static func inUseFailed(_ reason: String) -> String { String(localized: "voice.inUseFailed", defaultValue: "In use · \(reason)") }
        static let inUseSpeaking = LocalizedStringResource("voice.inUseSpeaking", defaultValue: "Connected · in use for Mode’s voice")
        static let inUseHearing = LocalizedStringResource("voice.inUseHearing", defaultValue: "Connected · in use for listening")
        static func couldntSpeakTitle(_ p: String) -> String { String(localized: "voice.couldntSpeakTitle", defaultValue: "\(p) couldn’t speak") }
        static func couldntHearTitle(_ p: String) -> String { String(localized: "voice.couldntHearTitle", defaultValue: "\(p) couldn’t hear") }
        static let chooseAnotherVoice = LocalizedStringResource("voice.chooseAnotherVoice", defaultValue: "Choose another voice")
        static let updateKey = LocalizedStringResource("voice.updateKey", defaultValue: "Update key")
        static let useAppleSpeaking = LocalizedStringResource("voice.useAppleSpeaking", defaultValue: "Use Apple instead")
        static let useAppleHearing = LocalizedStringResource("voice.useAppleHearing", defaultValue: "Listen with Apple instead")
        static let keySection = LocalizedStringResource("voice.keySection", defaultValue: "Key")
        static let editKey = LocalizedStringResource("voice.editKey", defaultValue: "Edit key")
        static func speakWith(_ p: String) -> String { String(localized: "voice.speakWith", defaultValue: "Speak with \(p)") }
        static func listenWith(_ p: String) -> String { String(localized: "voice.listenWith", defaultValue: "Listen with \(p)") }
        static func removeNote(_ p: String) -> String { String(localized: "voice.removeNote", defaultValue: "Removing the key here leaves your \(p) account as it is. Apple takes over if it was in use.") }
        static func editKeyTitle(_ p: String) -> String { String(localized: "voice.editKeyTitle", defaultValue: "Edit \(p) key") }
        static func inUseNowKey(_ masked: String) -> String { String(localized: "voice.inUseNowKey", defaultValue: "In use now: \(masked). Paste a new key to replace it.") }
        static func savedConnected(_ p: String) -> String { String(localized: "voice.savedConnected", defaultValue: "Saved · \(p) is connected and in use") }
        static let keyDidntWork = LocalizedStringResource("voice.keyDidntWork", defaultValue: "That key didn’t work")
        static func checkingKey(_ p: String) -> String { String(localized: "voice.checkingKey", defaultValue: "Checking your key with \(p)…") }
        static let checking = LocalizedStringResource("voice.checking", defaultValue: "Checking…")
        static let checkAgain = LocalizedStringResource("voice.checkAgain", defaultValue: "Check again")
        static let checkAndSave = LocalizedStringResource("voice.checkAndSave", defaultValue: "Check and save")
        static func couldntSpeakWith(_ p: String) -> String { String(localized: "voice.couldntSpeakWith", defaultValue: "Mode couldn’t speak with \(p)") }
        static func couldntHearWith(_ p: String) -> String { String(localized: "voice.couldntHearWith", defaultValue: "Mode couldn’t hear with \(p)") }
        static let updateInSettings = LocalizedStringResource("voice.updateInSettings", defaultValue: "Update in Settings")
        static let ourPick = LocalizedStringResource("voice.ourPick", defaultValue: "Our pick")
        static let speakSubtitle = LocalizedStringResource("voice.speakSubtitle", defaultValue: "Mode speaks with the one you pick.")
        static let listenTitle = LocalizedStringResource("voice.listenTitle", defaultValue: "How Mode listens")
        static let listenSubtitle = LocalizedStringResource("voice.listenSubtitle", defaultValue: "Mode listens with the one you pick.")
        static let connected = LocalizedStringResource("voice.connected", defaultValue: "Connected")
        static let needsKey = LocalizedStringResource("voice.needsKey", defaultValue: "needs a key")
        static let connectKey = LocalizedStringResource("voice.connectKey", defaultValue: "Connect")
        static let suggestDeepgram = LocalizedStringResource("voice.suggestDeepgram", defaultValue: "Most accurate: Deepgram")
        static let suggestDeepgramDetail = LocalizedStringResource("voice.suggestDeepgramDetail", defaultValue: "Free credit to start · Apple listens until then")
        static let whyDeepgramTitle = LocalizedStringResource("voice.whyDeepgramTitle", defaultValue: "Why Deepgram")
        static let whyDeepgram = LocalizedStringResource("voice.whyDeepgram", defaultValue: "Catches names, numbers and mixed languages best, even in a noisy room. Apple is private and free, and keeps listening if Deepgram can’t.")
        static func voicesFrom(_ provider: String) -> String { String(localized: "voice.voicesFrom", defaultValue: "Voices from \(provider)") }
        static func speakFallback(_ provider: String) -> String { String(localized: "voice.speakFallback", defaultValue: "If \(provider) can’t start, Apple speaks instead.") }
        static func useVoice(_ name: String) -> String { String(localized: "voice.useVoice", defaultValue: "Use \(name)") }
        static let allowMic = LocalizedStringResource("voice.allowMic", defaultValue: "Allow mic")
        static let notNow = LocalizedStringResource("voice.notNow", defaultValue: "Not now")
        // Apple voice traits
        static let female = LocalizedStringResource("voice.female", defaultValue: "Female")
        static let male = LocalizedStringResource("voice.male", defaultValue: "Male")
        static let premium = LocalizedStringResource("voice.premium", defaultValue: "Premium")
        static let enhanced = LocalizedStringResource("voice.enhanced", defaultValue: "Enhanced")
        static let personalVoice = LocalizedStringResource("voice.personalVoice", defaultValue: "Your Personal Voice")
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
        static let whenStuck = LocalizedStringResource("settings.whenStuck", defaultValue: "Brain")
        static let never = LocalizedStringResource("settings.never", defaultValue: "Never use a stronger model")
        static let search = LocalizedStringResource("settings.search", defaultValue: "Search all models")
        static let limits = LocalizedStringResource("settings.limits", defaultValue: "Limits")
        static let maxCost = LocalizedStringResource("settings.maxCost", defaultValue: "Most a task may cost")
        static let dailyCap = LocalizedStringResource("settings.dailyCap", defaultValue: "Daily limit")
        static let dailyCapOff = LocalizedStringResource("settings.dailyCapOff", defaultValue: "No daily limit")
        static let memory = LocalizedStringResource("settings.memory", defaultValue: "Memory")
        static let ai = LocalizedStringResource("settings.ai", defaultValue: "AI")
        static let addName = LocalizedStringResource("settings.addName", defaultValue: "Add")
        static let mainModel = LocalizedStringResource("settings.mainModel", defaultValue: "Phone control")
        static let whenStuckShort = LocalizedStringResource("settings.whenStuckShort", defaultValue: "Brain")
        static let off = LocalizedStringResource("settings.off", defaultValue: "Off")
        static let onYourPlan = LocalizedStringResource("settings.onYourPlan", defaultValue: "on your plan")
        static let all = LocalizedStringResource("settings.all", defaultValue: "All")
        static let neverDetail = LocalizedStringResource("settings.neverDetail", defaultValue: "Phone control does the thinking too")

        static func searchCount(_ n: Int) -> String {
            n > 0 ? String(localized: "settings.searchCount", defaultValue: "Search \(n) models") : String(localized: L10n.Settings.search)
        }
        static let noModels = LocalizedStringResource("settings.noModels", defaultValue: "No model matches that. Try a shorter name or another maker.")
        static let pickerNote = LocalizedStringResource("settings.pickerNote", defaultValue: "Only models that can see the screen and use tools. Prices are OpenRouter's, for a typical task of about 10 steps.")
        static let pickerPlanNote = LocalizedStringResource("settings.pickerPlanNote", defaultValue: "These run on your plan, so there is no cost per task.")
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
        static func step(_ n: Int, of total: Int) -> String {
            String(localized: "setup.step", defaultValue: "Step \(n) of \(total)")
        }

        // Welcome
        static let appName = LocalizedStringResource("setup.welcome.appName", defaultValue: "Mode")
        static let tagline = LocalizedStringResource("setup.welcome.tagline", defaultValue: "Tell your iPhone what to do.\nMode does it in your apps.")
        static let pillOpenSource = LocalizedStringResource("setup.welcome.openSource", defaultValue: "Open source")
        static let pillOwnKey = LocalizedStringResource("setup.welcome.ownKey", defaultValue: "Your own AI")
        static let pillOnPhone = LocalizedStringResource("setup.welcome.onPhone", defaultValue: "Runs on your iPhone")
        static let thisIPhone = LocalizedStringResource("setup.welcome.thisIPhone", defaultValue: "This iPhone")
        static let paired = LocalizedStringResource("setup.welcome.paired", defaultValue: "Paired with Mode Setup on your Mac")
        static let notPaired = LocalizedStringResource("setup.welcome.notPaired", defaultValue: "Not paired yet: pair once from your Mac with Mode Setup")
        static let stepName = LocalizedStringResource("setup.welcome.stepName", defaultValue: "Your name")
        static let stepNameDetail = LocalizedStringResource("setup.welcome.stepNameDetail", defaultValue: "From your contact card, one tap")
        static let stepAI = LocalizedStringResource("setup.welcome.stepAI", defaultValue: "Your AI")
        static let stepAIDetail = LocalizedStringResource("setup.welcome.stepAIDetail", defaultValue: "ChatGPT, Claude or OpenRouter, and a model")
        static let stepTest = LocalizedStringResource("setup.welcome.stepTest", defaultValue: "Permissions and a quick test")
        static let stepTestDetail = LocalizedStringResource("setup.welcome.stepTestDetail", defaultValue: "Mode opens Settings, taps Wi-Fi and comes back")
        static let getStarted = LocalizedStringResource("setup.welcome.getStarted", defaultValue: "Get started")
        static let seeCode = LocalizedStringResource("setup.welcome.seeCode", defaultValue: "See the code on GitHub")

        // Name
        static let nameTitle = LocalizedStringResource("setup.name.title", defaultValue: "What should Mode call you?")
        static let nameMessage = LocalizedStringResource("setup.name.message", defaultValue: "Mode uses your name when it writes messages or fills in forms for you.")
        static let fromCard = LocalizedStringResource("setup.name.fromCard", defaultValue: "From your contact card")
        static let callYou = LocalizedStringResource("setup.name.callYou", defaultValue: "Mode will call you")
        static let callYouPrompt = LocalizedStringResource("setup.name.callYouPrompt", defaultValue: "Your first name")
        static let chooseCard = LocalizedStringResource("setup.name.chooseCard", defaultValue: "Choose your card")
        static let chooseDifferentCard = LocalizedStringResource("setup.name.chooseDifferent", defaultValue: "Choose a different card")
        static let contactsNote = LocalizedStringResource("setup.name.note", defaultValue: "Mode doesn't read your contacts. It only sees the one card you pick, and keeps your name on this iPhone.")

        // Key
        static let keyTitle = LocalizedStringResource("setup.key.title", defaultValue: "Connect your AI")
        static let keyMessage = LocalizedStringResource("setup.key.message", defaultValue: "Mode is open source. You bring your own AI through OpenRouter and pay them directly, usually less than a cent a task.")
        static let connect = LocalizedStringResource("setup.key.connect", defaultValue: "Connect OpenRouter")
        static let connectNote = LocalizedStringResource("setup.key.connectNote", defaultValue: "Sign in or create a free account, then come straight back. No copying keys.")
        static let connected = LocalizedStringResource("setup.key.connected", defaultValue: "Connected to OpenRouter")
        static let savedKey = LocalizedStringResource("setup.key.saved", defaultValue: "A key is already saved. Continue to keep it.")
        static let or = LocalizedStringResource("setup.key.or", defaultValue: "or")
        static let keyLabel = LocalizedStringResource("setup.key.label", defaultValue: "OpenRouter key")
        static let keyPrompt = LocalizedStringResource("setup.key.prompt", defaultValue: "sk-or-…")
        static let paste = LocalizedStringResource("setup.key.paste", defaultValue: "Paste")
        static let keyNote = LocalizedStringResource("setup.key.note", defaultValue: "Your key stays in this iPhone's Keychain. It only goes to OpenRouter, never to us.")
        static let keyLooksWrong = LocalizedStringResource("setup.key.looksWrong", defaultValue: "OpenRouter keys start with sk-or-")

        // Model
        static let modelTitle = LocalizedStringResource("setup.model.title", defaultValue: "Pick how Mode thinks")
        static let modelMessage = LocalizedStringResource("setup.model.message", defaultValue: "Mode's recommended picks. Change them anytime in Settings.")
        static let phoneControl = LocalizedStringResource("setup.model.phone", defaultValue: "Phone control")
        static let phoneDetail = LocalizedStringResource("setup.model.phoneDetail", defaultValue: "Reads the screen and taps, every step")
        static let phoneWhy = LocalizedStringResource("setup.model.phoneWhy", defaultValue: "Fastest that stays accurate on your screens")
        static let brainWhy = LocalizedStringResource("setup.model.brainWhy", defaultValue: "Most accurate at working out what to do next")
        static let whyThese = LocalizedStringResource("setup.model.why", defaultValue: "Why these: a quick model on Medium for every step, and a thorough one on High for the hard ones.")
        static let onPlanNote = LocalizedStringResource("setup.model.onPlan", defaultValue: "Both run on your plan. No cost per task.")
        static let howHard = LocalizedStringResource("setup.model.howHard", defaultValue: "How hard it thinks")
        static let modelSection = LocalizedStringResource("setup.model.section", defaultValue: "Model")
        static func effortHint(_ recommended: String) -> String {
            String(localized: "setup.model.effortHint", defaultValue: "Recommended: \(recommended). Medium is quick and careful; High is slower; Low can miss small buttons.")
        }
        static let whenStuck = LocalizedStringResource("setup.model.whenStuck", defaultValue: "Brain")
        static let stuckDetail = LocalizedStringResource("setup.model.stuckDetail", defaultValue: "Plans, thinks through hard steps, checks the result")
        static let stuckOff = LocalizedStringResource("setup.model.stuckOff", defaultValue: "Off: phone control does the thinking too")
        static let allModels = LocalizedStringResource("setup.model.all", defaultValue: "All models on OpenRouter")
        static let search = LocalizedStringResource("setup.model.search", defaultValue: "Search")
        static let pricesNote = LocalizedStringResource("setup.model.prices", defaultValue: "Prices are OpenRouter's, for a typical task of about 10 steps.")

        static func perTask(_ cost: String) -> String {
            String(localized: "setup.model.perTask", defaultValue: "about \(cost) a task")
        }

        // Permissions
        static let permissionsTitle = LocalizedStringResource("setup.permissions.title", defaultValue: "Allow what Mode needs")
        static let permissionsMessage = LocalizedStringResource("setup.permissions.message", defaultValue: "iOS asks for each one. Here's why.")
        static let vpn = LocalizedStringResource("setup.permissions.vpn", defaultValue: "VPN")
        static let vpnWhy = LocalizedStringResource("setup.permissions.vpnWhy", defaultValue: "How Mode reaches its own iPhone. It only goes to this phone itself, so your internet doesn't pass through it.")
        static let notifications = LocalizedStringResource("setup.permissions.notifications", defaultValue: "Notifications")
        static let notificationsWhy = LocalizedStringResource("setup.permissions.notificationsWhy", defaultValue: "When a task is done, or Mode needs you.")
        static let siri = LocalizedStringResource("setup.permissions.siri", defaultValue: "Siri")
        static let siriWhy = LocalizedStringResource("setup.permissions.siriWhy", defaultValue: "Say \u{201C}Hey Siri, start Mode\u{201D}, then what to do. Nothing to allow.")
        static let microphone = LocalizedStringResource("setup.permissions.microphone", defaultValue: "Microphone")
        static let microphoneWhy = LocalizedStringResource("setup.permissions.microphoneWhy", defaultValue: "Asked the first time you speak a task.")
        static let allow = LocalizedStringResource("setup.permissions.allow", defaultValue: "Allow")
        static let allowed = LocalizedStringResource("setup.permissions.allowed", defaultValue: "Allowed")
        static let later = LocalizedStringResource("setup.permissions.later", defaultValue: "Later")
        static let denied = LocalizedStringResource("setup.permissions.denied", defaultValue: "Off in Settings")
        static let unavailable = LocalizedStringResource("setup.permissions.unavailable", defaultValue: "Needs a paid account")

        // Test
        static let testTitle = LocalizedStringResource("setup.test.title", defaultValue: "Let's check Mode works")
        static let testMessage = LocalizedStringResource("setup.test.message", defaultValue: "Mode will try a few things on its own. Don't touch the screen for about 10 seconds.")
        static let openSettings = LocalizedStringResource("setup.test.openSettings", defaultValue: "Open Settings")
        static let tapWifi = LocalizedStringResource("setup.test.tapWifi", defaultValue: "Tap Wi-Fi")
        static let comeBack = LocalizedStringResource("setup.test.comeBack", defaultValue: "Come back here")
        // The board also promises the Dynamic Island showing each step; that line returns with the Live Activity (next step).
        static let testNote = LocalizedStringResource("setup.test.note", defaultValue: "Mode switches to Settings while it works, then comes back here. On mobile data only, turn it off for a moment so Mode can connect.")
        static let runTest = LocalizedStringResource("setup.test.run", defaultValue: "Run the test")

        // Result
        static let resultTitle = LocalizedStringResource("setup.result.title", defaultValue: "Mode can use your iPhone")
        static let resultFailedTitle = LocalizedStringResource("setup.result.failedTitle", defaultValue: "Mode couldn't finish the test")
        static let whatModeDid = LocalizedStringResource("setup.result.whatModeDid", defaultValue: "What Mode did")
        static let didConnect = LocalizedStringResource("setup.result.connected", defaultValue: "Connected")
        static let didOpenSettings = LocalizedStringResource("setup.result.openedSettings", defaultValue: "Opened Settings")
        static let didTapWifi = LocalizedStringResource("setup.result.tappedWifi", defaultValue: "Saw and tapped Wi-Fi")
        static let didComeBack = LocalizedStringResource("setup.result.cameBack", defaultValue: "Came back to Mode")
        static let shotCaption = LocalizedStringResource("setup.result.shotCaption", defaultValue: "Left: what Mode saw on the Wi-Fi page")
        static let question = LocalizedStringResource("setup.result.question", defaultValue: "Did you see Settings open and Wi-Fi get tapped?")
        static let no = LocalizedStringResource("setup.result.no", defaultValue: "No")
        static let itWorked = LocalizedStringResource("setup.result.worked", defaultValue: "Yes, it worked")
        static let runAgain = LocalizedStringResource("setup.result.runAgain", defaultValue: "Run it again")

        // Diagnose
        static let diagnoseTitle = LocalizedStringResource("setup.diagnose.title", defaultValue: "Find the problem")
        static let diagnoseMessage = LocalizedStringResource("setup.diagnose.message", defaultValue: "Mode checks each part in order and stops at the first that fails.")
        static let checkPairingFile = LocalizedStringResource("setup.check.pairingFile", defaultValue: "Pairing file")
        static let checkVpn = LocalizedStringResource("setup.check.vpn", defaultValue: "VPN on")
        static let checkReach = LocalizedStringResource("setup.check.reach", defaultValue: "Reached this iPhone")
        static let checkPaired = LocalizedStringResource("setup.check.paired", defaultValue: "Still paired")
        static let checkDeveloperMode = LocalizedStringResource("setup.check.developerMode", defaultValue: "Developer Mode")
        static let checkDiskImage = LocalizedStringResource("setup.check.diskImage", defaultValue: "Developer disk image")
        static let checkScreen = LocalizedStringResource("setup.check.screen", defaultValue: "Sees the screen")
        static let checkHands = LocalizedStringResource("setup.check.hands", defaultValue: "Taps and types")
        static let notChecked = LocalizedStringResource("setup.check.notChecked", defaultValue: "Not checked")
        static let sendReport = LocalizedStringResource("setup.diagnose.sendReport", defaultValue: "Send report")
        static let checkAgain = LocalizedStringResource("setup.diagnose.checkAgain", defaultValue: "Check again")
        static let continueAnyway = LocalizedStringResource("setup.continueAnyway", defaultValue: "Continue anyway")
    }

    enum Developer {
        static let title = LocalizedStringResource("developer.title", defaultValue: "Developer")
    }
}
