package com.piyush.mode.android

import com.piyush.mode.core.telemetry.Operation
import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.core.telemetry.trace
import com.piyush.mode.ops.Op
import com.piyush.mode.ops.OpFailed
import com.piyush.mode.ops.PhoneBackend
import com.piyush.mode.ops.Unsupported
import com.piyush.mode.ops.wireName
import com.piyush.mode.screen.AppNames
import com.piyush.mode.screen.ImageTools
import com.piyush.mode.screen.InstalledApp
import com.piyush.mode.screen.ScreenInfo
import com.piyush.mode.screen.ScreenJson
import com.piyush.mode.screen.ShotCache
import com.piyush.mode.screen.TextReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.time.TimeSource

/**
 * The Android phone, driven through Mode's accessibility service.
 *
 * Points are dp. `screen.text` comes from the accessibility tree (`source:
 * "tree"`, with each box's window), falling back to OCR of the screenshot
 * when a [reader] is given and the app's window has no readable text (a
 * game, a canvas). A screen the phone will not show answers `private`.
 *
 * Ops run one at a time; the service's calls run on [io].
 */
class AccessibilityBackend(
    private val hands: AccessibilityHands,
    private val images: ImageTools,
    private val reader: TextReader? = null,
    private val io: CoroutineDispatcher = Dispatchers.Default,
    clock: TimeSource = TimeSource.Monotonic,
    private val telemetry: Telemetry = Telemetry.None,
    /** The display Mode works on: 0 is the phone's own screen; a hidden display in background mode. */
    private val displayId: Int = 0,
) : PhoneBackend {

    override val name = "android-a11y"

    private val lock = Mutex()
    private val shots = ShotCache(clock)
    private var apps: List<InstalledApp>? = null
    private val density = hands.density
    private val screen = ScreenInfo(hands.screenPx.first / density.toDouble(), hands.screenPx.second / density.toDouble())
    private val topInset = hands.topInsetPx / density.toDouble()

    override suspend fun ops(): Set<String> = SUPPORTED

    override suspend fun send(op: Op): JsonElement {
        if (op.wireName !in SUPPORTED) throw Unsupported("this Android phone cannot '${op.wireName}' through accessibility")
        return lock.withLock {
            withContext(io) {
                if (op.wireName !in READS) shots.invalidate()
                run(op)
            }
        }
    }

    private suspend fun run(op: Op): JsonElement = when (op) {
        is Op.Tap -> gesture(Strokes.tap(op.x, op.y, density, displayId))
        is Op.LongPress -> gesture(Strokes.longPress(op.x, op.y, op.duration, density, displayId))
        is Op.Drag -> gesture(Strokes.drag(op.x1, op.y1, op.x2, op.y2, op.duration, density, displayId))
        is Op.Scroll -> gesture(Strokes.scroll(op.x, op.y, op.dx, op.dy, density, displayId))
        is Op.TypeText -> type(op.text)
        is Op.PressKey -> key(op.combo)
        Op.Home -> global(GlobalAction.Home)
        Op.Back -> global(GlobalAction.Back)
        Op.Recents -> global(GlobalAction.Recents)
        Op.OpenNotifications -> global(GlobalAction.Notifications)
        Op.OpenQuickSettings -> global(GlobalAction.QuickSettings)
        is Op.LaunchApp -> JsonPrimitive(launch(op.name, op.fresh))
        Op.CurrentApp -> hands.currentPackage(displayId)?.let { JsonPrimitive(it) } ?: JsonNull
        is Op.OpenUrl -> {
            if (!hands.openUrl(op.url)) throw OpFailed("nothing on this phone opens '${op.url}'")
            JsonNull
        }
        Op.ScreenBounds -> ScreenJson.bounds(screen, topInset)
        is Op.ScreenCapture -> capture(op.maxSize)
        is Op.ScreenText -> JsonArray(readText(op.minConfidence))
        Op.SessionState -> JsonPrimitive("ready")
        else -> throw Unsupported("this Android phone cannot '${op.wireName}' through accessibility")
    }

    private suspend fun gesture(stroke: Stroke): JsonElement {
        if (!hands.gesture(stroke)) throw OpFailed("the phone did not take the gesture")
        return JsonNull
    }

    private fun global(action: GlobalAction): JsonElement {
        if (!hands.global(action)) throw OpFailed("the phone did not do $action")
        return JsonNull
    }

    /** Into the focused field, after what it holds; the field's hint is not text it holds. */
    private fun type(text: String): JsonElement {
        val field = hands.focusedEditable(displayId) ?: throw OpFailed("no text field is focused: tap one first")
        val held = field.text?.takeUnless { it == field.hint }.orEmpty()
        if (!hands.setText(field, held + text) && !hands.paste(field, text)) throw OpFailed("could not type into the field")
        return JsonNull
    }

    private fun key(combo: String): JsonElement {
        val field = hands.focusedEditable(displayId)
        when (combo.lowercase()) {
            "return", "enter" -> {
                if (field == null) throw OpFailed("no text field is focused, so there is nothing to submit")
                if (!hands.imeEnter(field)) throw OpFailed("the keyboard has no action key here")
            }
            "delete", "backspace" -> {
                if (field == null) throw OpFailed("no text field is focused")
                val held = field.text?.takeUnless { it == field.hint }.orEmpty()
                if (held.isNotEmpty() && !hands.setText(field, held.dropLast(1))) throw OpFailed("could not delete")
            }
            else -> throw Unsupported("this Android phone takes return and delete only, not '$combo'")
        }
        return JsonNull
    }

    private fun launch(name: String, fresh: Boolean): String {
        val id = if (AppNames.looksLikeBundleId(name)) name else findApp(name).bundleId
        if (!hands.launch(id, fresh, displayId)) throw OpFailed("could not open $id")
        return id
    }

    /** From the cached app list; an unknown name refreshes it once (apps get installed). */
    private fun findApp(name: String): InstalledApp {
        val cached = apps
        if (cached != null) runCatching { return AppNames.resolve(name, cached) }
        val fresh = hands.installedApps().also { apps = it }
        return AppNames.resolve(name, fresh)
    }

    private suspend fun capture(maxSize: Int?): JsonElement {
        val shot = telemetry.trace(Operation.ScreenCapture) { shoot() }
        return when (shot) {
            is Shot.Image -> {
                shots.put(shot.png)
                if (maxSize == null || maxSize <= 0) ScreenJson.capturePng(shot.png, screen, topInset)
                else ScreenJson.captureJpeg(telemetry.trace(Operation.ImageEncode) { images.shrinkToJpeg(shot.png, maxSize) }, screen, topInset)
            }
            Shot.Secure -> {
                shots.invalidate()
                ScreenJson.capturePrivate(screen, topInset)
            }
            is Shot.Throttled -> throw OpFailed("the phone is still busy with the last screenshot")
            is Shot.Failed -> throw OpFailed("could not take a screenshot (${shot.code})")
        }
    }

    /** A screenshot, waiting once when the phone says it is too soon after the last. */
    private suspend fun shoot(): Shot {
        val first = hands.screenshot(displayId)
        if (first !is Shot.Throttled) return first
        delay(first.retryAfterMs)
        return hands.screenshot(displayId)
    }

    /** Tree boxes in dp; OCR of the screen only when the app's window has nothing to read. */
    private suspend fun readText(minConfidence: Double): List<JsonElement> {
        val boxes = telemetry.trace(Operation.ScreenOcr) { TreeText.boxes(hands.windows(displayId), density) }
        val fromTree = boxes.map { ScreenJson.textBox(it.text, 1.0, "tree", it.x, it.y, it.w, it.h, it.window) }
        if (boxes.any { it.window == "app" } || reader == null) return fromTree
        val shot = shots.take() ?: (shoot() as? Shot.Image)?.png ?: return fromTree
        val ocr = telemetry.trace(Operation.ScreenOcr) { reader.read(shot) }
            .filter { it.confidence >= minConfidence && it.text.isNotBlank() }
            .map { ScreenJson.textBox(it, screen) }
        return ocr + fromTree
    }

    companion object {
        /** Wire names of every op this backend answers. */
        val SUPPORTED: Set<String> = setOf(
            "screen.text", "screen.capture", "screen.bounds",
            "input.tap", "input.press", "input.drag", "input.scroll", "input.text", "input.keys",
            "nav.home", "nav.back", "nav.recents", "notifications.open", "quicksettings.open",
            "apps.launch", "apps.current", "url.open", "session.state",
        )

        /** Ops that only look; they keep the last screenshot valid. */
        private val READS = setOf("screen.capture", "screen.text", "screen.bounds", "session.state", "apps.current")
    }
}
