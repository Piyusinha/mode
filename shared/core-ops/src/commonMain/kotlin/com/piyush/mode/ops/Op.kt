package com.piyush.mode.ops

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * What the agent can ask of a phone.
 *
 * Ops are named for what they mean, never for how a platform does them, the
 * same rule as phone-harness's transport.py: `nav.home` means one thing on an
 * iPhone and on an Android, though one is an XCUIDevice button press and the
 * other a key event.
 *
 * Wire names and parameter names are phone-harness's, so the engine speaks the
 * same `POST /op {"op", "kw"}` protocol as its cloud iPhone host.
 *
 * Coordinates are screen points, top-left origin, the space the engine taps in.
 * Durations are seconds.
 */
@Serializable
sealed interface Op {

    /** The foreground app's accessibility tree, nested, as the engine reports it. */
    @Serializable @SerialName("tree")
    data object Tree : Op

    /**
     * Visible text with tap-ready centres: `[{text, confidence, source, x, y, w, h}]`.
     * From the accessibility tree on iOS, so `source` is `"tree"`.
     */
    @Serializable @SerialName("screen.text")
    data class ScreenText(@SerialName("min_confidence") val minConfidence: Double = 0.3) : Op

    /** The screen as `{png_b64, bounds}`. */
    @Serializable @SerialName("screen.capture")
    data object ScreenCapture : Op

    /** Screen bounds `{x, y, w, h, id}` in points. */
    @Serializable @SerialName("screen.bounds")
    data object ScreenBounds : Op

    @Serializable @SerialName("input.tap")
    data class Tap(val x: Double, val y: Double) : Op

    @Serializable @SerialName("input.press")
    data class LongPress(val x: Double, val y: Double, val duration: Double = 0.8) : Op

    /** A finger drag. Fast and short is a flick (momentum); slow barely registers. */
    @Serializable @SerialName("input.drag")
    data class Drag(
        val x1: Double, val y1: Double,
        val x2: Double, val y2: Double,
        val duration: Double = 0.35,
        val steps: Int = 14,
    ) : Op

    /**
     * Scroll the view under (x, y) by content-space deltas, with no momentum.
     * A negative [dy] reveals content further down; a negative [dx] content to
     * the right. Helpers turn "show me what is below" into these signs.
     */
    @Serializable @SerialName("input.scroll")
    data class Scroll(
        val x: Double, val y: Double,
        val dy: Double, val dx: Double = 0.0,
        val steps: Int = 6,
    ) : Op

    /** Type into the focused field. Text sent with nothing focused goes nowhere. */
    @Serializable @SerialName("input.text")
    data class TypeText(
        @SerialName("s") val text: String,
        val delay: Double = 0.03,
        val keystrokes: Boolean = false,
    ) : Op

    /** A single key, such as `return`, `delete` or `tab`. */
    @Serializable @SerialName("input.keys")
    data class PressKey(val combo: String) : Op

    @Serializable @SerialName("nav.home")
    data object Home : Op

    /** Launch by bundle id, or by the name on the icon. Answers the bundle id in front afterwards. */
    @Serializable @SerialName("apps.launch")
    data class LaunchApp(val name: String, val fresh: Boolean = false) : Op

    /** The foreground app's bundle id, or null when the engine cannot tell. */
    @Serializable @SerialName("apps.current")
    data object CurrentApp : Op

    /** Open a link whole, without typing it. */
    @Serializable @SerialName("url.open")
    data class OpenUrl(val url: String) : Op

    /** `"ready"` when the phone can be driven. */
    @Serializable @SerialName("session.state")
    data object SessionState : Op
}

private const val OP_KEY = "op"

internal val opJson = Json {
    classDiscriminator = OP_KEY
    encodeDefaults = true
    explicitNulls = false
    ignoreUnknownKeys = true
}

/** The op as it goes on the wire: its name and its keyword arguments. */
fun Op.toCall(): Pair<String, JsonObject> {
    val encoded = opJson.encodeToJsonElement(Op.serializer(), this).jsonObject
    val name = (encoded.getValue(OP_KEY) as JsonPrimitive).content
    return name to JsonObject(encoded - OP_KEY)
}

/** The wire name, such as `input.tap`. */
val Op.wireName: String get() = toCall().first
