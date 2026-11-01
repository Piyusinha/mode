package com.piyush.mode.agent

import com.piyush.mode.core.common.SecretFilter
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.ops.Op
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The actions the model may choose from. Each phone action maps to one [Op]
 * and is offered only when the phone supports that op, so a new op on a
 * backend becomes available by adding its row here, never per app or task.
 *
 * The model works in screenshot pixels, the space it sees; [Geometry]
 * converts to screen points.
 */
object AgentTools {
    /** How screenshot pixels map to screen points. */
    class Geometry(val imageWidth: Int, val imageHeight: Int, val screenWidth: Double, val screenHeight: Double) {
        /** Screenshot pixel → screen point. */
        fun x(px: Double) = px * screenWidth / imageWidth
        fun y(py: Double) = py * screenHeight / imageHeight

        /** Screen point → screenshot pixel. */
        fun toPixelsX(pt: Double) = pt * imageWidth / screenWidth
        fun toPixelsY(pt: Double) = pt * imageHeight / screenHeight
    }

    class Tool(
        val name: String,
        val description: String,
        /** Wire name of the op it needs, or null for control tools (done, ask_user, wait). */
        val needs: String?,
        val params: List<Param>,
    )

    class Param(
        val name: String,
        val type: String,
        val description: String,
        val required: Boolean = true,
        val enum: List<String>? = null,
        /** For an array: the type of its items. */
        val items: String? = null,
    )

    private val reason = Param(
        "reason", "string",
        "Shown to the user as live progress: a few plain words on what you are doing for them (\"Searching for " +
            "Pintola oats\", \"Adding it to Dinner\"). No coordinates, button names or app internals.",
    )

    /** Offered on every action, so keeping a fact never costs a step of its own. */
    private val note = Param(
        "note", "string",
        "Optional. A fact from this screen to keep for the rest of the task: an event, a time, a name, a price, " +
            "anything the user asked to know. Never a password, code or card number.",
        required = false,
    )
    /** Offered on phone actions: the model states what should happen, and checks it on the next screen. */
    /** The plan item an action works on: progress on the Dynamic Island, without a step of its own. */
    private val item = Param(
        "item", "integer",
        "Optional. When you have a plan: the number of the plan item this action works on.",
        required = false,
    )

    private val expect = Param(
        "expect", "string",
        "Optional. What the screen should show after this action, in a few words (\"Appearance page opens\").",
        required = false,
    )

    private fun point(axis: String) = Param(axis, "number", "Screenshot pixel $axis of the target's centre.")

