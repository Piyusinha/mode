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
import kotlinx.coroutines.withTimeoutOrNull
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
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.time.TimeSource

private fun JsonElement.jsonPrimitiveOrNull(): String? = (this as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

data class AgentConfig(
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
    /** Stop before a step once the task has run this long; null: no limit. */
    val maxMillis: Long? = 15 * 60_000L,
    /** The planning mind: briefs the hands before the first tap. Null: no brief. */
    val brainModel: String? = null,
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

    /**
     * A question with [options] to tap (the [recommended] one marked), or a typed answer; null
     * stops the task. Without options it is the same as [ask].
     */
    suspend fun choose(question: String, options: List<String>, recommended: String?): String? = ask(question)
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
    /** The plan item (1-based) the model said this action works on, when it has a plan. */
    val planItem: Int? = null,
)

sealed interface AgentOutcome {
    class Done(val summary: String, val success: Boolean) : AgentOutcome
    class Stopped(val why: String) : AgentOutcome
    class Failed(val why: String) : AgentOutcome
}

class AgentResult(
    val outcome: AgentOutcome,
    val steps: Int,
    val costUsd: Double,
    val millis: Long,
    /** One line per step, as the model saw them ("3. tap \"Light\" — ok → screen changed"). */
    val transcript: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
    /** The user's answers to the agent's questions. */
    val answers: List<Pair<String, String>> = emptyList(),
    /** Apps opened during the task, in order, without repeats. */
    val appsOpened: List<String> = emptyList(),
    /** The last screenshot the model saw. */
    val finalScreen: ByteArray? = null,
)

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
    /** What Mode remembers; null runs without recall or app tips. */
    private val memory: AgentMemory? = null,
    /** Pausing between steps; null: the task can't be paused. */
    private val pause: PauseGate? = null,
    /** Each screenshot the agent looks at, for the live view. */
    private val onScreen: (ByteArray) -> Unit = {},
    /** The model's plan as items, each time it writes one, for progress on the Dynamic Island. */
    private val onPlan: (List<PlanItem>) -> Unit = {},
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

    private suspend fun runSteps(task: String, startsInMode: Boolean, startContext: TaskContext): AgentResult {
        /** Grows when an opened app brings tips with it. */
        var context = startContext
        val started = clock.markNow()
        val ops = phone.ops()
        val tools = AgentTools.available(ops, recall = memory != null)
        val schema = AgentTools.schema(tools)
        trace.runStarted(task, config, tools.map { it.name })
        // The brain reads the request first (Brain); a failure just means no brief.
        val brief = config.brainModel?.let { brainModel ->
            onStatus(WORKING)
            val r = Brain(llm, brainModel).brief(task, startContext)
            trace.briefed(brainModel, r.brief?.text(), r.costUsd, r.error)
            r
        }
        val history = mutableListOf<String>()
        var cost = brief?.costUsd ?: 0.0
        var lastScreen: String? = null
        var lastWasPhoneAction = false
        val stuck = StuckDetector(canStepUp = config.strongModel != null)
        /** The app the agent last opened, to return to after the user answers a question. */
        var lastApp: String? = null
        /** The model's own plan for multi-part tasks, shown back to it every step; the brain's steps first. */
        var plan: String? = brief?.brief?.steps?.mapIndexed { i, s -> "${i + 1}. $s" }?.joinToString("\n")
        brief?.brief?.let { b -> onPlan(b.steps.map { PlanItem(it, false) }) }
        /** The brief says start fresh: the first time its app opens, it opens on its home page. */
        var freshPending = brief?.brief?.fresh == true
        var inMode = startsInMode
        /** The user's answers, shown on their own every step so they are not lost in the history. */
        val answers = mutableListOf<Pair<String, String>>()
        /** The user answered and the agent has not acted since: another question would repeat it. */
        var answeredSinceAction = false
        /** The last phone action and the page it was made on, to tell a real page change from a moving banner. */
        var lastOp: String? = null
        var lastPage: List<String> = emptyList()
        /** What the agent changed for the user, with proof: never repeated, each checked before the next. */
        val ledger = ChangeLedger()
        /** Memory lookups made, by query, and the step they were made at. */
        val recalled = mutableMapOf<String, Int>()
        var stepUps = 0
        /** What the last strong-model step cost, to judge whether the budget allows another. */
        var lastStrongCost: Double? = null
        /** The text typed last, shown to the user when Mode confirms a risky tap such as Send. */
        var lastTyped: String? = null
        /** Facts the model kept on the way; shown every step for the rest of the task. */
        val notes = mutableListOf<String>()
        val appsOpened = mutableListOf<String>()
        var finalScreen: ByteArray? = null
        /** What the last phone action was expected to do; shown on the next step only. */
        var expectation: String? = null
        /** A successful done was sent back once to be checked; the next one is accepted. */
        var verifyAsked = false

        fun result(outcome: AgentOutcome, step: Int) = AgentResult(
            outcome, step, cost, started.elapsedNow().inWholeMilliseconds,
            transcript = history.toList(), notes = notes.toList(), answers = answers.toList(),
            appsOpened = appsOpened.toList(), finalScreen = finalScreen,
        )

        /** " Last: tap "Send"." for a stop message, from the latest step. */
        fun lastAction() = history.lastOrNull()?.let { " Last: ${it.substringAfter(". ").substringBefore(" — ")}." }.orEmpty()

        for (step in 1..config.maxSteps) {
            currentCoroutineContext().ensureActive()
            config.maxCostUsd?.let { limit ->
                if (cost >= limit) {
                    return result(
                        AgentOutcome.Stopped("Reached the ${money(limit)} limit for this task after ${step - 1} steps.${lastAction()}"),
                        step - 1,
                    )
                }
            }
            config.maxMillis?.let { limit ->
                if (started.elapsedNow().inWholeMilliseconds >= limit) {
                    return result(
                        AgentOutcome.Stopped("Took longer than ${limit / 60_000} minutes, so stopped after ${step - 1} steps.${lastAction()}"),
                        step - 1,
                    )
                }
            }
            pause?.let { gate ->
                if (gate.isPaused.value) {
                    onStatus("Paused")
                    gate.awaitRunning()
                }
            }
            val stepStarted = clock.markNow()
            onStatus(WORKING)
            val lookStarted = clock.markNow()
            val screen = look()
            finalScreen = screen.jpeg
            onScreen(screen.jpeg)
            trace.looked(
                step, screen.jpeg, screen.geometry.imageWidth, screen.geometry.imageHeight, screen.texts,
                lookStarted.elapsedNow().inWholeMilliseconds,
            )

            // Tell the model what its last action did, and notice when nothing changes.
            var changed = if (lastWasPhoneAction) screen.signature != lastScreen else null
            if (changed != null && history.isNotEmpty()) {
                // A rotating banner or an ad makes any page "change"; most of its text the same is the same page.
                val samePage = ChangeLedger.similarity(ChangeLedger.page(lastPage), ChangeLedger.page(screen.pageTexts())) >= SAME_PAGE
                history[history.lastIndex] += when {
                    lastOp == "back" && samePage -> { changed = false; BACK_DID_NOTHING }
                    !changed -> UNCHANGED
                    samePage && lastOp == "tap" -> " → same page (only small parts changed)"
                    else -> " → screen changed"
                }
            }
            lastScreen = screen.signature
            // Proof on screen confirms the last change; a different page means the agent went to check.
            ledger.looked(ChangeLedger.page(screen.pageTexts()), screen.texts.map { it.first }, navigated = lastOp != "scroll" && lastOp != "wait")
            // Right after an unconfirmed change, deciding whether it worked is what matters most.
            ledger.pending()?.takeIf { !it.escalated }?.let { it.escalated = true; stuck.critical() }
            val verdict = stuck.looked(screen.signature, changed)
            if (verdict is StuckDetector.Verdict.Stop) {
                trace.stuck(step, verdict.why)
                return result(AgentOutcome.Stopped("Stuck: ${verdict.why}.${lastAction()}"), step - 1)
            }
            (verdict as? StuckDetector.Verdict.StepUp)?.note?.let { note ->
                if (history.isNotEmpty()) history[history.lastIndex] += " ($note)"
            }
            // The strong model costs ~20× a step: at most a few per task, and only with budget to spare.
            val stepUp = verdict is StuckDetector.Verdict.StepUp && config.strongModel != null && stepUps < MAX_STEP_UPS &&
                (config.maxCostUsd == null || config.maxCostUsd - cost > STEP_UP_BUDGET_SHARE * (lastStrongCost ?: STRONG_STEP_GUESS_USD))
            if (stepUp) stepUps++

            val messages = ContextBuilder.build(
                ContextBuilder.Input(
                    systemPrompt = SYSTEM_PROMPT,
                    task = task,
                    brief = brief?.brief?.text(),
                    context = context,
                    step = step,
                    maxSteps = config.maxSteps,
                    answers = answers,
                    plan = plan,
                    notes = notes,
                    history = history,
                    recentSteps = config.historySteps,
                    lastApp = lastApp,
                    expectation = expectation,
                    done = ledger.lines(),
                    screen = ContextBuilder.Screen(screen.jpeg, screen.geometry.imageWidth, screen.geometry.imageHeight, screen.texts),
                    hideScreen = inMode,
                ),
            )
            inMode = false
            expectation = null
            // A step-up's prompt is not cached: it is not asked again soon, so a cache write would be wasted.
            val answer = think(step, if (stepUp) config.strongModel!! else config.model, stepUp, if (stepUp) uncached(messages) else messages, schema)
            val reply = answer.reply
            val model = answer.model
            cost += reply.usage.costUsd
            if (stepUp && model == config.strongModel) lastStrongCost = reply.usage.costUsd
            val call = reply.toolCall
            var actionStarted = clock.markNow()
            lastWasPhoneAction = false

            fun record(action: String, reason: String, outcome: String, op: String? = null) {
                onStep(StepRecord(step, model, action, reason, outcome, reply.usage.costUsd, stepStarted.elapsedNow().inWholeMilliseconds,
                    planItem = call?.let(AgentTools::itemOf)))
                trace.acted(step, action, op, outcome, actionStarted.elapsedNow().inWholeMilliseconds)
                history += "$step. $action — $outcome"
            }

            if (call == null || tools.none { it.name == call.name }) {
                stuck.badReply()
                record("(no valid action)", reply.text.take(120), "ignored: always call exactly one of the listed tools")
                continue
            }
            val reason = AgentTools.reasonOf(call)
            AgentTools.noteOf(call)?.let { note ->
                // Seen on the phone: the same note kept again a few steps later.
                if (notes.none { it.equals(note, ignoreCase = true) }) {
                    notes += note
                    trace.noted(step, note)
                }
            }
            when (call.name) {
                "done" -> {
                    val summary = call.arguments.string("summary").orEmpty()
                    val success = call.arguments["success"]?.jsonPrimitive?.booleanOrNull ?: true
                    val evidence = call.arguments.string("evidence")?.trim().orEmpty()
                    val lastUnchanged = history.lastOrNull()?.contains(UNCHANGED) == true
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
                    val options = AgentTools.optionsOf(call)
                    val recommended = call.arguments.string("recommended")?.trim()?.takeIf { it in options }
                    // Seen on the phone: four questions in a row about the same message,
                    // each answered. Nothing changed since the last answer, so act on it.
                    if (answeredSinceAction) {
                        stuck.badReply()
                        record(
                            "ask_user (not asked)", question,
                            "not asked: the user already answered \"${answers.last().second}\" and nothing has " +
                                "happened since. Act on that answer now; where it leaves something open, pick the " +
                                "most sensible reading yourself",
                        )
                        continue
                    }
                    onStatus("Waiting for you")
                    // After changing something, what to do with the answer matters: the strong model decides.
                    if (ledger.entries.isNotEmpty()) stuck.critical()
                    val reply2 = if (options.size >= 2) gate.choose(question, options, recommended) else gate.ask(question)
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
                    // Seen on the phone: nine plan rewrites in a 30-step run. A plan right after a plan is
                    // refused; progress is tracked by each action's item field and Mode's record of changes.
                    if (stuck.thought() >= 2) {
                        stuck.critical()
                        record("plan (not saved)", "", "not saved: you just planned. Do the next item of your plan now with a phone action")
                    } else {
                        plan = call.arguments.string("steps")?.trim()?.take(1500)
                        onPlan(PlanItem.parse(plan.orEmpty()))
                        record("plan", "", "plan saved; it is shown to you every step: now do the next open item")
                    }
                }
                "recall" -> {
                    val query = call.arguments.string("query")?.trim().orEmpty()
                    stuck.thought()
                    // Seen on the phone: the same lookup twice in one task, the same unrelated answer.
                    val asked = recalled[query.lowercase()]
                    if (asked != null) {
                        stuck.badReply()
                        record("recall \"$query\" (again)", "", "you already looked this up at step $asked: use what it said, or act")
                        continue
                    }
                    recalled[query.lowercase()] = step
                    val found = memory?.recall(query, appsOpened.toList()).orEmpty()
                    trace.recalled(step, query, found)
                    record(
                        "recall \"$query\"", "",
                        if (found.isEmpty()) "nothing remembered about that"
                        else "remembered:\n" + found.joinToString("\n").take(MAX_RECALL_CHARS),
                    )
                }
                "wait" -> {
                    val waits = stuck.waited()
                    val seconds = (call.arguments["seconds"]?.jsonPrimitive?.doubleOrNull ?: 2.0).coerceIn(0.5, 10.0)
                    delay((seconds * 1000).toLong())
                    // Waiting again and again is not progress: say so (the detector steps up).
                    if (waits >= 3) {
                        record("wait ${seconds}s", reason, "waited $waits times in a row; waiting is not getting anywhere, act instead")
                    } else {
                        record("wait ${seconds}s", reason, "waited")
                    }
                }
                else -> {
                    val op = try {
                        AgentTools.toOp(call, screen.geometry).let { o ->
                            // The brief said start fresh: the first opening of an app lands on its home page.
                            if (o is Op.LaunchApp && freshPending) { freshPending = false; o.copy(fresh = true) } else o
                        }
                    } catch (e: IllegalArgumentException) {
                        stuck.badReply()
                        record(call.name, reason, "not done: ${e.message}")
                        continue
                    }
                    val described = describe(call, op, screen)
                    // Do it once, then check (see ChangeLedger). Nothing here knows any app.
                    val label = (op as? Op.Tap)?.let { nearestText(it.x, it.y, screen) }
                    val target = (op as? Op.Tap)?.let { rowLabel(it.x, it.y, screen) ?: label } ?: described
                    val changes = op is Op.Tap && Changes.isChange(reason, label)
                    if (op is Op.Tap && Changes.isChangeButton(label) && Changes.isChecking(reason)) {
                        stuck.badReply()
                        stuck.critical()
                        record("$described (not done)", reason, ADD_IS_NOT_A_CHECK)
                        continue
                    }
                    if (changes) {
                        ledger.confirmedFor(target)?.let { done ->
                            stuck.critical()
                            record("$described (not done)", reason, alreadyDone(done))
                            continue
                        }
                        ledger.pending()?.let { last ->
                            stuck.badReply()
                            stuck.critical()
                            record("$described (held back)", reason, checkFirst(last))
                            continue
                        }
                    }
                    if (op is Op.Scroll) {
                        val direction = call.arguments.string("direction")
                        stuck.holdReversal(direction)?.let { kept ->
                            record(
                                "$described (not done)", reason,
                                "not done: you have scrolled back and forth. Keep scrolling $kept until you find it or the " +
                                    "page ends (the section you want is usually just past the last one you saw); if your " +
                                    "notes already answer the task, call done",
                            )
                            continue
                        }
                    }
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
                        lastOp = call.name
                        lastPage = screen.pageTexts()
                        stuck.acted()
                        stuck.scrolled(if (op is Op.Scroll) call.arguments.string("direction") else null)
                        if (op is Op.LaunchApp) {
                            lastApp = result.jsonPrimitiveOrNull() ?: op.name
                            if (op.name !in appsOpened) {
                                appsOpened += op.name
                                // The first time an app opens, what was learned about it comes along.
                                val tips = memory?.tipsFor(op.name).orEmpty().filter { it !in context.appNotes }
                                if (tips.isNotEmpty()) context = context.copy(appNotes = context.appNotes + tips)
                            }
                        }
                        expectation = AgentTools.expectOf(call)
                        // Apps often confirm a change only for a moment ("Added", a check): read the
                        // screen right away, twice, so the proof is not lost before the next look.
                        if (changes) {
                            val appeared = flashed(screen)
                            val proof = Changes.proof(appeared)
                            ledger.add(step, target, reason, ChangeLedger.page(screen.pageTexts()), proof)
                            "ok" + appeared.takeIf { it.isNotEmpty() }?.let { a -> "; right after it the screen showed: " + a.joinToString(", ") { "\"$it\"" } }.orEmpty() +
                                if (proof != null) DONE_IT else CHECK_IT
                        } else if (op is Op.Scroll && stuck.scrollsSameWay() >= SCROLLS_BEFORE_HINT - 1) {
                            "ok (several scrolls in a row: if the app has Sort, Filter or Search for this, use it; otherwise decide with what you've noted)"
                        } else {
                            "ok"
                        }
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
            trace.asking(step, candidate, steppedUp && candidate != config.model, messages.last().text)
            val started = clock.markNow()
            try {
                // Seen on the phone: a provider that never answered; the run sat silent. A step gets a fixed time.
                val reply = withTimeoutOrNull(STEP_ANSWER_MS) { llm.chat(candidate, messages, schema) }
                    ?: throw LlmFailed("$candidate gave no answer in ${STEP_ANSWER_MS / 1000} s")
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
        /**
         * Text and rough position: a scroll changes it even when the words do not. The status
         * bar is left out: its clock, and the Live Activity's timer on the Dynamic Island, change
         * every second and once made every screen look "changed" (an add was tapped four times).
         */
        val signature: String = texts.filterNot { it.third <= geometry.toPixelsY(STATUS_BAR_PT) && TICKING.matches(it.first.trim()) }
            .joinToString("|") { "${it.first}@${it.third / 12}" }

        /** The page's words, without the status bar: to tell one page from another. */
        fun pageTexts(): List<String> = texts.filter { it.third > geometry.toPixelsY(STATUS_BAR_PT) }.map { it.first }
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
        val texts = screenTexts(g)
        return Screen(Base64.decode(capture.getValue("jpeg_b64").jsonPrimitive.content), g, texts)
    }

    /**
     * Text that appeared right after a change and was not there before ("Food logged!"). Read
     * twice, at once and a moment later, since some apps confirm late; text only, no model cost.
     */
    private suspend fun flashed(before: Screen): List<String> {
        val had = before.texts.map { it.first.trim() }.toSet()
        val seen = mutableListOf<String>()
        for (wait in FLASH_READS_MS) {
            delay(wait)
            val now = runCatching { screenTexts(before.geometry) }.getOrNull() ?: continue
            now.filter { (text, _, y) ->
                val t = text.trim()
                // Nothing in the status bar is proof of a change (the clock, the island).
                t.isNotEmpty() && t !in had && t.length <= FLASH_TEXT_MAX && y > before.geometry.toPixelsY(STATUS_BAR_PT)
            }.forEach { if (it.first.trim() !in seen) seen += it.first.trim() }
            if (Changes.proof(seen) != null) break
        }
        return seen.take(FLASH_TEXTS)
    }

    /** The held-back reply: which change to check, and where. */
    private fun checkFirst(last: ChangeLedger.Entry): String =
        "not done: first check your change at step ${last.step} (\"${last.target}\"). Look for proof it worked: on " +
            "this screen (a check mark, \"added\", a count), or where the result lives. That is usually the page " +
            "this one was opened from (close it with X, Back, Cancel or Done) or the list it adds to; look for the " +
            "item there. Do it again only if it is truly missing. Waiting or scrolling here is not a check."

    /** The reply when a confirmed change is tried again. */
    private fun alreadyDone(done: ChangeLedger.Entry): String =
        "not done: this was already done at step ${done.step} (confirmed: \"${done.proof}\"). Doing it again would " +
            "add it twice. If the task is complete, call done and say what you changed; otherwise do the next part."

    /** The text on screen now, with centres in screenshot pixels. */
    private suspend fun screenTexts(g: AgentTools.Geometry): List<Triple<String, Int, Int>> =
        (phone.send(Op.ScreenText()) as JsonArray).map {
            val box = it.jsonObject
            Triple(
                box.getValue("text").jsonPrimitive.contentOrNull.orEmpty(),
                g.toPixelsX(box.getValue("x").jsonPrimitive.double).roundToInt(),
                g.toPixelsY(box.getValue("y").jsonPrimitive.double).roundToInt(),
            )
        }

    /**
     * The row a tap is on, for icons with no text of their own (a + beside a result): the
     * text left of it on the same line, title then detail ("Dark chocolate oats · 392 cal, 100 gram").
     */
    private fun rowLabel(x: Double, y: Double, screen: Screen): String? {
        val g = screen.geometry
        val px = g.toPixelsX(x)
        val py = g.toPixelsY(y)
        val half = g.toPixelsY(ROW_HALF_PT)
        val row = screen.texts
            .filter { abs(it.third - py) <= half && it.second < px - g.toPixelsX(ROW_GAP_PT) }
            .filter { !Changes.isChangeButton(it.first) }
            .sortedBy { it.third }
        if (row.isEmpty()) return null
        return row.take(2).joinToString(" · ") { it.first.trim() }.take(ROW_LABEL_MAX)
    }

    /** The same messages with no cache points. */
    private fun uncached(messages: List<ChatMessage>): List<ChatMessage> = messages.map { m ->
        ChatMessage(m.role, m.parts.map { p -> if (p is ChatMessage.Part.Text && p.cacheAfter) ChatMessage.Part.Text(p.text) else p })
    }

    /** A readable action for the log, naming the text tapped, or the row of an icon. */
    private fun describe(call: ToolCall, op: Op, screen: Screen): String = when (op) {
        is Op.Tap -> {
            val label = nearestText(op.x, op.y, screen)
            val row = if (label == null || label.trim().length <= 3) rowLabel(op.x, op.y, screen) else null
            "tap " + when {
                label != null && row != null -> "\"$label\" beside \"$row\""
                label != null -> "\"$label\""
                row != null -> "beside \"$row\""
                else -> "(${pixel(op.x, op.y, screen)})"
            }
        }
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
        private const val BACK_DID_NOTHING =
            " → same page: the back swipe did nothing here. Tap the page's own ← (top left), X, Cancel or Done instead"
        /** Pages sharing this much of their text are the same page. */
        private const val SAME_PAGE = 0.85
        /** The status bar and Dynamic Island, in screen points from the top. */
        private const val STATUS_BAR_PT = 60.0
        /** What ticks up there: the clock, a timer, the battery percentage, Mode's own "Step 4" on the island. */
        private val TICKING = Regex("""^(?:step\s*)?\d{1,3}(?:[:.]\d{2}){0,2}%?$""", RegexOption.IGNORE_CASE)
        /** At most this many strong-model steps per task, each only while the budget left is this many times one. */
        /** How long one decision may take before it is asked again (once). */
        private const val STEP_ANSWER_MS = 25_000L
        private const val MAX_STEP_UPS = 3
        private const val STEP_UP_BUDGET_SHARE = 3.0
        private const val STRONG_STEP_GUESS_USD = 0.02
        private const val SCROLLS_BEFORE_HINT = 3
        private const val CHECK_IT = ". This changed something: check it worked before any other change"
        private const val DONE_IT = ". That confirms it: it is in Mode's record of changes; do not do it again"
        private const val ADD_IS_NOT_A_CHECK =
            "not done: this button adds (or sends); tapping it does not check anything and would add again. To check, " +
                "look for the item where it would show: the list this page adds to (close this page with X, Back or Done)."
        /** When the screen is read after a change, from the tap: at once, then a moment later. */
        private val FLASH_READS_MS = listOf(400L, 800L)
        /** A row: half its height, and the gap between its text and the icon tapped (screen points). */
        private const val ROW_HALF_PT = 28.0
        private const val ROW_GAP_PT = 8.0
        private const val ROW_LABEL_MAX = 90
        private const val FLASH_TEXTS = 6
        private const val FLASH_TEXT_MAX = 40
        /** What the user sees while the agent looks and thinks; never a model or a step number. */
        const val WORKING = "Working on it"
        private const val MAX_RECALL_CHARS = 1200

        val SYSTEM_PROMPT = """
            You operate an iPhone for the user, one action at a time, to complete their task.
            Each turn you get the task, what you did so far (with whether each action changed the
            screen), a screenshot, and the text on the screen with positions. Reply with exactly one
            tool call. After each action you will see the new screen.

            You are run by an app called Mode. If Mode's own screen (its task log, "Step N: thinking",
            the Agent/Compare/Logs tabs) is showing, that is you: never wait on it, leave it and work in
            the app the task needs.

            THE BRIEF, when there is one, is from your planning mind: it has read the request. Gather what it
            says before acting, decide and ask as it says, finish when its "done when" is on screen, and report
            as it says. If the screen shows the brief is wrong, say so in a note and adapt.

            Getting oriented: each time an app opens, check where you are (the page, the date shown, any search
            already typed). If it is not where the task starts, get there first: the page's own ← (top left),
            X, Cancel or Done, or the app's main tab. The iOS back swipe does nothing in many apps. A search box
            that already holds text: clear it (its ×) and type yours. To start a new task in an app, open it with
            fresh true.

            How to act:
            - Coordinates are screenshot pixels. To tap a piece of text, use its listed centre. For icons
              without text, read the position off the screenshot.
            - Open apps with open_app. Apps reopen where they were left, so to go back use the screen's
              own Back / "<" / Cancel / Done / X button, or the back tool. Home then open_app does not reset
              an app.
            - To find the best, cheapest, nearest, highest-rated or newest, use the app's own Sort, Filter or
              Search first; scroll through a long list only when there is none. Read what a list already shows
              (ratings, prices, sizes); open an item only to act on it, or when the list does not say.
            - To see more of a list or page, use scroll (never swipe for scrolling). Keep going one way until
              you find it or the page ends: what you look for is usually just past the last thing you saw. Search fields and
              search pages: tap the field, type, then press return or pick a result; Cancel closes them.
            - Tap a text field before typing into it.
            - If an action did not change the screen, do not repeat it: try another way.
            - Changing things for the user (adding, logging, saving, sending, booking, buying, deleting) is
              where trust is won or lost. Work like a careful assistant:
              1. Before: be sure it is the right item. In search results pick only one that matches what the
                 user asked (name, brand, size). If nothing matches, search again with another spelling or fewer
                 words (spoken tasks are often misheard), then ask. Ask BEFORE changing anything, never after.
              2. Amounts: if the user gave an amount (100 g, 2 cups, 3 tickets), open the item (tap its name,
                 not the + or Add beside it) to set the amount, then add it from there. + adds the default.
              3. Do it once. A + or Add button adds; it never checks anything.
              4. Check: Mode reads the screen right after and tells you what appeared ("Food logged!"); that is
                 proof. Without proof, go where the result lives: usually the page this one was opened from
                 (close it with X, Back, Cancel or Done) or the list it adds to, and look for the item there.
              5. DONE SO FAR is Mode's record of your changes and their proof. Trust it: apps often show a + or
                 Add again a moment later even though it worked. Never repeat a confirmed change; Mode will
                 refuse it. When every part is confirmed, call done and say what you changed and where.
            - Say in expect what the screen should show after an action ("Appearance page opens"); on the
              next screen, check it happened before going on.
            - Deal with popups, permission prompts and ads yourself (dismiss or allow as the task needs).
            - Work on your own. Do not ask the user how to navigate or whether to go back.

            Read the task as the user means it:
            - A short command is a request: "Beast Life protein in my snacks" means log it there.
            - "Add" or "log" means a new entry, even if a similar one exists elsewhere or earlier (another
              meal, another day, an earlier serving). Only your own repeat within this task is a duplicate.
            - Dates mean today unless the user says otherwise. Apps reopen where they were left (yesterday's
              diary, last week's view): check the date on screen and move to today first.
            - What they hand you to decide is yours: "best", "highest rated", "cheapest". Break ties yourself
              (more ratings, then faster or nearer) and say why in your answer.
            Use what the task gives you: names, times, places and message text. In "send a message to
            Sonali saying I'm late" or "message Sonali: sending this automatically", the words after the
            name are the message: type them. If the task wants a message sent but gives no text, write a
            short, natural one that fits the task instead of asking.

            Do not ask for permission to send, pay, book, post or delete: Mode itself shows the user what
            you typed and asks them right before such a tap. So type the message and tap Send.

            Ask the user (ask_user) when:
            - you are blocked: a login, passcode, Face ID, verification code or payment details are needed;
            - a choice is theirs to make. Decide yourself what the task handed to you ("a highly rated
              restaurant", which screen or route, the obvious match for a name they gave). But when the task
              leaves open something they will eat, wear, use or pay for (which dish or product, size,
              quantity, seats, time slot) and there are several reasonable options, ask: once, BEFORE adding
              anything, with 2 to 4 options that say what tells them apart (name, price, size, veg or not) and
              your recommendation. Use what you know about them first (ABOUT THE USER, a preference they
              stated); if they said "any", "you choose" or "surprise me", choose yourself.
            - two people or things fit equally (two contacts with the same name, two addresses).
            Never ask "are you sure?" or to confirm: Mode does that itself before sending or paying.
            Ask once. Their answer is final: act on it, and never ask again to confirm it.
            Never type passwords, passcodes or card numbers.

            Plan multi-part tasks:
            - If the task has several parts ("turn off dark mode, set brightness to half and open YouTube"),
              first call plan with a short numbered list, then do the items in order. In each action, put
              the number of the item it works on in the item field (the user sees this as progress). Call
              plan again only to change course. Finish (done) only when every item is done.

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
            action. Notes are facts for the user's answer, not your progress (that is tracked already), and
            each fact is noted once. Notes stay with you for the whole task; older steps are only summarised.
            Never note passwords, passcodes or card numbers.
            Once your notes hold what the task asks for, finish (done) with them: scrolling back to re-check
            something you already noted wastes the user's time. To read a long page, go through it once.

            Finishing:
            - Before calling done, look at the screen: does it really show the task happened (the switch is
              off, the message shows as sent)? If your last action did nothing, fix that first.
            - Call done with the evidence on screen and a summary: your reply to the user, shown in a chat
              that renders Markdown. Write it like a good chat answer:
              - First line: the answer or what you did, in one short sentence.
              - Several things: a list, one item per line starting with "- ".
              - Timed things (calendar events, reminders, schedules): one per line as "- HH:MM – what"
                ("- 14:00 – Biozyme with lunch", "- 14:00–15:00 – Team call"); all-day ones as
                "- All day – what". 24-hour times, in order.
              - **Bold** the one or two facts that matter most (a price, a name, a code). Use a table only
                to compare several things across several details, and a "### Heading" only when the reply
                has separate parts.
              - Only the facts as they finally are. Never mention your notes, steps, screens or earlier
                guesses; if something is uncertain, say so once in plain words ("about 22:00").
              - Short: a few lines unless the user asked for detail. No closing offers or questions.
              If it cannot be done, call done with success false: say why in one sentence, then what the
              user could try, as a short list if there is more than one thing.
            - Plan once, then act. Plan again only to change course; progress is tracked for you (each action's
              item field, and DONE SO FAR).
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
