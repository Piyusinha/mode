import Foundation

extension L10n {
    /// Settings: a newer Mode, and this Mode's version.
    enum Updates {
        /// "Mode 0.1.4 is available."
        static func available(_ version: String) -> String {
            String(localized: "updates.available", defaultValue: "Mode \(version) is available.")
        }
        static let howTo = String(localized: "updates.howTo",
                                  defaultValue: "Update it with Mode Setup on your computer: open it, connect your iPhone and press Check for updates. Your chats and settings stay.")
        /// "What’s new in 0.1.4".
        static func whatsNew(_ version: String) -> String {
            String(localized: "updates.whatsNew", defaultValue: "What’s new in \(version)")
        }
        /// "Mode 0.1.3", at the bottom of Settings.
        static func version(_ version: String) -> String {
            String(localized: "updates.version", defaultValue: "Mode \(version)")
        }
    }
}
