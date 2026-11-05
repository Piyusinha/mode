package com.piyush.mode.app.screens.debug

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.telemetry.SpanRecord
import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.feature.connection.domain.PhoneChecks
import com.piyush.mode.feature.connection.presentation.PhoneSession
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.feature.settings.data.ClaudeAccount
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.domain.TaskInbox
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** A question about the task and the AI's answer (null while it is written; [failed] when it couldn't be). */
public data class DebugTurn(val question: String, val answer: String?, val failed: Boolean = false)

public data class TaskDebugUiState(
    val loaded: Boolean = false,
    val title: String = "",
    val startedAtMs: Long? = null,
    val findings: List<Finding> = emptyList(),
    /** What Repair did ("Reconnected phone control"). */
    val repaired: List<String> = emptyList(),
    val canRepair: Boolean = false,
    val repairing: Boolean = false,
    val turns: List<DebugTurn> = emptyList(),
    /** The AI that answers questions ("ChatGPT"). */
    val provider: String = "",
)

/**
 * What went wrong (board TaskDebug) and Ask about this task (board TaskDebugAsk). The check reads the task's step
 * log and its trace (kept in memory) on this iPhone, by rules; questions go to the user's own AI with the
 * task's steps, never its screenshots or keys.
 */
public class TaskDebugPresenter(
    parent: CoroutineScope,
    private val taskId: String,
    private val tasks: TaskRepository,
    private val runs: RunRepository,
    private val telemetry: Telemetry,
    private val settings: SettingsRepository,
    private val session: PhoneSession,
    private val phoneChecks: PhoneChecks,
    private val inbox: TaskInbox,
    private val llm: LlmClient?,
    private val chatGpt: ChatGptAccount,
    private val claude: ClaudeAccount?,
) : Presenter<TaskDebugUiState>(parent, TaskDebugUiState()) {
    private var steps: List<JsonObject> = emptyList()
    private var spans: List<SpanRecord> = emptyList()
    private var outcome: String? = null
    private var summary: String? = null

    init {
        load()
    }

    private fun load() {
        val task = tasks.get(taskId)
        val run = task?.runId?.let(runs::run)
        steps = task?.runId?.let { runCatching { runs.events(it) }.getOrDefault(emptyList()) }.orEmpty()
        spans = telemetry.recent?.let { r -> r.traceOfTask(taskId)?.let { r.trace(it).first } }.orEmpty()
        outcome = run?.outcome
        summary = run?.summary ?: task?.summary
        val findings = TaskDiagnosis.of(outcome, summary, steps, spans) + live()
        mutableState.value = mutableState.value.copy(
            loaded = true, title = task?.text.orEmpty(), startedAtMs = task?.startedAt ?: task?.createdAt,
            findings = findings, canRepair = findings.any { it.problem && it.kind in REPAIRABLE }, provider = providerName(),
        )
    }

    /** How things are now: phone control and the AI, so a repaired part shows as working. */
    private fun live(): List<Finding> = listOf(
        if (phoneChecks.hasPairingFile()) Finding(FindingKind.PhoneOk, "Phone control", "Paired with this iPhone", problem = false, ok = true)
        else Finding(FindingKind.PhoneUnreachable, "Phone control", "Not paired: pair once from your Mac with Mode Setup", problem = true),
        if (settings.hasAccess(settings.settings.value.provider)) Finding(FindingKind.AiOk, providerName(), "Signed in", problem = false, ok = true)
        else Finding(FindingKind.AiFailed, providerName(), "Not connected: connect it in Settings", problem = true),
    )

    /** Repairs what Mode can on this iPhone: reconnects phone control, refreshes the AI sign-in. */
    public fun repair() {
        if (mutableState.value.repairing) return
        scope.launch {
            mutableState.value = mutableState.value.copy(repairing = true)
            val kinds = mutableState.value.findings.filter { it.problem }.map { it.kind }.toSet()
            val done = mutableListOf<String>()
            if (kinds.any { it in PHONE }) {
                // Proves the connection works, then closes it: an open session keeps iOS's indicator and the screen on.
                runCatching { try { session.reconnect() } finally { session.disconnect() } }
                    .onSuccess { done += "Reconnected phone control" }
                    .onFailure { if (it is CancellationException) throw it }
            }
            if (kinds.any { it in AI }) {
                val refreshed = runCatching {
                    when (settings.settings.value.provider) {
                        ModelProvider.ChatGpt -> chatGpt.accessToken() != null
                        ModelProvider.ClaudeOAuth -> claude?.accessToken(forceRefresh = true) != null
                        ModelProvider.OpenRouter -> false
                    }
                }.onFailure { if (it is CancellationException) throw it }.getOrDefault(false)
                if (refreshed) done += "Refreshed your ${providerName()} sign-in"
            }
            AppLog.log("debug", "repair for task $taskId: ${done.ifEmpty { listOf("nothing to repair here") }}")
            mutableState.value = mutableState.value.copy(repairing = false, repaired = mutableState.value.repaired + done)
        }
    }

    /** Try again: the same task, in the same chat. */
    public fun retry(): Boolean {
        val task = tasks.get(taskId) ?: return false
        return inbox.submit(task.text, TaskSource.App, task.conversationId, false) != null
    }

    /** Asks the user's own AI about this task, with its steps, how it ended and what the check found. */
    public fun ask(question: String) {
        val q = question.trim()
        if (q.isEmpty() || mutableState.value.turns.lastOrNull()?.answer == null && mutableState.value.turns.isNotEmpty()) return
        mutableState.value = mutableState.value.copy(turns = mutableState.value.turns + DebugTurn(q, null))
        scope.launch {
            val answer = runCatching { answer(q) }.onFailure { if (it is CancellationException) throw it }.getOrNull()
            mutableState.value = mutableState.value.copy(turns = mutableState.value.turns.dropLast(1) +
                DebugTurn(q, answer ?: "I couldn't ask your AI just now. Check Health & repair, then try again.", failed = answer == null))
        }
    }

    private suspend fun answer(question: String): String? {
        val client = llm ?: return null
        val s = settings.settings.value
        val model = s.strongModel ?: s.model
        val context = buildString {
            append("THE TASK: ").append(mutableState.value.title).append('\n')
            append("HOW IT ENDED: ").append(outcome ?: "unknown").append(" — ").append(summary.orEmpty().take(600)).append('\n')
            append("WHAT MODE'S CHECK FOUND:\n")
            mutableState.value.findings.forEach { append("- ").append(it.title).append(": ").append(it.detail).append('\n') }
            append("STEPS (newest last):\n")
            steps.filter { it.str("event") == "act" }.takeLast(MAX_STEPS).forEach { e ->
                append(e.str("step")).append(". ").append(e.str("action")).append(" — ").append(e.str("outcome")?.take(160)).append('\n')
            }
            val slow = spans.filter { it.error != null || it.durationMs > SLOW_MS }.takeLast(MAX_SPANS)
            if (slow.isNotEmpty()) {
                append("TRACE (errors and slow parts):\n")
                slow.forEach { append("- ").append(it.name).append(" ").append(it.durationMs).append(" ms").append(it.error?.let { e -> " error $e" } ?: "").append('\n') }
            }
            mutableState.value.turns.dropLast(1).forEach { append("EARLIER Q: ").append(it.question).append("\nEARLIER A: ").append(it.answer).append('\n') }
            append("QUESTION: ").append(question)
        }
        val reply = withTimeoutOrNull(ANSWER_MS) {
            client.forProvider(s.provider).chat(model, listOf(ChatMessage("system", SYSTEM), ChatMessage("user", context)), TOOLS)
        } ?: return null
        val args = reply.toolCall?.takeIf { it.name == TOOL }?.arguments
        return ((args?.get("answer") as? JsonPrimitive)?.contentOrNull ?: reply.text).trim().ifEmpty { null }
    }

    private fun providerName(): String = when (settings.settings.value.provider) {
        ModelProvider.ChatGpt -> "ChatGPT"
        ModelProvider.ClaudeOAuth -> "Claude"
        ModelProvider.OpenRouter -> "OpenRouter"
    }

    private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull

    private companion object {
        val PHONE = setOf(FindingKind.PhoneUnreachable, FindingKind.Stuck, FindingKind.Interrupted)
        val AI = setOf(FindingKind.NoAction, FindingKind.AiFailed)
        val REPAIRABLE = PHONE + AI
        const val MAX_STEPS = 40
        const val MAX_SPANS = 20
        const val SLOW_MS = 8_000L
        const val ANSWER_MS = 60_000L
        const val TOOL = "answer"
        val SYSTEM = """
            You help the user understand why Mode, the assistant that does tasks on their iPhone, couldn't finish one,
            and what to do about it. You get the task, how it ended, what Mode's own check found, its steps and the
            slow or failed parts of its trace. Answer their question plainly, in a few sentences: what happened, then
            what to do (Try again, a setting to change, something to do in the app). No step numbers, models, tokens
            or other jargon. If the steps don't say, say so. Call answer.
        """.trimIndent()
        val TOOLS = buildJsonArray {
            add(buildJsonObject {
                put("type", "function")
                putJsonObject("function") {
                    put("name", TOOL)
                    put("description", "Your answer to the user's question.")
                    putJsonObject("parameters") {
                        put("type", "object")
                        putJsonObject("properties") { putJsonObject("answer") { put("type", "string") } }
                        putJsonArray("required") { add(JsonPrimitive("answer")) }
                    }
                }
            })
        }
    }
}
