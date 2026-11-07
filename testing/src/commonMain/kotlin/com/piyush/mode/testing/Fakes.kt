package com.piyush.mode.testing

import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.ChatResult
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.llm.Usage
import com.piyush.mode.ops.Op
import com.piyush.mode.ops.PhoneBackend
import com.piyush.mode.ops.wireName
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** A 402×874-point phone whose screen is 500×1000 in screenshots, with scripted text per screen. */
@OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
public class FakePhone(
    var screens: List<List<Pair<String, Pair<Double, Double>>>>,
    private val supported: Set<String> = setOf(
        "screen.capture", "screen.text", "input.tap", "input.scroll", "input.text", "input.keys", "nav.home", "apps.launch",
        "input.drag",
    ),
) : PhoneBackend {
    val actions = mutableListOf<Op>()
    /** Base64 frames handed out by successive captures; the last one repeats. */
    var frames: MutableList<String> = mutableListOf("AAAA")
    var captures = 0
    /** Which window a text is in ("ime" for the keyboard's keys), for phones that report windows. */
    var windows: Map<String, String> = emptyMap()
    /** Screens (by index) the phone will not show: the capture says private. */
    var privateScreens: Set<Int> = emptySet()
    private var screen = 0
    override val name = "fake"
    override suspend fun ops() = supported
    private fun frameFor(index: Int): String = kotlin.io.encoding.Base64.encode("frame $index".encodeToByteArray())

    override suspend fun send(op: Op): JsonElement = when (op) {
        is Op.ScreenCapture -> if (screen in privateScreens) buildJsonObject {
            captures++
            put("private", true)
            put("bounds", buildJsonObject { put("x", 0); put("y", 0); put("w", 402.0); put("h", 874.0); put("id", 0) })
        } else buildJsonObject {
            captures++
            // A frame per screen, as a phone gives (the agent treats an identical frame after an action as stale).
            put("jpeg_b64", if (frames.size > 1) frames.removeAt(0) else if (frames.first() == "AAAA") frameFor(screen) else frames.first())
            put("bounds", buildJsonObject { put("x", 0); put("y", 0); put("w", 402.0); put("h", 874.0); put("id", 0) })
            put("img_px", buildJsonObject { put("w", 500); put("h", 1000) })
        }
        is Op.ScreenText -> buildJsonArray {
            for ((text, at) in screens[screen.coerceAtMost(screens.lastIndex)]) add(
                buildJsonObject {
                    put("text", text); put("x", at.first); put("y", at.second); put("confidence", 1.0)
                    windows[text]?.let { put("window", it) }
                },
            )
        }
        else -> {
            require(op.wireName in supported)
            actions += op
            screen++
            if (op is Op.LaunchApp) JsonPrimitive("bundle.${op.name}") else JsonNull
        }
    }
}

public fun call(name: String, vararg args: Pair<String, Any>) = ToolCall(
    name,
    JsonObject(
        args.associate { (k, v) ->
            k to when (v) {
                is Number -> JsonPrimitive(v)
                is Boolean -> JsonPrimitive(v)
                is List<*> -> kotlinx.serialization.json.JsonArray(v.map { JsonPrimitive(it.toString()) })
                else -> JsonPrimitive(v.toString())
            }
        },
    ),
)

/** Answers each step with the next scripted call and records which model was asked. */
public class ScriptedModel(vararg replies: ToolCall?) : LlmClient {
    private val script = replies.toList()
    private var next = 0
    val models = mutableListOf<String>()
    var lastTools: JsonArray? = null
    var lastUserText = ""
    var lastSystemText = ""
    override suspend fun chat(model: String, messages: List<ChatMessage>, tools: JsonArray): ChatResult {
        models += model
        lastTools = tools
        lastUserText = messages.last().text
        lastSystemText = messages.firstOrNull { it.role == "system" }?.text.orEmpty()
        val reply = if (next < script.size) script[next] else call("done", "evidence" to "on screen", "summary" to "out of script")
        next++
        return ChatResult(reply, "", Usage(100, 10, 0.001))
    }
}