    val ALL: List<Tool> = listOf(
        Tool("tap", "Tap a point on the screen.", "input.tap", listOf(point("x"), point("y"), reason)),
        Tool("long_press", "Press and hold a point (context menus, rearranging).", "input.press", listOf(point("x"), point("y"), reason)),
        Tool(
            "swipe", "Drag a finger from one point to another: sliders, swiping cards, pull to refresh. " +
                "Not for scrolling lists (use scroll).", "input.drag",
            listOf(
                Param("x1", "number", "Start x, screenshot pixels."), Param("y1", "number", "Start y, screenshot pixels."),
                Param("x2", "number", "End x, screenshot pixels."), Param("y2", "number", "End y, screenshot pixels."),
                reason,
            ),
        ),
        Tool(
            "scroll", "Scroll to reveal more content. 'down' shows what is further down the page.", "input.scroll",
            listOf(
                Param("direction", "string", "Which way the content you want is.", enum = listOf("down", "up", "left", "right")),
                Param("x", "number", "Screenshot pixel x of the area to scroll; omit for the middle.", required = false),
                Param("y", "number", "Screenshot pixel y of the area to scroll; omit for the middle.", required = false),
                reason,
            ),
        ),
        Tool(
            "type_text", "Type into the focused text field (tap the field first). Any language works.", "input.text",
            listOf(Param("text", "string", "The text to type."), reason),
        ),
        Tool(
            "press_key", "Press one keyboard key, such as return to submit a search.", "input.keys",
            listOf(
                Param("key", "string", "The key.", enum = listOf("return", "delete", "tab", "escape", "space", "up", "down", "left", "right")),
                reason,
            ),
        ),
        Tool(
            "back", "Go back one screen inside the current app with the iOS edge swipe. Prefer the screen's own " +
                "Back / \"<\" button when there is one. To return to a different app, use open_app with its name.", "input.drag", listOf(reason),
        ),
        Tool(
            "control_center", "Open Control Center (quick toggles: Wi-Fi, Bluetooth, brightness, Focus, " +
                "flashlight, and Dark Mode by long-pressing brightness).", "input.drag", listOf(reason),
        ),
        Tool("home", "Go to the Home Screen.", "nav.home", listOf(reason)),
        Tool(
            "open_app", "Open an installed app by its name (or bundle id). Faster than finding its icon.", "apps.launch",
            listOf(
                Param("name", "string", "The app's name, as on its icon."),
                Param(
                    "fresh", "boolean",
                    "Optional. true starts the app on its home page (closes where it was left); use it to begin a new task in that app.",
                    required = false,
                ),
                reason,
            ),
        ),
        Tool(
            "plan", "Write your plan for a task with several parts: a short numbered list of a few words per item. " +
                "It is shown back to you every step; each action's item field says which item it works on.", null,
            listOf(Param("steps", "string", "The plan, one item per line, e.g. \"1. Turn off dark mode\\n2. Set brightness to half\"")),
        ),
        Tool(
            "recall", "Look up what Mode remembers: a person's details, a place, a preference, or the steps of a " +
                "skill listed under WHAT WORKED BEFORE. Use it when the task needs something you don't have.", null,
            listOf(Param("query", "string", "What to look up, e.g. \"Sonali\" or \"Message [Person] on WhatsApp\".")),
        ),
        Tool(
            "wait", "Wait for something to load or arrive before looking again.", null,
            listOf(Param("seconds", "number", "1 to 10."), reason),
        ),
        Tool(
            "ask_user", "Ask the user when blocked (a login, passcode or code), or for a choice that is theirs to " +
                "make (see the rules). Not to confirm sending, paying or deleting (Mode confirms those itself), and " +
                "never twice about the same thing. Give options when there are a few to pick from: the user taps one.", null,
            listOf(
                Param("question", "string", "One clear, short question."),
                Param(
                    "options", "array",
                    "Optional: 2 to 4 short choices the user can tap, each with what tells them apart " +
                        "(\"Ebi Tempura · ₹345 · prawn\"). They can still type something else.",
                    required = false, items = "string",
                ),
                Param("recommended", "string", "Optional: the option you would pick, exactly as written in options.", required = false),
            ),
        ),
        Tool(
            "done", "The task is complete, or cannot be done. Check the screen first.", null,
            listOf(
                Param(
                    "summary", "string",
                    "Your reply to the user, in Markdown, like a helpful chat answer: the answer first, then any " +
                        "details they asked to know (from your notes). Not your steps or notes.",
                ),
                Param("success", "boolean", "Whether the task was achieved."),
                Param(
                    "evidence", "string",
                    "What on the current screen shows it is done (\"Dark Mode switch is off\", \"message shows Delivered\"), " +
                        "or why it cannot be done.",
                ),
            ),
        ),
    ).map { tool ->
        when {
            tool.name == "done" -> tool
            tool.name == "plan" -> Tool(tool.name, tool.description, tool.needs, tool.params + note)
            tool.needs != null -> Tool(tool.name, tool.description, tool.needs, tool.params + note + item + expect)
            else -> Tool(tool.name, tool.description, tool.needs, tool.params + note + item)
        }
    }

    /** The tools this phone can honour; recall only when there is a memory to look in. */
    fun available(phoneOps: Set<String>, recall: Boolean = false): List<Tool> =
        ALL.filter { (it.needs == null || it.needs in phoneOps) && (recall || it.name != "recall") }

