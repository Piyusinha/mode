package com.piyush.mode.feature.tasks.presentation

import com.piyush.mode.core.telemetry.TraceSpan
import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.core.telemetry.Operation
import com.piyush.mode.core.telemetry.Field
import com.piyush.mode.core.telemetry.Event
import com.piyush.mode.core.telemetry.currentContext
import com.piyush.mode.core.telemetry.trace
import com.piyush.mode.core.telemetry.withLock

import com.piyush.mode.llm.ModelProvider

import com.piyush.mode.core.platform.QuestionKind
import kotlinx.datetime.toLocalDateTime
import com.piyush.mode.core.common.PlainText
import com.piyush.mode.agent.Agent
import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.agent.AgentResult
import com.piyush.mode.agent.PauseGate
import com.piyush.mode.agent.TaskContext
import com.piyush.mode.agent.UserGate
import com.piyush.mode.feature.settings.domain.AccessRules
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
import com.piyush.mode.feature.settings.voice.Heard
import com.piyush.mode.feature.settings.voice.Voices
import com.piyush.mode.feature.conversations.data.ConversationRepository
import com.piyush.mode.feature.conversations.domain.TurnRole
import com.piyush.mode.feature.tasks.data.TaskMemory
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.domain.PendingQuestion
import com.piyush.mode.feature.tasks.domain.Task
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.domain.TaskState
import com.piyush.mode.feature.tasks.domain.TaskStatus
import com.piyush.mode.feature.tasks.domain.VoiceTurn
import com.piyush.mode.feature.tasks.domain.VoiceTurnPhase
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
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
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
    /** Speaks questions and the summary, and hears a spoken answer. Null keeps today's taps. */
    private val voices: Voices? = null,
    private val telemetry: Telemetry = Telemetry.None,
    /** Spoken updates while a spoken task works (null: Mode stays quiet until the answer). */
    private val narrator: Narrator? = null,
) {
    /** Messages for the running task, taken by the agent at its next step. */
    private val steers = MutableStateFlow<List<String>>(emptyList())

    /** The spoken question being asked now (Later cancels it). */
    private var voiceJob: kotlinx.coroutines.Job? = null

    /** The running task was spoken to Mode: its questions and summary are spoken and heard. */
    @kotlin.concurrent.Volatile private var spoken = false
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
    /** Mode came to the front for the last question (no notifications), so the agent must reopen its app. */
    private var askedInMode = true

    private fun whereAnswered(answer: String): String =
        if (askedInMode) "$answer (The user answered in the Mode app, so Mode is now on screen: reopen the app you were using.)"
        else "$answer (The user answered from the notification; the app you were using is still in front.)"

    /** An answer to the question [questionId] (from a notification or the Live Activity); ignored if it was already answered. */
    public fun answer(questionId: String, text: String?) {
        if (state.value.question?.id == questionId) answer(text)
    }

    public fun answer(text: String?) {
        val question = state.value.question ?: return
        if (!question.answer.complete(text)) return
        mutableState.update { if (it.question?.id == question.id) it.copy(question = null) else it }
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

    private suspend fun runSaved(saved: Task, config: AgentConfig, tag: String?, stayConnected: Boolean): AgentResult? {
        val taskStarted = kotlin.time.TimeSource.Monotonic.markNow()
        return telemetry.trace(Operation.Task, mapOf(Field.TaskId to saved.id, Field.Source to saved.source.name)) { span ->
            telemetry.withLock(phoneLock, Operation.TaskLock) {
                activity.track {
                    coroutineScope {
                        current = coroutineContext[Job]
                        try {
                            run(saved, config, tag, stayConnected, taskStarted, span)
                        } finally {
                            current = null
                        }
                    }
                }
            }
        }
    }

    private suspend fun run(saved: Task, config: AgentConfig, tag: String?, stayConnected: Boolean, taskStarted: kotlin.time.TimeMark, span: TraceSpan): AgentResult? {
        val task = saved.text
        // Only a task the user spoke is answered by voice: others keep the quiet notification and Live Activity.
        spoken = saved.source == TaskSource.Voice && settings.settings.value.talkBack
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
            if (!settings.hasAccess(config.provider)) {
                outcome = TaskStatus.NotStarted
                reply = when (config.provider) {
                    ModelProvider.ClaudeOAuth -> "Connect Claude in Settings first."
                    ModelProvider.ChatGpt -> "Continue with ChatGPT in Settings first."
                    ModelProvider.OpenRouter -> "Connect OpenRouter in Settings first."
                }
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
            val taskContext = telemetry.currentContext()
            var firstAction = true
            val phone = try {
                val connected = session.phone()
                object : PhoneBackend by connected {
                    override suspend fun send(op: Op): kotlinx.serialization.json.JsonElement {
                        val result = connected.send(op)
                        if (firstAction && op !is Op.ScreenCapture && op !is Op.ScreenText && op != Op.ScreenBounds && op != Op.SessionState && op != Op.Tree && op != Op.CurrentApp) {
                            firstAction = false
                            telemetry.event(Event.FirstAction, taskContext, mapOf(Field.DurationMs to taskStarted.elapsedNow().inWholeMilliseconds.toString()))
                        }
                        return result
                    }
                }
            } catch (e: Exception) {
                recorder.failedToStart(task, "could not connect: ${e.message}")
                throw e
            }
            // A trigger word ("just do it, order my usual") gives this task full access; the agent reads the rest.
            val access = AccessRules.forTask(task, settings.settings.value.triggerWords)
            val agentTask = if (budget.fullAccess) task else access.task
            val runConfig = if (access.fullAccess && !budget.fullAccess) {
                AppLog.log("agent", "full access for this task (trigger word)")
                budget.copy(fullAccess = true, askAboveAmount = settings.settings.value.askAboveAmount)
            } else budget
            val useMemory = !private && tag == null
            val base = TaskContext(userName = settings.settings.value.userName, earlierTurns = earlier, today = today())
            val prepared = telemetry.trace(Operation.MemoryPrepare) { memory?.prepare(agentTask, base, useMemory, saved.id) }
            val agent = Agent(
                phone = phone,
                llm = llm.forProvider(budget.provider),
                gate = gate(phone),
                config = runConfig,
                onStep = { step -> mutableState.update { it.copy(steps = it.steps + step) } },
                onPlan = { items -> mutableState.update { it.copy(plan = items) } },
                onStatus = ::status,
                trace = recorder,
                telemetry = telemetry,
                memory = prepared?.agentMemory,
                pause = gate,
                onScreen = { jpeg -> mutableState.update { it.copy(latestScreen = jpeg) } },
            )
            // Spoken tasks: Mode keeps the user posted while it works, and stops before the answer.
            val updates = if (spoken) narrator?.let { n -> scope.launch { narrate(n, task, taskStarted) } } else null
            // Started from Mode's own screen; the agent goes straight to the app it needs.
            val finished = try {
                background.run { agent.run(agentTask, startsInMode = true, context = prepared?.context ?: base) }
            } finally {
                updates?.cancel()
                if (updates != null) voices?.stop()
            }
            result = finished
            if (useMemory && prepared != null) learnFrom(saved.id, task, finished, prepared.usedIds, budget)
            outcome = statusOf(finished.outcome)
            reply = replyFor(finished.outcome)
            // Time, steps and cost stay in the run log; what the user sees is the answer.
            mutableState.update { it.copy(lastResult = finished, status = PlainText.fromMarkdown(reply, oneLine = true)) }
            // Spoken while the microphone is off. The stream stays up.
            if (spoken && settings.settings.value.speakResult && finished.outcome is AgentOutcome.Done) {
                // The agent's spoken version (a sentence or two); the chat reply flattened if it gave none.
                val said = (finished.outcome as? AgentOutcome.Done)?.spoken ?: PlainText.fromMarkdown(reply, oneLine = true)
                runCatching { voices?.speak(said) }
            }
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
            span.fields(mapOf(Field.Outcome to outcome.name))
            if (outcome == TaskStatus.Failed) span.failed("TaskFailed")
            tasks.finish(saved.id, outcome, reply, result?.steps,
                if (config.provider.reportsCurrencyCost) result?.costUsd else null)
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
        // OAuth reports tokens but no currency cost; only provider quotas and step/time limits apply.
        if (!config.provider.reportsCurrencyCost) return config.copy(maxCostUsd = null)
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
    private suspend fun learnFrom(taskId: String, task: String, result: AgentResult, usedIds: List<String>, config: AgentConfig) {
        val memory = memory ?: return
        memory.recordUse(result, usedIds)
        val settings = settings.settings.value
        val reflectionModel = settings.reflectionModel.takeIf { settings.provider == config.provider } ?: config.model
        if (!settings.learnFromTasks) return
        val parent = telemetry.currentContext()
        scope.launch {
            try {
                val reflection = telemetry.trace(Operation.Reflection, mapOf(Field.TaskId to taskId), parent = parent) { memory.learn(taskId, task, result, reflectionModel, config.provider) }
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

    /**
     * Says a short update now and then while the task runs: when the plan moves on, paced by
     * [Narrator.Pacing], never over a spoken question, never the same thing twice.
     */
    private suspend fun narrate(narrator: Narrator, task: String, started: kotlin.time.TimeMark) {
        val pacing = Narrator.Pacing()
        val said = mutableListOf<String>()
        while (true) {
            delay(NARRATE_TICK_MS)
            val s = state.value
            if (!s.running) return
            if (s.voice != null || s.question != null || s.paused) continue
            val done = s.plan.filter { it.done }.map { it.text }
            val next = s.plan.firstOrNull { !it.done }?.text
            val progress = "${done.size}|${next ?: s.status}"
            val elapsed = started.elapsedNow().inWholeMilliseconds
            if (!pacing.due(elapsed, progress)) continue
            pacing.said(elapsed, progress)
            telemetry.trace(Operation.VoiceUpdate) { span ->
                val line = narrator.line(task, done, next ?: s.status, said, settings.settings.value.userName)
                val stillQuiet = state.value.running && state.value.voice == null && state.value.question == null
                if (line == null || !stillQuiet) {
                    span.fields(mapOf(Field.Outcome to if (line == null) "Skipped" else "Overtaken"))
                } else {
                    said += line
                    voices?.speak(line)
                    span.fields(mapOf(Field.Outcome to "Spoke"))
                }
            }
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
        /** "Yes, and don't ask again" was tapped in this task: the rest of it asks no more. */
        var alwaysYes = false

        override fun steering(): List<String> = steers.getAndUpdate { emptyList() }

        override suspend fun confirm(question: String): Boolean {
            if (alwaysYes) return true
            val answer = askUser(phone, question, yesNo = true, allowAlways = true)
            if (answer == PendingQuestion.ALWAYS) {
                // From now on every task has full access (Settings → Access turns it back).
                settings.update { it.copy(fullAccess = true) }
                alwaysYes = true
                AppLog.log("agent", "full access turned on from a question")
            }
            return answer == "yes" || answer == PendingQuestion.ALWAYS
        }

        override suspend fun yourTurn(what: String): Boolean = askUser(phone, what, yesNo = false, yourTurn = true) != null

        override suspend fun ask(question: String): String? = askUser(phone, question, yesNo = false)?.let(::whereAnswered)

        override suspend fun choose(question: String, options: List<String>, recommended: String?): String? =
            askUser(phone, question, yesNo = false, options = options, recommended = recommended)?.let(::whereAnswered)
    }

    private suspend fun askUser(
        phone: PhoneBackend,
        text: String,
        yesNo: Boolean,
        options: List<String> = emptyList(),
        recommended: String? = null,
        allowAlways: Boolean = false,
        yourTurn: Boolean = false,
    ): String? {
        val pending = PendingQuestion(text, yesNo, options, recommended, allowAlways = allowAlways, yourTurn = yourTurn)
        AppLog.log("agent", (if (yesNo) "confirm: " else "ask: ") + text)
        mutableState.update { it.copy(question = pending, status = "Waiting for you") }
        state.value.taskId?.let { tasks.setStatus(it, TaskStatus.WaitingForUser) }
        // Answerable from the notification and the Live Activity: the app being used stays in front.
        // Without notifications Mode opens to ask (and the agent reopens the app after).
        val kind = when {
            yourTurn -> QuestionKind.YourTurn
            yesNo && allowAlways -> QuestionKind.YesNoAlways
            yesNo -> QuestionKind.YesNo
            options.size >= 2 -> QuestionKind.Choice
            else -> QuestionKind.Text
        }
        notifier.ask(pending.id, text, kind, options)
        // A PIN or password screen must stay in front: Mode never comes forward for the user's turn.
        val parent = telemetry.currentContext()
        val voice = if (yourTurn || voices == null || !spoken) null else scope.launch {
            telemetry.trace(Operation.VoiceTurn, parent = parent) { hearQuestion(phone, pending) }
        }.also { voiceJob = it }
        askedInMode = !yourTurn && (!notifier.canAskInPlace() || voice != null)
        if (askedInMode && voice == null) runCatching { phone.send(Op.LaunchApp(appInfo.bundleId)) }
        try {
            return pending.answer.await().also { AppLog.log("agent", "user answered: ${it ?: "(stopped)"}") }
        } finally {
            // Stopped (or the task ended) while asking: the voice turn ends too. Answered: it finishes on its own,
            // saying it heard the answer, and holds the phone until then.
            if (!pending.answer.isCompleted || pending.answer.getCompletedOrNull() == null) voice?.cancel()
            // Also when the agent moved on by itself (the PIN screen went away): the question is gone.
            notifier.withdraw(pending.id)
            mutableState.update { state -> if (state.question?.id == pending.id) state.copy(question = null) else state }
            state.value.taskId?.let { id -> tasks.setStatus(id, TaskStatus.Running) }
        }
    }

    /**
     * Speaks [pending], then listens, on the session the agent already holds.
     * Agent ops wait. A notification answer for the same id wins once and this
     * does not record. The stream is restored even if this is cancelled.
     */
    private suspend fun hearQuestion(phone: PhoneBackend, pending: PendingQuestion) {
        val task = state.value.taskId?.let { tasks.get(it)?.text }.orEmpty()
        fun turn(change: (VoiceTurn) -> VoiceTurn) = mutableState.update { s -> s.voice?.let { s.copy(voice = change(it)) } ?: s }
        session.duringVoice { live ->
            val hands = live ?: return@duringVoice
            mutableState.update { it.copy(voice = VoiceTurn(VoiceTurnPhase.Asking, pending.text, pending.options, task)) }
            runCatching { hands.send(Op.LaunchApp(appInfo.bundleId)) }
            hands.pauseStream()
            // A tap on an option, or an answer from the notification, closes the microphone at once.
            val stopListening = pending.answer.invokeOnCompletion { voices?.stop() }
            try {
                voices?.speak(pending.text)
                if (!pending.answer.isCompleted) {
                    turn { it.copy(phase = VoiceTurnPhase.Listening) }
                    val heard = voices?.hear { words -> turn { it.copy(heard = words) } }
                    if (heard is Heard.Words) answer(pending.id, heard.text)
                }
                val given = pending.answer.getCompletedOrNull()
                if (given != null) {
                    turn { it.copy(phase = VoiceTurnPhase.Answered, heard = given, reply = GOT_IT) }
                    voices?.speak(GOT_IT)
                    delay(ANSWERED_PAUSE_MS)
                }
            } finally {
                stopListening.dispose()
                mutableState.update { it.copy(voice = null) }
                withContext(NonCancellable) { runCatching { hands.resumeStream() } }
            }
        }
    }

    /**
     * A message for the running task from its conversation ("It's not there, search for Dark Mode"): the agent takes
     * it at its next step and does it that way. False when no task runs.
     */
    public fun steer(text: String): Boolean {
        val said = text.trim()
        if (said.isEmpty() || !state.value.running) return false
        steers.update { it + said }
        AppLog.log("agent", "steered while working")
        return true
    }

    /** Later: the spoken question stops listening and waits on the notification and Live Activity instead. */
    public fun voiceLater() {
        voiceJob?.cancel()
        voices?.stop()
        mutableState.update { it.copy(voice = null) }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun <T> kotlinx.coroutines.CompletableDeferred<T>.getCompletedOrNull(): T? = if (isCompleted) runCatching { getCompleted() }.getOrNull() else null

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

/** What Mode says when it heard a spoken answer, before it goes back to the app. */
/** How often a spoken task checks whether an update is worth saying. */
private const val NARRATE_TICK_MS = 1_000L
private const val GOT_IT = "Got it. Going back."

/** How long "Got it" stays on screen after it is said, before Mode leaves. */
private const val ANSWERED_PAUSE_MS = 900L