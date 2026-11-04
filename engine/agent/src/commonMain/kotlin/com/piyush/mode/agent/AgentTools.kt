package com.piyush.mode.agent

import com.piyush.mode.core.common.SecretFilter
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.ops.DeviceData
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

    /** Tools that reach a result without the screen (offered when the platform has them). */
    val DIRECT = setOf("open_url", "run_shortcut", "read_calendar", "find_contact")

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
    /** Offered on every action: key facts, kept and shown every step, so nothing found is looked for twice. */
    private val facts = Param(
        "facts", "array",
        "Optional. Key facts you found, each as \"name: value\" (\"flight: 6E223\", \"PNR: ABC123\", \"total: ₹521\"). " +
            "Kept for the task and shown every step: use them instead of looking again. Never a password, code or card number.",
        required = false, items = "string",
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

    private fun point(axis: String) = Param(
        axis, "number", "Screenshot pixel $axis of the target's centre; only for what has no id (an icon without text).", required = false,
    )

    private val id = Param("id", "integer", "The number [id] of the text to act on, from the screen's text. Exact: prefer it.", required = false)

    /** Tools that act on an element: they take its [id] in place of x and y. */
    private val BY_ID = setOf("tap", "long_press", "type_text")

    val ALL: List<Tool> = listOf(
        Tool("tap", "Tap a piece of text by its id, or a point (x and y) for an icon without text.", "input.tap", listOf(id, point("x"), point("y"), reason)),
        Tool("long_press", "Press and hold (context menus, rearranging): an id, or x and y.", "input.press", listOf(id, point("x"), point("y"), reason)),
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
            "type_text", "Type into a text field. Give the field's id (or x and y): Mode taps it first if the keyboard is not up. " +
                "Any language works.", "input.text",
            listOf(
                Param("text", "string", "The text to type."),
                Param("id", "integer", "Optional. The field's [id] (its placeholder or label), to focus it.", required = false),
                Param("x", "number", "Optional. The field's centre, in screenshot pixels, to focus it.", required = false),
                Param("y", "number", "Optional. The field's centre, in screenshot pixels.", required = false),
                Param("submit", "boolean", "Optional. true presses return after typing (to search or send).", required = false),
                Param("clear", "boolean", "Optional. true deletes what the field holds first (a quantity's 1), then types.", required = false),
                reason,
            ),
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
            "decide", "Ask your planning mind to make a choice for you: which option is best, which item matches, " +
                "what to do next when you are unsure. It sees the brief, everything you saw while scrolling and " +
                "this screen, and tells you what to pick and tap. Use it instead of scrolling back to re-check or " +
                "planning again.", null,
            listOf(Param("question", "string", "The choice to make, e.g. \"Which sushi restaurant is highest rated, ignoring ads?\"")),
        ),
        Tool(
            "recall", "Look up what Mode remembers: a person's details, a place, a preference, or the steps of a " +
                "skill listed under WHAT WORKED BEFORE. Use it when the task needs something you don't have.", null,
            listOf(Param("query", "string", "What to look up, e.g. \"Asha\" or \"Message [Person] on WhatsApp\".")),
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
            "wait_for_user", "Hand over to the user for something only they can do on this screen: a UPI or bank PIN, " +
                "a password or passcode, Face ID, or a screen that is black or blank (hidden from Mode). Mode waits " +
                "until the screen moves on, then you carry on.", null,
            listOf(Param("what", "string", "What they should do, in a few words: \"Enter your UPI PIN in PhonePe\"."), reason),
        ),
        Tool(
            "open_url", "Open a link or an app's own URL directly, with no navigation: a web page, a search or product " +
                "page in an app that has a URL scheme, a map, or shortcuts://run-shortcut?name=… Surer and shorter than " +
                "finding it by taps. Then read the screen.", null,
            listOf(Param("url", "string", "The full URL (https://…, or an app scheme such as zomato://, maps://, tel:, mailto:)."), reason),
        ),
        Tool(
            "run_shortcut", "Run one of the user's Shortcuts by name, with optional text input; then read the screen.", null,
            listOf(
                Param("name", "string", "The Shortcut's exact name."),
                Param("input", "string", "Optional. Text to hand the Shortcut.", required = false),
                reason,
            ),
        ),
        Tool(
            "read_calendar", "Read the user's calendar events from now, without opening the Calendar app.", null,
            listOf(Param("days", "integer", "How many days ahead to read (1 for today, 7 for the week)."), reason),
        ),
        Tool(
            "find_contact", "Look up a person in the user's contacts: their number and email, without opening Contacts.", null,
            listOf(Param("name", "string", "Their name, or part of it."), reason),
        ),
        Tool(
            "done", "The task is complete, or cannot be done. Check the screen first.", null,
            listOf(
                Param(
                    "summary", "string",
                    "Your reply to the user, in Markdown, as their personal assistant: warm and first person, the answer " +
                        "first, then any details they asked to know (from your notes). Not your steps or notes.",
                ),
                Param(
                    "spoken", "string",
                    "The same reply said out loud: one or two short, natural sentences, as a personal assistant would " +
                        "say it to them (\"Done, Dark Mode is off.\", \"You've got three things today; the first is standup at ten.\"). " +
                        "No lists, tables, links or Markdown.",
                ),
                Param("success", "boolean", "Whether the task was achieved."),
                Param(
                    "evidence", "string",
                    "What on the current screen shows it is done (\"Dark Mode switch is off\", \"message shows Delivered\"), " +
                        "or why it cannot be done: quote the screen.",
                ),
                Param(
                    "blocked_by", "string",
                    "When success is false: what blocks it. closed or unavailable (the place or item), needs_info or " +
                        "needs_choice (the user must say), login_or_payment (only they can do it; use wait_for_user instead), " +
                        "app_broken, not_possible.",
                    required = false,
                    enum = listOf("closed", "unavailable", "needs_info", "needs_choice", "login_or_payment", "app_broken", "not_possible"),
                ),
                Param(
                    "routes_tried", "array",
                    "When success is false: each route you tried and what happened (\"Zomato search: closed until 11\", " +
                        "\"Mail search 'IndiGo': nothing\").",
                    required = false, items = "string",
                ),
            ),
        ),
    ).map { tool ->
        when {
            tool.name == "done" -> tool
            tool.name == "plan" -> Tool(tool.name, tool.description, tool.needs, tool.params + note)
            tool.needs != null -> Tool(tool.name, tool.description, tool.needs, tool.params + note + facts + item + expect)
            tool.name in DIRECT -> Tool(tool.name, tool.description, tool.needs, tool.params + note + facts + item + expect)
            else -> Tool(tool.name, tool.description, tool.needs, tool.params + note + facts + item)
        }
    }

    /** The tools this phone can honour; recall only when there is a memory to look in. */
    /**
     * The tools this phone can honour; recall only with a memory to look in; decide only with a brain.
     * With a brief, plan is left out: the brief's steps are the plan (seen on the phone: nine re-plans
     * instead of one choice).
     */
    fun available(
        phoneOps: Set<String>, recall: Boolean = false, brain: Boolean = false, briefed: Boolean = false,
        device: DeviceData? = null,
    ): List<Tool> {
        val urls = "url.open" in phoneOps || device?.canOpenUrl == true
        return ALL.filter {
            (it.needs == null || it.needs in phoneOps) && (recall || it.name != "recall") &&
                (brain || it.name != "decide") && !(briefed && it.name == "plan") &&
                when (it.name) {
                    "open_url", "run_shortcut" -> urls
                    "read_calendar" -> device?.hasCalendar == true
                    "find_contact" -> device?.hasContacts == true
                    else -> true
                }
        }
    }

    /** The direct routes on offer, for the brief ("open_url, read_calendar"). */
    fun directRoutes(phoneOps: Set<String>, device: DeviceData?): List<String> =
        available(phoneOps, device = device).map { it.name }.filter { it in DIRECT }

    /** The Shortcuts app's URL to run [name], with [input] as text when given. */
    fun shortcutUrl(name: String, input: String?): String =
        "shortcuts://run-shortcut?name=" + percent(name) + (input?.takeIf { it.isNotBlank() }?.let { "&input=text&text=" + percent(it) }.orEmpty())

    private fun percent(s: String): String = buildString {
        for (b in s.encodeToByteArray()) {
            val c = b.toInt() and 0xff
            if (c in 0x30..0x39 || c in 0x41..0x5a || c in 0x61..0x7a || c == 0x2d || c == 0x2e || c == 0x5f || c == 0x7e) append(c.toChar())
            else append('%').append(((c shr 4) and 0xf).toString(16).uppercase()).append((c and 0xf).toString(16).uppercase())
        }
    }


    /** The facts a call carries, as "name: value" lines. */
    fun factsOf(call: ToolCall): List<String> =
        (call.arguments["facts"] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf { f -> f.isNotEmpty() } }

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
    /** The call with an element's [id] turned into its centre (x and y), as the rest of Mode expects. */
    fun withElement(call: ToolCall, layout: ScreenLayout): ToolCall {
        if (call.name !in BY_ID) return call
        // Seen on the phone: "id": "[8]", copied with its brackets.
        val wanted = call.arguments["id"]?.jsonPrimitive?.contentOrNull?.trim()?.trim('[', ']', ' ')?.toDoubleOrNull()?.toInt() ?: return call
        val e = layout.byId(wanted) ?: return call
        return ToolCall(call.name, JsonObject(call.arguments + mapOf("x" to JsonPrimitive(e.x), "y" to JsonPrimitive(e.y))))
    }

    fun toOp(call: ToolCall, g: Geometry): Op {
        // An id that was not turned into a point (withElement) is not on this screen.
        if (call.name in BY_ID && call.arguments["id"] != null && call.arguments["x"] == null) {
            throw IllegalArgumentException(
                "there is no [${call.arguments["id"]?.jsonPrimitive?.contentOrNull}] on this screen: use an id from the list, or x and y",
            )
        }
        val a = call.arguments
        // Seen on the phone: one model sent "x": [228, 269] (a point) for a tap; read the pair.
        fun pair(name: String): List<Double>? =
            (a[name] as? kotlinx.serialization.json.JsonArray)?.mapNotNull { it.jsonPrimitive.doubleOrNull }?.takeIf { it.size == 2 }
        fun raw(name: String): Double? = a[name]?.let { v ->
            (v as? kotlinx.serialization.json.JsonPrimitive)?.doubleOrNull
                ?: pair(name)?.let { p -> if (name.startsWith("x")) p[0] else p[1] }
                ?: if (name.startsWith("y")) pair("x" + name.drop(1))?.get(1) else null
        } ?: if (name.startsWith("y")) pair("x" + name.drop(1))?.get(1) else null
        // A point outside the screenshot is not tapped somewhere off screen: the model is told the range.
        // (Seen: x 659 on a 460-pixel-wide screenshot, a tap at 576 pt on a 402 pt screen.)
        fun inRange(name: String, v: Double): Double {
            val max = if (name.startsWith("x")) g.imageWidth else g.imageHeight
            require(v in 0.0..max.toDouble()) {
                "'$name' $v is outside the screenshot: use screenshot pixels, 0 to $max (it is ${g.imageWidth}×${g.imageHeight})"
            }
            return v
        }
        fun num(name: String): Double = raw(name)?.let { inRange(name, it) } ?: throw IllegalArgumentException("'${call.name}' needs a number '$name'")
        fun optNum(name: String): Double? = raw(name)?.let { inRange(name, it) }
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
                // Most of a screen: fewer scrolls through a list, with overlap (SEEN WHILE SCROLLING keeps every line).
                val v = g.screenHeight * SCROLL_SHARE
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
            "open_url" -> Op.OpenUrl(str("url"))
            "run_shortcut" -> Op.OpenUrl(shortcutUrl(str("name"), call.arguments["input"]?.jsonPrimitive?.contentOrNull))
            else -> throw IllegalArgumentException("unknown action '${call.name}'")
        }
    }

    fun reasonOf(call: ToolCall): String = call.arguments["reason"]?.jsonPrimitive?.contentOrNull.orEmpty()

    /** What the model expects this action to do, if it said. */
    fun expectOf(call: ToolCall): String? = call.arguments["expect"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

    /** The fact the model asked to keep with this action, if any. */
    /** The choices offered with a question, in order; empty when it is a free question. */
    fun routesTriedOf(call: ToolCall): List<String> =
        (call.arguments["routes_tried"] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }.distinct()

    fun optionsOf(call: ToolCall): List<String> =
        (call.arguments["options"] as? kotlinx.serialization.json.JsonArray).orEmpty()
            .mapNotNull { it.jsonPrimitive.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }
            .distinct().take(MAX_OPTIONS)

    const val MAX_OPTIONS = 4
    const val SCROLL_SHARE = 0.6

    /** Where type_text was told the field is (screen points), when it gave x and y inside the screenshot. */
    fun focusPoint(call: ToolCall, g: Geometry): Pair<Double, Double>? {
        if (call.arguments["x"] == null || call.arguments["y"] == null) return null
        val tap = runCatching { toOp(ToolCall("tap", call.arguments), g) }.getOrNull() as? Op.Tap ?: return null
        return tap.x to tap.y
    }

    fun itemOf(call: ToolCall): Int? = call.arguments["item"]?.jsonPrimitive?.contentOrNull?.trim()?.toIntOrNull()?.takeIf { it > 0 }

    fun noteOf(call: ToolCall): String? = call.arguments["note"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        // Notes reach the prompt and the run log on disk: a secret is never kept, whatever the model wrote.
        ?.let(SecretFilter::hide)
}

internal fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
