package com.piyush.mode.core.platform

import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The activity in front, for the few things Android only does from one: permission
 * prompts and system pickers. The app attaches its activity when it is created; it
 * is let go when that activity is destroyed.
 */
public object ActivityBridge {
    private val state = MutableStateFlow<ComponentActivity?>(null)
    public val current: StateFlow<ComponentActivity?> = state.asStateFlow()
    private var counter = 0

    public fun attach(activity: ComponentActivity) {
        state.value = activity
        activity.lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onDestroy(owner: LifecycleOwner) {
                if (state.value === activity) state.value = null
            }
        })
    }

    /** Shows Android's prompt for [permission]; false when nothing is in front to show it. */
    public suspend fun requestPermission(permission: String): Boolean {
        val activity = state.value ?: return false
        return suspendCancellableCoroutine { cont ->
            val key = "mode.permission.${counter++}"
            lateinit var launcher: androidx.activity.result.ActivityResultLauncher<String>
            launcher = activity.activityResultRegistry.register(key, ActivityResultContracts.RequestPermission()) { granted ->
                launcher.unregister()
                if (cont.isActive) cont.resume(granted)
            }
            cont.invokeOnCancellation { launcher.unregister() }
            launcher.launch(permission)
        }
    }
}
