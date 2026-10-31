package com.piyush.mode.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.piyush.mode.agent.Agent
import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.agent.AgentResult
import com.piyush.mode.agent.OpenRouterClient
import com.piyush.mode.agent.StepRecord
import com.piyush.mode.agent.UserGate
import com.piyush.mode.ops.Op
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** A question from the agent that the screen shows until the user answers. */
class PendingQuestion(val text: String, val yesNo: Boolean, val answer: CompletableDeferred<String?>)

/** Runs agent tasks for the UI: live steps, questions, notifications. */
class AgentRunner(
    private val link: PhoneLink,
    private val services: AppServices,
    private val holder: PhoneHolder,
    val settings: AgentSettings,
) {
    var running by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var question by mutableStateOf<PendingQuestion?>(null)
        private set
    var lastResult by mutableStateOf<AgentResult?>(null)
        private set
    val steps = mutableStateListOf<StepRecord>()
    val log = mutableStateListOf<String>()

    private var job: Job? = null
    private val llm by lazy { OpenRouterClient(services.http, apiKey = { settings.apiKey }) }

    fun start(scope: CoroutineScope, task: String, config: AgentConfig = settings.config(), onFinished: (AgentResult?) -> Unit = {}) {
        if (running || task.isBlank()) return
        job = scope.launch {
            val result = runTask(task, config)
            onFinished(result)
        }
    }

    fun stop() {
        job?.cancel()
        question?.answer?.complete(null)
    }

    fun answer(text: String?) {
        question?.answer?.complete(text)
        question = null
    }

    /** Runs one task to the end; null if it could not start. */
    suspend fun runTask(task: String, config: AgentConfig): AgentResult? {
        running = true
        steps.clear()
        lastResult = null
        services.requestNotifications()
        try {
            if (settings.apiKey.isNullOrBlank()) {
                status = "Add your OpenRouter API key in Settings first."
                return null
            }
            status = "Connecting to the phone…"
            val phone = holder.phone()
            val agent = Agent(
                phone = phone,
                llm = llm,
                gate = gate(phone),
                config = config,
                onStep = { steps.add(it) },
                onStatus = { status = it },
            )
            val result = link.inBackground { agent.run(task) }
            lastResult = result
            status = describe(result)
            services.notify("Mode", describe(result))
            // Come back so the user sees how it went.
            runCatching { phone.send(Op.LaunchApp(link.ownBundleId)) }
            return result
        } catch (e: kotlinx.coroutines.CancellationException) {
            status = "Stopped."
            throw e
        } catch (e: Exception) {
            status = "Failed: ${e.message}"
            services.notify("Mode", "Task failed: ${e.message}")
            log += "✗ ${e.message}"
            return null
        } finally {
            running = false
            question = null
        }
    }

    /**
     * Questions bring Mode to the front (and notify) so the user can answer;
     * the model is then told Mode is in front so it reopens its app.
     */
    private fun gate(phone: com.piyush.mode.ops.PhoneBackend) = object : UserGate {
        override suspend fun confirm(question: String): Boolean = askUser(phone, question, yesNo = true) == "yes"

        override suspend fun ask(question: String): String? =
            askUser(phone, question, yesNo = false)?.let { "$it (The user answered in the Mode app, so Mode is now on screen: reopen the app you were using.)" }
    }

    private suspend fun askUser(phone: com.piyush.mode.ops.PhoneBackend, text: String, yesNo: Boolean): String? {
        val pending = PendingQuestion(text, yesNo, CompletableDeferred())
        question = pending
        status = "Waiting for you"
        services.notify("Mode needs you", text)
        runCatching { phone.send(Op.LaunchApp(link.ownBundleId)) }
        return pending.answer.await().also { question = null }
    }

    companion object {
        fun describe(result: AgentResult): String {
            val cost = "$" + ((result.costUsd * 10000).roundToInt() / 10000.0)
            val time = "${result.millis / 1000}s"
            return when (val o = result.outcome) {
                is AgentOutcome.Done -> (if (o.success) "Done: " else "Could not finish: ") + o.summary + " ($time, ${result.steps} steps, $cost)"
                is AgentOutcome.Stopped -> "Stopped: ${o.why} ($time, ${result.steps} steps, $cost)"
                is AgentOutcome.Failed -> "Failed: ${o.why}"
            }
        }
    }
}

/** One row of a model comparison. */
class CompareRow(val model: String, val task: String, val outcome: String, val steps: Int, val seconds: Long, val costUsd: Double)

/**
 * Runs each task the user wrote with each model, from the Home Screen, and
 * tabulates success, steps, time and cost. The tasks come from the user.
 */
class CompareRunner(private val runner: AgentRunner, private val holder: PhoneHolder) {
    val rows = mutableStateListOf<CompareRow>()
    var running by mutableStateOf(false)
        private set

    suspend fun run(tasks: List<String>, models: List<String>) {
        if (running) return
        running = true
        rows.clear()
        try {
            for (model in models) for (task in tasks) {
                runCatching { holder.phone().send(Op.Home) }
                val base = runner.settings.config()
                val result = runner.runTask(task, AgentConfig(model = model, strongModel = null, maxSteps = base.maxSteps))
                rows += if (result == null) {
                    CompareRow(model, task, "did not run: ${runner.status}", 0, 0, 0.0)
                } else {
                    val outcome = when (val o = result.outcome) {
                        is AgentOutcome.Done -> if (o.success) "✅ ${o.summary}" else "❌ ${o.summary}"
                        is AgentOutcome.Stopped -> "⏹ ${o.why}"
                        is AgentOutcome.Failed -> "❌ ${o.why}"
                    }
                    CompareRow(model, task, outcome, result.steps, result.millis / 1000, result.costUsd)
                }
            }
        } finally {
            running = false
        }
    }
}
