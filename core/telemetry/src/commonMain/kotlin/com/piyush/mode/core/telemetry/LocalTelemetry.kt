@file:OptIn(io.opentelemetry.kotlin.ExperimentalApi::class)

package com.piyush.mode.core.telemetry

import com.piyush.mode.core.storage.FileStore
import io.opentelemetry.kotlin.Clock
import io.opentelemetry.kotlin.attributes.AttributesMutator
import io.opentelemetry.kotlin.context.Context
import io.opentelemetry.kotlin.createOpenTelemetry
import io.opentelemetry.kotlin.export.OperationResultCode
import io.opentelemetry.kotlin.logging.data.LogRecordData
import io.opentelemetry.kotlin.logging.export.LogRecordProcessor
import io.opentelemetry.kotlin.logging.export.toProtobufByteArray as logsToProtobuf
import io.opentelemetry.kotlin.logging.model.ReadWriteLogRecord
import io.opentelemetry.kotlin.propagation.TextMapGetter
import io.opentelemetry.kotlin.tracing.Span
import io.opentelemetry.kotlin.tracing.StatusData
import io.opentelemetry.kotlin.tracing.data.SpanData
import io.opentelemetry.kotlin.tracing.export.SpanProcessor
import io.opentelemetry.kotlin.tracing.export.toProtobufByteArray as tracesToProtobuf
import io.opentelemetry.kotlin.tracing.model.ReadWriteSpan
import io.opentelemetry.kotlin.tracing.model.ReadableSpan
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource
import kotlin.uuid.Uuid

data class TelemetryResource(
    val installationId: String,
    val appId: String,
    val version: String,
    val build: String,
    val platform: String,
)

data class TelemetryHealth(val droppedRecords: Long = 0, val failedBatches: Long = 0, val writtenRecords: Long = 0)

/**
 * Official KMP SDK + OTLP protobuf, with no network exporter. All persistence happens
 * on one bounded background worker. The same implementation runs on iOS/JVM/Android.
 */
class LocalTelemetry(
    files: FileStore,
    resource: TelemetryResource,
    dispatcher: CoroutineDispatcher = Dispatchers.Default,
    queueCapacity: Int = 512,
    retention: TelemetryRetention = TelemetryRetention(),
) : Telemetry {
    private val pipeline = LocalPipeline(files, dispatcher, queueCapacity, retention)
    val health = pipeline.health
    private val start = TimeSource.Monotonic.markNow()
    private val epochNanos = kotlin.time.Clock.System.now().toEpochMilliseconds() * 1_000_000
    private val otel = createOpenTelemetry(clock = Clock { epochNanos + start.elapsedNow().inWholeNanoseconds }) {
        serviceName = "mode"
        resource(mapOf(
            "service.instance.id" to resource.installationId,
            "service.version" to resource.version,
            "service.name" to "mode",
            "mode.app.id" to resource.appId,
            "mode.app.build" to resource.build,
            "mode.platform" to resource.platform,
            "mode.telemetry.schema_version" to "1",
        ))
        propagator { w3cTraceContext() }
        // Error text from SDK failures can contain attributes; expose counts instead.
        errorHandler { pipeline.sdkFailed() }
        attributeLimits { attributeCountLimit = 32; attributeValueLengthLimit = 160 }
        tracerProvider {
            spanLimits { eventCountLimit = 32; linkCountLimit = 8 }
            export { pipeline }
        }
        loggerProvider { export { pipeline } }
    }
    private val tracer = otel.tracerProvider.getTracer("app.mode.core", version = "1")
    private val logger = otel.loggerProvider.getLogger("app.mode.core", version = "1")

    override fun start(operation: Operation, parent: TraceContext?, fields: Fields): TraceSpan =
        runCatching {
            val span = tracer.startSpan(operation.wireName, parentContext = context(parent)) {
                applySafe(fields)
            }
            LocalSpan(span, otel.context.root().storeSpan(span))
        }.getOrDefault(TraceSpan.None)

    override fun event(event: Event, parent: TraceContext?, fields: Fields) {
        runCatching {
            logger.emit(eventName = event.wireName, context = context(parent)) { applySafe(fields) }
        }
    }

    /** Waits for records already queued. Intended for background/export, never task startup. */
    suspend fun flush(): Boolean = pipeline.forceFlush() == OperationResultCode.Success
    suspend fun shutdown(): Boolean = pipeline.shutdown() == OperationResultCode.Success

    private fun context(parent: TraceContext?): Context {
        (parent?.local as? Context)?.let { return it }
        val carrier = parent?.traceparent?.let { mapOf("traceparent" to it) }
        return otel.propagator.extract(otel.context.root(), carrier, Headers)
    }

    private inner class LocalSpan(private val span: Span, context: Context) : TraceSpan {
        override val context = TraceContext(
            "00-${span.spanContext.traceId}-${span.spanContext.spanId}-" +
                if (span.spanContext.traceFlags.isSampled) "01" else "00",
            context,
        )
        override fun fields(fields: Fields) { runCatching { span.applySafe(fields) } }
        override fun event(event: Event, fields: Fields) {
            runCatching { span.addEvent(event.wireName) { applySafe(fields) } }
            this@LocalTelemetry.event(event, context, fields)
        }
        override fun failed(type: String) {
            runCatching {
                span.setStatus(StatusData.Error(null))
                span.applySafe(mapOf(Field.ErrorType to type))
            }
        }
        override fun end() { runCatching { span.end() } }
    }
}

