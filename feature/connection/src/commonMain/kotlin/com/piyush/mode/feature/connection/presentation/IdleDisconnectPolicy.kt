package com.piyush.mode.feature.connection.presentation

import com.piyush.mode.core.common.ActivityTracker
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.AppLifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Leaving Mode while nothing is running ends the session, so iOS drops its
 * "viewing and controlling" indicator and the VPN goes off. Ending Mode
 * closes the session at once.
 */
public class IdleDisconnectPolicy(
    private val session: PhoneSession,
    private val activity: ActivityTracker,
    private val scope: CoroutineScope,
) {
    public fun install(lifecycle: AppLifecycle) {
        lifecycle.onTerminate { session.closeNow() }
        lifecycle.onBackground {
            if (!activity.isBusy) {
                AppLog.log("app", "left Mode while idle")
                scope.launch { session.disconnect() }
            }
        }
    }
}
