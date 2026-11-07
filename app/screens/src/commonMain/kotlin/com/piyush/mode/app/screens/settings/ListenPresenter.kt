package com.piyush.mode.app.screens.settings

import kotlinx.coroutines.flow.first
import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.core.telemetry.withParent
import com.piyush.mode.core.telemetry.trace
import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.voice.DeviceVoice
import com.piyush.mode.feature.settings.voice.Heard
import com.piyush.mode.feature.settings.voice.Voices
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.domain.VoiceTurnPhase
import com.piyush.mode.feature.tasks.presentation.TaskQueue
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Talking mode, one screen from "Start Mode" to done:
 * - NeedsMic: the microphone (or Apple's speech model) is not allowed yet.
 * - Greeting: Mode says hello out loud; the mic opens after.
 * - Listening: the user's words appear as they speak.
 * - Starting: Mode says what it will do and works in the background.
 * - Asking / Answering / Answered: a question in the task, asked out loud, answered by voice or a tap.
 * - Typing: nothing can listen, or the user chose to type.
 */
public enum class TalkPhase { NeedsMic, Greeting, Listening, Starting, Asking, Answering, Answered, Typing }

/** One line of a spoken conversation, as it happened: what they asked, what Mode said, and each task's card. */
public data class TalkTurn(
    val id: Int,
    /** True for what the user said or typed; false for Mode. */
    val mine: Boolean,
    val kind: Kind,
    val text: String,
    /** [Kind.Task]: the task, as it works and how it ended. */
    val card: TalkTaskCard? = null,
    /** [Kind.Next]: what Mode offers to do, with the two answers to tap. */
    val offer: TalkOffer? = null,
) {
    public enum class Kind {
        /** Their request, or Mode's greeting or reply to it. */
        Said,
        /** Mode's line after a task ("Anything else?", or an offer). */
        Next,
        /** A task: what Mode said while working, then the result. */
        Task,
    }
}

/** A task in the conversation: the lines Mode said while working, then the result, part by part. */
public data class TalkTaskCard(
    val taskId: String,
    val conversationId: String?,
    val title: String,
    val state: State,
    val startedAtMs: Long,
    /** How long it took, once it ended. */
    val seconds: Long = 0,
    val steps: List<TalkStep> = emptyList(),
    /** The plan's parts done so far, of [parts] (0 when there is no plan). */
    val doneParts: Int = 0,
    val parts: Int = 0,
    /** Once it ended: the result in a line, and one row per part. */
    val headline: String = "",
    val rows: List<TalkResultRow> = emptyList(),
) {
    public enum class State { Working, Done, Partial, Failed, Stopped }
}

/** A line Mode said while working, and when ("10:03"). */
public data class TalkStep(val text: String, val time: String)

/** One part of a result: done or not, what, and the detail. */
public data class TalkResultRow(val title: String, val detail: String, val done: Boolean)

/** Mode offers to run [task]: [yes] ("Yes, look there") runs it, [no] ("Skip it") doesn't. */
public data class TalkOffer(val task: String, val yes: String, val no: String)

public data class ListenUiState(
    val phase: TalkPhase = TalkPhase.Greeting,
    /** What Mode said first ("Hey Alex, good evening. What do you want me to do?"). */
    val greeting: String = "",
    /** The words heard so far (live), while listening or answering. */
    val partial: String = "",
    /** The task the user gave (said or typed), and what Mode said back ("I'll …"). */
    val task: String = "",
    val reply: String = "",
    /** What Mode is doing now, while it works. */
    val working: String = "",
    /** A spoken question: its text and options, the answer given, and what Mode said back. */
    val question: String = "",
    val options: List<String> = emptyList(),
    val answer: String = "",
    val answerReply: String = "",
    /** Who listens and who speaks ([com.piyush.mode.feature.settings.domain.VoiceKind.id]), with the voice's name. */
    val listener: String? = null,
    val speaker: String? = null,
    val voiceName: String? = null,
    /** The microphone is off for Mode in iPhone Settings. */
    val micDenied: Boolean = false,
    /** The task was handed to the queue; [finished] once it ended (or, started by voice, once the conversation ended). */
    val sent: Boolean = false,
    val finished: Boolean = false,
    /** What was done so far in this conversation, oldest first ("Turn on Do Not Disturb"). */
    val doneSoFar: List<String> = emptyList(),
    /** The whole conversation so far, oldest first, with the running task's updates as they come. */
    val turns: List<TalkTurn> = emptyList(),
    /** The chosen voice or listener failed ("Mode couldn't hear with Deepgram"), with a way to Settings. */
    val voiceNotice: VoiceNotice? = null,
)

/** The chosen voice ([hearing] false) or listener [provider] failed, and why ([trouble]: a VoiceTrouble name). */
public data class VoiceNotice(val provider: String, val hearing: Boolean, val trouble: String) {
    public companion object {
        public fun of(p: com.piyush.mode.feature.settings.voice.VoiceProblem): VoiceNotice = VoiceNotice(p.kind.id, p.hearing, p.trouble.name)
    }
}

public class ListenPresenter(
    parent: CoroutineScope,
    private val voices: Voices,
    private val queue: TaskQueue,
    private val settings: SettingsRepository,
    private val runner: TaskRunner,
    private val clock: ScreenClock = ScreenClock(),
    /** Opened for a question in a spoken task (Mode came forward): no greeting, no listening of its own. */
    private val forQuestion: Boolean = false,
    /** The chat whose mic opened this: the spoken task goes there (null: a new chat). */
    conversationId: String? = null,
    private val telemetry: com.piyush.mode.core.telemetry.Telemetry = com.piyush.mode.core.telemetry.Telemetry.None,
    /** How this opened, for the trace: siri, ask_mic, chat_mic, question. */
    private val entry: String = "app",
    /** Writes what Mode says when handed a task, like a personal assistant (null: Mode's own short lines). */
    private val acknowledger: com.piyush.mode.feature.memory.data.Acknowledger? = null,
    /** Writes Mode's first words, with what's coming up in their day (null: Mode's own greeting). */
    private val greeter: com.piyush.mode.feature.memory.data.Greeter? = null,
    /** What's coming up in their day (calendar, repeats due, the last task), read when talking mode opens. */
    private val day: suspend () -> com.piyush.mode.feature.memory.data.DayContext? = { null },
) : Presenter<ListenUiState>(parent, ListenUiState()) {
    /** Their day, read once when this opened: the greeting and the reply to their task both use it. */
    private var today: com.piyush.mode.feature.memory.data.DayContext? = null
    /** One trace per talking-mode screen: how it opened, each speak and listen inside it, how it ended. */
    private var session: com.piyush.mode.core.telemetry.TraceSpan? = null
    private var outcome = "Closed"

    private fun session(): com.piyush.mode.core.telemetry.TraceSpan = session ?: runCatching {
        telemetry.start(com.piyush.mode.core.telemetry.Operation.VoiceSession,
            fields = mapOf(com.piyush.mode.core.telemetry.Field.Entry to entry))
    }.getOrDefault(com.piyush.mode.core.telemetry.TraceSpan.None).also { session = it }

    private fun mark(event: com.piyush.mode.core.telemetry.Event, fields: com.piyush.mode.core.telemetry.Fields = emptyMap()) {
        runCatching { session().event(event, fields) }
    }

    /** Runs [block] inside this session's trace, so its speak and listen spans nest under it. */
    private suspend fun inSession(block: suspend () -> Unit) {
        val context = session().context
        if (context == null) block() else telemetry.withParent(context) { block() }
    }

    private fun endSession() {
        val span = session ?: return
        session = null
        runCatching { span.fields(mapOf(com.piyush.mode.core.telemetry.Field.Outcome to outcome)); span.end() }
    }
    private val own = MutableStateFlow(ListenUiState(sent = forQuestion, phase = if (forQuestion) TalkPhase.Asking else TalkPhase.Greeting))
    private var job: Job? = null
    private var taskId: String? = null

    /**
     * Started by voice ("Hey Siri, start Mode" on the iPhone, holding power on Android) or Ask's mic: one task
     * after another until they say "that's all", go quiet, or close it.
     */
    private val keepsGoing = entry in SESSION_ENTRIES

    /** Where the tasks go: the chat it opened in, else the first task's own chat, so a conversation stays together. */
    private var chat: String? = conversationId

    init {
        if (forQuestion) session()
        scope.coroutineContext[kotlinx.coroutines.Job]?.invokeOnCompletion { endSession() }
        // A spoken question in the task takes over the screen while it lasts.
        scope.launch {
            combine(own, runner.state, voices.problem) { mine, task, problem ->
                val turn = task.voice
                when {
                    turn != null && mine.sent -> mine.copy(
                        phase = when (turn.phase) {
                            VoiceTurnPhase.Asking -> TalkPhase.Asking
                            VoiceTurnPhase.Listening -> TalkPhase.Answering
                            VoiceTurnPhase.Answered -> TalkPhase.Answered
                        },
                        task = turn.task.ifEmpty { mine.task },
                        question = turn.question, options = turn.options,
                        partial = if (turn.phase == VoiceTurnPhase.Listening) turn.heard else "",
                        answer = if (turn.phase == VoiceTurnPhase.Answered) turn.heard else "", answerReply = turn.reply,
                    )
                    mine.sent -> mine.copy(
                        phase = if (mine.phase in QUESTION_PHASES) TalkPhase.Starting else mine.phase,
                        working = task.status,
                        finished = if (keepsGoing) mine.finished else !task.running && (taskId == null || task.taskId == taskId),
                        turns = mine.turns.map { t ->
                            val card = t.card
                            if (card != null && card.state == TalkTaskCard.State.Working && card.taskId == task.taskId) t.copy(card = live(card, task)) else t
                        },
                    )
                    else -> mine
                }.copy(voiceNotice = problem?.let(VoiceNotice::of))
            }.collect { mutableState.value = it }
        }
    }

    public fun begin() {
        if (job?.isActive == true || own.value.sent) return
        val speaker = voices.speaker()
        val listener = voices.listener()
        val base = ListenUiState(listener = listener?.id, speaker = speaker?.first?.id, voiceName = speaker?.second?.name)
        session()
        if (listener == null) {
            val apple = DeviceVoice.hearingState()
            outcome = "MicBlocked"
            mark(com.piyush.mode.core.telemetry.Event.MicBlocked, mapOf(com.piyush.mode.core.telemetry.Field.ErrorType to apple))
            own.value = base.copy(
                phase = if (apple == "permission" || apple == "asset" || apple == "denied") TalkPhase.NeedsMic else TalkPhase.Typing,
                micDenied = apple == "denied",
            )
            return
        }
        // Every listener needs the microphone: without it iOS records silence (Soniox and Deepgram included).
        val mic = DeviceVoice.hearingState()
        if (mic == "permission" || mic == "denied") {
            outcome = "MicBlocked"
            mark(com.piyush.mode.core.telemetry.Event.MicBlocked, mapOf(com.piyush.mode.core.telemetry.Field.ErrorType to mic))
            own.value = base.copy(phase = TalkPhase.NeedsMic, micDenied = mic == "denied")
            return
        }
        own.value = base.copy(phase = TalkPhase.Greeting, greeting = "")
        job = scope.launch {
            inSession {
                val greeting = greet()
                own.value = own.value.copy(greeting = greeting)
                say(false, greeting)
                converse(greeting, followUp = false)
            }
        }
    }

    /**
     * Says [line], then listens: what they say is the next task. First time, two quiet tries then typing; after a
     * task ([followUp]), [FOLLOW_UP_QUIET_MS] of quiet, or "that's all", ends the conversation.
     */
    private suspend fun converse(line: String, followUp: Boolean) {
        // Talking over the line: what they say is the task.
        val over = if (line.isBlank()) null else voices.speakOrHear(line) { words -> own.value = own.value.copy(phase = TalkPhase.Listening, partial = words) }
        if (over is Heard.Words) { heard(over.text); return }
        own.value = own.value.copy(phase = TalkPhase.Listening)
        mark(com.piyush.mode.core.telemetry.Event.ListenStarted)
        val since = clock.now()
        var tries = 0
        var failures = 0
        while (true) {
            when (val heard = voices.hear { words -> own.value = own.value.copy(partial = words) }) {
                is Heard.Words -> { heard(heard.text); return }
                Heard.Nothing -> {
                    // The listener failed (the notice says why). In a conversation it tries once more first, so a
                    // hiccup doesn't end hands-free talking.
                    if (keepsGoing && ++failures <= LISTEN_RETRIES) { delay(LISTEN_RETRY_MS); continue }
                    outcome = "HearingFailed"; own.value = own.value.copy(phase = TalkPhase.Typing); return
                }
                Heard.Silence -> {
                    tries++
                    val quietLongEnough = if (followUp) clock.now() - since >= FOLLOW_UP_QUIET_MS else tries >= MAX_SILENT_LISTENS
                    if (quietLongEnough) {
                        if (followUp) { end("QuietAfter"); return }
                        outcome = "NothingHeard"
                        mark(com.piyush.mode.core.telemetry.Event.NothingHeard)
                        own.value = own.value.copy(phase = TalkPhase.Typing)
                        return
                    }
                }
            }
        }
    }

    /** What they said: yes or no to Mode's offer, the next task, or the end of the conversation ("that's all"). */
    private suspend fun heard(text: String, source: TaskSource = TaskSource.Voice) {
        val offer = pendingOffer
        pendingOffer = null
        if (offer != null) {
            when {
                same(text, offer.yes) || isYes(text) -> { start(offer.task, source, shown = text); return }
                same(text, offer.no) || isDecline(text) -> { declined(text, offer); return }
            }
        }
        if (keepsGoing && isWrapUp(text)) { end("ThatsAll"); return }
        start(text, source)
    }

    /** No to Mode's offer: it asks if there's anything else instead, and listens. */
    private suspend fun declined(text: String, offer: TalkOffer) {
        say(true, text)
        val s = settings.settings.value
        val next = greeter?.next(s.userName, hour(), today ?: com.piyush.mode.feature.memory.data.DayContext(""), lastTask, lastDone, lastSaid,
            budgetMs = GREET_WAIT_MS, language = speakLanguage(), declined = offer.task)
        com.piyush.mode.core.common.AppLog.log("talk", "offer declined; next: \"${next?.line}\"")
        nextLine(next)
    }

    /** Mode's line after a task (with its offer, if any), said out loud, then it listens again. */
    private suspend fun nextLine(written: com.piyush.mode.feature.memory.data.NextLine?) {
        // Without the AI's line (none connected, too slow, nothing to say) Mode still asks, so the conversation
        // never goes quiet on the user.
        val next = written ?: com.piyush.mode.feature.memory.data.NextLine(if (lastPartial) PART_DONE else ANYTHING_ELSE)
        lastPartial = false
        val offer = next?.offer?.let { TalkOffer(it.task, it.yes, it.no) }
        pendingOffer = offer
        own.value = own.value.copy(phase = TalkPhase.Greeting, greeting = next?.line.orEmpty(), task = "", reply = "", partial = "", working = "", sent = false)
        if (next != null) {
            own.value = own.value.copy(turns = own.value.turns + TalkTurn(nextTurnId++, false, TalkTurn.Kind.Next, next.line, offer = offer))
        }
        converse(next?.line.orEmpty(), followUp = true)
    }

    /** Their tap on one of Mode's offered answers: as if they said it. */
    public fun pick(answer: String) {
        job?.cancel()
        voices.stop()
        job = scope.launch { inSession { heard(answer, TaskSource.Voice) } }
    }

    /** That's all: the conversation ends and the screen closes. */
    public fun thatsAll() {
        job?.cancel()
        end("ThatsAll")
    }

    private var pendingOffer: TalkOffer? = null
    private var lastTask = ""
    private var lastDone = true
    private var lastSaid: String? = null
    /** The last task finished only part of what was asked (said once, in the line after it). */
    private var lastPartial = false

    /**
     * After a task in a conversation: unless it left them in another app (a video playing) or was stopped, Mode
     * says what's next (the AI's: anything else, sort out what didn't work, or one thing from their day) and
     * listens again.
     */
    private suspend fun afterTask(task: String) {
        val id = taskId ?: return
        // Wait for the task to end (a failed one may leave no result: then it simply didn't finish).
        val finishedState = runner.state.first { !it.running && it.taskId == id }
        val result = finishedState.lastResult?.outcome
        val done = result as? com.piyush.mode.agent.AgentOutcome.Done
        val said = done?.let { it.spoken ?: it.summary }
        // The card keeps what Mode said while working and gets its result.
        own.value = own.value.copy(sent = false, turns = own.value.turns.map { t ->
            val card = t.card
            if (card != null && card.taskId == id) t.copy(card = ended(live(card, finishedState), result, finishedState.status)) else t
        })
        if (result is com.piyush.mode.agent.AgentOutcome.Stopped) { end("Stopped"); return }
        if (done != null && done.stay) { end("LeftInApp"); return }
        failureLine(task, result, finishedState.status)?.let { line ->
            lastTask = task
            lastDone = false
            lastSaid = null
            own.value = own.value.copy(doneSoFar = own.value.doneSoFar + task)
            com.piyush.mode.core.common.AppLog.log("talk", "task didn't finish: offering to try again")
            nextLine(line)
            return
        }
        lastTask = task
        lastDone = done?.success == true
        lastPartial = done != null && !done.success
        lastSaid = said
        own.value = own.value.copy(doneSoFar = own.value.doneSoFar + task)
        val s = settings.settings.value
        val context = today ?: runCatching { kotlinx.coroutines.withTimeoutOrNull(DAY_WAIT_MS) { day() } }.getOrNull()
            ?: com.piyush.mode.feature.memory.data.DayContext("")
        val started = clock.now()
        val next = greeter?.next(s.userName, hour(), context, task, lastDone, said, budgetMs = GREET_WAIT_MS, language = speakLanguage())
        com.piyush.mode.core.common.AppLog.log("talk", if (next != null) "next by ${greeter?.lastModel} in ${clock.now() - started} ms: \"${next.line}\"${next.offer?.let { " offers: ${it.task}" }.orEmpty()}" else "next: none, listening")
        nextLine(next)
    }

    /**
     * What Mode says when a task didn't finish: why, in the runner's plain words, and an offer to try again (yes runs
     * the same task). A task that never started (no AI connected, today's limit) says only why. Null when it finished.
     */
    private fun failureLine(task: String, result: com.piyush.mode.agent.AgentOutcome?, status: String): com.piyush.mode.feature.memory.data.NextLine? {
        val why = when (result) {
            null -> status
            is com.piyush.mode.agent.AgentOutcome.Failed -> result.why
            else -> return null
        }.removePrefix(FAILED_PREFIX).trim().trimEnd('.')
        if (result == null && !status.startsWith(FAILED_PREFIX)) {
            return com.piyush.mode.feature.memory.data.NextLine(status.trim())
        }
        val reason = if (why.isBlank()) "" else ": $why"
        return com.piyush.mode.feature.memory.data.NextLine(
            "$DIDNT_WORK$reason. $TRY_AGAIN_QUESTION",
            com.piyush.mode.feature.memory.data.NextLine.Offer(task, TRY_AGAIN, SOMETHING_ELSE),
        )
    }

    /** The card with what the running task said so far and how far its plan is. */
    private fun live(card: TalkTaskCard, task: com.piyush.mode.feature.tasks.domain.TaskState): TalkTaskCard = card.copy(
        steps = task.updates.map { TalkStep(it.text, timeOf(it.atMs)) },
        doneParts = task.plan.count { it.done }, parts = task.plan.size,
    )

    /** The card once the task ended: how it went, how long it took, the result and its parts. */
    private fun ended(card: TalkTaskCard, result: com.piyush.mode.agent.AgentOutcome?, status: String): TalkTaskCard {
        val done = result as? com.piyush.mode.agent.AgentOutcome.Done
        val rows = done?.parts.orEmpty().map { TalkResultRow(it.title, it.detail, it.done) }
        val state = when {
            result is com.piyush.mode.agent.AgentOutcome.Stopped -> TalkTaskCard.State.Stopped
            done?.success == true && rows.all { it.done } -> TalkTaskCard.State.Done
            rows.any { it.done } || done?.success == true -> TalkTaskCard.State.Partial
            else -> TalkTaskCard.State.Failed
        }
        val headline = done?.headline ?: done?.spoken ?: done?.summary?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()?.trimStart('#', ' ')
            // The card's label already says it couldn't finish.
            ?: status.removePrefix(FAILED_PREFIX)
        return card.copy(state = state, seconds = ((clock.now() - card.startedAtMs) / 1000).coerceAtLeast(0), headline = headline, rows = rows)
    }

    @OptIn(ExperimentalTime::class)
    private fun timeOf(ms: Long): String {
        val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(clock.zone())
        return "${t.hour.toString().padStart(2, '0')}:${t.minute.toString().padStart(2, '0')}"
    }

    /** Adds a line to the conversation. */
    private fun say(mine: Boolean, text: String) {
        if (text.isBlank()) return
        own.value = own.value.copy(turns = own.value.turns + TalkTurn(nextTurnId++, mine, TalkTurn.Kind.Said, text.trim()))
    }

    private var nextTurnId = 0

    /** The conversation is over: the screen closes. */
    private fun end(why: String) {
        outcome = why
        com.piyush.mode.core.common.AppLog.log("talk", "conversation ended: $why after ${own.value.doneSoFar.size} task(s)")
        voices.stop()
        own.value = own.value.copy(finished = true)
    }

    /**
     * Mode's first words: the AI's, with what's coming up in their day ([GREET_WAIT_MS] at most), else Mode's own
     * short greeting. Logs which, and how long it took, so a fixed line is never a mystery.
     */
    private suspend fun greet(): String {
        val s = settings.settings.value
        val started = clock.now()
        val context = runCatching { kotlinx.coroutines.withTimeoutOrNull(DAY_WAIT_MS) { day() } }.getOrNull()
        today = context
        val written = context?.let { c ->
            telemetry.trace(com.piyush.mode.core.telemetry.Operation.VoiceAcknowledge) { span ->
                greeter?.line(s.userName, hour(), c, budgetMs = GREET_WAIT_MS, language = speakLanguage()).also {
                    span.fields(mapOf(com.piyush.mode.core.telemetry.Field.Outcome to if (it == null) "GreetingFallback" else "GreetingAI"))
                }
            }
        }
        val ms = clock.now() - started
        com.piyush.mode.core.common.AppLog.log("talk", if (written != null) "greeting by ${greeter?.lastModel} in $ms ms: \"$written\"" else "greeting: Mode's own line (${whyNot(ms, GREET_WAIT_MS)}, ${greeter?.lastModel})")
        return written ?: greetingLine(s.userName, hour())
    }

    private fun speakLanguage(): String? {
        val s = settings.settings.value
        val chosen = com.piyush.mode.feature.settings.domain.SpeechLanguage.fromId(s.language)
        return chosen.takeIf { !it.isEnglish && s.replyInSpokenLanguage }?.languageName
    }

    private fun whyNot(ms: Long, budget: Long): String = when {
        greeter == null && acknowledger == null -> "no AI connected"
        ms >= budget -> "the AI took longer than ${budget / 1000.0} s"
        else -> "the AI gave nothing usable"
    }

    /** Allow microphone: the prompt and Apple's speech model; listening starts once both are ready. */
    public fun allowMic() {
        DeviceVoice.prepare()
        scope.launch {
            repeat(PREPARE_CHECKS) {
                delay(1_000)
                val state = DeviceVoice.hearingState()
                // Soniox and Deepgram need only the microphone; Apple also needs its speech model.
                val cloud = voices.listener()?.let { it != com.piyush.mode.feature.settings.domain.VoiceKind.Device } == true
                if (cloud && state != "permission" && state != "denied") { begin(); return@launch }
                when (state) {
                    "ready" -> { begin(); return@launch }
                    "denied" -> { own.value = own.value.copy(micDenied = true); return@launch }
                    "unsupported" -> { own.value = own.value.copy(phase = TalkPhase.Typing); return@launch }
                }
            }
        }
    }

    /** Done: stop recording now and use what was said so far. */
    public fun finish() { voices.finishListening() }

    /** Type instead: the microphone closes and nothing heard so far is kept. */
    public fun typeInstead() {
        job?.cancel()
        job = null
        voices.stop()
        own.value = own.value.copy(phase = TalkPhase.Typing, partial = "")
    }

    /** A typed task: no spoken questions later, but Mode still says what it will do. */
    public fun submitTyped(text: String) {
        val task = text.trim()
        if (task.isEmpty()) return
        job?.cancel()
        voices.stop()
        job = scope.launch { inSession { heard(task, if (typedSpeaks) TaskSource.Voice else TaskSource.App) } }
    }

    /** The overlay talks the whole way: what's typed there is answered and asked about out loud too. */
    private val typedSpeaks = entry == OVERLAY

    /** A tapped option answers Mode's spoken question. */
    public fun answer(option: String) { runner.answer(option) }

    /** Later: the question waits on the notification and Live Activity instead. */
    public fun later() { runner.voiceLater() }

    /** Stops listening and speaking, and the task if it was sent from here. */
    public fun stop() {
        if (!own.value.sent) outcome = "Stopped"
        job?.cancel()
        job = null
        voices.stop()
        taskId?.let(queue::cancel)
    }

    /** Closes the screen: anything still being said or heard stops; a task already sent carries on. */
    public fun dismiss() {
        endSession()
        if (!own.value.sent) stop() else { job?.cancel(); voices.stop() }
    }

    /**
     * Sends [task] at once and, while it starts, has the AI write what Mode says (a personal line, up to
     * [ACK_WAIT_MS]); Mode's own short line only if the AI can't. The phone connects and the agent plans in
     * the meantime, so the line never holds the task up.
     */
    private suspend fun start(task: String, source: TaskSource, shown: String = task) {
        own.value = own.value.copy(phase = TalkPhase.Starting, task = task, reply = "", partial = "")
        say(true, shown)
        val asked = clock.now()
        val written = scope.async {
            telemetry.trace(com.piyush.mode.core.telemetry.Operation.VoiceAcknowledge) { span ->
                val s = settings.settings.value
                acknowledger?.line(task, s.userName, hour(), budgetMs = ACK_WAIT_MS, language = speakLanguage(), day = today?.events.orEmpty()).also {
                    span.fields(mapOf(com.piyush.mode.core.telemetry.Field.Outcome to if (it == null) "Fallback" else "AI"))
                }
            }
        }
        val submitted = queue.submit(task, source, chat, false)
        taskId = submitted?.id
        if (chat == null) chat = submitted?.conversationId
        submitted?.let {
            val card = TalkTaskCard(it.id, it.conversationId, task.trim(), TalkTaskCard.State.Working, clock.now())
            own.value = own.value.copy(turns = own.value.turns + TalkTurn(nextTurnId++, false, TalkTurn.Kind.Task, task.trim(), card = card))
        }
        outcome = if (source == TaskSource.Voice) "TaskSent" else "TaskTyped"
        mark(com.piyush.mode.core.telemetry.Event.TaskSent, mapOf(com.piyush.mode.core.telemetry.Field.Source to source.name))
        own.value = own.value.copy(sent = true)
        val line = written.await()
        val ms = clock.now() - asked
        com.piyush.mode.core.common.AppLog.log("talk", if (line != null) "reply by ${acknowledger?.lastModel} in $ms ms: \"$line\"" else "reply: none (${whyNot(ms, ACK_WAIT_MS)}, ${acknowledger?.lastModel})")
        // With an AI connected, its words or none (the agent reports as it goes); Mode's own line only without one.
        val reply = line ?: if (acknowledger == null) replyLine(task) else ""
        own.value = own.value.copy(reply = reply)
        // Mode's reply sits between their request and the task's card.
        if (reply.isNotBlank()) {
            val turns = own.value.turns
            val at = turns.indexOfLast { it.card?.taskId == taskId }.takeIf { it >= 0 } ?: turns.size
            own.value = own.value.copy(turns = turns.take(at) + TalkTurn(nextTurnId++, false, TalkTurn.Kind.Said, reply.trim()) + turns.drop(at))
        }
        // Spoken with the microphone off: the task is already connecting, and a microphone opened beside the
        // screen stream records only silence (seen on the phone, 5 Nov). Talking over Mode works for the greeting
        // and for its questions, where the stream is off or paused.
        if (reply.isNotEmpty()) voices.speak(reply)
        if (keepsGoing) afterTask(task)
    }

    @OptIn(ExperimentalTime::class)
    private fun hour(): Int = Instant.fromEpochMilliseconds(clock.now()).toLocalDateTime(clock.zone()).hour

    internal companion object {
        /** How talking mode opened when it should carry on after a task: Siri, Android's assistant, Ask's mic. */
        const val OVERLAY = "overlay"
        val SESSION_ENTRIES = setOf("siri", "assistant", "ask_mic", OVERLAY)
        const val ANYTHING_ELSE = "Anything else?"
        const val PART_DONE = "I did part of it. Anything else?"
        const val DIDNT_WORK = "That didn't work"
        const val TRY_AGAIN_QUESTION = "Want me to try again?"
        const val TRY_AGAIN = "Try again"
        const val SOMETHING_ELSE = "Something else"
        /** A listener that failed gets this many more tries in a conversation, this far apart. */
        const val LISTEN_RETRIES = 1
        const val LISTEN_RETRY_MS = 1_500L

        /**
         * How long the AI may take to write what Mode says on taking a task. Long on purpose: the task is
         * already going, and the AI's own words late beat a fixed line on time.
         */
        const val ACK_WAIT_MS = 20_000L

        /** How long Mode's first words may wait for the AI (Mo shows itself listening meanwhile). */
        const val GREET_WAIT_MS = 12_000L

        /** Reading their day (calendar, repeats) for the greeting: never longer than this. */
        const val DAY_WAIT_MS = 600L

        private const val MAX_SILENT_LISTENS = 2
        /** How the runner starts a failed task's status line. */
        private const val FAILED_PREFIX = "Failed: "

        /** After a task, this long with nothing said ends the conversation. */
        const val FOLLOW_UP_QUIET_MS = 20_000L

        /** "That's all", "thanks", "bye", "nahi bas": they're done (the whole reply is just that). */
        fun isWrapUp(text: String): Boolean {
            val said = text.lowercase().replace(Regex("[^a-z' ]"), " ").replace(Regex("\\s+"), " ").trim()
            if (said.isEmpty() || said.split(' ').size > WRAP_UP_WORDS) return false
            // Only wrap-up words and fillers ("that's it, thanks", "bye Mo"): anything else is a request.
            var rest = " $said "
            (WRAP_UPS + FILLERS).sortedByDescending { it.length }.forEach { rest = rest.replace(" $it ", " ") }
            return rest.isBlank()
        }

        /** "Yes", "sure, go ahead": yes to Mode's offer. */
        fun isYes(text: String): Boolean = onlyWords(text, YESES)

        /** "Skip it", "no thanks", "leave it": no to Mode's offer. */
        fun isDecline(text: String): Boolean = onlyWords(text, DECLINES)

        /** They said the button's words ("yes look there"). */
        fun same(text: String, label: String): Boolean = norm(text) == norm(label)

        private fun norm(text: String) = text.lowercase().replace(Regex("[^a-z' ]"), " ").replace(Regex("\\s+"), " ").trim()

        private fun onlyWords(text: String, words: List<String>): Boolean {
            val said = norm(text)
            if (said.isEmpty() || said.split(' ').size > WRAP_UP_WORDS) return false
            var rest = " $said "
            (words + FILLERS).sortedByDescending { it.length }.forEach { rest = rest.replace(" $it ", " ") }
            return rest.isBlank()
        }

        private val YESES = listOf("yes", "yeah", "yep", "yup", "sure", "go ahead", "do it", "do that", "please do", "haan", "ha", "ji", "of course", "why not")
        private val DECLINES = listOf("no", "nope", "skip", "skip it", "no thanks", "no thank you", "leave it", "not now", "don't", "dont", "nahi", "rehne do", "never mind", "nevermind")

        private val FILLERS = listOf("mo", "mode", "ok", "okay", "please", "for now", "yeah", "yes", "sure")

        private const val WRAP_UP_WORDS = 5
        private val WRAP_UPS = listOf(
            "that's all", "that is all", "that's it", "that is it", "nothing", "nothing else", "no", "nope", "no thanks",
            "no thank you", "thanks", "thank you", "bye", "goodbye", "i'm done", "im done", "done", "stop", "all good",
            "bas", "nahi", "kuch nahi", "bas itna hi", "nahi bas", "theek hai bas",
        )
        private const val PREPARE_CHECKS = 60
        private val QUESTION_PHASES = setOf(TalkPhase.Asking, TalkPhase.Answering, TalkPhase.Answered)

        /** "Hey Alex, good evening. What do you want me to do?" (by the hour; late at night just "Hey Alex."). */
        fun greetingLine(name: String?, hour: Int): String {
            val who = name?.trim()?.split(' ')?.firstOrNull()?.takeIf { it.isNotEmpty() }
            val part = when (hour) {
                in 5..11 -> "good morning"
                in 12..16 -> "good afternoon"
                in 17..21 -> "good evening"
                else -> null
            }
            val hey = listOfNotNull("Hey" + (who?.let { " $it" } ?: ""), part).joinToString(", ")
            return "$hey. What do you want me to do?"
        }

        /** A question, not a task: Mode looks it up. */
        private val QUESTION = Regex("""^(what|when|where|who|which|why|how|is|are|do|does|did|am i|have i|can you tell|tell me)\b""", RegexOption.IGNORE_CASE)

        /** What a personal assistant says when handed a task: short, warm, and never the task read back. */
        private val ON_IT = listOf("Sure, let me do that for you.", "On it.", "Sure, I'll take care of it.", "Got it, doing it now.")

        /** "Sure, let me do that for you." for a task; "Let me check." for a question. The same task gets the same line. */
        fun replyLine(task: String): String {
            val said = task.trim()
            if (QUESTION.containsMatchIn(said.removePrefix("hey mode").trim(' ', ',')) || said.endsWith("?")) return "Let me check."
            return ON_IT[said.lowercase().hashCode().mod(ON_IT.size)]
        }
    }
}
