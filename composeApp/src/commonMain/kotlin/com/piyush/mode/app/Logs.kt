package com.piyush.mode.app

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.agent.AgentResult
import com.piyush.mode.agent.AgentTrace
import com.piyush.mode.llm.Usage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Files under Mode's Documents/logs (visible in the Files app and Finder, and
 * pulled to a Mac with `devicectl device copy from`). Paths are relative.
 */
interface LogStore {
    fun append(path: String, text: String)
    fun write(path: String, bytes: ByteArray)
    fun read(path: String): ByteArray?
    /** Names directly inside [dir], or empty. */
    fun list(dir: String): List<String>
    fun delete(path: String)
}

@OptIn(ExperimentalTime::class)
internal fun nowIso(): String = Clock.System.now().toString()

/** A sortable, file-name-safe timestamp, like 2026-10-31T23-40-12. */
@OptIn(ExperimentalTime::class)
internal fun nowId(): String = Clock.System.now().toString().substring(0, 19).replace(':', '-')

/**
 * The app-wide event timeline: connecting, the VPN, disk-image mounts,
 * reconnects, keep-awake, questions, errors. Goes to logs/events.log and to
 * the system log (so a Mac can watch it live).
 */
object AppLog {
    private var sink: ((String) -> Unit)? = null

    fun install(sink: (String) -> Unit) {
        this.sink = sink
    }

    fun log(tag: String, message: String) {
        sink?.invoke("${nowIso()} [$tag] $message")
    }

    fun error(tag: String, message: String, e: Throwable? = null) =
        log(tag, "ERROR $message" + (e?.let { ": ${it.message ?: it}" } ?: ""))
}

/**
 * Writes one agent run to logs/runs/<id>/: run.json (task, settings,
 * outcome, totals), steps.jsonl (one line per event: look, ask, answer, act)
 * and step-N.jpg (what the model saw).
 */
class RunRecorder(private val store: LogStore, val id: String = nowId(), private val tag: String? = null) : AgentTrace {
    val dir = "runs/$id"
    private var task = ""
    private var config: AgentConfig? = null
    private val started = nowIso()

    override fun runStarted(task: String, config: AgentConfig, tools: List<String>) {
        this.task = task
        this.config = config
        writeRun(null)
        event("start", JsonObject(mapOf("tools" to buildJsonArray { tools.forEach { add(JsonPrimitive(it)) } })))
        AppLog.log("agent", "run $id started: \"$task\" with ${config.model}")
    }

    override fun looked(step: Int, jpeg: ByteArray, imageWidth: Int, imageHeight: Int, texts: List<Triple<String, Int, Int>>, millis: Long) {
        store.write("$dir/step-$step.jpg", jpeg)
        event("look", buildJsonObject {
            put("step", step)
            put("ms", millis)
            put("image", "step-$step.jpg")
            put("w", imageWidth)
            put("h", imageHeight)
            put("texts", texts.size)
        })
    }

    override fun asking(step: Int, model: String, steppedUp: Boolean, prompt: String) {
        event("ask", buildJsonObject {
            put("step", step)
            put("model", model)
            put("stepped_up", steppedUp)
            put("prompt", prompt)
        })
    }

    override fun answered(step: Int, model: String, toolName: String?, arguments: JsonObject?, text: String, usage: Usage?, millis: Long, error: String?) {
        event("answer", buildJsonObject {
            put("step", step)
            put("model", model)
            put("ms", millis)
            put("tool", toolName?.let(::JsonPrimitive) ?: JsonNull)
            put("arguments", arguments ?: JsonNull)
            if (text.isNotBlank()) put("text", text)
            usage?.let {
                put("prompt_tokens", it.promptTokens)
                put("completion_tokens", it.completionTokens)
                put("cost_usd", it.costUsd)
            }
            error?.let { put("error", it) }
        })
        if (error != null) AppLog.error("agent", "model $model failed at step $step: $error")
    }

    override fun acted(step: Int, action: String, op: String?, outcome: String, millis: Long) {
        event("act", buildJsonObject {
            put("step", step)
            put("ms", millis)
            put("action", action)
            op?.let { put("op", it) }
            put("outcome", outcome)
        })
        if (outcome.startsWith("failed")) AppLog.error("agent", "step $step $action: $outcome")
    }

    override fun runEnded(result: AgentResult) {
        writeRun(result)
        AppLog.log("agent", "run $id ended: ${AgentRunner.describe(result)}")
    }

    /** For runs that never got going (no key, no phone). */
    fun failedToStart(task: String, why: String) {
        this.task = task
        writeRun(null, why)
        AppLog.error("agent", "run $id could not start: $why")
    }

    private fun event(kind: String, body: JsonObject) {
        val line = JsonObject(mapOf("t" to JsonPrimitive(nowIso()), "event" to JsonPrimitive(kind)) + body)
        store.append("$dir/steps.jsonl", line.toString() + "\n")
    }

    private fun writeRun(result: AgentResult?, startError: String? = null) {
        val run = buildJsonObject {
            put("id", id)
            put("task", task)
            put("started", started)
            tag?.let { put("tag", it) }
            config?.let {
                put("model", it.model)
                put("strong_model", it.strongModel?.let(::JsonPrimitive) ?: JsonNull)
                put("max_steps", it.maxSteps)
            }
            if (result != null) {
                put("ended", nowIso())
                put("steps", result.steps)
                put("ms", result.millis)
                put("cost_usd", result.costUsd)
                when (val o = result.outcome) {
                    is AgentOutcome.Done -> { put("outcome", if (o.success) "done" else "gave_up"); put("summary", o.summary) }
                    is AgentOutcome.Stopped -> { put("outcome", "stopped"); put("summary", o.why) }
                    is AgentOutcome.Failed -> { put("outcome", "failed"); put("summary", o.why) }
                }
            } else if (startError != null) {
                put("outcome", "not_started")
                put("summary", startError)
            } else {
                put("outcome", "running")
            }
        }
        store.write("$dir/run.json", run.toString().encodeToByteArray())
    }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /** Run summaries, newest first. */
        fun runs(store: LogStore): List<JsonObject> = store.list("runs").sortedDescending().mapNotNull { id ->
            store.read("runs/$id/run.json")?.decodeToString()?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        }

        /** The events of one run, in order. */
        fun events(store: LogStore, id: String): List<JsonObject> =
            store.read("runs/$id/steps.jsonl")?.decodeToString()?.lines()?.filter { it.isNotBlank() }
                ?.mapNotNull { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }.orEmpty()

        /** Keeps the newest [keep] runs. */
        fun prune(store: LogStore, keep: Int = 60) {
            store.list("runs").sortedDescending().drop(keep).forEach { store.delete("runs/$it") }
        }
    }
}

internal fun JsonElement?.text(): String = (this as? JsonPrimitive)?.content.orEmpty()
