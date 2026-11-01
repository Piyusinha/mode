import Foundation
import ModeKit
import UserNotifications

/// Answers to the agent's questions, given from the notification itself (Allow / Don't allow, an option,
/// or a typed reply): passed to the task runner by question id, so the app Mode works in stays in front.
final class QuestionAnswers: NSObject, UNUserNotificationCenterDelegate {
    static let shared = QuestionAnswers()

    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        let info = response.notification.request.content.userInfo
        if let id = info[IosNotifier.companion.KEY_QUESTION] as? String, let answer = Self.answer(response, options: info[IosNotifier.companion.KEY_OPTIONS] as? [String] ?? []) {
            Updates { @MainActor in IosModeGraphKt.iosModeGraph.tasks.answer(questionId: id, text: answer) }
        }
        completionHandler()
    }

    /// While Mode is in front its own sheet asks; the banner would only repeat it.
    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        let isQuestion = notification.request.content.userInfo[IosNotifier.companion.KEY_QUESTION] != nil
        completionHandler(isQuestion ? [] : [.banner, .sound])
    }

    /// The answer an action stands for; nil for just opening the notification.
    private static func answer(_ response: UNNotificationResponse, options: [String]) -> String? {
        let action = response.actionIdentifier
        switch action {
        case IosNotifier.companion.ACTION_YES: return "yes"
        case IosNotifier.companion.ACTION_NO: return "no"
        case IosNotifier.companion.ACTION_TEXT:
            return (response as? UNTextInputNotificationResponse)?.userText.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        default:
            guard action.hasPrefix(IosNotifier.companion.ACTION_OPTION),
                  let index = Int(action.dropFirst(IosNotifier.companion.ACTION_OPTION.count)), options.indices.contains(index) else { return nil }
            return options[index]
        }
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
