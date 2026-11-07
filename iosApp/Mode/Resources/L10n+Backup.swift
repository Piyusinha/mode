import Foundation

extension L10n {
    /// Settings → Backup and restore, Restore, Restored (board Backup).
    enum Backup {
        static let section = LocalizedStringResource("backup.section", defaultValue: "Your data")
        static let title = LocalizedStringResource("backup.title", defaultValue: "Backup and restore")
        static let intro = LocalizedStringResource("backup.intro", defaultValue: "Everything Mode has on this iPhone, in one file you keep. Restore it here or on a new iPhone.")
        static let whatsInIt = LocalizedStringResource("backup.whatsInIt", defaultValue: "What’s in it")

        static let chats = String(localized: "backup.chats", defaultValue: "Chats")
        static let memory = String(localized: "backup.memory", defaultValue: "What Mode knows")
        static let tasks = String(localized: "backup.tasks", defaultValue: "Tasks and history")
        static let repeats = String(localized: "backup.repeats", defaultValue: "Repeats")
        static let settings = String(localized: "backup.settings", defaultValue: "Settings and voice")
        static let signIns = String(localized: "backup.signIns", defaultValue: "AI and voice sign-ins")
        static let all = String(localized: "backup.all", defaultValue: "All")
        /// "42 chats · 318 messages".
        static func chatsCount(_ chats: Int, messages: Int) -> String {
            let c = chats == 1 ? String(localized: "backup.chatsCount.one", defaultValue: "1 chat")
                : String(localized: "backup.chatsCount", defaultValue: "\(chats) chats")
            let m = messages == 1 ? String(localized: "backup.messagesCount.one", defaultValue: "1 message")
                : String(localized: "backup.messagesCount", defaultValue: "\(messages) messages")
            return String(localized: "backup.chatsAndMessages", defaultValue: "\(c) · \(m)")
        }

        static let includeSignIns = LocalizedStringResource("backup.includeSignIns", defaultValue: "Include my AI and voice sign-ins")
        /// "ChatGPT, OpenRouter, ElevenLabs. Locked with a password you choose."
        static func signInsDetail(_ names: String) -> String {
            String(localized: "backup.signInsDetail", defaultValue: "\(names). Locked with a password you choose.")
        }
        static let password = LocalizedStringResource("backup.password", defaultValue: "Password")
        static let again = LocalizedStringResource("backup.again", defaultValue: "Again")
        static let passwordsMatch = LocalizedStringResource("backup.passwordsMatch", defaultValue: "Passwords match")
        static let lockedWarning = LocalizedStringResource("backup.lockedWarning", defaultValue: "The whole backup is locked with it. Mode can’t get it back for you: without it, this backup can’t be opened.")

        static let make = LocalizedStringResource("backup.make", defaultValue: "Make a backup")
        static let making = LocalizedStringResource("backup.making", defaultValue: "Making a backup…")
        static let saveNote = String(localized: "backup.saveNote", defaultValue: "Save it to Files or iCloud Drive, or AirDrop it to your Mac.")
        /// "Save it to Files … Last backup: yesterday, 21:40."
        static func saveNote(lastBackup: String) -> String {
            String(localized: "backup.saveNoteLast", defaultValue: "Save it to Files or iCloud Drive, or AirDrop it to your Mac. Last backup: \(lastBackup).")
        }
        /// "yesterday, 21:40", "today, 9:05".
        static func dayAndTime(_ day: String, _ time: String) -> String {
            String(localized: "backup.dayAndTime", defaultValue: "\(day), \(time)")
        }
        static let today = String(localized: "backup.today", defaultValue: "today")
        static let yesterday = String(localized: "backup.yesterday", defaultValue: "yesterday")

        static let restoreSection = LocalizedStringResource("backup.restoreSection", defaultValue: "Restore")
        static let restoreFrom = LocalizedStringResource("backup.restoreFrom", defaultValue: "Restore from a backup")
        static let restoreNote = LocalizedStringResource("backup.restoreNote", defaultValue: "Adds what’s in the backup to Mode. Nothing on this iPhone is deleted.")

