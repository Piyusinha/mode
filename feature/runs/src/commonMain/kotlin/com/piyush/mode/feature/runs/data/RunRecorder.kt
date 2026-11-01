package com.piyush.mode.feature.runs.data

import com.piyush.mode.agent.AgentConfig
import com.piyush.mode.agent.AgentOutcome
import com.piyush.mode.agent.AgentResult
import com.piyush.mode.agent.AgentTrace
import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.common.nowId
import com.piyush.mode.core.common.nowIso
import com.piyush.mode.core.storage.FileStore
import com.piyush.mode.feature.runs.domain.describeResult
import com.piyush.mode.llm.Usage
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Writes one agent run to logs/runs/<id>/: run.json (task, settings,
 * outcome, totals), steps.jsonl (one line per event: look, ask, answer, act)
 * and step-N.jpg (what the model saw).
 */
public class RunRecorder @OptIn(ExperimentalTime::class) constructor(
    private val store: FileStore,
    public val id: String = nowId(),
    private val tag: String? = null,
    /** Where the run is listed; null records files only. */
    private val index: RunRepository? = null,
    private val taskId: String? = null,
    private val startedAt: Long = Clock.System.now().toEpochMilliseconds(),
) : AgentTrace {
    private val dir = "runs/$id"
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
                if (it.cachedTokens > 0) put("cached_tokens", it.cachedTokens)
                if (it.cacheWriteTokens > 0) put("cache_write_tokens", it.cacheWriteTokens)
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

    override fun noted(step: Int, text: String) {
        event("note", buildJsonObject { put("step", step); put("text", text) })
    }

    override fun verifyRejected(step: Int, why: String) {
        event("verify", buildJsonObject { put("step", step); put("why", why) })
    }

    override fun stuck(step: Int, why: String) {
        event("stuck", buildJsonObject { put("step", step); put("why", why) })
        AppLog.log("agent", "run $id stopped as stuck at step $step: $why")
    }

    override fun runEnded(result: AgentResult) {
        writeRun(result)
        AppLog.log("agent", "run $id ended: ${describeResult(result)}")
    }

    /** For runs that never got going (no key, no phone). */
    public fun failedToStart(task: String, why: String) {
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
        index?.record(run, taskId, startedAt)
    }
}
