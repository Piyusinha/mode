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

    override fun onServiceConnected() {
        super.onServiceConnected()
        AppLog.log("phone", "accessibility service on")
        ServiceRegistry.connected(this)
    }

    override fun onUnbind(intent: Intent?): Boolean {
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
                kind = when (window.type) {
                    AccessibilityWindowInfo.TYPE_APPLICATION -> WindowKind.App
                    AccessibilityWindowInfo.TYPE_INPUT_METHOD -> WindowKind.Ime
                    AccessibilityWindowInfo.TYPE_SYSTEM -> WindowKind.System
                    else -> WindowKind.Other
                },
                packageName = root?.packageName?.toString(),
                bounds = bounds.toPx(),
                root = root?.let { snapshot(it, depth = 0, budget = intArrayOf(MAX_NODES)) },
                displayId = window.displayId,
            )
        }.filter { it.kind != WindowKind.Other || it.root != null }
    }

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
            children = children,
        )
    }

    override suspend fun screenshot(displayId: Int): Shot = suspendCancellableCoroutine { cont ->
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

    override suspend fun gesture(stroke: Stroke): Boolean = suspendCancellableCoroutine { cont ->
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
        val focus = findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return null
        if (!focus.isEditable) return null
        if (displayId != Display.DEFAULT_DISPLAY && focus.window?.displayId != displayId) return null
        snapshot(focus, depth = MAX_DEPTH, budget = intArrayOf(1))
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
            ?: findFocus(AccessibilityNodeInfo.FOCUS_INPUT)?.takeIf { it.isEditable }

    // Apps ------------------------------------------------------------------------------------

    override fun launch(packageName: String, fresh: Boolean, displayId: Int): Boolean {
        // Coming back to Mode after a task: on a hidden display the user never left it, and opening Mode
        // there would pull its window off their screen.
        if (packageName == this.packageName && displayId != Display.DEFAULT_DISPLAY) return true
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
        /** A tree is cut off at this many nodes and this depth (long lists, web views). */
        const val MAX_NODES = 2000
        const val MAX_DEPTH = 40
        /** Android lets a service take a screenshot about three times a second. */
        const val SHOT_INTERVAL_MS = 350L
    }
}
