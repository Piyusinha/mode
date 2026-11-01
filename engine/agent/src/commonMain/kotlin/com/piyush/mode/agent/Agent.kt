package com.piyush.mode.agent

import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.ChatResult
import com.piyush.mode.llm.ToolCall
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.LlmFailed
import com.piyush.mode.ops.Op
import com.piyush.mode.ops.PhoneBackend
import com.piyush.mode.ops.wireName
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.double
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.time.TimeSource

private fun JsonElement.jsonPrimitiveOrNull(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

class AgentConfig(
    /** OpenRouter model id for every step. */
    val model: String = DEFAULT_MODEL,
    /** Used for one step after the cheap model makes no progress twice; null to never step up. */
    val strongModel: String? = DEFAULT_STRONG_MODEL,
    val maxSteps: Int = 30,
    /** Longer side of the screenshot sent to the model, in pixels. */
    val imageMaxSide: Int = 1000,
    /** Pause after each action before checking that the screen is still. */
    val settleMs: Long = 400,
    /** After each action, wait until the screen stops changing before looking again. */
    val waitForStill: Boolean = true,
    /** How many past steps the model is reminded of. */
    val historySteps: Int = 12,
    /** Stop before a step once the task has cost this much (US dollars); null: no limit. */
    val maxCostUsd: Double? = null,
) {
    companion object {
        const val DEFAULT_MODEL = "anthropic/claude-haiku-5.5"
        const val DEFAULT_STRONG_MODEL = "anthropic/claude-sonnet-5.5"
    }
}

/** What the agent needs from the person it works for. */
interface UserGate {
    /** Yes/no before a risky action. */
    suspend fun confirm(question: String): Boolean

    /** A free-text answer, or null to stop the task. */
    suspend fun ask(question: String): String?
}

/** One step, for the live log. */
class StepRecord(
    val index: Int,
    val model: String,
    val action: String,
    val reason: String,
    val outcome: String,
    val costUsd: Double,
    val millis: Long,
)

sealed interface AgentOutcome {
    class Done(val summary: String, val success: Boolean) : AgentOutcome
    class Stopped(val why: String) : AgentOutcome
    class Failed(val why: String) : AgentOutcome
}

class AgentResult(val outcome: AgentOutcome, val steps: Int, val costUsd: Double, val millis: Long)

/**
 * The look → decide → act loop. It knows nothing about any app or task: each
 * step it shows the model the task, what happened so far and the screen (a
 * small screenshot plus the text on it with positions), the model picks one
 * tool, and the agent carries it out through the [PhoneBackend].
 */
class Agent(
    private val phone: PhoneBackend,
    private val llm: LlmClient,
    private val gate: UserGate,
    private val config: AgentConfig = AgentConfig(),
    private val onStep: (StepRecord) -> Unit = {},
    private val onStatus: (String) -> Unit = {},
    private val trace: AgentTrace = AgentTrace.None,
) {
    private val clock = TimeSource.Monotonic

    /**
     * Runs [task]. [startsInMode]: the task was started from Mode's own screen,
     * which the model must not mistake for the task (it once waited 12 steps
     * on Mode's live log); the first step then shows no screen and the model
     * goes straight to the app it needs. Started from anywhere else (Siri,
     * Shortcuts), the agent works from whatever is on screen.
     */
    suspend fun run(task: String, startsInMode: Boolean = false, context: TaskContext = TaskContext.Empty): AgentResult {
        val result = runSteps(task, startsInMode, context)
        trace.runEnded(result)
        return result
    }

    private suspend fun runSteps(task: String, startsInMode: Boolean, context: TaskContext): AgentResult {
        val started = clock.markNow()
        val ops = phone.ops()
        val tools = AgentTools.available(ops)
        val schema = AgentTools.schema(tools)
        trace.runStarted(task, config, tools.map { it.name })
        val history = mutableListOf<String>()
        var cost = 0.0
        var lastScreen: String? = null
        var lastWasPhoneAction = false
        var noProgress = 0
        var badReplies = 0
        var waits = 0
        /** The app the agent last opened, to return to after the user answers a question. */
        var lastApp: String? = null
        /** The model's own plan for multi-part tasks, shown back to it every step. */
        var plan: String? = null
        var inMode = startsInMode
        /** The user's answers, shown on their own every step so they are not lost in the history. */
        val answers = mutableListOf<Pair<String, String>>()
        /** The user answered and the agent has not acted since: another question would repeat it. */
        var answeredSinceAction = false
        /** The text typed last, shown to the user when Mode confirms a risky tap such as Send. */
        var lastTyped: String? = null
        /** Facts the model kept on the way; shown every step for the rest of the task. */
        val notes = mutableListOf<String>()
        /** A successful done was sent back once to be checked; the next one is accepted. */
        var verifyAsked = false

        fun result(outcome: AgentOutcome, step: Int) =
            AgentResult(outcome, step, cost, started.elapsedNow().inWholeMilliseconds)

        for (step in 1..config.maxSteps) {
            currentCoroutineContext().ensureActive()
            config.maxCostUsd?.let { limit ->
                if (cost >= limit) {
                    val last = history.lastOrNull()?.let { " Last: ${it.substringAfter(". ").substringBefore(" — ")}." }.orEmpty()
                    return result(
                        AgentOutcome.Stopped("Reached the ${money(limit)} limit for this task after ${step - 1} steps.$last"),
                        step - 1,
                    )
                }
            }
            val stepStarted = clock.markNow()
            onStatus("Step $step: looking")
            val lookStarted = clock.markNow()
            val screen = look()
            trace.looked(
                step, screen.jpeg, screen.geometry.imageWidth, screen.geometry.imageHeight, screen.texts,
                lookStarted.elapsedNow().inWholeMilliseconds,
            )

            // Tell the model what its last action did, and notice when nothing changes.
            if (lastWasPhoneAction) {
                val changed = screen.signature != lastScreen
                noProgress = if (changed) 0 else noProgress + 1
                if (history.isNotEmpty()) {
                    history[history.lastIndex] += if (changed) " → screen changed" else UNCHANGED
                }
            }
            lastScreen = screen.signature
            val stepUp = config.strongModel != null && (noProgress >= 2 || badReplies >= 2)
            if (stepUp) {
                noProgress = 0
                badReplies = 0
            }

            val messages = ContextBuilder.build(
                ContextBuilder.Input(
                    systemPrompt = SYSTEM_PROMPT,
                    task = task,
                    context = context,
                    step = step,
                    maxSteps = config.maxSteps,
                    answers = answers,
                    plan = plan,
                    notes = notes,
                    history = history,
                    recentSteps = config.historySteps,
                    lastApp = lastApp,
                    screen = ContextBuilder.Screen(screen.jpeg, screen.geometry.imageWidth, screen.geometry.imageHeight, screen.texts),
                    hideScreen = inMode,
                ),
            )
            inMode = false
            val answer = think(step, if (stepUp) config.strongModel!! else config.model, stepUp, messages, schema)
            val reply = answer.reply
            val model = answer.model
            cost += reply.usage.costUsd
            val call = reply.toolCall
            var actionStarted = clock.markNow()
            lastWasPhoneAction = false

            fun record(action: String, reason: String, outcome: String, op: String? = null) {
                onStep(StepRecord(step, model, action, reason, outcome, reply.usage.costUsd, stepStarted.elapsedNow().inWholeMilliseconds))
                trace.acted(step, action, op, outcome, actionStarted.elapsedNow().inWholeMilliseconds)
                history += "$step. $action — $outcome"
            }

            if (call == null || tools.none { it.name == call.name }) {
                badReplies++
                record("(no valid action)", reply.text.take(120), "ignored: always call exactly one of the listed tools")
                continue
            }
            val reason = AgentTools.reasonOf(call)
            AgentTools.noteOf(call)?.let {
                notes += it
                trace.noted(step, it)
            }
            when (call.name) {
                "done" -> {
                    val summary = call.arguments.string("summary").orEmpty()
                    val success = call.arguments["success"]?.jsonPrimitive?.booleanOrNull ?: true
                    val evidence = call.arguments.string("evidence")?.trim().orEmpty()
                    val lastUnchanged = history.lastOrNull()?.endsWith(UNCHANGED) == true
                    // Catch "I tapped Send" when nothing was sent: once per task, ask for a check.
                    if (success && !verifyAsked && (evidence.isEmpty() || lastUnchanged)) {
                        verifyAsked = true
                        val why = if (lastUnchanged) "your last action did not change the screen" else "you did not say what on the screen shows it"
                        trace.verifyRejected(step, why)
                        record(
                            "done (not accepted yet)", summary,
                            "not accepted yet: $why. Look at the screen and check the task really happened; if it did, " +
                                "call done again and say what on the screen shows it",
                        )
                        continue
                    }
                    record("done", summary, (if (success) "finished" else "gave up") + evidence.takeIf { it.isNotEmpty() }?.let { " ($it)" }.orEmpty())
                    return result(AgentOutcome.Done(summary, success), step)
                }
                "ask_user" -> {
                    val question = call.arguments.string("question").orEmpty()
                    // Seen on the phone: four questions in a row about the same message,
                    // each answered. Nothing changed since the last answer, so act on it.
                    if (answeredSinceAction) {
                        badReplies++
                        record(
                            "ask_user (not asked)", question,
                            "not asked: the user already answered \"${answers.last().second}\" and nothing has " +
                                "happened since. Act on that answer now; where it leaves something open, pick the " +
                                "most sensible reading yourself",
                        )
                        continue
                    }
                    onStatus("Waiting for you")
                    val reply2 = gate.ask(question)
                    if (reply2 == null) {
                        record("ask_user", question, "you stopped the task")
                        return result(AgentOutcome.Stopped("Stopped at: $question"), step)
                    }
                    // Answering happened in Mode; go straight back to where the agent was.
                    val back = lastApp?.let { app ->
                        runCatching { phone.send(Op.LaunchApp(app)) }.isSuccess.also { settle() }
                    } == true
                    answers += question to reply2
                    answeredSinceAction = true
                    record(
                        "ask_user", question,
                        "the user answered \"$reply2\"" + if (back) " (Mode reopened $lastApp)" else
                            " (Mode is on screen now: open the app you need)",
                    )
                }
                "plan" -> {
                    plan = call.arguments.string("steps")?.trim()?.take(1500)
                    record("plan", "", "plan saved; it is shown to you every step: now do the first open item")
                }
                "wait" -> {
                    waits++
                    val seconds = (call.arguments["seconds"]?.jsonPrimitive?.doubleOrNull ?: 2.0).coerceIn(0.5, 10.0)
                    delay((seconds * 1000).toLong())
                    // Waiting again and again is not progress: say so, and step up.
                    if (waits >= 3) {
                        noProgress = 2
                        record("wait ${seconds}s", reason, "waited $waits times in a row; waiting is not getting anywhere, act instead")
                    } else {
                        record("wait ${seconds}s", reason, "waited")
                    }
                }
                else -> {
                    waits = 0
                    val op = try {
                        AgentTools.toOp(call, screen.geometry)
                    } catch (e: IllegalArgumentException) {
                        badReplies++
                        record(call.name, reason, "not done: ${e.message}")
                        continue
                    }
                    val described = describe(call, op, screen)
                    val risky = riskyTarget(op, screen)
                    val typed = lastTyped?.let { " It typed: \"${it.take(200)}\"." }.orEmpty()
                    if (risky != null && !gate.confirm("Mode is about to tap \"$risky\" ($reason).$typed Allow it?")) {
                        record(described, reason, "the user did not allow it; do not try again without asking")
                        continue
                    }
                    actionStarted = clock.markNow()
                    val outcome = try {
                        val result = phone.send(op)
                        lastWasPhoneAction = true
                        answeredSinceAction = false
                        if (op is Op.TypeText) lastTyped = op.text
                        badReplies = 0
                        if (op is Op.LaunchApp) lastApp = result.jsonPrimitiveOrNull() ?: op.name
                        "ok"
                    } catch (e: Exception) {
                        "failed: ${e.message}"
                    }
                    // Never look (or tap) while the screen is still moving.
                    if (lastWasPhoneAction) settle()
                    record(described, reason, outcome, op.toString())
                }
            }
        }
        return result(AgentOutcome.Stopped("Reached the limit of ${config.maxSteps} steps"), config.maxSteps)
    }

    private class Answer(val reply: ChatResult, val model: String)

    /**
     * Asks the model. A failure is not the end of the task: a transient error is
     * retried once, and a stronger model that fails hands the step back to the
     * main model. Only the main model failing twice stops the run.
     */
    private suspend fun think(step: Int, model: String, steppedUp: Boolean, messages: List<ChatMessage>, schema: JsonArray): Answer {
        val tries = buildList {
            add(model)
            if (steppedUp && model != config.model) add(config.model) else add(model)
        }
        var last: Exception? = null
        for ((attempt, candidate) in tries.withIndex()) {
            onStatus("Step $step: thinking ($candidate)")
            trace.asking(step, candidate, steppedUp && candidate != config.model, messages.last().text)
            val started = clock.markNow()
            try {
                val reply = llm.chat(candidate, messages, schema)
                trace.answered(
                    step, candidate, reply.toolCall?.name, reply.toolCall?.arguments, reply.text, reply.usage,
                    started.elapsedNow().inWholeMilliseconds,
                )
                return Answer(reply, candidate)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                trace.answered(step, candidate, null, null, "", null, started.elapsedNow().inWholeMilliseconds, e.message ?: e.toString())
                last = e
                if (attempt == 0) delay(1500)
            }
        }
        throw last ?: LlmFailed("the model did not answer")
    }

    /**
     * Waits until the screen stops changing (a scroll coasting, a page
     * loading, an animation), so the next look and tap see where things
     * really are. Compares tiny screenshots; gives up after about 3 s.
     */
    private suspend fun settle() {
        delay(config.settleMs)
        if (!config.waitForStill) return
        var previous = tinyShot() ?: return
        repeat(6) {
            delay(150)
            val current = tinyShot() ?: return
            if (current.contentEquals(previous)) return
            previous = current
        }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun tinyShot(): ByteArray? = runCatching {
        val capture = phone.send(Op.ScreenCapture(maxSize = 160)).jsonObject
        Base64.decode(capture.getValue("jpeg_b64").jsonPrimitive.content)
    }.getOrNull()

    /** What the model sees of the screen. */
    private class Screen(
        val jpeg: ByteArray,
        val geometry: AgentTools.Geometry,
        /** Text with its centre in screenshot pixels. */
        val texts: List<Triple<String, Int, Int>>,
    ) {
        /** Text and rough position: a scroll changes it even when the words do not. */
        val signature: String = texts.joinToString("|") { "${it.first}@${it.third / 12}" }
    }

    @OptIn(ExperimentalEncodingApi::class)
    private suspend fun look(): Screen {
        val capture = phone.send(Op.ScreenCapture(maxSize = config.imageMaxSide)).jsonObject
        val px = capture.getValue("img_px").jsonObject
        val bounds = capture.getValue("bounds").jsonObject
        val g = AgentTools.Geometry(
            px.getValue("w").jsonPrimitive.int, px.getValue("h").jsonPrimitive.int,
            bounds.getValue("w").jsonPrimitive.double, bounds.getValue("h").jsonPrimitive.double,
        )
        val texts = (phone.send(Op.ScreenText()) as JsonArray).map {
            val box = it.jsonObject
            Triple(
                box.getValue("text").jsonPrimitive.contentOrNull.orEmpty(),
                g.toPixelsX(box.getValue("x").jsonPrimitive.double).roundToInt(),
                g.toPixelsY(box.getValue("y").jsonPrimitive.double).roundToInt(),
            )
        }
        return Screen(Base64.decode(capture.getValue("jpeg_b64").jsonPrimitive.content), g, texts)
    }

    /** A readable action for the log, naming the text tapped when there is one. */
    private fun describe(call: ToolCall, op: Op, screen: Screen): String = when (op) {
        is Op.Tap -> "tap " + (nearestText(op.x, op.y, screen)?.let { "\"$it\"" } ?: "(${pixel(op.x, op.y, screen)})")
        is Op.LongPress -> "long press " + (nearestText(op.x, op.y, screen)?.let { "\"$it\"" } ?: "(${pixel(op.x, op.y, screen)})")
        is Op.TypeText -> "type \"${op.text}\""
        is Op.LaunchApp -> "open ${op.name}"
        is Op.PressKey -> "press ${op.combo}"
        is Op.Scroll -> "scroll ${call.arguments.string("direction").orEmpty()}"
        is Op.Drag -> "swipe"
        Op.Home -> "home"
        else -> call.name
    }

    private fun pixel(x: Double, y: Double, screen: Screen): String =
        "${screen.geometry.toPixelsX(x).roundToInt()}, ${screen.geometry.toPixelsY(y).roundToInt()}"

    /** The text whose centre is closest to a point (in screen points), if any is near. */
    private fun nearestText(x: Double, y: Double, screen: Screen): String? {
        val g = screen.geometry
        val px = g.toPixelsX(x)
        val py = g.toPixelsY(y)
        return screen.texts
            .map { it to hypot(it.second - px, it.third - py) }
            .filter { it.second < NEAR_PX }
            .minByOrNull { it.second }
            ?.first?.first
    }

    /** The label of a tap target that looks irreversible (pay, book, send…), or null. */
    private fun riskyTarget(op: Op, screen: Screen): String? {
        if (op !is Op.Tap) return null
        val label = nearestText(op.x, op.y, screen) ?: return null
        return label.takeIf { RiskGuard.isRisky(it) }
    }

    private fun money(usd: Double): String = "$" + ((usd * 10000).roundToInt() / 10000.0).toString()

    companion object {
        private const val NEAR_PX = 60.0
        private const val UNCHANGED = " → screen did NOT change"

        val SYSTEM_PROMPT = """
            You operate an iPhone for the user, one action at a time, to complete their task.
            Each turn you get the task, what you did so far (with whether each action changed the
            screen), a screenshot, and the text on the screen with positions. Reply with exactly one
            tool call. After each action you will see the new screen.

            You are run by an app called Mode. If Mode's own screen (its task log, "Step N: thinking",
            the Agent/Compare/Logs tabs) is showing, that is you: never wait on it, leave it and work in
            the app the task needs.

            How to act:
            - Coordinates are screenshot pixels. To tap a piece of text, use its listed centre. For icons
              without text, read the position off the screenshot.
            - Open apps with open_app. Apps reopen where they were left, so to go back use the screen's
              own Back / "<" / Cancel / Done / X button, or the back tool. Home then open_app does not reset
              an app.
            - To see more of a list or page, use scroll (never swipe for scrolling). Search fields and
              search pages: tap the field, type, then press return or pick a result; Cancel closes them.
            - Tap a text field before typing into it.
            - If an action did not change the screen, do not repeat it: try another way.
            - Deal with popups, permission prompts and ads yourself (dismiss or allow as the task needs).
            - Work on your own. Do not ask the user how to navigate or whether to go back.

            Use what the task gives you: names, times, places and message text. In "send a message to
            Sonali saying I'm late" or "message Sonali: sending this automatically", the words after the
            name are the message: type them. If the task wants a message sent but gives no text, write a
            short, natural one that fits the task instead of asking.

            Do not ask for permission to send, pay, book, post or delete: Mode itself shows the user what
            you typed and asks them right before such a tap. So type the message and tap Send.

            Ask the user (ask_user) only when you are truly blocked:
            - a login, passcode, Face ID, verification code or payment details are needed;
            - a choice only the user can make, with no sensible default (which of two contacts with the
              same name, which address, which plan).
            Ask once. Their answer is final: act on it, and never ask again to confirm it.
            Never type passwords, passcodes or card numbers.

            Plan multi-part tasks:
            - If the task has several parts ("turn off dark mode, set brightness to half and open YouTube"),
              first call plan with a short numbered list, then do the items in order. Call plan again to
              mark items done or change course. Finish (done) only when every item is done.

            Know iOS and pick the fastest route (from wherever you are):
            - Settings has a search field at the top of its main page: searching a setting's name ("Dark
              Mode", "Wi-Fi", "Brightness", "Bluetooth") jumps straight to it.
            - Control Center (control_center tool) has quick toggles: Wi-Fi, Bluetooth, Airplane Mode,
              brightness, volume, Focus, Flashlight; long-press a control for more (e.g. Dark Mode under
              brightness). Swipe it closed or press home when done.
            - Spotlight (swipe down in the middle of the Home Screen) finds apps, settings and contacts.
            - Most apps put Search, Back and tabs in the same places: tabs at the bottom, Back at top left.
            - You can start from any screen: you do not need to go home first.

            Keep what you find: when a screen shows something you will need later or the user asked to know
            (calendar events, times, prices, names, a tracking number), put it in the note field of your next
            action. Notes stay with you for the whole task; older steps are only summarised. Never note
            passwords, passcodes or card numbers.

            Finishing:
            - Before calling done, look at the screen: does it really show the task happened (the switch is
              off, the message shows as sent)? If your last action did nothing, fix that first.
            - Call done with a short summary (include what the user asked to know, from your notes) and the
              evidence on screen. If it cannot be done, call done with success false and say why.
            - Keep each reason to a few words.
        """.trimIndent()
    }
}

/** Generic words that mark an action as hard to undo. Not tied to any app. */
object RiskGuard {
    private val WORDS = listOf(
        "pay", "buy", "purchase", "order", "book", "confirm", "request", "send", "post", "delete",
        "remove", "subscribe", "checkout", "check out", "transfer", "submit", "place", "donate",
    )

    fun isRisky(label: String): Boolean {
        val text = label.lowercase()
        return WORDS.any { word -> Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(text) }
    }
}
