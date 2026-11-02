package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.voice.Heard
import com.piyush.mode.feature.settings.voice.Voices
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.presentation.TaskQueue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** Where "Start Mode" is: listening, heard (Mode says what it will do), or typing (nothing could listen). */
public enum class ListenPhase { Listening, Heard, Typing }

public data class ListenUiState(
    val phase: ListenPhase = ListenPhase.Listening,
    /** What the user said (or typed), once heard. */
    val transcript: String = "",
    /** What Mode says back ("I'll turn on dark mode."). */
    val reply: String = "",
    /** Who is listening ([com.piyush.mode.feature.settings.domain.VoiceKind.id]), and who speaks, with the voice's name. */
    val listener: String? = null,
    val speaker: String? = null,
    val voiceName: String? = null,
    /** The task was handed to the queue. */
    val sent: Boolean = false,
)

/** Opens the microphone, hears one task, says what it will do, then hands it to the queue as a spoken task. */
public class ListenPresenter(
    parent: CoroutineScope,
    private val voices: Voices,
    private val queue: TaskQueue,
) : Presenter<ListenUiState>(parent, ListenUiState()) {
    private var job: Job? = null
    private var taskId: String? = null

    public fun begin() {
        if (job?.isActive == true) return
        val speaker = voices.speaker()
        val listener = voices.listener()
        mutableState.value = ListenUiState(
            phase = if (listener == null) ListenPhase.Typing else ListenPhase.Listening,
            listener = listener?.id, speaker = speaker?.first?.id, voiceName = speaker?.second?.name,
        )
        if (listener == null) return
        job = scope.launch {
            while (true) {
                when (val heard = voices.hear()) {
                    Heard.Silence -> Unit
                    is Heard.Words -> { start(heard.text, TaskSource.Voice); return@launch }
                    Heard.Nothing -> { mutableState.value = state.value.copy(phase = ListenPhase.Typing); return@launch }
                }
            }
        }
    }

    /** Type instead: the microphone closes and nothing heard so far is kept. */
    public fun typeInstead() {
        job?.cancel()
        job = null
        voices.stop()
        mutableState.value = state.value.copy(phase = ListenPhase.Typing)
    }

    /** Done: stop recording now and hear what was said so far. */
    public fun finish() { voices.finishListening() }

    /** A typed task: no spoken questions later, but Mode still says what it will do. */
    public fun submitTyped(text: String) {
        val task = text.trim()
        if (task.isEmpty()) return
        job?.cancel()
        job = scope.launch { start(task, TaskSource.App) }
    }

    /** Stops listening, speaking, and the task if it was sent. */
    public fun stop() {
        job?.cancel()
        job = null
        voices.stop()
        taskId?.let(queue::cancel)
    }

    private suspend fun start(task: String, source: TaskSource) {
        val reply = replyLine(task)
        mutableState.value = state.value.copy(phase = ListenPhase.Heard, transcript = task, reply = reply)
        voices.speak(reply)
        taskId = queue.submit(task, source, null, false)?.id
        mutableState.value = state.value.copy(sent = true)
    }

    internal companion object {
        private val POLITE = Regex(
            """^(please\s+|can you\s+|could you\s+|would you\s+|hey mode,?\s+|mode,?\s+|i want to\s+|i need to\s+|i'd like to\s+|i would like to\s+)+""",
            RegexOption.IGNORE_CASE,
        )
        /** A question, not a task: Mode looks it up. */
        private val QUESTION = Regex("""^(what|when|where|who|which|why|how|is|are|do|does|did|am i|have i)\b""", RegexOption.IGNORE_CASE)
        private val TRAILING = Regex("""[\s,]*(please|for me|thanks|thank you)?[\s.!?]*$""", RegexOption.IGNORE_CASE)

        /** "can you turn on dark mode please" → "I'll turn on dark mode." Plain, from the user's own words. */
        fun replyLine(task: String): String {
            val core = task.trim().replace(POLITE, "").replace(TRAILING, "").trim()
            if (core.isEmpty()) return "On it."
            if (QUESTION.containsMatchIn(core) || task.trim().endsWith("?")) return "Let me check."
            return "I'll " + core.replaceFirstChar { it.lowercaseChar() } + "."
        }
    }
}