        static let restoreTitle = LocalizedStringResource("backup.restoreTitle", defaultValue: "Restore")
        static let file = LocalizedStringResource("backup.file", defaultValue: "Mode backup")
        /// "Thu 6 Nov, 16:20 · Alex’s iPhone · 2.4 MB".
        static func fileDetail(_ when: String, device: String, size: String) -> String {
            device.isEmpty ? String(localized: "backup.fileDetailNoDevice", defaultValue: "\(when) · \(size)")
                : String(localized: "backup.fileDetail", defaultValue: "\(when) · \(device) · \(size)")
        }
        static let inThisBackup = LocalizedStringResource("backup.inThisBackup", defaultValue: "In this backup")
        static let backupPassword = LocalizedStringResource("backup.backupPassword", defaultValue: "Backup password")
        static let lockedNote = String(localized: "backup.lockedNote", defaultValue: "This backup is locked: it has your sign-ins.")
        static let wrongPassword = String(localized: "backup.wrongPassword", defaultValue: "That password doesn’t open this backup. Try again.")
        static let restoreInto = LocalizedStringResource("backup.restoreInto", defaultValue: "Restore into Mode")
        static let restoring = LocalizedStringResource("backup.restoring", defaultValue: "Restoring…")
        static let mergeNote = LocalizedStringResource("backup.mergeNote", defaultValue: "Adds what’s new. Chats and memories already on this iPhone stay as they are.")

        static let restoredTitle = LocalizedStringResource("backup.restoredTitle", defaultValue: "Restored")
        /// "Your chats, memory, tasks and settings from Thursday’s backup are in Mode."
        static func restoredLine(day: String) -> String {
            String(localized: "backup.restoredLine", defaultValue: "Your chats, memory, tasks and settings from \(day)’s backup are in Mode.")
        }
        static let restoredLinePlain = String(localized: "backup.restoredLinePlain", defaultValue: "Your chats, memory, tasks and settings from the backup are in Mode.")
        /// "38 added".
        static func added(_ n: Int) -> String {
            String(localized: "backup.added", defaultValue: "\(n) added")
        }
        /// "4 chats", "3 memories", in "… were already on this iPhone".
        static func skippedChats(_ n: Int) -> String {
            n == 1 ? String(localized: "backup.skippedChats.one", defaultValue: "1 chat") : String(localized: "backup.skippedChats", defaultValue: "\(n) chats")
        }
        static func skippedMemories(_ n: Int) -> String {
            n == 1 ? String(localized: "backup.skippedMemories.one", defaultValue: "1 memory") : String(localized: "backup.skippedMemories", defaultValue: "\(n) memories")
        }
        static func skippedTasks(_ n: Int) -> String {
            n == 1 ? String(localized: "backup.skippedTasks.one", defaultValue: "1 task") : String(localized: "backup.skippedTasks", defaultValue: "\(n) tasks")
        }
        static func skippedRepeats(_ n: Int) -> String {
            n == 1 ? String(localized: "backup.skippedRepeats.one", defaultValue: "1 repeat") : String(localized: "backup.skippedRepeats", defaultValue: "\(n) repeats")
        }
        /// "4 chats and 3 memories were already on this iPhone, so they were left as they are."
        static func skipped(_ list: String, one: Bool) -> String {
            one ? String(localized: "backup.skipped.one", defaultValue: "\(list) was already on this iPhone, so it was left as it is.")
                : String(localized: "backup.skipped", defaultValue: "\(list) were already on this iPhone, so they were left as they are.")
        }

        static let notABackup = String(localized: "backup.notABackup", defaultValue: "That file isn’t a Mode backup.")
        static let newerVersion = String(localized: "backup.newerVersion", defaultValue: "This backup is from a newer Mode. Update Mode, then try again.")
        static let unreadable = String(localized: "backup.unreadable", defaultValue: "Mode couldn’t read this backup. It may be damaged.")
        static let couldNotSave = String(localized: "backup.couldNotSave", defaultValue: "Mode couldn’t make the backup. Check there’s space on this iPhone, then try again.")
        static let ok = LocalizedStringResource("backup.ok", defaultValue: "OK")
    }
}
