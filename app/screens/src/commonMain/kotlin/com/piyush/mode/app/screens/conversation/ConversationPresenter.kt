package com.piyush.mode.app.screens.conversation

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.common.RelativeDay
import com.piyush.mode.app.screens.common.RunningTask
import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.common.badge
import com.piyush.mode.app.screens.common.running
import com.piyush.mode.app.screens.common.shots
import com.piyush.mode.feature.conversations.data.ConversationRepository
import com.piyush.mode.feature.conversations.domain.TurnRole
import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.data.Teacher
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.domain.TaskInbox
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

public data class ConversationUiState(
    val title: String = "",
    val turns: List<TurnItem> = emptyList(),
    /** This conversation's task, while it runs. */
    val running: RunningTask? = null,
    /** Facts its latest task taught Mode, for "Remembered · Undo". */
    val remembered: List<Remembered> = emptyList(),
    /** The next message is private: no memory used or saved. */
    val privateNext: Boolean = false,
    /** Tips taught in this chat, each shown under Mode's reply ([Lesson.afterTurnId]) with Edit and Undo. */
    val lessons: List<Lesson> = emptyList(),
    /** What the user just steered the running task with: offered as "Also do it this way next time?". */
    val offerRemember: String? = null,
    /** Mode is saving a lesson (the model is writing the tip). */
    val teaching: Boolean = false,
    /** In a spoken chat: the chosen voice or listener failed ("Mode couldn't speak with ElevenLabs · Update in Settings"). */
    val voiceNotice: com.piyush.mode.app.screens.settings.VoiceNotice? = null,
)

/** A tip the user taught in this chat. */
public data class Lesson(val itemId: String, val app: String?, val title: String, val text: String, val afterTurnId: String)

public data class TurnItem(
    val id: String,
    val fromUser: Boolean,
    val text: String,
    val day: RelativeDay,
    val taskId: String?,
    /** On Mode's replies: how the task ended. */
    val badge: TaskBadge?,
    /** On Mode's replies: how many screens the agent saw, for "See all N screens". */
    val screens: Int,
    /** Mode's reply as blocks (paragraphs, lists, an agenda, tables…); yours as one paragraph. */
    val parts: List<ReplyPart>,
    /** On your messages: where the task came from (Siri and the Watch show as spoken). */
    val source: TaskSource?,
    val atMs: Long,
    /** On your messages: said while Mode worked, to change how it does the running task (board TeachSteer). */
    val steered: Boolean = false,
)

/** Part of a message: a paragraph, or consecutive "HH:MM thing" lines shown as a timetable. */
public data class Remembered(val itemId: String, val text: String)

