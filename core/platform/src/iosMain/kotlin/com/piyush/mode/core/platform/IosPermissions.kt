package com.piyush.mode.core.platform

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.AVFAudio.AVAudioApplication
import platform.AVFAudio.AVAudioApplicationRecordPermissionDenied
import platform.AVFAudio.AVAudioApplicationRecordPermissionGranted
import platform.Foundation.NSBundle
import platform.Intents.INPreferences
import platform.Intents.INSiriAuthorizationStatusAuthorized
import platform.Intents.INSiriAuthorizationStatusDenied
import platform.Intents.INSiriAuthorizationStatusNotDetermined
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNAuthorizationStatusAuthorized
import platform.UserNotifications.UNAuthorizationStatusDenied
import platform.UserNotifications.UNAuthorizationStatusEphemeral
import platform.UserNotifications.UNAuthorizationStatusProvisional
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

/** iOS: notifications, the microphone and Siri (paid builds only: free accounts can't sign the Siri entitlement). */
public class IosPermissions : Permissions {
    private val siriAvailable: Boolean
        get() = NSBundle.mainBundle.objectForInfoDictionaryKey("ModeSiri") != null

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
        Permission.Siri -> if (!siriAvailable) PermissionStatus.Unavailable else when (INPreferences.siriAuthorizationStatus()) {
            INSiriAuthorizationStatusAuthorized -> PermissionStatus.Allowed
            INSiriAuthorizationStatusDenied -> PermissionStatus.Denied
            INSiriAuthorizationStatusNotDetermined -> PermissionStatus.NotAsked
            else -> PermissionStatus.Denied
        }
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
            Permission.Siri -> if (siriAvailable) suspendCancellableCoroutine { cont ->
                INPreferences.requestSiriAuthorization { _ -> cont.resume(Unit) }
            }
        }
        return status(permission)
    }
}
