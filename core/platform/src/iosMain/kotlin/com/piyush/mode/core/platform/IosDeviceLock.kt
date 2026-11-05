@file:OptIn(ExperimentalForeignApi::class)

package com.piyush.mode.core.platform

import com.piyush.mode.core.common.AppLog
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.ULongVar
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationProtectedDataDidBecomeAvailable
import platform.UIKit.UIApplicationProtectedDataWillBecomeUnavailable
import platform.darwin.NOTIFY_STATUS_OK
import platform.darwin.dispatch_get_main_queue
import platform.darwin.notify_get_state
import platform.darwin.notify_register_dispatch

/**
 * The lock as iOS reports it. SpringBoard posts "lockstate" the moment the screen locks or unlocks
 * (state 1 locked, 0 unlocked); the protected-data notices back it up (they come about ten seconds
 * after locking, and on unlock). Delivered only while Mode is running, so a waiting task keeps Mode
 * awake (BackgroundWork). Main thread.
 */
public class IosDeviceLock : DeviceLock {
    private val state = MutableStateFlow(!UIApplication.sharedApplication.protectedDataAvailable)
    override val locked: StateFlow<Boolean> = state.asStateFlow()

    init {
        memScoped {
            val token = alloc<IntVar>()
            val status = notify_register_dispatch(LOCK_STATE, token.ptr, dispatch_get_main_queue()) { t ->
                memScoped {
                    val value = alloc<ULongVar>()
                    if (notify_get_state(t, value.ptr) == NOTIFY_STATUS_OK.toUInt()) set(value.value != 0uL, "lockstate")
                }
            }
            if (status != NOTIFY_STATUS_OK.toUInt()) AppLog.log("lock", "lockstate not available ($status)")
        }
        val center = NSNotificationCenter.defaultCenter
        center.addObserverForName(UIApplicationProtectedDataWillBecomeUnavailable, null, NSOperationQueue.mainQueue) { _ -> set(true, "data") }
        center.addObserverForName(UIApplicationProtectedDataDidBecomeAvailable, null, NSOperationQueue.mainQueue) { _ -> set(false, "data") }
    }

    private fun set(locked: Boolean, source: String) {
        if (state.value == locked) return
        AppLog.log("lock", "${if (locked) "locked" else "unlocked"} ($source)")
        state.value = locked
    }

    private companion object {
        const val LOCK_STATE = "com.apple.springboard.lockstate"
    }
}
