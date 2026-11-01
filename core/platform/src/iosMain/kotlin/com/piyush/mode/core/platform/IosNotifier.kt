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

/** Local notifications through UNUserNotificationCenter. Main thread. */
public class IosNotifier : Notifier {
    override fun requestPermission() {
        UNUserNotificationCenter.currentNotificationCenter()
            .requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> }
    }

    override fun post(title: String, body: String) {
        if (UIApplication.sharedApplication.applicationState == UIApplicationState.UIApplicationStateActive) return
        val content = UNMutableNotificationContent().apply {
            setTitle(title)
            setBody(body)
            setSound(UNNotificationSound.defaultSound)
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

    override fun cancel(id: String) {
        UNUserNotificationCenter.currentNotificationCenter().removePendingNotificationRequestsWithIdentifiers(listOf(id))
    }
}
