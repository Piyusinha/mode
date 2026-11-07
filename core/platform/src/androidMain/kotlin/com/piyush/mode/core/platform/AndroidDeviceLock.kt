package com.piyush.mode.core.platform

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.piyush.mode.core.common.AppLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The lock as Android reports it: the keyguard is up from the moment the screen
 * goes off (on a phone with a PIN) until the user gets past it (USER_PRESENT).
 */
public class AndroidDeviceLock(context: Context) : DeviceLock {
    private val app = context.applicationContext
    private val keyguard = app.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
    private val state = MutableStateFlow(keyguard.isKeyguardLocked)
    override val locked: StateFlow<Boolean> = state.asStateFlow()

    init {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_USER_PRESENT -> set(false, "present")
                    Intent.ACTION_SCREEN_OFF -> set(keyguard.isDeviceSecure, "screen off")
                    Intent.ACTION_SCREEN_ON -> set(keyguard.isKeyguardLocked, "screen on")
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        app.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    private fun set(locked: Boolean, source: String) {
        if (state.value == locked) return
        AppLog.log("lock", "${if (locked) "locked" else "unlocked"} ($source)")
        state.value = locked
    }
}
