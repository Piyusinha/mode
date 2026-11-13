package com.piyush.mode.app.screens.home

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.AppLifecycle
import com.piyush.mode.feature.connection.domain.CheckState
import com.piyush.mode.feature.connection.domain.DeviceStatus
import com.piyush.mode.feature.connection.domain.PhoneCheckId
import com.piyush.mode.feature.connection.domain.PhoneChecks
import com.piyush.mode.feature.settings.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/** Something that stopped working after setup, shown at the top while Mode is open (boards W01–W06). */
public enum class WarningKind {
    /** Android locked phone control's switch (restricted settings) and it's off. */
    Locked,
    /** Phone control's switch is off. */
    PhoneControlOff,
    /** The switch is on but the system stopped Mode's service (a battery manager, a force stop, an update). */
    PhoneControlStopped,
    /** Background mode is on; Wireless debugging can't be reached. */
    BackgroundLinkLost,
    /** Background mode is on; there's no Wi-Fi for its link. */
    BackgroundWaitingWifi,
    /** No speech recognizer on this phone: Mode can't hear. */
    NoSpeech,
}

/** How serious: a Problem stays until fixed; a Warning or Info can be closed for a few days. */
public enum class WarningTone { Problem, Warning, Info }

/** [brand]: the phone's maker when it changes the words ("Samsung put Mode to sleep"). */
public data class Warning(val kind: WarningKind, val tone: WarningTone, val brand: String? = null) {
    public val closable: Boolean get() = tone != WarningTone.Problem
}

public data class WarningsUiState(
    /** The one banner to show: the most serious warning not closed, or none. */
    val current: Warning? = null,
)

/**
 * The warning banner's state: read from the phone's quick checks whenever Mode comes to the front (and when the
 * screen asks, on a change it noticed), one warning at a time, the most serious first. Closing one hides that kind
 * for a few days; a Problem can't be closed.
 */
public class WarningsPresenter(
    parent: CoroutineScope,
    private val checks: PhoneChecks,
    private val settings: SettingsRepository,
    lifecycle: AppLifecycle,
    private val now: () -> Long,
    /** This phone's maker, for the words only (null: a plain Android phone). */
    private val brand: () -> String? = { null },
) : Presenter<WarningsUiState>(parent, WarningsUiState()) {

    init {
        refresh()
        val stop = lifecycle.watchForeground { refresh() }
        scope.coroutineContext.job.invokeOnCompletion { stop() }
    }

    public fun refresh() {
        scope.launch {
            val status = checks.status().associate { it.id to it.state }
            val snoozed = settings.settings.value.warningSnoozes
            val time = now()
            val next = warnings(status).firstOrNull { (snoozed[it.kind.name] ?: 0L) <= time }
            if (next != mutableState.value.current) AppLog.log("warnings", "showing ${next?.kind ?: "nothing"}")
            mutableState.value = WarningsUiState(next)
        }
    }

    /** ✕ on a Warning or Info: not shown again for a few days (a Problem stays). */
    public fun dismiss() {
        val w = mutableState.value.current ?: return
        if (!w.closable) return
        settings.update { it.copy(warningSnoozes = it.warningSnoozes + (w.kind.name to now() + SNOOZE_MS)) }
        refresh()
    }

    /** Every warning the status holds, the most serious first. */
    internal fun warnings(status: Map<PhoneCheckId, CheckState>): List<Warning> {
        val engine = status[PhoneCheckId.Engine] as? CheckState.Failed
        val link = status[PhoneCheckId.BackgroundLink] as? CheckState.Failed
        return listOfNotNull(
            Warning(WarningKind.Locked, WarningTone.Problem).takeIf { status[PhoneCheckId.RestrictedSettings] is CheckState.Failed },
            Warning(WarningKind.PhoneControlOff, WarningTone.Problem).takeIf { engine != null && engine.reason == DeviceStatus.ENGINE_OFF },
            Warning(WarningKind.PhoneControlStopped, WarningTone.Warning, brand()).takeIf { engine != null && engine.reason == DeviceStatus.ENGINE_STOPPED },
            Warning(WarningKind.BackgroundLinkLost, WarningTone.Warning).takeIf { link?.reason == DeviceStatus.LINK_LOST },
            Warning(WarningKind.BackgroundWaitingWifi, WarningTone.Warning).takeIf { link?.reason == DeviceStatus.NO_WIFI },
            Warning(WarningKind.NoSpeech, WarningTone.Info).takeIf { status[PhoneCheckId.Speech] is CheckState.Failed },
        ).let { all ->
            // One lock is enough: the switch being off is the same problem.
            if (all.any { it.kind == WarningKind.Locked }) all.filter { it.kind != WarningKind.PhoneControlOff } else all
        }
    }

    public companion object {
        /** How long a closed warning stays away. */
        public const val SNOOZE_MS: Long = 3L * 24 * 60 * 60 * 1000
    }
}
