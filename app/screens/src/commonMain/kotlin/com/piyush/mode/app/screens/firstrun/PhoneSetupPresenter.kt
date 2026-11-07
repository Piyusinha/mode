package com.piyush.mode.app.screens.firstrun

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.AppLifecycle
import com.piyush.mode.core.platform.Permission
import com.piyush.mode.core.platform.PermissionStatus
import com.piyush.mode.core.platform.Permissions
import com.piyush.mode.feature.connection.domain.CheckState
import com.piyush.mode.feature.connection.domain.PhoneCheckId
import com.piyush.mode.feature.connection.domain.PhoneChecks
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** One thing phone control needs, on the "Let Mode use your phone" page. */
public enum class PhoneSetupKind {
    /** The accessibility switch: required. */
    PhoneControl,
    /** Questions and progress in notifications. */
    Notifications,
    /** Hold power to talk to Mode: optional. */
    Assistant,
    /** Asked the first time the user speaks. */
    Microphone,
    /** Unrestricted battery, so a task is not cut off: optional. */
    Battery,
}

/** A page that walks the user through one Android setting, then sends them there. */
public enum class PhoneSetupGuide {
    /** Android 13+ locks the accessibility switch of an app not from a store: App info › ⋮ › Allow restricted settings. */
    RestrictedSettings,
    /** Settings › Accessibility › Mode › on. */
    PhoneControl,
    /** Default apps › Digital assistant › Mode. */
    Assistant,
    /** App info › Battery › Unrestricted. */
    Battery,
}

public data class PhoneSetupRow(
    val kind: PhoneSetupKind,
    val status: PermissionStatus,
    /** Not needed for Continue. */
    val optional: Boolean,
    /** Asked later, by itself (the microphone): nothing to do here. */
    val later: Boolean = false,
)

public data class PhoneSetupUiState(
    val rows: List<PhoneSetupRow> = emptyList(),
    /** The guide page open now, or none. */
    val guide: PhoneSetupGuide? = null,
    /** Phone control is on: Continue works. */
    val ready: Boolean = false,
    /** Android locks the switch until restricted settings are allowed (the guide for that comes first). */
    val locked: Boolean = false,
)

/**
 * "Let Mode use your phone" (Android): what phone control needs, each with its state, and a guide page for
 * the ones the user turns on in Settings. Everything is read again when Mode comes back to the front, so a
 * switch turned on in Settings shows at once, and a guide whose setting is done closes itself.
 */
public class PhoneSetupPresenter(
    parent: CoroutineScope,
    private val checks: PhoneChecks,
    private val permissions: Permissions,
    lifecycle: AppLifecycle,
) : Presenter<PhoneSetupUiState>(parent, PhoneSetupUiState()) {

    init {
        refresh()
        lifecycle.onForeground { refresh() }
    }

    /** Reads every setting again (also on returning from Settings). */
    public fun refresh() {
        scope.launch {
            val status = checks.status().associate { it.id to it.state }
            val control = status[PhoneCheckId.Engine]
            val locked = status[PhoneCheckId.RestrictedSettings] is CheckState.Failed
            val battery = status[PhoneCheckId.Battery]
            val rows = listOf(
                PhoneSetupRow(PhoneSetupKind.PhoneControl, if (control is CheckState.Passed) PermissionStatus.Allowed else PermissionStatus.NotAsked, optional = false),
                PhoneSetupRow(PhoneSetupKind.Notifications, permissions.status(Permission.Notifications), optional = true),
                PhoneSetupRow(PhoneSetupKind.Assistant, permissions.status(Permission.Assistant), optional = true),
                PhoneSetupRow(PhoneSetupKind.Battery, if (battery is CheckState.Passed) PermissionStatus.Allowed else PermissionStatus.NotAsked, optional = true),
                PhoneSetupRow(PhoneSetupKind.Microphone, permissions.status(Permission.Microphone), optional = true, later = true),
            )
            val ready = control is CheckState.Passed
            mutableState.value = mutableState.value.let { s ->
                val done = s.guide != null && guideDone(s.guide, rows, locked)
                if (done) AppLog.log("setup", "${s.guide} done")
                s.copy(rows = rows, ready = ready, locked = locked, guide = if (done) null else s.guide)
            }
        }
    }

    /** The row's button: a prompt for notifications, else the guide for that setting. */
    public fun allow(kind: PhoneSetupKind) {
        when (kind) {
            PhoneSetupKind.Notifications -> scope.launch {
                permissions.request(Permission.Notifications)
                refresh()
            }
            PhoneSetupKind.Microphone -> scope.launch {
                permissions.request(Permission.Microphone)
                refresh()
            }
            PhoneSetupKind.PhoneControl -> open(if (state.value.locked) PhoneSetupGuide.RestrictedSettings else PhoneSetupGuide.PhoneControl)
            PhoneSetupKind.Assistant -> open(PhoneSetupGuide.Assistant)
            PhoneSetupKind.Battery -> open(PhoneSetupGuide.Battery)
        }
    }

    /** "I've done this" on the restricted-settings guide: on to turning the switch on. */
    public fun unlocked() {
        open(PhoneSetupGuide.PhoneControl)
    }

    public fun closeGuide() {
        mutableState.value = mutableState.value.copy(guide = null)
    }

    private fun open(guide: PhoneSetupGuide) {
        AppLog.log("setup", "guide $guide")
        mutableState.value = mutableState.value.copy(guide = guide)
    }

    private fun guideDone(guide: PhoneSetupGuide, rows: List<PhoneSetupRow>, locked: Boolean): Boolean {
        fun allowed(kind: PhoneSetupKind) = rows.first { it.kind == kind }.status == PermissionStatus.Allowed
        return when (guide) {
            // Unlocking can't be read directly; the switch being on proves it.
            PhoneSetupGuide.RestrictedSettings -> allowed(PhoneSetupKind.PhoneControl) || !locked
            PhoneSetupGuide.PhoneControl -> allowed(PhoneSetupKind.PhoneControl)
            PhoneSetupGuide.Assistant -> allowed(PhoneSetupKind.Assistant)
            PhoneSetupGuide.Battery -> allowed(PhoneSetupKind.Battery)
        }
    }
}
