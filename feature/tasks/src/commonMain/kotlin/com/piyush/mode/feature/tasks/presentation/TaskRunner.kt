package com.piyush.mode.feature.tasks.presentation

import com.piyush.mode.agent.Agent
import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.agent.AgentResult
import com.piyush.mode.agent.UserGate
import com.piyush.mode.core.common.ActivityTracker
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.platform.AppInfo
import com.piyush.mode.core.platform.BackgroundWork
import com.piyush.mode.core.platform.Notifier
import com.piyush.mode.core.storage.FileStore
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.runs.data.RunRecorder
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.feature.runs.domain.describeResult
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.conversations.data.ConversationRepository
import com.piyush.mode.feature.conversations.domain.TurnRole
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.domain.PendingQuestion
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Runs agent tasks: saves the task and its conversation, connects, records
 * the run, relays the agent's questions to the user, notifies when done, and
 * always lets go of the phone after.
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
) {
    private val mutableState = MutableStateFlow(TaskState())
    public val state: StateFlow<TaskState> = mutableState.asStateFlow()

    private var job: Job? = null

    /** Starts [task] unless one is running; a [conversationId] makes it a follow-up there. */
    public fun start(
        task: String,
        config: AgentConfig = settings.agentConfig(),
        conversationId: String? = null,
        source: TaskSource = TaskSource.App,
        onFinished: (AgentResult?) -> Unit = {},
    ) {
        if (state.value.running || task.isBlank()) return
        job = scope.launch { onFinished(runTask(task, config, conversationId = conversationId, source = source)) }
    }

    /** Stops the running task; a pending question counts as stopped. */
    public fun stop() {
        job?.cancel()
        state.value.question?.answer?.complete(null)
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
     * with a [tag] (model comparisons) get none.
     */
    public suspend fun runTask(
        task: String,
        config: AgentConfig,
        tag: String? = null,
        stayConnected: Boolean = false,
        conversationId: String? = null,
        source: TaskSource = TaskSource.App,
    ): AgentResult? = activity.track { run(task, config, tag, stayConnected, conversationId, source) }

    private suspend fun run(
        task: String,
        config: AgentConfig,
        tag: String?,
        stayConnected: Boolean,
        conversationId: String?,
        source: TaskSource,
    ): AgentResult? {
        val conversation = conversationId ?: if (tag == null) conversations.create(task).id else null
        val saved = tasks.create(task, source, conversation)
        conversation?.let { conversations.addTurn(it, TurnRole.User, task, saved.id) }
        mutableState.update { TaskState(running = true, taskId = saved.id, conversationId = conversation) }
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
            status("Connecting to the phone…")
            val phone = try {
                session.phone()
            } catch (e: Exception) {
                recorder.failedToStart(task, "could not connect: ${e.message}")
                throw e
            }
            val agent = Agent(
                phone = phone,
                llm = llm,
                gate = gate(phone),
                config = config,
                onStep = { step -> mutableState.update { it.copy(steps = it.steps + step) } },
                onStatus = ::status,
                trace = recorder,
            )
            // Started from Mode's own screen; the agent goes straight to the app it needs.
            val finished = background.run { agent.run(task, startsInMode = true) }
            result = finished
            outcome = statusOf(finished.outcome)
            reply = replyFor(finished.outcome)
            val line = describeResult(finished)
            mutableState.update { it.copy(lastResult = finished, status = line) }
            notifier.post("Mode", line)
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
            reply = "Failed: ${e.message}"
            AppLog.error("agent", "run ${recorder.id} failed", e)
            status(reply)
            notifier.post("Mode", "Task failed: ${e.message}")
            return null
        } finally {
            tasks.finish(saved.id, outcome, reply, result?.steps, result?.costUsd)
            conversation?.let { conversations.addTurn(it, TurnRole.Mode, reply, saved.id) }
            mutableState.update { it.copy(running = false, question = null) }
            // Also after Stop: cleanup must not be cancelled with the task.
            if (!stayConnected) withContext(NonCancellable) { session.disconnect() }
        }
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
    }

    private suspend fun askUser(phone: PhoneBackend, text: String, yesNo: Boolean): String? {
        val pending = PendingQuestion(text, yesNo)
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

    /** What Mode says in the conversation when a task ends. */
    private fun replyFor(outcome: AgentOutcome): String = when (outcome) {
        is AgentOutcome.Done -> if (outcome.success) outcome.summary else "I couldn't finish: ${outcome.summary}"
        is AgentOutcome.Stopped -> outcome.why
        is AgentOutcome.Failed -> "Failed: ${outcome.why}"
    }
}
