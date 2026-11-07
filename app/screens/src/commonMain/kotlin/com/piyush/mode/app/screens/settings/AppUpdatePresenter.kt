package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.data.ModeUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Settings: this Mode's version, and a newer Mode when one is out (updated with Mode Setup on a computer). */
public data class AppUpdateUiState(
    /** This Mode's version ("0.1.3"). */
    val current: String = "",
    /** The newer Mode's version, or null when this is the newest. */
    val newer: String? = null,
    /** The newer Mode's release page: what's new in it. */
    val notesUrl: String? = null,
)

public class AppUpdatePresenter(parent: CoroutineScope, private val updates: ModeUpdates?) :
    Presenter<AppUpdateUiState>(parent, AppUpdateUiState(current = updates?.current.orEmpty())) {
    init {
        updates?.let { u ->
            scope.launch { u.available.collect { a -> mutableState.value = AppUpdateUiState(u.current, a?.version, a?.notesUrl) } }
            scope.launch { u.check() }
        }
    }
}
