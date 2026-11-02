package com.piyush.mode.feature.settings.data

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
 * and accuracy are this phone's numbers, not a vendor's. Each probe is a screenshot-like image showing a
 * number; the model must read it and answer with a tool call, as it does on every step of a task.
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

    /** What was measured for [provider], by model id (with its effort). */
    public fun results(provider: String): Map<String, Result> =
        prefs.getString(KEY)?.let { runCatching { Json.decodeFromString<Map<String, Result>>(it) }.getOrNull() }.orEmpty()
            .filterKeys { it.startsWith("$provider|") }.mapKeys { it.key.substringAfter('|') }

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
        val result = Result(if (probes.isEmpty()) 0 else totalMs / probes.size, correct, probes.size, now())
        val all = prefs.getString(KEY)?.let { runCatching { Json.decodeFromString<Map<String, Result>>(it) }.getOrNull() }.orEmpty()
        prefs.putString(KEY, Json.encodeToString(all + ("$provider|$model" to result)))
        return result
    }

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