    /** OpenAI-style function-calling schema for [tools]. */
    fun schema(tools: List<Tool>): JsonArray = buildJsonArray {
        for (tool in tools) add(
            buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", tool.name)
                    put("description", tool.description)
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") {
                            for (p in tool.params) putJsonObject(p.name) {
                                put("type", p.type)
                                put("description", p.description)
                                p.enum?.let { values -> putJsonArray("enum") { values.forEach { add(JsonPrimitive(it)) } } }
                                p.items?.let { type -> putJsonObject("items") { put("type", type) } }
                            }
                        }
                        putJsonArray("required") { tool.params.filter { it.required }.forEach { add(JsonPrimitive(it.name)) } }
                        put("additionalProperties", false)
                    }
                }
            },
        )
    }

    /**
     * The op for a phone tool call, in screen points. Throws [IllegalArgumentException]
     * with a message for the model when an argument is missing or wrong.
     */
    fun toOp(call: ToolCall, g: Geometry): Op {
        val a = call.arguments
        fun num(name: String): Double = a[name]?.jsonPrimitive?.doubleOrNull ?: throw IllegalArgumentException("'${call.name}' needs a number '$name'")
        fun optNum(name: String): Double? = a[name]?.jsonPrimitive?.doubleOrNull
        fun str(name: String): String = a[name]?.jsonPrimitive?.contentOrNull ?: throw IllegalArgumentException("'${call.name}' needs '$name'")
        return when (call.name) {
            "tap" -> Op.Tap(g.x(num("x")), g.y(num("y")))
            "long_press" -> Op.LongPress(g.x(num("x")), g.y(num("y")))
            "swipe" -> {
                val (x1, y1, x2, y2) = listOf(num("x1"), num("y1"), num("x2"), num("y2"))
                // Seen on the phone: a "placeholder" swipe from a point to itself.
                require(kotlin.math.hypot(x2 - x1, y2 - y1) >= 20) {
                    "a swipe needs start and end points apart: to tap use tap, to scroll use scroll"
                }
                Op.Drag(g.x(x1), g.y(y1), g.x(x2), g.y(y2), duration = 0.3)
            }
            "scroll" -> {
                val x = optNum("x")?.let(g::x) ?: (g.screenWidth / 2)
                val y = optNum("y")?.let(g::y) ?: (g.screenHeight / 2)
                val v = g.screenHeight * 0.4
                val h = g.screenWidth * 0.6
                when (str("direction")) {
                    "down" -> Op.Scroll(x, y, dy = -v)
                    "up" -> Op.Scroll(x, y, dy = v)
                    "right" -> Op.Scroll(x, y, dy = 0.0, dx = -h)
                    "left" -> Op.Scroll(x, y, dy = 0.0, dx = h)
                    else -> throw IllegalArgumentException("scroll direction must be down, up, left or right")
                }
            }
            "type_text" -> Op.TypeText(str("text"))
            "press_key" -> Op.PressKey(str("key"))
            "home" -> Op.Home
            // Control Center: down from the top-right corner.
            "control_center" -> Op.Drag(g.screenWidth - 30, 2.0, g.screenWidth - 30, g.screenHeight * 0.55, duration = 0.35)
            // The iOS back gesture: from the very left edge, across, at mid height.
            "back" -> Op.Drag(1.0, g.screenHeight * 0.45, g.screenWidth * 0.75, g.screenHeight * 0.45, duration = 0.25)
            "open_app" -> Op.LaunchApp(str("name"), fresh = call.arguments["fresh"]?.jsonPrimitive?.booleanOrNull == true)
            else -> throw IllegalArgumentException("unknown action '${call.name}'")
        }
    }

    fun reasonOf(call: ToolCall): String = call.arguments["reason"]?.jsonPrimitive?.contentOrNull.orEmpty()

    /** What the model expects this action to do, if it said. */
    fun expectOf(call: ToolCall): String? = call.arguments["expect"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** The fact the model asked to keep with this action, if any. */
    /** The choices offered with a question, in order; empty when it is a free question. */
    fun optionsOf(call: ToolCall): List<String> =
        (call.arguments["options"] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            .distinct().take(MAX_OPTIONS)

    const val MAX_OPTIONS = 4

    fun itemOf(call: ToolCall): Int? = call.arguments["item"]?.jsonPrimitive?.contentOrNull?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    fun noteOf(call: ToolCall): String? = call.arguments["note"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        // Notes reach the prompt and the run log on disk: a secret is never kept, whatever the model wrote.
        ?.let(SecretFilter::hide)
}

internal fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
