package com.piyush.mode.app.background

import com.piyush.mode.android.ServiceRegistry
import com.piyush.mode.app.screens.run.ActivityUiState
import com.piyush.mode.app.screens.run.LiveActivityPresenter
import com.piyush.mode.app.screens.run.LiveRunPresenter
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.feature.connection.data.BackgroundMode
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The user has the app Mode was using on the hidden display: they took it over from the overlay, or simply opened it
 * themselves (an app has one window, so Android moves it from the hidden display to their screen). Mode pauses, and
 * when the user leaves that app (or gives it back) the app goes back to the hidden display and Mode carries on.
 * The user's turn (a PIN) is the runner's own hand-over and is left alone here.
 */
object UserTakeover {
    private val _active = MutableStateFlow(false)

    /** The user has the app; Mode is paused until it's back. */
    val active: StateFlow<Boolean> = _active.asStateFlow()

    private var mode: BackgroundMode? = null
    private var live: LiveRunPresenter? = null
    /** The app Mode was last seen using on the hidden display. */
    private var using: String? = null
    private var leftFor = 0
    private var gone = 0

    fun watch(mode: BackgroundMode, activity: LiveActivityPresenter, live: LiveRunPresenter, ownPackage: String) {
        this.mode = mode
        this.live = live
        MainScope().launch {
            while (true) {
                delay(LOOK_MS)
                runCatching { look(mode, activity.state.value, ownPackage) }
                    .onFailure { AppLog.log("background", "takeover check failed: ${it::class.simpleName}") }
            }
        }
    }

    /** From the overlay's Take over: the app comes to the user's screen and Mode waits. */
    suspend fun takeOver(): Boolean {
        val m = mode ?: return false
        live?.pause()
        if (!m.bringToUser()) {
            live?.resume()
            return false
        }
        _active.value = true
        leftFor = 0
        return true
    }

    /** The app goes back to the hidden display and Mode carries on. */
    suspend fun giveBack() {
        if (!_active.value) return
        _active.value = false
        mode?.giveBack()
        live?.resume()
    }

    private suspend fun look(mode: BackgroundMode, task: ActivityUiState, ownPackage: String) {
        val busy = task is ActivityUiState.Working || (task is ActivityUiState.Waiting && !task.yourTurn)
        if (!busy || !mode.working) {
            if (_active.value && task !is ActivityUiState.Waiting) giveBack()
            if (!busy) using = null
            return
        }
        val service = ServiceRegistry.service.value ?: return
        val hidden = mode.hiddenDisplay ?: return
        val onHidden = service.appInFront(hidden)
        if (onHidden != null && onHidden != ownPackage) using = onHidden
        val app = using ?: return
        val onUsers = service.appInFront(USER_DISPLAY)
        if (!_active.value) {
            // The hidden display lost its app to the user's screen: they opened it (two looks, not a passing moment).
            gone = if (onHidden == null && onUsers == app && !mode.withTheUser) gone + 1 else 0
            if (gone >= LEFT_LOOKS && mode.userOpenedIt()) {
                gone = 0
                live?.pause()
                _active.value = true
                leftFor = 0
                AppLog.log("background", "paused while the user has $app")
            }
            return
        }
        // They've gone to another app (or home): theirs is done, Mode takes it back.
        leftFor = if (onUsers != null && onUsers != app) leftFor + 1 else 0
        if (leftFor >= LEFT_LOOKS) {
            AppLog.log("background", "the user left $app: carrying on")
            giveBack()
        }
    }

    private const val LOOK_MS = 700L
    /** Two looks in a row on another app: not a passing flicker. */
    private const val LEFT_LOOKS = 2
    private const val USER_DISPLAY = 0
}
