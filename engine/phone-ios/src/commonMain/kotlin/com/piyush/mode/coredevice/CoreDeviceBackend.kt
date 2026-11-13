package com.piyush.mode.coredevice

import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.core.telemetry.Operation
import com.piyush.mode.core.telemetry.trace
import com.piyush.mode.ops.Op
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlin.math.hypot
import kotlin.math.roundToLong
import kotlin.time.TimeSource

/**
 * The iPhone, driven through CoreDevice remote control (`native/modecore`).
 *
 * There is no accessibility tree on this path, so `screen.text` is OCR of a
 * screenshot and [Op.Tree] / [Op.CurrentApp] raise [Unsupported]. Text the
 * hardware keyboard cannot type goes through the clipboard.
 *
 * Ops run one at a time; the blocking native calls run on [io].
 */
class CoreDeviceBackend(
    private val session: DeviceSession,
    private val screen: ScreenInfo,
    private val reader: TextReader,
    private val images: ImageTools,
    private val io: CoroutineDispatcher = Dispatchers.Default,
    clock: TimeSource = TimeSource.Monotonic,
    private val telemetry: Telemetry = Telemetry.None,
) : PhoneBackend, AutoCloseable {

    override val name = "ios-coredevice"

    private val lock = Mutex()
    private var apps: List<InstalledApp>? = null
    private val shots = ShotCache(clock)

    override suspend fun ops(): Set<String> = SUPPORTED

    override suspend fun send(op: Op): JsonElement {
        if (op.wireName !in SUPPORTED) throw Unsupported("this iPhone cannot '${op.wireName}' over CoreDevice")
        return lock.withLock {
            withContext(io) {
                if (op.wireName !in READS) shots.invalidate()
                run(op)
            }
        }
    }

    override fun close() = session.close()

    override suspend fun pauseStream() {
        lock.withLock { withContext(io) { session.pauseStream() } }
    }

    override suspend fun resumeStream() {
        lock.withLock { withContext(io) { session.resumeStream() } }
    }

    private suspend fun run(op: Op): JsonElement = when (op) {
        is Op.Tap -> touch(op.x, op.y, op.x, op.y, holdMs = TAP_HOLD_MS, travelMs = 0)
        is Op.LongPress -> touch(op.x, op.y, op.x, op.y, holdMs = seconds(op.duration), travelMs = 0)
        is Op.Drag -> touch(op.x1, op.y1, op.x2, op.y2, holdMs = TAP_HOLD_MS, travelMs = seconds(op.duration))
        // Content-space deltas: a negative dy reveals what is further down, so
        // the finger moves up. Slow, so the list stops where the finger stops.
        is Op.Scroll -> {
            val travel = (hypot(op.dx, op.dy) / SCROLL_SPEED_PT_PER_S * 1000).roundToLong()
            touch(op.x, op.y, op.x + op.dx, op.y + op.dy, holdMs = TAP_HOLD_MS, travelMs = travel.coerceAtLeast(MIN_SCROLL_MS))
        }
        is Op.TypeText -> {
            if (Keys.typeable(op.text)) session.typeAscii(op.text) else session.pasteText(op.text)
            JsonNull
        }
        is Op.PressKey -> {
            if (!Keys.isNamed(op.combo)) {
                throw Unsupported("this iPhone takes single keys only (${Keys.NAMED.sorted().joinToString()}), not '${op.combo}'")
            }
            session.key(op.combo.lowercase())
            JsonNull
        }
        Op.Home -> {
            session.button(HardwareButton.Home)
            JsonNull
        }
        is Op.LaunchApp -> JsonPrimitive(launch(op.name, op.fresh))
        Op.ScreenBounds -> ScreenJson.bounds(screen)
        is Op.ScreenCapture -> capture(op.maxSize)
        is Op.ScreenText -> JsonArray(readText(op.minConfidence))
        Op.SessionState -> JsonPrimitive("ready")
        else -> throw Unsupported("this iPhone cannot '${op.wireName}' over CoreDevice")
    }

    private fun touch(x1: Double, y1: Double, x2: Double, y2: Double, holdMs: Long, travelMs: Long): JsonElement {
        val (a, b) = screen.toTouch(x1, y1)
        val (c, d) = screen.toTouch(x2, y2)
        session.touch(a, b, c, d, holdMs, travelMs)
        return JsonNull
    }

    private fun launch(name: String, fresh: Boolean): String {
        val bundleId = if (AppNames.looksLikeBundleId(name)) name else findApp(name).bundleId
        session.launch(bundleId, fresh)
        return bundleId
    }

    /** From the cached app list; an unknown name refreshes it once (apps get installed). */
    private fun findApp(name: String): InstalledApp {
        val cached = apps
        if (cached != null) {
            runCatching { return AppNames.resolve(name, cached) }
        }
        val fresh = session.listApps().also { apps = it }
        return AppNames.resolve(name, fresh)
    }

    private suspend fun capture(maxSize: Int?): JsonElement {
        val png = telemetry.trace(Operation.ScreenCapture) { session.screenshotPng() }.also { shots.put(it) }
        if (maxSize == null || maxSize <= 0) return ScreenJson.capturePng(png, screen)
        // A smaller JPEG for models: the full PNG is several megabytes.
        val small = telemetry.trace(Operation.ImageEncode) { images.shrinkToJpeg(png, maxSize) }
        return ScreenJson.captureJpeg(small, screen)
    }

    /** OCR boxes in screen points, centres ready to tap: `{text, confidence, source, x, y, w, h}`. */
    private suspend fun readText(minConfidence: Double): List<JsonElement> {
        val shot = shots.take() ?: telemetry.trace(Operation.ScreenCapture) { session.screenshotPng() }
        return telemetry.trace(Operation.ScreenOcr) { reader.read(shot) }
            .filter { it.confidence >= minConfidence && it.text.isNotBlank() }
            .map { ScreenJson.textBox(it, screen) }
    }

    private fun seconds(s: Double): Long = (s * 1000).roundToLong().coerceAtLeast(0)

    companion object {
        /** Wire names of every op this backend answers. */
        val SUPPORTED: Set<String> = setOf(
            "screen.text", "screen.capture", "screen.bounds",
            "input.tap", "input.press", "input.drag", "input.scroll", "input.text", "input.keys",
            "nav.home", "apps.launch", "session.state",
        )

        /** Ops that only look; they keep the last screenshot valid. */
        private val READS = setOf("screen.capture", "screen.text", "screen.bounds", "session.state")

        private const val TAP_HOLD_MS = 50L
        private const val SCROLL_SPEED_PT_PER_S = 800.0
        private const val MIN_SCROLL_MS = 200L
    }
}