private fun AttributesMutator.applySafe(fields: Fields) {
    fields.forEach { (key, value) ->
        val safe = key.safe(value) ?: return@forEach
        when (key) {
            Field.InputTokens, Field.OutputTokens, Field.CachedTokens, Field.CacheWriteTokens, Field.DurationMs ->
                setLongAttribute(key.wireName, safe.toLong())
            Field.Cancelled, Field.BuiltInVpn -> setBooleanAttribute(key.wireName, safe.toBoolean())
            else -> setStringAttribute(key.wireName, safe)
        }
    }
}

private object Headers : TextMapGetter<Map<String, String>> {
    override fun keys(carrier: Map<String, String>) = carrier.keys
    override fun get(carrier: Map<String, String>?, key: String) = carrier?.entries?.firstOrNull { it.key.equals(key, true) }?.value
    override fun getAll(carrier: Map<String, String>?, key: String) = listOfNotNull(get(carrier, key))
}

/** Counts are independent of SDK logs, so a failing exporter cannot recurse into itself. */
private class LocalPipeline(
    files: FileStore,
    dispatcher: CoroutineDispatcher,
    capacity: Int,
    retention: TelemetryRetention,
) : SpanProcessor, LogRecordProcessor {
    private sealed interface Record {
        class Trace(val value: SpanData) : Record
        class Log(val value: LogRecordData) : Record
        class Flush(val done: CompletableDeferred<OperationResultCode>) : Record
    }
    private val status = MutableStateFlow(TelemetryHealth())
    val health = status.asStateFlow()
    private val queue = Channel<Record>(capacity.also { require(it > 0) })
    private val store = TelemetryArchiveStore(files, retention)
    private val worker = CoroutineScope(SupervisorJob() + dispatcher).launch {
        runCatching { store.prune() }.onFailure { sdkFailed() }
        for (record in queue) {
            if (record is Record.Flush) {
                complete(record)
                continue
            }
            val batch = mutableListOf(record)
            val since = TimeSource.Monotonic.markNow()
            var barrier: Record.Flush? = null
            while (batch.size < 64) {
                val next = withTimeoutOrNull(250.milliseconds - since.elapsedNow()) {
                    queue.receiveCatching().getOrNull()
                } ?: break
                if (next is Record.Flush) {
                    barrier = next
                    break
                }
                batch += next
            }
            val spans = batch.filterIsInstance<Record.Trace>().map { it.value }
            val logs = batch.filterIsInstance<Record.Log>().map { it.value }
            if (spans.isNotEmpty()) persist("traces", spans.size) { spans.tracesToProtobuf() }
            if (logs.isNotEmpty()) persist("logs", logs.size) { logs.logsToProtobuf() }
            saveHealth()
            barrier?.let { it.done.complete(result()) }
        }
        saveHealth()
    }

    private fun complete(barrier: Record.Flush) {
        saveHealth()
        barrier.done.complete(result())
    }

    private fun saveHealth() {
        runCatching { store.health(status.value) }.onFailure { sdkFailed() }
    }

    private fun persist(signal: String, count: Int, encode: () -> ByteArray) {
        runCatching {
            store.write(signal, encode())
            status.update { it.copy(writtenRecords = it.writtenRecords + count) }
        }.onFailure {
            status.update { it.copy(failedBatches = it.failedBatches + 1, droppedRecords = it.droppedRecords + count) }
        }
    }

    fun sdkFailed() { status.update { it.copy(failedBatches = it.failedBatches + 1) } }
    private fun offer(record: Record) {
        if (queue.trySend(record).isFailure) status.update { it.copy(droppedRecords = it.droppedRecords + 1) }
    }
    private fun result() = if (status.value.failedBatches == 0L) OperationResultCode.Success else OperationResultCode.Failure
    override fun onStart(span: ReadWriteSpan, parentContext: Context) {}
    override fun onEnding(span: ReadWriteSpan) {}
    override fun onEnd(span: ReadableSpan) { offer(Record.Trace(span.toSpanData())) }
    override fun onEmit(log: ReadWriteLogRecord, context: Context) { offer(Record.Log(log.toLogRecordData())) }
    override fun isStartRequired() = false
    override fun isEndRequired() = true
    override fun isOnEndingRequired() = false
    override suspend fun forceFlush(): OperationResultCode {
        val done = CompletableDeferred<OperationResultCode>()
        return try {
            queue.send(Record.Flush(done))
            done.await()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            OperationResultCode.Failure
        }
    }
    override suspend fun shutdown(): OperationResultCode {
        queue.close()
        worker.join()
        return result()
    }
}

