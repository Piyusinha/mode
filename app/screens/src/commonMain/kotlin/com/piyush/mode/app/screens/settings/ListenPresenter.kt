package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
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

public data class ListenUiState(
    val phase: TalkPhase = TalkPhase.Greeting,
    /** What Mode said first ("Hey Piyush, good evening. What do you want me to do?"). */
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
    /** The task was handed to the queue; [finished] once it ended. */
    val sent: Boolean = false,
    val finished: Boolean = false,
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
) : Presenter<ListenUiState>(parent, ListenUiState()) {
    private val own = MutableStateFlow(ListenUiState(sent = forQuestion, phase = if (forQuestion) TalkPhase.Asking else TalkPhase.Greeting))
    private var job: Job? = null
    private var taskId: String? = null

    init {
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
                        finished = !task.running && (taskId == null || task.taskId == taskId),
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
        if (listener == null) {
            val apple = DeviceVoice.hearingState()
            own.value = base.copy(
                phase = if (apple == "permission" || apple == "asset" || apple == "denied") TalkPhase.NeedsMic else TalkPhase.Typing,
                micDenied = apple == "denied",
            )
            return
        }
        val greeting = greetingLine(settings.settings.value.userName, hour())
        own.value = base.copy(phase = TalkPhase.Greeting, greeting = greeting)
        job = scope.launch {
            voices.speak(greeting)
            own.value = own.value.copy(phase = TalkPhase.Listening)
            repeat(MAX_SILENT_LISTENS) {
                when (val heard = voices.hear { words -> own.value = own.value.copy(partial = words) }) {
                    Heard.Silence -> Unit
                    is Heard.Words -> { start(heard.text, TaskSource.Voice); return@launch }
                    Heard.Nothing -> { own.value = own.value.copy(phase = TalkPhase.Typing); return@launch }
                }
            }
            own.value = own.value.copy(phase = TalkPhase.Typing)
        }
    }

    /** Allow microphone: the prompt and Apple's speech model; listening starts once both are ready. */
    public fun allowMic() {
        DeviceVoice.prepare()
        scope.launch {
            repeat(PREPARE_CHECKS) {
                delay(1_000)
                when (DeviceVoice.hearingState()) {
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
        job = scope.launch { start(task, TaskSource.App) }
    }

    /** A tapped option answers Mode's spoken question. */
    public fun answer(option: String) { runner.answer(option) }

    /** Later: the question waits on the notification and Live Activity instead. */
    public fun later() { runner.voiceLater() }

    /** Stops listening and speaking, and the task if it was sent from here. */
    public fun stop() {
        job?.cancel()
        job = null
        voices.stop()
        taskId?.let(queue::cancel)
    }

    /** Closes the screen: anything still being said or heard stops; a task already sent carries on. */
    public fun dismiss() {
        if (!own.value.sent) stop() else { job?.cancel(); voices.stop() }
    }

    private suspend fun start(task: String, source: TaskSource) {
        val reply = replyLine(task)
        own.value = own.value.copy(phase = TalkPhase.Starting, task = task, reply = reply, partial = "")
        voices.speak(reply)
        taskId = queue.submit(task, source, null, false)?.id
        own.value = own.value.copy(sent = true)
    }

    @OptIn(ExperimentalTime::class)
    private fun hour(): Int = Instant.fromEpochMilliseconds(clock.now()).toLocalDateTime(clock.zone()).hour

    internal companion object {
        private const val MAX_SILENT_LISTENS = 2
        private const val PREPARE_CHECKS = 60
        private val QUESTION_PHASES = setOf(TalkPhase.Asking, TalkPhase.Answering, TalkPhase.Answered)

        /** "Hey Piyush, good evening. What do you want me to do?" (by the hour; late at night just "Hey Piyush."). */
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
