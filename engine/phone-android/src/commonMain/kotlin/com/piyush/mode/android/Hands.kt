package com.piyush.mode.android

import com.piyush.mode.screen.InstalledApp

/** A rectangle in screen pixels, top-left origin. */
data class PxRect(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val centerX: Double get() = (left + right) / 2.0
    val centerY: Double get() = (top + bottom) / 2.0
}

/** Which kind of window a node sits in: the app in front, the keyboard, or the system's bars and dialogs. */
/** [Overlay]: Mode's own overlay (the agent overlay over the user's app), which the agent never reads. */
enum class WindowKind { App, Ime, System, Other, Overlay }

/**
 * One node of the accessibility tree, copied: what it says, where it is and what it can do.
 * [id] is the service's handle for the live node behind it, valid for the snapshot it came from.
 */
data class NodeSnapshot(
    val id: Int,
    val text: String? = null,
    val contentDescription: String? = null,
    val hint: String? = null,
    val className: String? = null,
    val bounds: PxRect,
    val visible: Boolean = true,
    val clickable: Boolean = false,
    val editable: Boolean = false,
    val focused: Boolean = false,
    val password: Boolean = false,
    val children: List<NodeSnapshot> = emptyList(),
)

data class WindowSnapshot(
    val kind: WindowKind,
    val packageName: String?,
    val bounds: PxRect,
    val root: NodeSnapshot?,
    val displayId: Int = 0,
)

/** What a screenshot attempt gave. */
sealed interface Shot {
    class Image(val png: ByteArray, val widthPx: Int, val heightPx: Int) : Shot
    /** The phone will not show this screen (a bank, a PIN pad): the user's turn. */
    data object Secure : Shot
    /** Too soon after the last one; try again after [retryAfterMs]. */
    class Throttled(val retryAfterMs: Long) : Shot
    class Failed(val code: Int) : Shot
}

/** A finger path in screen pixels, from the first point to the last over [durationMs]. */
data class Stroke(val points: List<Pair<Float, Float>>, val durationMs: Long, val displayId: Int = 0)

enum class GlobalAction { Back, Home, Recents, Notifications, QuickSettings }

/**
 * What Mode's accessibility service can do, as the backend sees it. The service
 * implements it on the phone; tests use a fake over a hand-built tree.
 */
interface AccessibilityHands {
    /** Pixels per dp. */
    val density: Float
    /** The display in pixels. */
    val screenPx: Pair<Int, Int>
    /** How far down the status bar reaches, in pixels. */
    val topInsetPx: Int

    fun windows(displayId: Int = 0): List<WindowSnapshot>
    suspend fun screenshot(displayId: Int = 0): Shot
    suspend fun gesture(stroke: Stroke): Boolean
    fun global(action: GlobalAction): Boolean

    /** The text field with input focus, if any. */
    fun focusedEditable(displayId: Int = 0): NodeSnapshot?
    fun setText(node: NodeSnapshot, text: String): Boolean
    fun paste(node: NodeSnapshot, text: String): Boolean
    /** The keyboard's action key (Search, Send, Go) on [node]. */
    fun imeEnter(node: NodeSnapshot): Boolean

    fun launch(packageName: String, fresh: Boolean, displayId: Int = 0): Boolean
    fun installedApps(): List<InstalledApp>
    fun currentPackage(displayId: Int = 0): String?
    fun openUrl(url: String): Boolean
}
