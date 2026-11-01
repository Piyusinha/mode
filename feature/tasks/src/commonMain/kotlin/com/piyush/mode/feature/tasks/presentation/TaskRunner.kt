package com.piyush.mode.feature.tasks.presentation

import kotlinx.datetime.toLocalDateTime
import com.piyush.mode.core.common.PlainText
import com.piyush.mode.agent.Agent
import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.agent.AgentResult
import com.piyush.mode.agent.PauseGate
import com.piyush.mode.agent.TaskContext
import com.piyush.mode.agent.UserGate
import com.piyush.mode.core.common.ActivityTracker
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.common.startOfTodayMillis
import com.piyush.mode.core.platform.AppInfo
import com.piyush.mode.core.platform.BackgroundWork
import com.piyush.mode.core.platform.Notifier
import com.piyush.mode.core.storage.FileStore
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.runs.data.RunRecorder
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.conversations.data.ConversationRepository
import com.piyush.mode.feature.conversations.domain.TurnRole
import com.piyush.mode.feature.tasks.data.TaskMemory
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.domain.PendingQuestion
import com.piyush.mode.feature.tasks.domain.Task
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.domain.TaskState
import com.piyush.mode.feature.tasks.domain.TaskStatus
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.ops.Op
import com.piyush.mode.ops.PhoneBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Runs agent tasks: saves the task and its conversation, connects, records
 * the run, relays the agent's questions to the user, notifies when done, and
 * always lets go of the phone after. One task at a time, whoever asks: there
 * is one phone. Tasks from every source arrive through [TaskQueue].
 */
