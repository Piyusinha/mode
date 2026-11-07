package com.piyush.mode.core.platform

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

/**
 * Android: notifications and the microphone are runtime permissions; "start by voice" (Permission.Assistant)
 * is the "Ask Mode" entry the user's own assistant can open ("Hey Google, open Ask Mode"), which the app layer
 * reports through [assistantHeld] and switches on with [turnOnAssistant]. Mode never replaces their assistant.
 * Android does not say whether a permission was ever asked for, so Mode keeps
 * that itself, to tell "not yet" from "refused".
 */
public class AndroidPermissions(
    context: Context,
    private val assistantHeld: () -> Boolean = { false },
    /** Whether this build can be started by voice. */
    private val assistantAvailable: () -> Boolean = { false },
    private val turnOnAssistant: () -> Unit = {},
) : Permissions {
    private val app = context.applicationContext
    private val asked = app.getSharedPreferences("mode.permissions", Context.MODE_PRIVATE)

    override suspend fun status(permission: Permission): PermissionStatus = when (permission) {
        Permission.Notifications -> runtime(Manifest.permission.POST_NOTIFICATIONS)
        Permission.Microphone -> runtime(Manifest.permission.RECORD_AUDIO)
        Permission.Assistant -> when {
            !assistantAvailable() -> PermissionStatus.Unavailable
            assistantHeld() -> PermissionStatus.Allowed
            else -> PermissionStatus.NotAsked
        }
    }

    override suspend fun request(permission: Permission): PermissionStatus {
        val name = when (permission) {
            Permission.Notifications -> Manifest.permission.POST_NOTIFICATIONS
            Permission.Microphone -> Manifest.permission.RECORD_AUDIO
            Permission.Assistant -> {
                if (assistantAvailable()) turnOnAssistant()
                return status(permission)
            }
        }
        if (runtime(name) != PermissionStatus.Allowed) {
            ActivityBridge.requestPermission(name)
            asked.edit().putBoolean(name, true).apply()
        }
        return status(permission)
    }

    private fun runtime(name: String): PermissionStatus = when {
        app.checkSelfPermission(name) == PackageManager.PERMISSION_GRANTED -> PermissionStatus.Allowed
        asked.getBoolean(name, false) -> PermissionStatus.Denied
        else -> PermissionStatus.NotAsked
    }
}
