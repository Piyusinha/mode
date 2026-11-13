package com.piyush.mode.android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.hardware.display.DisplayManager
import android.net.Uri
import android.os.Bundle
import android.view.Display
import android.view.WindowInsets
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.screen.InstalledApp
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executors
import kotlin.coroutines.resume

/**
 * Mode's hands and eyes on Android. The user turns it on once in Settings ›
 * Accessibility; from then on Android runs it, and the [AccessibilityBackend]
 * drives the phone through it while a task runs. Mode reads nothing and does
 * nothing here between tasks: it receives no events it acts on.
 */
class ModeAccessibilityService : AccessibilityService(), AccessibilityHands {

    /** The live nodes behind the last snapshot, by the ids handed out in it. */
    private val nodes = HashMap<Int, AccessibilityNodeInfo>()
    private var nextId = 1
    private val shotThread = Executors.newSingleThreadExecutor()

    /** The bitmap behind the last screenshot's PNG, so shrinking it skips a decode. */
    @Volatile private var lastShot: Pair<ByteArray, Bitmap>? = null

    /** The accessibility shortcut (button, gesture or volume keys) was used: Mode opens over the app in front. */
    private val shortcut = object : android.accessibilityservice.AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: android.accessibilityservice.AccessibilityButtonController) {
            AppLog.log("phone", "accessibility shortcut")
            ServiceRegistry.onShortcut?.invoke()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLog.log("phone", "accessibility service on")
        runCatching { accessibilityButtonController.registerAccessibilityButtonCallback(shortcut) }
        ServiceRegistry.connected(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        runCatching { accessibilityButtonController.unregisterAccessibilityButtonCallback(shortcut) }
        AppLog.log("phone", "accessibility service off")
        ServiceRegistry.disconnected(this)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        ServiceRegistry.disconnected(this)
        shotThread.shutdown()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    // The screen ------------------------------------------------------------------------------

    override val density: Float get() = resources.displayMetrics.density

    override val screenPx: Pair<Int, Int>
        get() = metricsContext()?.let { ctx ->
            val bounds = ctx.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
            bounds.width() to bounds.height()
        } ?: (resources.displayMetrics.widthPixels to resources.displayMetrics.heightPixels)

    override val topInsetPx: Int
        get() = metricsContext()?.getSystemService(WindowManager::class.java)?.maximumWindowMetrics
            ?.windowInsets?.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout())?.top
            ?: 0

    /** A context tied to the phone's own display, which window metrics need. */
    private fun metricsContext() = runCatching {
        val display = getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return@runCatching null
        createWindowContext(display, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY, null)
    }.getOrNull()

    override fun windows(displayId: Int): List<WindowSnapshot> = synchronized(nodes) {
        nodes.clear()
        val list = if (displayId == Display.DEFAULT_DISPLAY) windows.orEmpty() else windowsOnAllDisplays.get(displayId).orEmpty()
        list.sortedBy { it.layer }.map { window ->
            val bounds = Rect().also(window::getBoundsInScreen)
            val root = window.root
            WindowSnapshot(
                kind = when {
                    isOwnOverlay(window) -> WindowKind.Overlay
                    window.type == AccessibilityWindowInfo.TYPE_APPLICATION -> WindowKind.App
                    window.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD -> WindowKind.Ime
                    window.type == AccessibilityWindowInfo.TYPE_SYSTEM -> WindowKind.System
                    else -> WindowKind.Other
                },
                packageName = root?.packageName?.toString(),
                bounds = bounds.toPx(),
                root = root?.let { snapshot(it, depth = 0, budget = intArrayOf(MAX_NODES)) },
                displayId = window.displayId,
            )
        }.filter { it.kind != WindowKind.Other || it.root != null }
    }

    /**
     * The app in front on [displayId] (its package), null when none: a quick look that leaves what the agent is
     * reading alone (unlike [windows]).
     */
    fun appInFront(displayId: Int): String? {
        // Not `windows`: with a hidden display that is the list of whichever display was touched last.
        val list = windowsOnAllDisplays.get(displayId).orEmpty()
        return list.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }.maxByOrNull { it.layer }?.root?.packageName?.toString()
    }

    /** Mode's own overlay window (the agent overlay): the agent never reads or acts on it. */
    private fun isOwnOverlay(window: AccessibilityWindowInfo): Boolean =
        window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY && window.root?.packageName == packageName

    /** A copy of [node] and what is below it, at most [budget] nodes in all. */
    private fun snapshot(node: AccessibilityNodeInfo, depth: Int, budget: IntArray): NodeSnapshot {
        budget[0]--
        val id = nextId++
        nodes[id] = node
        val bounds = Rect().also(node::getBoundsInScreen)
        val children = if (depth >= MAX_DEPTH) emptyList() else buildList {
            for (i in 0 until node.childCount) {
                if (budget[0] <= 0) break
                val child = node.getChild(i) ?: continue
                add(snapshot(child, depth + 1, budget))
            }
        }
        return NodeSnapshot(
            id = id,
            text = node.text?.toString(),
            contentDescription = node.contentDescription?.toString(),
            hint = node.hintText?.toString(),
            className = node.className?.toString(),
            bounds = bounds.toPx(),
            visible = node.isVisibleToUser,
            clickable = node.isClickable,
            editable = node.isEditable,
            focused = node.isFocused,
            password = node.isPassword,
            showingHint = node.isShowingHintText,
            children = children,
        )
    }

    /**
     * The user's screen is captured without Mode's own overlay on it (it hides for the shot). Android answers a
     * screenshot through a callback; one that never comes (seen on the OnePlus, 13 Nov: a task stood still for
     * minutes after a confirmation) is a failure after [SHOT_TIMEOUT_MS], not a wait without end.
     */
    override suspend fun screenshot(displayId: Int): Shot {
        val guarded = displayId == Display.DEFAULT_DISPLAY
        if (guarded) OverlayGuard.beforeShot()
        try {
            return withTimeoutOrNull(SHOT_TIMEOUT_MS) { takeShot(displayId) } ?: run {
                AppLog.log("phone", "screenshot: no answer from the system in ${SHOT_TIMEOUT_MS / 1000} s")
                Shot.Failed(SHOT_TIMED_OUT)
            }
        } finally {
            if (guarded) OverlayGuard.afterShot()
        }
    }

    private suspend fun takeShot(displayId: Int): Shot = suspendCancellableCoroutine { cont ->
        takeScreenshot(displayId, shotThread, object : TakeScreenshotCallback {
            override fun onSuccess(result: ScreenshotResult) {
                val shot = runCatching {
                    val hardware = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                        ?: return@runCatching Shot.Failed(-1)
                    val bitmap = hardware.copy(Bitmap.Config.ARGB_8888, false)
                    hardware.recycle()
                    result.hardwareBuffer.close()
                    val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    lastShot = png to bitmap
                    Shot.Image(png, bitmap.width, bitmap.height)
                }.getOrElse { Shot.Failed(-2) }
                if (cont.isActive) cont.resume(shot)
            }

            override fun onFailure(errorCode: Int) {
                val shot = when (errorCode) {
                    ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> Shot.Secure
                    ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> Shot.Throttled(SHOT_INTERVAL_MS)
                    else -> Shot.Failed(errorCode)
                }
                if (cont.isActive) cont.resume(shot)
            }
        })
    }

    /** The bitmap of the screenshot [png] came from, when it is the last one taken. */
    fun bitmapFor(png: ByteArray): Bitmap? = lastShot?.takeIf { it.first === png }?.second

    // Hands -----------------------------------------------------------------------------------

    /**
     * A gesture on the user's screen goes through Mode's own overlay (it lets touches pass while it runs). The
     * system reports a gesture done or cancelled through a callback; one that never comes counts as not done
     * after the stroke's own length plus [GESTURE_GRACE_MS].
     */
    override suspend fun gesture(stroke: Stroke): Boolean {
        val guarded = stroke.displayId == Display.DEFAULT_DISPLAY
        if (guarded) OverlayGuard.beforeGesture()
        try {
            return withTimeoutOrNull(stroke.durationMs + GESTURE_GRACE_MS) { dispatch(stroke) } ?: run {
                AppLog.log("phone", "gesture: no answer from the system in ${(stroke.durationMs + GESTURE_GRACE_MS) / 1000} s")
                false
            }
        } finally {
            if (guarded) OverlayGuard.afterGesture()
        }
    }

    private suspend fun dispatch(stroke: Stroke): Boolean = suspendCancellableCoroutine { cont ->
        val path = Path().apply {
            val (x0, y0) = stroke.points.first()
            moveTo(x0.coerceAtLeast(0f), y0.coerceAtLeast(0f))
            stroke.points.drop(1).forEach { (x, y) -> lineTo(x.coerceAtLeast(0f), y.coerceAtLeast(0f)) }
        }
        val duration = stroke.durationMs.coerceIn(1, GestureDescription.getMaxGestureDuration())
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .setDisplayId(stroke.displayId)
            .build()
        val sent = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(gestureDescription: GestureDescription?) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!sent && cont.isActive) cont.resume(false)
    }

    override fun global(action: GlobalAction): Boolean = performGlobalAction(
        when (action) {
            GlobalAction.Back -> GLOBAL_ACTION_BACK
            GlobalAction.Home -> GLOBAL_ACTION_HOME
            GlobalAction.Recents -> GLOBAL_ACTION_RECENTS
            GlobalAction.Notifications -> GLOBAL_ACTION_NOTIFICATIONS
            GlobalAction.QuickSettings -> GLOBAL_ACTION_QUICK_SETTINGS
        },
    )

    override fun focusedEditable(displayId: Int): NodeSnapshot? = synchronized(nodes) {
        typingDisplay = displayId
        val focus = inputFocus(displayId) ?: return null
        snapshot(focus, depth = MAX_DEPTH, budget = intArrayOf(1))
    }

    /** The display Mode last looked for a text field on (its field is the one typing goes to). */
    @Volatile private var typingDisplay = Display.DEFAULT_DISPLAY

    /**
     * The text field with the keyboard's focus on [displayId]. The phone-wide focus follows the display the user last
     * touched (their own screen, or Mode's overlay on it), so a hidden display's field is looked up in its own windows.
     */
    private fun inputFocus(displayId: Int): AccessibilityNodeInfo? {
        if (displayId == Display.DEFAULT_DISPLAY) return findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }
        val global = findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (global != null && global.isEditable && global.window?.displayId == displayId) return global
        return windowsOnAllDisplays.get(displayId).orEmpty()
            .sortedByDescending { it.isFocused }
            .firstNotNullOfOrNull { window -> window.root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable } }
    }

    override fun setText(node: NodeSnapshot, text: String): Boolean {
        val live = live(node) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        if (!live.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
        // The cursor at the end, so the next words follow these.
        live.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, Bundle().apply {
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, text.length)
            putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, text.length)
        })
        return true
    }

    override fun paste(node: NodeSnapshot, text: String): Boolean {
        val live = live(node) ?: return false
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Mode", text))
        return live.performAction(AccessibilityNodeInfo.ACTION_PASTE)
    }

    override fun imeEnter(node: NodeSnapshot): Boolean =
        live(node)?.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id) == true

    private fun live(node: NodeSnapshot): AccessibilityNodeInfo? =
        synchronized(nodes) { nodes[node.id] }?.takeIf { it.refresh() }
            ?: inputFocus(typingDisplay)

    // Apps ------------------------------------------------------------------------------------

    override fun launch(packageName: String, fresh: Boolean, displayId: Int): Boolean {
        // Coming back to Mode after a task: on a hidden display the user never left it, and opening Mode
        // there would pull its window off their screen.
        if (packageName == this.packageName && displayId != Display.DEFAULT_DISPLAY) return true
        // An app opened under the notification shade or a system dialog (Quick Settings' "Modes") stays hidden
        // behind it: close those first.
        if (displayId == Display.DEFAULT_DISPLAY) closeSystemUi()
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or if (fresh) Intent.FLAG_ACTIVITY_CLEAR_TASK else Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val options = ActivityOptions.makeBasic().apply { if (displayId != Display.DEFAULT_DISPLAY) launchDisplayId = displayId }
        return try {
            startActivity(intent, options.toBundle())
            true
        } catch (e: ActivityNotFoundException) {
            AppLog.log("phone", "could not open $packageName: ${e.message}")
            false
        }
    }

    /**
     * A task over the user's own app ended and left a System UI panel open (Quick Settings it read from, the shade):
     * closed, as the user had it. Blocks briefly: call off the main thread.
     */
    fun closePanels() = closeSystemUi()

    /** Closes System UI's dialogs (Back) and the shade, so what opens next is seen. */
    private fun closeSystemUi() {
        repeat(SYSTEM_UI_TRIES) {
            val top = windows.orEmpty().filterNot(::isOwnOverlay).firstOrNull { it.isFocused || it.isActive } ?: return@repeat
            if (top.type != AccessibilityWindowInfo.TYPE_SYSTEM || top.root?.packageName != SYSTEM_UI) return@repeat
            performGlobalAction(GLOBAL_ACTION_BACK)
            Thread.sleep(SYSTEM_UI_SETTLE_MS)
        }
        performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
    }

    override fun installedApps(): List<InstalledApp> {
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return packageManager.queryIntentActivities(launcher, 0)
            .map { InstalledApp(it.loadLabel(packageManager).toString(), it.activityInfo.packageName) }
            .distinctBy { it.bundleId }
    }

    override fun currentPackage(displayId: Int): String? {
        val list = if (displayId == Display.DEFAULT_DISPLAY) windows.orEmpty() else windowsOnAllDisplays.get(displayId).orEmpty()
        val app = list.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
        return (app.firstOrNull { it.isFocused } ?: app.maxByOrNull { it.layer })?.root?.packageName?.toString()
    }

    override fun openUrl(url: String): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }

    private fun Rect.toPx() = PxRect(left, top, right, bottom)

    private companion object {
        const val SYSTEM_UI = "com.android.systemui"
        /** Nested System UI dialogs (a tile's dialog over the shade) close one Back at a time. */
        const val SYSTEM_UI_TRIES = 3
        const val SYSTEM_UI_SETTLE_MS = 200L

        /** A tree is cut off at this many nodes and this depth (long lists, web views). */
        const val MAX_NODES = 2000
        const val MAX_DEPTH = 40
        /** Android lets a service take a screenshot about three times a second. */
        const val SHOT_INTERVAL_MS = 350L
        /** How long a screenshot may take before it counts as failed. */
        const val SHOT_TIMEOUT_MS = 6_000L
        /** A screenshot whose callback never came. */
        const val SHOT_TIMED_OUT = -3
        /** How long past its own length a gesture may take to be reported done. */
        const val GESTURE_GRACE_MS = 4_000L
    }
}
