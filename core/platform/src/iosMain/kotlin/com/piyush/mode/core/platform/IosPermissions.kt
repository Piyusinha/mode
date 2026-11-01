package com.piyush.mode.core.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionDenied
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/**
 * iOS: notifications and the microphone. Siri needs nothing to allow: Mode's
 * App Shortcuts ("Hey Siri, start Mode") work on every build, free or paid.
 */
public class IosPermissions : Permissions {

    override suspend fun status(permission: Permission): PermissionStatus = when (permission) {
        Permission.Notifications -> suspendCancellableCoroutine { cont ->
            UNUserNotificationCenter.currentNotificationCenter().getNotificationSettingsWithCompletionHandler { settings ->
                cont.resume(
                    when (settings?.authorizationStatus) {
                        UNAuthorizationStatusAuthorized, UNAuthorizationStatusProvisional, UNAuthorizationStatusEphemeral -> PermissionStatus.Allowed
                        UNAuthorizationStatusDenied -> PermissionStatus.Denied
                        else -> PermissionStatus.NotAsked
                    },
                )
            }
        }
        Permission.Microphone -> when (AVAudioApplication.sharedInstance.recordPermission) {
            AVAudioApplicationRecordPermissionGranted -> PermissionStatus.Allowed
            AVAudioApplicationRecordPermissionDenied -> PermissionStatus.Denied
            else -> PermissionStatus.NotAsked
        }
        Permission.Siri -> PermissionStatus.Allowed
    }

    override suspend fun request(permission: Permission): PermissionStatus {
        when (permission) {
            Permission.Notifications -> suspendCancellableCoroutine { cont ->
                UNUserNotificationCenter.currentNotificationCenter()
                    .requestAuthorizationWithOptions(UNAuthorizationOptionAlert or UNAuthorizationOptionSound) { _, _ -> cont.resume(Unit) }
            }
            Permission.Microphone -> suspendCancellableCoroutine { cont ->
                AVAudioApplication.requestRecordPermissionWithCompletionHandler { _ -> cont.resume(Unit) }
            }
            Permission.Siri -> Unit
        }
        return status(permission)
    }
}
