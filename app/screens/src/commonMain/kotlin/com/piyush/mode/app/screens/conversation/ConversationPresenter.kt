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
)

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
)

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
) : Presenter<ConversationUiState>(parent, ConversationUiState()) {
    private val privateNext = MutableStateFlow(false)

    init {
        scope.launch {
            combine(conversations.turns(conversationId), runner.state, tasks.recent(TASKS), privateNext) { turns, run, _, private ->
                ConversationUiState(
                    title = conversations.get(conversationId)?.title.orEmpty(),
                    turns = turns.map { t ->
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
                        )
                    },
                    running = run.running(tasks)?.takeIf { it.conversationId == conversationId },
                    remembered = if (run.conversationId == conversationId) run.remembered.map { Remembered(it.id, it.text) } else emptyList(),
                    privateNext = private,
                )
            }.collect { mutableState.value = it }
        }
    }

    /** A follow-up in this conversation; false for an empty message. */
    public fun send(text: String): Boolean {
        val sent = inbox.submit(text, TaskSource.App, conversationId, private = privateNext.value) != null
        if (sent) privateNext.value = false
        return sent
    }

    public fun setPrivate(on: Boolean) {
        privateNext.value = on
    }

    public fun undoRemembered(itemId: String) {
        runner.undoRemembered(itemId)
    }

    public fun stop() {
        runner.stop()
    }

    private companion object {
        const val TASKS = 50L
    }
}
