package com.piyush.mode.app.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.PixelFormat
import android.view.Choreographer
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.piyush.mode.core.common.AppLog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** What Compose needs from a window that isn't an activity's: a lifecycle, saved state and view models. */
internal class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner, ViewModelStoreOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()

    fun start() {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
        viewModelStore.clear()
    }
}

/** Which of the overlay's windows: the sheet or bar, the Mo button, the screen glow. */
internal enum class OverlayWindow { Sheet, Button, Glow }

/** How one window sits: where, how big, and whether it takes touches and the keyboard. */
internal data class WindowSpec(
    val gravity: Int,
    val fullScreen: Boolean = false,
    val touchable: Boolean = true,
    val focusable: Boolean = false,
    val y: Int = 0,
)

/**
 * Mode's overlay windows over the user's app, added through Mode's accessibility service
 * (TYPE_ACCESSIBILITY_OVERLAY: no "draw over other apps" permission). Main thread only.
 */
internal class OverlayHost(private val service: AccessibilityService) {
    private val manager = service.getSystemService(WindowManager::class.java)
    private val owner = OverlayOwner().also { it.start() }
    private val views = mutableMapOf<OverlayWindow, ComposeView>()
    private val specs = mutableMapOf<OverlayWindow, WindowSpec>()
    /** While Mode acts on the user's screen, the windows let every touch through. */
    private var passThrough = false

    val showing: Boolean get() = views.isNotEmpty()

    fun show(window: OverlayWindow, spec: WindowSpec, content: @Composable () -> Unit) {
        val existing = views[window]
        if (existing != null) {
            if (specs[window] != spec) {
                specs[window] = spec
                runCatching { manager.updateViewLayout(existing, params(spec)) }
            }
            existing.setContent(content)
            return
        }
        val view = ComposeView(service).apply {
            setViewTreeLifecycleOwner(owner)
            setViewTreeSavedStateRegistryOwner(owner)
            setViewTreeViewModelStoreOwner(owner)
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent(content)
        }
        runCatching { manager.addView(view, params(spec)) }
            .onSuccess {
                views[window] = view
                specs[window] = spec
            }
            .onFailure { AppLog.log("overlay", "couldn't show $window: ${it::class.simpleName}") }
    }

    fun hide(window: OverlayWindow) {
        val view = views.remove(window) ?: return
        specs.remove(window)
        runCatching { manager.removeViewImmediate(view) }
    }

    fun hideAll() {
        views.keys.toList().forEach(::hide)
    }

    fun close() {
        hideAll()
        owner.stop()
    }

    /** Invisible (for Mode's own screenshot of the user's screen), then visible again. */
    fun setVisible(visible: Boolean) {
        views.values.forEach { it.alpha = if (visible) 1f else 0f }
    }

    /** Lets every touch through (Mode's own gesture on the user's screen), then takes them again. */
    fun setPassThrough(on: Boolean) {
        if (passThrough == on) return
        passThrough = on
        views.forEach { (window, view) -> specs[window]?.let { runCatching { manager.updateViewLayout(view, params(it)) } } }
    }

    private fun params(spec: WindowSpec): WindowManager.LayoutParams {
        var flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        if (!spec.focusable) flags = flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        if (!spec.touchable || passThrough) flags = flags or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
        val size = if (spec.fullScreen) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT
        return WindowManager.LayoutParams(
            if (spec.gravity and Gravity.HORIZONTAL_GRAVITY_MASK == Gravity.FILL_HORIZONTAL || spec.fullScreen) WindowManager.LayoutParams.MATCH_PARENT else size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            flags,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = spec.gravity
            y = spec.y
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
    }

    companion object {
        /** Waits until the next frame is drawn (twice: the change is on screen, not just scheduled). */
        suspend fun nextFrames() {
            repeat(2) {
                suspendCancellableCoroutine { cont ->
                    Choreographer.getInstance().postFrameCallback { if (cont.isActive) cont.resume(Unit) }
                }
            }
        }
    }
}

/** A view that is gone takes no space (the sheet when only the button shows). */
internal fun View.gone() {
    visibility = View.GONE
}
