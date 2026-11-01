package com.piyush.mode.app.screens.common

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job

/**
 * The state of one screen and what the user can do on it. A native screen
 * collects [state], draws it, and calls the presenter's intent functions; it
 * makes no decisions of its own. [close] when the screen goes away.
 */
public abstract class Presenter<S : Any>(parent: CoroutineScope, initial: S) {
    protected val scope: CoroutineScope = CoroutineScope(parent.coroutineContext + SupervisorJob(parent.coroutineContext.job))
    protected val mutableState: MutableStateFlow<S> = MutableStateFlow(initial)
    public val state: StateFlow<S> = mutableState.asStateFlow()

    public fun close() {
        scope.cancel()
    }
}
