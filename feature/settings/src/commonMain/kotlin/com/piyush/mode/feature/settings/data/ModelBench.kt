package com.piyush.mode.feature.settings.data

import com.piyush.mode.agent.Brain
import com.piyush.mode.core.storage.KeyValueStore
import com.piyush.mode.llm.ChatMessage
import com.piyush.mode.llm.LlmClient
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.TimeSource

/**
 * "Measure on this iPhone": the same short test for each model and effort, timed and scored here, so speed
 * and accuracy are this phone's numbers, not a vendor's. Two tests, one per role:
 * - phone control: each probe is a screenshot-like image showing a number; the model must read it and answer
 *   with a tool call, as it does on every step of a task;
 * - the brain: [BrainProbe]s, moments where Mode must plan or decide, run through the brain's own code.
 */
public class ModelBench @OptIn(ExperimentalTime::class) constructor(
    private val prefs: KeyValueStore,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    @Serializable
    public data class Result(
        /** Average time of one answer. */
        val millis: Long,
        val correct: Int,
        val total: Int,
        val measuredAt: Long,
    )

    /** What was measured for [provider] in one role's test, by model id (with its effort). */
    public fun results(provider: String, brain: Boolean = false): Map<String, Result> {
        val prefix = prefix(provider, brain)
        return all().filterKeys { it.startsWith(prefix) && (brain || !it.startsWith(prefix(provider, true))) }
            .mapKeys { it.key.removePrefix(prefix) }
    }

    /** Runs the brain's test against [model] (id with its effort) and keeps the result. */
    public suspend fun measureBrain(provider: String, client: LlmClient, model: String, probes: List<BrainProbe> = BrainProbe.ALL): Result {
        val brain = Brain(client, model)
        var correct = 0
        var totalMs = 0L
        for (probe in probes) {
            val started = timeSource.markNow()
            // The brain's own code turns a failed call into no answer: it counts as wrong, as on a task.
            if (probe.passes(brain)) correct++
            totalMs += started.elapsedNow().inWholeMilliseconds
        }
        return keep(prefix(provider, true) + model, correct, probes.size, totalMs)
    }

    /** Runs the probes against [model] (id with its effort) and keeps the result. */
    public suspend fun measure(provider: String, client: LlmClient, model: String, probes: List<Probe>): Result {
        var correct = 0
        var totalMs = 0L
        for (probe in probes) {
            val started = timeSource.markNow()
            val ok = try {
                val reply = client.chat(model, listOf(ChatMessage("system", PROMPT), ChatMessage("user", ASK, probe.image)), TOOLS)
                reply.toolCall?.takeIf { it.name == "read_number" }?.arguments?.get("number")?.jsonPrimitive?.contentOrNull?.trim() == probe.answer
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                false
            }
            totalMs += started.elapsedNow().inWholeMilliseconds
            if (ok) correct++
        }
        return keep(prefix(provider, false) + model, correct, probes.size, totalMs)
    }

    private fun all(): Map<String, Result> =
        prefs.getString(KEY)?.let { runCatching { Json.decodeFromString<Map<String, Result>>(it) }.getOrNull() }.orEmpty()

    private fun keep(key: String, correct: Int, total: Int, totalMs: Long): Result {
        val result = Result(if (total == 0) 0 else totalMs / total, correct, total, now())
        prefs.putString(KEY, Json.encodeToString(all() + (key to result)))
        return result
    }

    /** Phone control's results keep the first format ("chatgpt|gpt-6.1-terra@low"); the brain's add "brain|". */
    private fun prefix(provider: String, brain: Boolean) = if (brain) "$provider|brain|" else "$provider|"

    /** An image showing [answer], made by the app (no screen or personal data). */
    public class Probe(public val image: ByteArray, public val answer: String)

    private companion object {
        const val KEY = "model_bench"
        const val PROMPT = "This is a quick test of reading a phone screen. Read the number shown in the image and call read_number once with it. Do not answer in prose."
        const val ASK = "What number does this screen show?"
        val TOOLS = Json.parseToJsonElement(
            """[{"type":"function","function":{"name":"read_number","description":"Report the number on the screen","parameters":{"type":"object","properties":{"number":{"type":"string"}},"required":["number"]}}}]""",
        ).jsonArray
    }
}
