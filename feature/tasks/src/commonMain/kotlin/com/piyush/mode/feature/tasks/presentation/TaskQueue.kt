package com.piyush.mode.feature.tasks.presentation

import com.piyush.mode.core.common.AppLog
import com.piyush.mode.feature.conversations.data.ConversationRepository
import com.piyush.mode.feature.conversations.domain.TurnRole
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.domain.Task
import com.piyush.mode.feature.tasks.domain.TaskInbox
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.domain.TaskStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Tasks from every source wait here, saved in the database, and run one after
 * another through the [TaskRunner]. The queue survives Mode being closed: at
 * the next launch [recover] marks what was interrupted and runs what is still
 * fresh.
 */
public class TaskQueue(
    private val runner: TaskRunner,
    private val tasks: TaskRepository,
    private val conversations: ConversationRepository,
    private val scope: CoroutineScope,
) : TaskInbox {
    private val waiting = MutableStateFlow<List<Task>>(emptyList())

    /** Tasks waiting their turn, oldest first (not the one running). */
    public val queue: StateFlow<List<Task>> = waiting.asStateFlow()

    private var drain: Job? = null

    override fun submit(text: String, source: TaskSource, conversationId: String?, private: Boolean): Task? {
        if (text.isBlank()) return null
        val task = runner.admit(text.trim(), source, conversationId, private = private)
        AppLog.log("queue", "task ${task.id} from ${source.name.lowercase()} queued")
        refresh()
        kick()
        return task
    }

    override fun cancel(taskId: String) {
        if (runner.state.value.taskId == taskId && runner.state.value.running) {
            runner.stop()
            return
        }
        val task = tasks.get(taskId)?.takeIf { it.status == TaskStatus.Queued } ?: return
        tasks.finish(task.id, TaskStatus.Stopped, "Cancelled before it started.", null, null)
        task.conversationId?.let { conversations.addTurn(it, TurnRole.Mode, "Cancelled before it started.", task.id) }
        refresh()
    }

    /** At launch: tidy up after the last session, then run whatever is still waiting. */
    public fun recover() {
        tasks.tidyAfterRelaunch(MAX_WAIT_MS)
        refresh()
        kick()
    }

    /** Runs the oldest waiting task, then the next, until none are left. */
    private fun kick() {
        if (drain?.isActive == true) return
        drain = scope.launch {
            while (true) {
                val next = tasks.queued().firstOrNull() ?: break
                refresh(excluding = next.id)
                try {
                    // Stay connected when another task is already waiting behind this one.
                    runner.runQueued(next, stayConnected = tasks.queued().size > 1)
                } catch (e: CancellationException) {
                    // Stop ends this task only; the queue goes on unless Mode itself is closing.
                    if (!currentCoroutineContext().isActive) throw e
                }
                refresh()
            }
        }
    }

    private fun refresh(excluding: String? = null) {
        val running = runner.state.value.takeIf { it.running }?.taskId
        waiting.value = tasks.queued().filter { it.id != excluding && it.id != running }
    }

    public companion object {
        /** A task that waited longer than this while Mode was closed is not run at the next launch. */
        public const val MAX_WAIT_MS: Long = 60 * 60 * 1000L
    }
}
