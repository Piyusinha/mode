package com.piyush.mode.feature.tasks.presentation

import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.BackgroundWork
import com.piyush.mode.core.platform.DeviceLock
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
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tasks from every source wait here, saved in the database, and run one after
 * another through the [TaskRunner]. The queue survives Mode being closed: at
 * the next launch [recover] marks what was interrupted and runs what is still
 * fresh.
 *
 * Mode can't drive other apps on a locked iPhone, so a task that comes in while it is locked (from
 * Siri, a schedule, a Shortcut) waits in [waitingForUnlock] and starts the moment the iPhone unlocks,
 * by Face ID, the passcode, or the user's own "Unlock by voice" phrase. Mode stays awake meanwhile.
 */
public class TaskQueue(
    private val runner: TaskRunner,
    private val tasks: TaskRepository,
    private val conversations: ConversationRepository,
    private val scope: CoroutineScope,
    private val lock: DeviceLock = DeviceLock.None,
    private val background: BackgroundWork? = null,
    private val now: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
    private val telemetry: com.piyush.mode.core.telemetry.Telemetry = com.piyush.mode.core.telemetry.Telemetry.None,
) : TaskInbox {
    private val waiting = MutableStateFlow<List<Task>>(emptyList())
    private val lockWait = MutableStateFlow<Task?>(null)

    /** Tasks waiting their turn, oldest first (not the one running). */
    public val queue: StateFlow<List<Task>> = waiting.asStateFlow()

    /** The next task, held until the iPhone unlocks; null when nothing is waiting for that. */
    public val waitingForUnlock: StateFlow<Task?> = lockWait.asStateFlow()

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
        tasks.finish(task.id, TaskStatus.Stopped, CANCELLED, null, null)
        task.conversationId?.let { conversations.addTurn(it, TurnRole.Mode, CANCELLED, task.id) }
        if (lockWait.value?.id == taskId) lockWait.value = null
        refresh()
    }

    /** Reset Mode: nothing more is started; the waiting list empties (their rows are deleted next). */
    public fun clear() {
        drain?.cancel()
        drain = null
        waiting.value = emptyList()
        lockWait.value = null
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
                if (lock.locked.value) {
                    // Cancelled, timed out, or Mode closing: look at the queue again (the task may be gone).
                    awaitUnlock(next)
                    continue
                }
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

    /** Holds [task] until the iPhone unlocks, it is cancelled, or it has waited [MAX_WAIT_MS]. */
    private suspend fun awaitUnlock(task: Task) {
        AppLog.log("queue", "task ${task.id} waits for the iPhone to unlock")
        lockWait.value = task
        val since = now()
        val work: suspend () -> Boolean? = {
            withTimeoutOrNull(MAX_WAIT_MS) {
                combine(lock.locked, lockWait) { locked, held -> !locked || held?.id != task.id }.first { it }
            }
        }
        val ended = try {
            background?.run(work) ?: work()
        } finally {
            if (lockWait.value?.id == task.id) lockWait.value = null
        }
        val outcome = when {
            ended == null -> "timed_out"
            tasks.get(task.id)?.status != TaskStatus.Queued -> "cancelled"
            else -> "started"
        }
        telemetry.event(com.piyush.mode.core.telemetry.Event.WaitedForUnlock, fields = mapOf(com.piyush.mode.core.telemetry.Field.Outcome to outcome))
        if (ended == null && tasks.get(task.id)?.status == TaskStatus.Queued) {
            AppLog.log("queue", "task ${task.id} waited ${(now() - since) / 1000} s for an unlock; not started")
            tasks.finish(task.id, TaskStatus.Stopped, WAITED_TOO_LONG, null, null)
            task.conversationId?.let { conversations.addTurn(it, TurnRole.Mode, WAITED_TOO_LONG, task.id) }
            refresh()
        } else if (!lock.locked.value) {
            AppLog.log("queue", "unlocked after ${(now() - since) / 1000} s; task ${task.id} starts")
        }
    }

    private fun refresh(excluding: String? = null) {
        val running = runner.state.value.takeIf { it.running }?.taskId
        waiting.value = tasks.queued().filter { it.id != excluding && it.id != running }
    }

    public companion object {
        /** A task that waited longer than this while Mode was closed is not run at the next launch. */
        public const val MAX_WAIT_MS: Long = 60 * 60 * 1000L
        internal const val CANCELLED: String = "Cancelled before it started."
        internal const val WAITED_TOO_LONG: String = "Your iPhone stayed locked, so I didn't start this. Ask again when it's unlocked."
    }
}