data class TelemetryRetention(
    val maxBytes: Long = 50L * 1024 * 1024,
    val maxAgeMillis: Long = 7L * 24 * 60 * 60 * 1000,
    val maxFiles: Int = 4096,
) {
    init { require(maxBytes > 0 && maxAgeMillis > 0 && maxFiles > 0) }
}

/**
 * One directory, atomic FileStore writes, no database/index to corrupt. Length and
 * creation time in filenames allow pruning without loading historical payloads.
 */
internal class TelemetryArchiveStore(
    private val files: FileStore,
    private val retention: TelemetryRetention,
    private val now: () -> Long = { kotlin.time.Clock.System.now().toEpochMilliseconds() },
) {
    private val session = Uuid.random().toString()
    private var sequence = 0L
    private data class Entry(val name: String, val created: Long, val bytes: Long)
    fun write(signal: String, bytes: ByteArray) {
        require(bytes.size <= retention.maxBytes)
        val path = "$DIRECTORY/${now()}_${session}_${sequence++}_${bytes.size}.$signal.pb"
        files.write(path, bytes)
        check(files.read(path)?.size == bytes.size) { "Telemetry persistence failed" }
        prune()
    }

    fun health(status: TelemetryHealth) {
        files.write("$DIRECTORY/health.json", (
            "{\"schemaVersion\":1,\"droppedRecords\":${status.droppedRecords}," +
                "\"failedBatches\":${status.failedBatches},\"writtenRecords\":${status.writtenRecords}}"
            ).encodeToByteArray())
    }

    fun prune() {
        val cutoff = now() - retention.maxAgeMillis
        val entries = files.list(DIRECTORY).mapNotNull { name ->
            val parts = name.substringBefore('.').split('_')
            if (parts.size != 4 || !(name.endsWith(".traces.pb") || name.endsWith(".logs.pb"))) return@mapNotNull null
            val created = parts[0].toLongOrNull() ?: return@mapNotNull null
            val bytes = parts[3].toLongOrNull()?.takeIf { it >= 0 } ?: return@mapNotNull null
            Entry(name, created, bytes)
        }.sortedWith(compareBy<Entry> { it.created }.thenBy { it.name })
        var total = entries.sumOf { it.bytes }
        var count = entries.size
        entries.forEach {
            if (it.created < cutoff || total > retention.maxBytes || count > retention.maxFiles) {
                files.delete("$DIRECTORY/${it.name}")
                total -= it.bytes
                count--
            }
        }
    }

    companion object { const val DIRECTORY = "telemetry/v1" }
}
