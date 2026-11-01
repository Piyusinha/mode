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
        static let emptyMessage = LocalizedStringResource("home.empty.message", defaultValue: "Tap the microphone and say what to do: \u{201C}Turn off dark mode\u{201D}, \u{201C}Text Sonali I'm on my way\u{201D}.")

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

    enum Developer {
        static let title = LocalizedStringResource("developer.title", defaultValue: "Developer")
    }
}
