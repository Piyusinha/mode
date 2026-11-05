import Foundation

extension L10n {
    /// "Did you know?" on Home for the first days (its board: Home · Did you know).
    enum Tips {
        static let label = LocalizedStringResource("tips.label", defaultValue: "Did you know?")
        static let close = LocalizedStringResource("tips.close", defaultValue: "Don't show tips again")
        static func dot(_ n: Int, of total: Int) -> String {
            String(localized: "tips.dot", defaultValue: "Tip \(n) of \(total)")
        }
        static let hidden = LocalizedStringResource("tips.hidden", defaultValue: "Tips hidden")
        static let hiddenDetail = LocalizedStringResource("tips.hiddenDetail", defaultValue: "They're all in Settings if you want them.")
        static let undo = LocalizedStringResource("tips.undo", defaultValue: "Undo")

        static let voiceTitle = String(localized: "tips.voice.title", defaultValue: "Tired of the robotic voice?")
        static let voiceMessage = String(localized: "tips.voice.message", defaultValue: "Mo speaks with Apple's voice for now. Switch to a natural one, like ElevenLabs or Fish Audio.")
        static let voiceAction = String(localized: "tips.voice.action", defaultValue: "Change the voice")

        static let listeningTitle = String(localized: "tips.listening.title", defaultValue: "Talk to Mo in your language")
        static let listeningMessage = String(localized: "tips.listening.message", defaultValue: "Choose who listens: private on this iPhone, or a cloud service for Hindi, Tamil and more.")
        static let listeningAction = String(localized: "tips.listening.action", defaultValue: "Choose who listens")

        static let accessTitle = String(localized: "tips.access.title", defaultValue: "You decide what Mo does alone")
        static let accessMessage = String(localized: "tips.access.message", defaultValue: "Mo asks before it sends or pays. Choose how often it checks with you first.")
        static let accessAction = String(localized: "tips.access.action", defaultValue: "Set what Mo asks")

        static let startTitle = String(localized: "tips.start.title", defaultValue: "Start Mo without opening it")
        static let startMessage = String(localized: "tips.start.message", defaultValue: "Say \u{201C}Hey Siri, Start Mode\u{201D}, or unlock your iPhone by voice and let Mo begin.")
        static let startAction = String(localized: "tips.start.action", defaultValue: "Set it up")

        static let nameTitle = String(localized: "tips.name.title", defaultValue: "Tell Mo your name")
        static let nameMessage = String(localized: "tips.name.message", defaultValue: "Mo greets you by name and remembers the people and orders you use most.")
        static let nameAction = String(localized: "tips.name.action", defaultValue: "Add your name")
    }
}
