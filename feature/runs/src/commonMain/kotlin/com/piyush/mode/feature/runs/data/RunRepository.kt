package com.piyush.mode.feature.runs.data

import com.piyush.mode.core.storage.FileStore
import com.piyush.mode.feature.runs.domain.RunSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

/** Recorded runs under runs/ in the [FileStore], as [RunRecorder] writes them. */
public class RunRepository(private val store: FileStore) {

    /** Every run, newest first. */
    public fun runs(): List<RunSummary> = store.list(DIR).sortedDescending().mapNotNull { id ->
        store.read("$DIR/$id/run.json")?.decodeToString()?.let(::parse)?.toSummary()
    }

    public fun run(id: String): RunSummary? = store.read("$DIR/$id/run.json")?.decodeToString()?.let(::parse)?.toSummary()

    /**
     * The events of one run, in order: start, then look / ask / answer / act
     * per step. Kept as JSON: the fields differ by event.
     */
    public fun events(id: String): List<JsonObject> =
        store.read("$DIR/$id/steps.jsonl")?.decodeToString()?.lines()?.filter { it.isNotBlank() }?.mapNotNull(::parse).orEmpty()

    /** A screenshot a "look" event names (its "image" field). */
    public fun image(id: String, name: String): ByteArray? = store.read("$DIR/$id/$name")

    /** Keeps the newest [keep] runs. */
    public fun prune(keep: Int = 60) {
        store.list(DIR).sortedDescending().drop(keep).forEach { store.delete("$DIR/$it") }
    }

    private fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    private fun JsonObject.toSummary(): RunSummary? {
        val id = string("id") ?: return null
        return RunSummary(
            id = id,
            task = string("task").orEmpty(),
            outcome = string("outcome").orEmpty(),
            summary = string("summary").orEmpty(),
            model = string("model"),
            steps = primitive("steps")?.intOrNull,
            millis = primitive("ms")?.longOrNull,
            costUsd = primitive("cost_usd")?.doubleOrNull,
            tag = string("tag"),
        )
    }

    private fun JsonObject.primitive(name: String) = this[name] as? JsonPrimitive
    private fun JsonObject.string(name: String) = primitive(name)?.takeIf { it.isString }?.content

    private companion object {
        const val DIR = "runs"
        val json = Json { ignoreUnknownKeys = true }
    }
}