/** One conversation: its turns, the task running in it, and the message box. */
public class ConversationPresenter(
    parent: CoroutineScope,
    private val conversationId: String,
    private val conversations: ConversationRepository,
    private val runner: TaskRunner,
    private val inbox: TaskInbox,
    private val tasks: TaskRepository,
    private val runs: RunRepository,
    private val clock: ScreenClock = ScreenClock(),
    /** Turns what the user teaches into tips (null: lessons are sent as tasks). */
    private val teacher: Teacher? = null,
    private val memory: MemoryRepository? = null,
    /** For the voice notice in spoken chats (null: none). */
    private val voices: com.piyush.mode.feature.settings.voice.Voices? = null,
) : Presenter<ConversationUiState>(parent, ConversationUiState()) {
    private val noticeDismissed = MutableStateFlow<com.piyush.mode.feature.settings.voice.VoiceProblem?>(null)
    private val notice = combine(voices?.problem ?: MutableStateFlow(null), noticeDismissed) { p, dismissed -> p?.takeIf { it != dismissed } }

    /** The voice notice was closed (it returns if the voice fails again in another way). */
    public fun dismissVoiceNotice() { noticeDismissed.value = voices?.problem?.value }
    private val privateNext = MutableStateFlow(false)
    private val lessons = MutableStateFlow<List<Lesson>>(emptyList())
    private val offer = MutableStateFlow<String?>(null)
    private val teaching = MutableStateFlow(false)

    init {
        scope.launch {
            val extras = combine(lessons, offer, teaching, notice) { l, o, t, n -> Extras(l, o, t, n) }
            combine(conversations.turns(conversationId), runner.state, tasks.recent(TASKS), privateNext, extras) { turns, run, _, private, (taught, offered, busy, problem) ->
                ConversationUiState(
                    title = conversations.get(conversationId)?.title.orEmpty(),
                    turns = turns.mapIndexed { i, t ->
                        val task = t.taskId?.let(tasks::get)
                        val reply = t.role == TurnRole.Mode
                        TurnItem(
                            id = t.id,
                            fromUser = !reply,
                            text = t.text,
                            day = clock.relativeDay(t.createdAt),
                            taskId = t.taskId,
                            badge = if (reply) task?.status?.badge() else null,
                            screens = if (reply) task?.runId?.let { runs.shots(it).size } ?: 0 else 0,
                            parts = if (reply) replyParts(withoutOldPrefix(t.text)) else listOf(ReplyPart.Paragraph(t.text)),
                            source = if (reply) null else task?.source,
                            atMs = t.createdAt,
                            steered = !reply && turns.getOrNull(i + 1)?.let { n -> n.role == TurnRole.Mode && n.text == STEERED } == true,
                        )
                    },
                    running = run.running(tasks)?.takeIf { it.conversationId == conversationId },
                    remembered = if (run.conversationId == conversationId) run.remembered.map { Remembered(it.id, it.text) } else emptyList(),
                    privateNext = private,
                    lessons = taught,
                    offerRemember = offered.takeIf { run.running && run.conversationId == conversationId },
                    teaching = busy,
                    voiceNotice = problem?.takeIf { turns.any { t -> t.taskId?.let(tasks::get)?.source == TaskSource.Voice } }?.let(com.piyush.mode.app.screens.settings.VoiceNotice::of),
                )
            }.collect { mutableState.value = it }
        }
    }

    /**
     * A message in this conversation; false for an empty one. While its task runs, it steers that task ("It's not
     * there, search for Dark Mode"). After a task, a lesson ("Next time use Control Center for this") is kept as a
     * tip for next time. Anything else is a follow-up task.
     */
    public fun send(text: String): Boolean {
        val said = text.trim()
        if (said.isEmpty()) return false
        val run = runner.state.value
        if (run.running && run.conversationId == conversationId && runner.steer(said)) {
            conversations.addTurn(conversationId, TurnRole.User, said)
            conversations.addTurn(conversationId, TurnRole.Mode, STEERED)
            offer.value = said
            return true
        }
        if (teacher != null && Teacher.isLesson(said) && lastTask() != null) {
            conversations.addTurn(conversationId, TurnRole.User, said)
            learn(said, step = null)
            return true
        }
        val sent = inbox.submit(said, TaskSource.App, conversationId, private = privateNext.value) != null
        if (sent) privateNext.value = false
        return sent
    }

    /** "Also do it this way next time?" → Remember: what the user steered with becomes a tip. */
    public fun remember() {
        val said = offer.value ?: return
        offer.value = null
        learn(said, step = null)
    }

    public fun dismissRemember() { offer.value = null }

    /** Undo on a lesson: the tip is forgotten (or, if it changed an older one, that one comes back). */
    public fun undoLesson(itemId: String) {
        val item = memory?.get(itemId)
        if (item?.previousText != null) memory.undo(itemId) else memory?.forget(itemId)
        lessons.value = lessons.value.filterNot { it.itemId == itemId }
    }

    /** Edit on a lesson: the tip in the user's own words. */
    public fun editLesson(itemId: String, text: String) {
        val item = memory?.get(itemId) ?: return
        val updated = memory.update(itemId, text.trim(), item.evidence) ?: return
        lessons.value = lessons.value.map { if (it.itemId == itemId) it.copy(text = updated.text) else it }
    }

    private fun learn(said: String, step: String?) {
        val t = teacher ?: return
        val task = lastTask().orEmpty()
        teaching.value = true
        scope.launch {
            try {
                val lesson = t.teach(said, task, step)
                val reply = conversations.addTurn(conversationId, TurnRole.Mode, lesson?.reply ?: NOT_SAVED)
                if (lesson != null) {
                    lessons.value = lessons.value + Lesson(lesson.item.id, lesson.item.app, lesson.item.title, lesson.item.text, reply.id)
                }
            } finally {
                teaching.value = false
            }
        }
    }

    /** What the user asked in this conversation's latest task. */
    private fun lastTask(): String? =
        conversations.turnsNow(conversationId).lastOrNull { it.role == TurnRole.User && it.taskId != null }?.taskId?.let(tasks::get)?.text

    public fun setPrivate(on: Boolean) {
        privateNext.value = on
    }

    public fun undoRemembered(itemId: String) {
        runner.undoRemembered(itemId)
    }

    public fun stop() {
        runner.stop()
    }

    /** Asks again what led to the reply [turnId]: the user's message just before it. */
    public fun retry(turnId: String): Boolean {
        val turns = state.value.turns
        val at = turns.indexOfFirst { it.id == turnId }
        val asked = turns.take(if (at < 0) 0 else at).lastOrNull { it.fromUser } ?: return false
        return send(asked.text)
    }

    /** The conversation as plain text, to share. */
    public fun transcript(): String = state.value.turns.joinToString("\n\n") { (if (it.fromUser) "› " else "") + it.text }

    /** The first thing asked here, to repeat on a schedule. */
    public fun taskToRepeat(): String = state.value.turns.firstOrNull { it.fromUser }?.text.orEmpty()

    /** Deletes the conversation and its turns; its tasks stay in Tasks. */
    public fun delete() {
        conversations.delete(conversationId)
    }

    private companion object {
        const val TASKS = 50L
        const val STEERED = "Okay, I'll do it that way."
        const val NOT_SAVED = "I couldn't keep that one. Try saying it another way."

        /** Replies saved before the status moved to a tag started with it in words. */
        val OLD_PREFIXES = listOf("I couldn't finish: ", "Failed: ")

        fun withoutOldPrefix(text: String): String =
            OLD_PREFIXES.firstOrNull { text.startsWith(it) }?.let { text.removePrefix(it).replaceFirstChar(Char::uppercaseChar) } ?: text
    }
}

private data class Extras(
    val lessons: List<Lesson>,
    val offered: String?,
    val busy: Boolean,
    val problem: com.piyush.mode.feature.settings.voice.VoiceProblem?,
)
