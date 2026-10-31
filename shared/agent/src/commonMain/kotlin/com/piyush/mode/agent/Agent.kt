package com.piyush.mode.agent

import com.piyush.mode.ops.Op
import com.piyush.mode.ops.PhoneBackend
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.json.JsonArray
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

class AgentConfig(
    /** OpenRouter model id for every step. */
    val model: String = DEFAULT_MODEL,
    /** Used for one step after the cheap model makes no progress twice; null to never step up. */
    val strongModel: String? = DEFAULT_STRONG_MODEL,
    val maxSteps: Int = 30,
    /** Longer side of the screenshot sent to the model, in pixels. */
    val imageMaxSide: Int = 1000,
    /** Pause after each action for the screen to settle. */
    val settleMs: Long = 900,
    /** How many past steps the model is reminded of. */
    val historySteps: Int = 12,
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

    suspend fun run(task: String): AgentResult {
        val result = runSteps(task)
        trace.runEnded(result)
        return result
    }

    private suspend fun runSteps(task: String): AgentResult {
        val started = clock.markNow()
        val tools = AgentTools.available(phone.ops())
        val schema = AgentTools.schema(tools)
        trace.runStarted(task, config, tools.map { it.name })
        val history = mutableListOf<String>()
        var cost = 0.0
        var lastScreen: String? = null
        var lastWasPhoneAction = false
        var noProgress = 0
        var badReplies = 0

        fun result(outcome: AgentOutcome, step: Int) =
            AgentResult(outcome, step, cost, started.elapsedNow().inWholeMilliseconds)

        for (step in 1..config.maxSteps) {
            currentCoroutineContext().ensureActive()
            val stepStarted = clock.markNow()
            onStatus("Step $step: looking")
            val lookStarted = clock.markNow()
            val screen = look()
            trace.looked(
                step, screen.jpeg, screen.geometry.imageWidth, screen.geometry.imageHeight, screen.texts,
                lookStarted.elapsedNow().inWholeMilliseconds,
            )

            // The same text before and after an action means it changed nothing.
            if (lastWasPhoneAction) noProgress = if (screen.signature == lastScreen) noProgress + 1 else 0
            lastScreen = screen.signature
            val stepUp = config.strongModel != null && (noProgress >= 2 || badReplies >= 2)
            val model = if (stepUp) config.strongModel!! else config.model
            if (stepUp) {
                noProgress = 0
                badReplies = 0
            }

            onStatus("Step $step: thinking ($model)")
            val messages = prompt(task, history, screen, step)
            trace.asking(step, model, stepUp, messages.last().text)
            val llmStarted = clock.markNow()
            val reply = try {
                llm.chat(model, messages, schema)
            } catch (e: Exception) {
                trace.answered(step, model, null, null, "", null, llmStarted.elapsedNow().inWholeMilliseconds, e.message ?: e.toString())
                throw e
            }
            trace.answered(
                step, model, reply.toolCall?.name, reply.toolCall?.arguments, reply.text, reply.usage,
                llmStarted.elapsedNow().inWholeMilliseconds,
            )
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
            when (call.name) {
                "done" -> {
                    val summary = call.arguments.string("summary").orEmpty()
                    val success = call.arguments["success"]?.jsonPrimitive?.booleanOrNull ?: true
                    record("done", summary, if (success) "finished" else "gave up")
                    return result(AgentOutcome.Done(summary, success), step)
                }
                "ask_user" -> {
                    val question = call.arguments.string("question").orEmpty()
                    onStatus("Waiting for you")
                    val answer = gate.ask(question)
                    if (answer == null) {
                        record("ask_user", question, "you stopped the task")
                        return result(AgentOutcome.Stopped("Stopped at: $question"), step)
                    }
                    record("ask_user", question, "you answered: $answer")
                }
                "wait" -> {
                    val seconds = (call.arguments["seconds"]?.jsonPrimitive?.doubleOrNull ?: 2.0).coerceIn(0.5, 10.0)
                    delay((seconds * 1000).toLong())
                    record("wait ${seconds}s", reason, "waited")
                }
                else -> {
                    val op = try {
                        AgentTools.toOp(call, screen.geometry)
                    } catch (e: IllegalArgumentException) {
                        badReplies++
                        record(call.name, reason, "not done: ${e.message}")
                        continue
                    }
                    val described = describe(call, op, screen)
                    val risky = riskyTarget(op, screen)
                    if (risky != null && !gate.confirm("Mode is about to tap \"$risky\" ($reason). Allow it?")) {
                        record(described, reason, "you did not allow it; do not try again without asking")
                        continue
                    }
                    actionStarted = clock.markNow()
                    val outcome = try {
                        phone.send(op)
                        lastWasPhoneAction = true
                        badReplies = 0
                        "ok"
                    } catch (e: Exception) {
                        "failed: ${e.message}"
                    }
                    record(described, reason, outcome, op.toString())
                    delay(config.settleMs)
                }
            }
        }
        return result(AgentOutcome.Stopped("Reached the limit of ${config.maxSteps} steps"), config.maxSteps)
    }

    /** What the model sees of the screen. */
    private class Screen(
        val jpeg: ByteArray,
        val geometry: AgentTools.Geometry,
        /** Text with its centre in screenshot pixels. */
        val texts: List<Triple<String, Int, Int>>,
    ) {
        val signature: String = texts.joinToString("|") { it.first }
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

    private fun prompt(task: String, history: List<String>, screen: Screen, step: Int): List<ChatMessage> {
        val g = screen.geometry
        val user = buildString {
            append("TASK: ").append(task).append("\n\n")
            append("STEP ").append(step).append(" of at most ").append(config.maxSteps).append("\n\n")
            if (history.isEmpty()) {
                append("No actions yet.\n\n")
            } else {
                append("WHAT HAPPENED SO FAR (latest last):\n")
                history.takeLast(config.historySteps).forEach { append(it).append('\n') }
                append('\n')
            }
            append("THE SCREEN NOW: the screenshot is ${g.imageWidth}×${g.imageHeight} pixels. ")
            append("Text on it, with each piece's centre in screenshot pixels:\n")
            if (screen.texts.isEmpty()) append("(no text found)\n")
            screen.texts.take(MAX_TEXTS).forEach { (text, x, y) -> append("\"").append(text).append("\" at (").append(x).append(", ").append(y).append(")\n") }
            append("\nChoose the next single action.")
        }
        return listOf(ChatMessage("system", SYSTEM_PROMPT), ChatMessage("user", user, screen.jpeg))
    }

    /** A readable action for the log, naming the text tapped when there is one. */
    private fun describe(call: com.piyush.mode.agent.ToolCall, op: Op, screen: Screen): String = when (op) {
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

    companion object {
        private const val MAX_TEXTS = 80
        private const val NEAR_PX = 60.0

        val SYSTEM_PROMPT = """
            You operate an iPhone for the user, one action at a time, to complete their task.
            Each turn you get the task, what you did so far, a screenshot, and the text on the screen with
            positions. Reply with exactly one tool call. After each action you will see the new screen.

            How to act:
            - Coordinates are screenshot pixels. To tap a piece of text, use its listed centre. For icons
              without text, read the position off the screenshot.
            - Prefer open_app to open apps. To find something not on screen, scroll. Tap a text field
              before typing into it; press return to submit a search if there is no button.
            - Check the new screen to see whether your last action worked. If nothing changed, try a
              different way instead of repeating the same action.
            - If a popup, permission request or ad is in the way, deal with it sensibly (dismiss or allow
              as the task needs).

            Safety:
            - Before anything that spends money, books, orders, sends or posts a message, deletes, or can
              not be undone, use ask_user to confirm, even if the task seems to ask for it.
            - Never type passwords, passcodes or card numbers. If a login, passcode, Face ID, payment
              details or a CAPTCHA is needed, use ask_user.

            Finishing:
            - When the task is done, call done with a short summary of what you did and what the screen
              shows. If it cannot be done, call done with success false and say why.
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
