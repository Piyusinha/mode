package com.piyush.mode.feature.runs.data

import com.piyush.mode.core.database.ModeDatabase
import com.piyush.mode.core.storage.FileStore
import com.piyush.mode.feature.runs.domain.RunSummary
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlin.time.ExperimentalTime
import kotlin.time.Instant
import com.piyush.mode.core.database.Run as RunRow

/**
 * Recorded runs: listed from the database, while each run's events and
 * screenshots stay as files under runs/<id>/ (as [RunRecorder] writes them).
 */
public class RunRepository(private val store: FileStore, private val db: ModeDatabase) {

    /** Runs, newest first. */
    public fun runs(limit: Long = 200): List<RunSummary> = db.runQueries.recent(limit).executeAsList().map { it.toSummary() }

    public fun run(id: String): RunSummary? = db.runQueries.byId(id).executeAsOneOrNull()?.toSummary()

    /**
     * The events of one run, in order: start, then look / ask / answer / act
     * per step. Kept as JSON: the fields differ by event.
     */
    public fun events(id: String): List<JsonObject> =
        store.read("$DIR/$id/steps.jsonl")?.decodeToString()?.lines()?.filter { it.isNotBlank() }?.mapNotNull(::parse).orEmpty()

    /** A screenshot a "look" event names (its "image" field). */
    public fun image(id: String, name: String): ByteArray? = store.read("$DIR/$id/$name")

    /** Lists a run from its run.json; called by [RunRecorder] as the run starts and ends. */
    public fun record(run: JsonObject, taskId: String?, startedAt: Long) {
        val summary = run.toSummary(taskId, startedAt) ?: return
        db.runQueries.upsert(
            summary.id, summary.taskId, summary.task, summary.outcome, summary.summary, summary.model,
            summary.steps?.toLong(), summary.millis, summary.costUsd, summary.tag, summary.startedAt,
        )
    }

    /**
     * Lists runs recorded before the database existed (or while it wasn't
     * listing them). Safe to run every launch; returns how many it added.
     */
    public fun backfill(): Int {
        var added = 0
        for (id in store.list(DIR)) {
            if (db.runQueries.byId(id).executeAsOneOrNull() != null) continue
            val run = store.read("$DIR/$id/run.json")?.decodeToString()?.let(::parse) ?: continue
            record(run, taskId = null, startedAt = run.startedAt())
            added++
        }
        return added
    }

    /** Keeps the newest [keep] runs, with their files. */
    public fun prune(keep: Int = 60) {
        db.runQueries.idsBeyond(keep.toLong()).executeAsList().forEach {
            db.runQueries.deleteById(it)
            store.delete("$DIR/$it")
        }
        // Folders the database never listed (or lost) are pruned by name.
        store.list(DIR).sortedDescending().drop(keep).forEach { store.delete("$DIR/$it") }
    }

    /** Forgets every run and its files. */
    public fun deleteAll() {
        db.runQueries.deleteAll()
        store.delete(DIR)
    }

    private fun parse(text: String): JsonObject? = runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    private fun JsonObject.toSummary(taskId: String?, startedAt: Long): RunSummary? {
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
            taskId = taskId,
            startedAt = startedAt,
        )
    }

    @OptIn(ExperimentalTime::class)
    private fun JsonObject.startedAt(): Long =
        string("started")?.let { runCatching { Instant.parse(it).toEpochMilliseconds() }.getOrNull() } ?: 0

    private fun RunRow.toSummary() = RunSummary(
        id = id,
        task = task,
        outcome = outcome,
        summary = summary,
        model = model,
        steps = steps?.toInt(),
        millis = ms,
        costUsd = cost_usd,
        tag = tag,
        taskId = task_id,
        startedAt = started_at,
    )

    private fun JsonObject.primitive(name: String) = this[name] as? JsonPrimitive
    private fun JsonObject.string(name: String) = primitive(name)?.takeIf { it.isString }?.content

    private companion object {
        const val DIR = "runs"
        val json = Json { ignoreUnknownKeys = true }
    }
}