public class TaskRunner(
    private val session: PhoneSession,
    private val settings: SettingsRepository,
    private val llm: LlmClient,
    private val files: FileStore,
    private val runs: RunRepository,
    private val conversations: ConversationRepository,
    private val tasks: TaskRepository,
    private val notifier: Notifier,
    private val background: BackgroundWork,
    private val appInfo: AppInfo,
    private val activity: ActivityTracker,
    private val scope: CoroutineScope,
    /** What Mode remembers; null runs every task without memory. */
    private val memory: TaskMemory? = null,
    /** Midnight today, for the daily spend cap. */
    private val startOfToday: () -> Long = ::startOfTodayMillis,
) {
    private val mutableState = MutableStateFlow(TaskState())
    public val state: StateFlow<TaskState> = mutableState.asStateFlow()

    private var job: Job? = null
    /** The task running now, to stop it without stopping the queue. */
    private var current: Job? = null
    /** Pause for the task running now. */
    private var pauseGate: PauseGate? = null

    /** One phone: a queued task and a comparison run never drive it at once. */
    private val phoneLock = Mutex()

    /** Starts [task] unless one is running; a [conversationId] makes it a follow-up there. */
    public fun start(
        task: String,
        config: AgentConfig = settings.agentConfig(),
        conversationId: String? = null,
        source: TaskSource = TaskSource.App,
        private: Boolean = false,
        onFinished: (AgentResult?) -> Unit = {},
    ) {
        if (state.value.running || task.isBlank()) return
        job = scope.launch { onFinished(runTask(task, config, conversationId = conversationId, source = source, private = private)) }
    }

    /** Takes back something a task taught Mode, from the "Remembered · Undo" chip. */
    public fun undoRemembered(itemId: String) {
        memory?.undo(itemId)
        mutableState.update { s -> s.copy(remembered = s.remembered.filter { it.id != itemId }) }
    }

    /** Stops the running task; a pending question counts as stopped. */
    public fun stop() {
        job?.cancel()
        current?.cancel()
        state.value.question?.answer?.complete(null)
    }

    /** Pauses the running task after its current step. */
    public fun pause() {
        pauseGate?.pause()
        mutableState.update { it.copy(paused = true, status = "Paused") }
    }

    public fun resume() {
        pauseGate?.resume()
        mutableState.update { it.copy(paused = false) }
    }

    /** Answers the pending question; null stops the task. */
    public fun answer(text: String?) {
        state.value.question?.answer?.complete(text)
        mutableState.update { it.copy(question = null) }
    }

    /**
     * Runs one task to the end; null if it could not start or failed.
     * Disconnects after, unless [stayConnected] (more tasks follow at once).
     * A new conversation is started unless [conversationId] is given; runs
     * with a [tag] (model comparisons) get none. A [private] task neither
     * uses nor adds to memory; neither do comparison runs.
     */
    public suspend fun runTask(
        task: String,
        config: AgentConfig,
        tag: String? = null,
        stayConnected: Boolean = false,
        conversationId: String? = null,
        source: TaskSource = TaskSource.App,
        private: Boolean = false,
    ): AgentResult? {
        val saved = admit(task, source, conversationId, tag, private)
        return runSaved(saved, config, tag, stayConnected)
    }

    /** Runs a task that was saved earlier (it waited in the queue). */
    public suspend fun runQueued(task: Task, config: AgentConfig = settings.agentConfig(), stayConnected: Boolean = false): AgentResult? =
        runSaved(task, config, tag = null, stayConnected)

    /**
     * Saves a new task, with its conversation (a new one unless
     * [conversationId] is given; none for comparison runs, which have a
     * [tag]) and the user's turn, ready to run.
     */
    public fun admit(text: String, source: TaskSource, conversationId: String?, tag: String? = null, private: Boolean = false): Task {
        val conversation = conversationId ?: if (tag == null) conversations.create(text).id else null
        val saved = tasks.create(text, source, conversation, private)
        conversation?.let { conversations.addTurn(it, TurnRole.User, text, saved.id) }
        return saved
    }

    private suspend fun runSaved(saved: Task, config: AgentConfig, tag: String?, stayConnected: Boolean): AgentResult? =
        phoneLock.withLock {
            activity.track {
                coroutineScope {
                    current = coroutineContext[Job]
                    try {
                        run(saved, config, tag, stayConnected)
                    } finally {
                        current = null
                    }
                }
            }
        }

    private suspend fun run(saved: Task, config: AgentConfig, tag: String?, stayConnected: Boolean): AgentResult? {
        val task = saved.text
        val private = saved.private
        val conversation = saved.conversationId
        // A follow-up sees how the earlier tasks in its conversation went (not this task's own turn).
        val earlier = conversation?.let { earlierTurns(it, excluding = saved.id) }.orEmpty()
        mutableState.update { TaskState(running = true, taskId = saved.id, conversationId = conversation, startedAtMs = startOfNow()) }
        val gate = PauseGate().also { pauseGate = it }
        notifier.requestPermission()
        runs.prune()
        val recorder = RunRecorder(files, tag = tag, index = runs, taskId = saved.id)
        tasks.markRunning(saved.id, recorder.id)
        mutableState.update { it.copy(lastRunId = recorder.id) }
        var outcome = TaskStatus.Failed
        var reply = "Failed."
        var result: AgentResult? = null
        try {
            if (!settings.hasApiKey) {
                outcome = TaskStatus.NotStarted
                reply = "Add your OpenRouter API key in Settings first."
                status(reply)
                recorder.failedToStart(task, "no API key")
                return null
            }
            val budget = withinDailyCap(config)
            if (budget == null) {
                outcome = TaskStatus.NotStarted
                reply = "Today's spending limit of ${money(settings.settings.value.dailyCapUsd ?: 0.0)} is reached. " +
                    "You can change it in Settings."
                status(reply)
                recorder.failedToStart(task, "daily limit reached")
                return null
            }
            status("Getting started…")
            val phone = try {
                session.phone()
            } catch (e: Exception) {
                recorder.failedToStart(task, "could not connect: ${e.message}")
                throw e
            }
            val useMemory = !private && tag == null
            val base = TaskContext(userName = settings.settings.value.userName, earlierTurns = earlier, today = today())
            val prepared = memory?.prepare(task, base, useMemory, saved.id)
            val agent = Agent(
                phone = phone,
                llm = llm,
                gate = gate(phone),
                config = budget,
                onStep = { step -> mutableState.update { it.copy(steps = it.steps + step) } },
                onPlan = { items -> mutableState.update { it.copy(plan = items) } },
                onStatus = ::status,
                trace = recorder,
                memory = prepared?.agentMemory,
                pause = gate,
                onScreen = { jpeg -> mutableState.update { it.copy(latestScreen = jpeg) } },
            )
            // Started from Mode's own screen; the agent goes straight to the app it needs.
            val finished = background.run { agent.run(task, startsInMode = true, context = prepared?.context ?: base) }
            result = finished
            if (useMemory && prepared != null) learnFrom(saved.id, task, finished, prepared.usedIds, budget.model)
            outcome = statusOf(finished.outcome)
            reply = replyFor(finished.outcome)
            // Time, steps and cost stay in the run log; what the user sees is the answer.
            mutableState.update { it.copy(lastResult = finished, status = PlainText.fromMarkdown(reply, oneLine = true)) }
            // The notification reads like the chat reply, as plain text (it can't draw Markdown).
            notifier.postResult("Mode", PlainText.fromMarkdown(reply))
            // Come back so the user sees how it went.
            runCatching { phone.send(Op.LaunchApp(appInfo.bundleId)) }
            return finished
        } catch (e: CancellationException) {
            outcome = TaskStatus.Stopped
            reply = "Stopped."
            status(reply)
            AppLog.log("agent", "run ${recorder.id} stopped by the user")
            throw e
        } catch (e: Exception) {
            outcome = TaskStatus.Failed
            // The chat shows "Failed" as a tag; the reply is just what went wrong.
            reply = e.message ?: "Something went wrong."
            AppLog.error("agent", "run ${recorder.id} failed", e)
            status("Failed: $reply")
            notifier.post("Mode", "Task failed: ${e.message}")
            return null
        } finally {
            tasks.finish(saved.id, outcome, reply, result?.steps, result?.costUsd)
            conversation?.let { conversations.addTurn(it, TurnRole.Mode, reply, saved.id) }
            mutableState.update { it.copy(running = false, question = null, paused = false) }
            pauseGate = null
            // Also after Stop: cleanup must not be cancelled with the task.
            if (!stayConnected) withContext(NonCancellable) { session.disconnect() }
        }
    }

    /**
     * [config] with its per-task limit cut to what is left of today's cap, or null
     * when the cap is already spent. Unchanged when there is no daily cap.
     */
    private fun withinDailyCap(config: AgentConfig): AgentConfig? {
        val cap = settings.settings.value.dailyCapUsd ?: return config
        val left = cap - tasks.spentSince(startOfToday())
        if (left <= 0.0) return null
        return config.copy(maxCostUsd = minOf(config.maxCostUsd ?: left, left))
    }

    /**
     * After a task: record how it went with what it was shown, and, when
     * learning is on, reflect on it in the background (the user already has
     * the answer, so this never slows a task down).
     */
    private fun learnFrom(taskId: String, task: String, result: AgentResult, usedIds: List<String>, model: String) {
        val memory = memory ?: return
        memory.recordUse(result, usedIds)
        val settings = settings.settings.value
        if (!settings.learnFromTasks) return
        scope.launch {
            try {
                val reflection = memory.learn(taskId, task, result, settings.reflectionModel ?: model)
                AppLog.log(
                    "memory",
                    "task $taskId: saved ${reflection.saved.size}, refused ${reflection.refused.size}" +
                        (reflection.failureType?.let { ", failure $it: ${reflection.rootCause}" } ?: ""),
                )
                if (reflection.remembered.isNotEmpty()) {
                    mutableState.update { s -> if (s.taskId == taskId) s.copy(remembered = reflection.remembered) else s }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppLog.error("memory", "could not learn from task $taskId", e)
            }
        }
    }

    /** Each earlier task in the conversation with Mode's reply to it. */
    private fun earlierTurns(conversationId: String, excluding: String): List<TaskContext.EarlierTurn> {
        val turns = conversations.turnsNow(conversationId).filter { it.taskId != excluding }
        return turns.mapIndexedNotNull { i, turn ->
            if (turn.role != TurnRole.User) return@mapIndexedNotNull null
            val reply = turns.getOrNull(i + 1)?.takeIf { it.role == TurnRole.Mode }?.text ?: "(no result)"
            TaskContext.EarlierTurn(turn.text, reply)
        }
    }

    private fun money(usd: Double): String = "$" + ((usd * 100).roundToInt() / 100.0).toString()

    private fun startOfNow(): Long = kotlin.time.Clock.System.now().toEpochMilliseconds()

    /** "Sunday 1 November 2026, 19:05", for the brain: "today" means today. */
    private fun today(): String {
        val now = kotlin.time.Clock.System.now().toLocalDateTime(kotlinx.datetime.TimeZone.currentSystemDefault())
        fun name(s: String) = s.lowercase().replaceFirstChar(Char::uppercaseChar)
        return "${name(now.dayOfWeek.name)} ${now.day} ${name(now.month.name)} ${now.year}, " +
            "${now.hour.toString().padStart(2, '0')}:${now.minute.toString().padStart(2, '0')}"
    }

    private fun status(text: String) {
        mutableState.update { it.copy(status = text) }
    }

    /**
     * Questions bring Mode to the front (and notify) so the user can answer;
     * the model is then told Mode is in front so it reopens its app.
     */
    private fun gate(phone: PhoneBackend) = object : UserGate {
        override suspend fun confirm(question: String): Boolean = askUser(phone, question, yesNo = true) == "yes"

        override suspend fun ask(question: String): String? = askUser(phone, question, yesNo = false)
            ?.let { "$it (The user answered in the Mode app, so Mode is now on screen: reopen the app you were using.)" }

        override suspend fun choose(question: String, options: List<String>, recommended: String?): String? =
            askUser(phone, question, yesNo = false, options = options, recommended = recommended)
                ?.let { "$it (The user answered in the Mode app, so Mode is now on screen: reopen the app you were using.)" }
    }

    private suspend fun askUser(
        phone: PhoneBackend,
        text: String,
        yesNo: Boolean,
        options: List<String> = emptyList(),
        recommended: String? = null,
    ): String? {
        val pending = PendingQuestion(text, yesNo, options, recommended)
        AppLog.log("agent", (if (yesNo) "confirm: " else "ask: ") + text)
        mutableState.update { it.copy(question = pending, status = "Waiting for you") }
        state.value.taskId?.let { tasks.setStatus(it, TaskStatus.WaitingForUser) }
        notifier.post("Mode needs you", text)
        runCatching { phone.send(Op.LaunchApp(appInfo.bundleId)) }
        return pending.answer.await().also {
            AppLog.log("agent", "user answered: ${it ?: "(stopped)"}")
            mutableState.update { state -> state.copy(question = null) }
            state.value.taskId?.let { id -> tasks.setStatus(id, TaskStatus.Running) }
        }
    }

    private fun statusOf(outcome: AgentOutcome): TaskStatus = when (outcome) {
        is AgentOutcome.Done -> if (outcome.success) TaskStatus.Done else TaskStatus.GaveUp
        is AgentOutcome.Stopped -> TaskStatus.Stopped
        is AgentOutcome.Failed -> TaskStatus.Failed
    }

    /** What Mode says in the conversation when a task ends; how it ended shows as a tag beside it. */
    private fun replyFor(outcome: AgentOutcome): String = when (outcome) {
        is AgentOutcome.Done -> outcome.summary
        is AgentOutcome.Stopped -> outcome.why
        is AgentOutcome.Failed -> outcome.why
    }
}
