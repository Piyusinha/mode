package com.piyush.mode.core.platform

import platform.Foundation.NSDate
import platform.Foundation.NSUUID
import platform.Foundation.timeIntervalSince1970
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationState
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNNotificationSound
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNTextInputNotificationAction
import platform.UserNotifications.UNNotificationCategoryOptionNone
import platform.UserNotifications.UNNotificationCategory
import platform.UserNotifications.UNNotificationActionOptionDestructive
import platform.UserNotifications.UNNotificationActionOptionNone
import platform.UserNotifications.UNNotificationAction

/** Local notifications through UNUserNotificationCenter. Main thread. */
public class IosNotifier : Notifier {
    /** Notifications are allowed, so questions can be answered from them. Kept fresh from the settings. */
    private var allowed = false

    init {
        refreshAllowed()
    }

    private fun refreshAllowed() {
        UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
            allowed = settings?.authorizationStatus == UNAuthorizationStatusAuthorized ||
                settings?.authorizationStatus == UNAuthorizationStatusProvisional
        }
    }

    override fun requestPermission() {
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { granted, _ ->
                allowed = granted
            }
    }

    override fun canAskInPlace(): Boolean {
        refreshAllowed()
        return allowed
    }

    /**
     * The question as a notification with its answers on it: Allow / Don't allow, the options, or a
     * reply field. The app's own handler (QuestionAnswers.swift) passes the answer to the runner by [id].
     */
    override fun ask(id: String, text: String, kind: QuestionKind, options: List<String>) {
        val category = when (kind) {
            QuestionKind.YesNo -> CATEGORY_YES_NO
            QuestionKind.YesNoAlways -> CATEGORY_YES_NO_ALWAYS
            QuestionKind.Text -> CATEGORY_TEXT
            QuestionKind.Choice -> CATEGORY_CHOICE
            QuestionKind.YourTurn -> CATEGORY_YOUR_TURN
        }
        registerCategories(options.take(MAX_OPTIONS))
        val content = UNMutableNotificationContent().apply {
            setTitle(if (kind == QuestionKind.YourTurn) "Your turn" else "Mode needs you")
            setBody(text)
            setSound(UNNotificationSound.defaultSound)
            setCategoryIdentifier(category)
            setUserInfo(mapOf<Any?, Any?>(KEY_QUESTION to id, KEY_OPTIONS to options.take(MAX_OPTIONS)))
        }
        val request = UNNotificationRequest.requestWithIdentifier(id, content, null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
    }

    /** The three kinds of question; the choice one carries this question's options as its buttons. */
    private fun registerCategories(options: List<String>) {
        val center = UNUserNotificationCenter.currentNotificationCenter()
        val yesNo = UNNotificationCategory.categoryWithIdentifier(
            CATEGORY_YES_NO,
            listOf(
                UNNotificationAction.actionWithIdentifier(ACTION_YES, "Allow", UNNotificationActionOptionNone),
                UNNotificationAction.actionWithIdentifier(ACTION_NO, "Don't allow", UNNotificationActionOptionDestructive),
            ),
            emptyList<String>(), UNNotificationCategoryOptionNone,
        )
        val yesNoAlways = UNNotificationCategory.categoryWithIdentifier(
            CATEGORY_YES_NO_ALWAYS,
            listOf(
                UNNotificationAction.actionWithIdentifier(ACTION_YES, "Yes", UNNotificationActionOptionNone),
                UNNotificationAction.actionWithIdentifier(ACTION_ALWAYS, "Yes, and don't ask again", UNNotificationActionOptionNone),
                UNNotificationAction.actionWithIdentifier(ACTION_NO, "No", UNNotificationActionOptionDestructive),
            ),
            emptyList<String>(), UNNotificationCategoryOptionNone,
        )
        val yourTurn = UNNotificationCategory.categoryWithIdentifier(
            CATEGORY_YOUR_TURN,
            listOf(UNNotificationAction.actionWithIdentifier(ACTION_DONE, "I've entered it", UNNotificationActionOptionNone)),
            emptyList<String>(), UNNotificationCategoryOptionNone,
        )
        val reply = UNTextInputNotificationAction.actionWithIdentifier(
            ACTION_TEXT, "Reply", UNNotificationActionOptionNone, "Reply", "Your answer",
        )
        val text = UNNotificationCategory.categoryWithIdentifier(CATEGORY_TEXT, listOf(reply), emptyList<String>(), UNNotificationCategoryOptionNone)
        val choice = UNNotificationCategory.categoryWithIdentifier(
            CATEGORY_CHOICE,
            options.mapIndexed { i, o -> UNNotificationAction.actionWithIdentifier("$ACTION_OPTION$i", o, UNNotificationActionOptionNone) } +
                UNTextInputNotificationAction.actionWithIdentifier(ACTION_TEXT, "Something else…", UNNotificationActionOptionNone, "Send", "Your answer"),
            emptyList<String>(), UNNotificationCategoryOptionNone,
        )
        center.setNotificationCategories(setOf(yesNo, yesNoAlways, yourTurn, text, choice))
    }

    public companion object {
        public const val CATEGORY_YES_NO: String = "mode.yesno"
        public const val CATEGORY_TEXT: String = "mode.text"
        public const val CATEGORY_CHOICE: String = "mode.choice"
        public const val CATEGORY_YES_NO_ALWAYS: String = "mode.yesno.always"
        public const val CATEGORY_YOUR_TURN: String = "mode.yourturn"
        public const val ACTION_ALWAYS: String = "answer.always"
        public const val ACTION_DONE: String = "answer.done"
        public const val ACTION_YES: String = "answer.yes"
        public const val ACTION_NO: String = "answer.no"
        public const val ACTION_TEXT: String = "answer.text"
        public const val ACTION_OPTION: String = "answer.option."
        public const val KEY_QUESTION: String = "question"
        /** The chat a notification opens when tapped. */
        public const val KEY_CONVERSATION: String = "conversation"
        public const val KEY_OPTIONS: String = "options"
        private const val MAX_OPTIONS = 4
    }

    override fun post(title: String, body: String) {
        postFor(title, body, null)
    }

    override fun postFor(title: String, body: String, conversationId: String?) {
        if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) return
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
            // Tapping it opens this chat (QuestionAnswers reads it back).
            conversationId?.let { setUserInfo(mapOf<Any?, Any?>(KEY_CONVERSATION to it)) }
        }
        val request = UNNotificationRequest.requestWithIdentifier(NSUUID().UUIDString, content, null)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
    }

    override fun schedule(id: String, title: String, body: String, atEpochMs: Long) {
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
        }
        val seconds = ((atEpochMs - NSDate().timeIntervalSince1970 * 1000) / 1000).coerceAtLeast(1.0)
        val trigger = UNTimeIntervalNotificationTrigger.triggerWithTimeInterval(seconds, repeats = false)
        val request = UNNotificationRequest.requestWithIdentifier(id, content, trigger)
        UNUserNotificationCenter.currentNotificationCenter().addNotificationRequest(request, null)
    }

    override fun withdraw(id: String) {
        UNUserNotificationCenter.currentNotificationCenter().removeDeliveredNotificationsWithIdentifiers(listOf(id))
    }

    override fun cancel(id: String) {
        UNUserNotificationCenter.currentNotificationCenter().removePendingNotificationRequestsWithIdentifiers(listOf(id))
    }
}
