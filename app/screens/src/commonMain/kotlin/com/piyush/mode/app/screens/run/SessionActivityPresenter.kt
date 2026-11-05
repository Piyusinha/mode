package com.piyush.mode.app.screens.run

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.feature.connection.domain.ConnectionState
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The "Mode is connected" Live Activity: the Device Hub session is open while no task runs. */
public sealed interface SessionActivityUiState {
    public data object Hidden : SessionActivityUiState

    /** Mode can see and control this iPhone (iOS keeps the screen on). [sinceMs]: when it was left open, epoch ms. */
    public data class Connected(val sinceMs: Long) : SessionActivityUiState
}

/**
 * Shows the open Device Hub session when nothing is running, so the user can end it from the Lock Screen.
 * Disconnect closes the session and stops Mode's VPN; unlike Stop on iOS's own indicator, it does not unpair,
 * and the next task connects again by itself.
 *
 * @param settle how long the session must sit open with no task before the activity shows: queued tasks
 *   keep it open between them, and a task's reconnects open it for a moment.
 */
public class SessionActivityPresenter(
    parent: CoroutineScope,
    runner: TaskRunner,
    private val session: PhoneSession,
    private val clock: ScreenClock = ScreenClock(),
    private val settle: Duration = 3.seconds,
) : Presenter<SessionActivityUiState>(parent, SessionActivityUiState.Hidden) {

    init {
        scope.launch {
            combine(session.state, runner.state) { connection, task -> connection is ConnectionState.Connected && !task.running }
                .distinctUntilChanged()
                .collectLatest { idle ->
                    if (!idle) {
                        mutableState.value = SessionActivityUiState.Hidden
                        return@collectLatest
                    }
                    delay(settle)
                    AppLog.log("activity", "session open with no task: showing Disconnect")
                    mutableState.value = SessionActivityUiState.Connected(clock.now())
                }
        }
    }

    /** Disconnect, from the Lock Screen or the Dynamic Island. */
    public fun disconnect() {
        AppLog.log("activity", "Disconnect tapped")
        mutableState.value = SessionActivityUiState.Hidden
        scope.launch { session.stopSession() }
    }

    /** What happened to the Live Activity, for the logs. */
    public fun log(message: String) {
        AppLog.log("activity", message)
    }
}
