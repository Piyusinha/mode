package com.piyush.mode.core.telemetry

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** A finished span kept in memory: its trace, parent, name, timing, the allowed fields, events, and an error type. */
data class SpanRecord(
    val traceId: String,
    val spanId: String,
    val parentId: String?,
    val name: String,
    val startMs: Long,
    val endMs: Long,
    val fields: Map<String, String>,
    val events: List<EventRecord>,
    val error: String?,
) {
    val durationMs: Long get() = endMs - startMs
}

/** An event, on a span or on its own (with the trace it belongs to, when it has one). */
data class EventRecord(val traceId: String?, val name: String, val atMs: Long, val fields: Map<String, String>)

/**
 * The newest spans and events, in memory, up to a limit: a second listener beside the files, so a task can be
 * debugged from its trace at once. Only allow-listed fields ([Field]) are ever kept, as in the files.
 */
class RecentTelemetry(private val maxSpans: Int = 4_000, private val maxEvents: Int = 2_000) {
    private val spans = MutableStateFlow<List<SpanRecord>>(emptyList())
    private val events = MutableStateFlow<List<EventRecord>>(emptyList())

    fun add(span: SpanRecord) = spans.update { (it + span).takeLast(maxSpans) }
    fun add(event: EventRecord) = events.update { (it + event).takeLast(maxEvents) }

    /** Every span kept, oldest first. */
    fun spans(): List<SpanRecord> = spans.value
    fun events(): List<EventRecord> = events.value

    /** The spans and events of one trace (a task, a voice session), oldest first. */
    fun trace(traceId: String): Pair<List<SpanRecord>, List<EventRecord>> =
        spans.value.filter { it.traceId == traceId } to events.value.filter { it.traceId == traceId }

    /** The trace of the task with [taskId] (its mode.task span), if still in memory. */
    fun traceOfTask(taskId: String): String? =
        spans.value.lastOrNull { it.name == Operation.Task.wireName && it.fields[Field.TaskId.wireName] == taskId }?.traceId
}
