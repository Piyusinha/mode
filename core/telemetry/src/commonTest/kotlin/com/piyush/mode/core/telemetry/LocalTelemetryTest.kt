@file:OptIn(io.opentelemetry.kotlin.ExperimentalApi::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.piyush.mode.core.telemetry

import com.piyush.mode.core.storage.FileStore
import io.opentelemetry.kotlin.logging.export.toLogRecordDataList
import io.opentelemetry.kotlin.tracing.StatusCode
import io.opentelemetry.kotlin.tracing.export.toSpanDataList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LocalTelemetryTest {
    private val resource = TelemetryResource("installation-test", "app.mode.test", "0.1", "42", "test")

    @Test fun `parallel coroutine traces preserve parents and remain isolated`() = runTest {
        val files = MemoryFiles()
        val telemetry = LocalTelemetry(files, resource, StandardTestDispatcher(testScheduler))
        (1..2).map { n -> async {
            telemetry.trace(Operation.Task, mapOf(Field.TaskId to "task-$n")) {
                coroutineScope {
                    (1..3).map { async {
                        withContext(StandardTestDispatcher(testScheduler)) { telemetry.trace(Operation.PhoneOp) {} }
                    } }.awaitAll()
                }
            }
        } }.awaitAll()
        assertTrue(telemetry.flush())
        val spans = files.traces()
        val roots = spans.filter { it.name == Operation.Task.wireName }
        assertEquals(2, roots.size)
        assertNotEquals(roots[0].spanContext.traceId, roots[1].spanContext.traceId)
        roots.forEach { root ->
            val children = spans.filter { it.parent.spanId == root.spanContext.spanId }
            assertEquals(3, children.size)
            assertTrue(children.all { it.spanContext.traceId == root.spanContext.traceId })
        }
        assertTrue(telemetry.shutdown())
    }

    @Test fun `W3C remote parent is accepted and invalid carrier starts a new trace`() = runTest {
        val files = MemoryFiles()
        val telemetry = LocalTelemetry(files, resource, StandardTestDispatcher(testScheduler))
        val traceId = "1234567890abcdef1234567890abcdef"
        val parentId = "1234567890abcdef"
        telemetry.withParent(TraceContext("00-$traceId-$parentId-01")) {
            telemetry.trace(Operation.DeviceOpen) {}
        }
        telemetry.withParent(TraceContext("invalid-user-secret")) {
            telemetry.trace(Operation.VpnStart) {}
        }
        assertTrue(telemetry.flush())
        val remote = files.traces().single { it.name == Operation.DeviceOpen.wireName }
        assertEquals(traceId, remote.spanContext.traceId)
        assertEquals(parentId, remote.parent.spanId)
        assertTrue(remote.parent.isRemote)
        val fresh = files.traces().single { it.name == Operation.VpnStart.wireName }
        assertNotEquals(traceId, fresh.spanContext.traceId)
        assertFalse(fresh.parent.isValid)
        telemetry.shutdown()
    }

    @Test fun `exports are official OTLP and sensitive values cannot enter spans or logs`() = runTest {
        val files = MemoryFiles()
        val telemetry = LocalTelemetry(files, resource, StandardTestDispatcher(testScheduler))
        telemetry.trace(Operation.ModelRequest, mapOf(
            Field.Model to "gpt-test", Field.Effort to "medium", Field.InputTokens to "123",
            Field.Outcome to "Bearer secret", Field.TaskId to "user@example.com", Field.Provider to "sk-secret",
        )) { span ->
            span.event(Event.FirstAction, mapOf(Field.DurationMs to "500"))
        }
        try {
            telemetry.trace(Operation.PhoneOp) { throw IllegalStateException("secret password and prompt") }
        } catch (_: IllegalStateException) { }
        assertTrue(telemetry.flush())
        val spans = files.traces()
        val model = spans.single { it.name == Operation.ModelRequest.wireName }
        assertEquals("gpt-test", model.attributes[Field.Model.wireName])
        assertEquals(123L, model.attributes[Field.InputTokens.wireName])
        assertNull(model.attributes[Field.Outcome.wireName])
        assertNull(model.attributes[Field.TaskId.wireName])
        assertNull(model.attributes[Field.Provider.wireName])
        assertEquals("mode", model.resource.attributes["service.name"])
        assertEquals("installation-test", model.resource.attributes["service.instance.id"])
        val failed = spans.single { it.name == Operation.PhoneOp.wireName }
        assertEquals(StatusCode.ERROR, failed.status.statusCode)
        assertNull(failed.status.description)
        assertEquals("IllegalStateException", failed.attributes[Field.ErrorType.wireName])
        val log = files.logs().single()
        assertEquals(model.spanContext.traceId, log.spanContext.traceId)
        assertEquals(model.spanContext.spanId, log.spanContext.spanId)
        assertNull(log.body)
        val all = files.values.values.joinToString { it.decodeToString() }
        listOf("secret", "password", "prompt", "user@example.com", "Bearer").forEach { assertFalse(all.contains(it), it) }
        telemetry.shutdown()
    }

    @Test fun `cancelled operation ends once and cancellation reaches caller`() = runTest {
        val files = MemoryFiles()
        val telemetry = LocalTelemetry(files, resource, StandardTestDispatcher(testScheduler))
        val entered = CompletableDeferred<Unit>()
        val job = launch { telemetry.trace(Operation.Task) { entered.complete(Unit); CompletableDeferred<Unit>().await() } }
        entered.await()
        job.cancel()
        job.join()
        assertTrue(job.isCancelled)
        assertTrue(telemetry.flush())
        val span = files.traces().single()
        assertEquals(true, span.attributes[Field.Cancelled.wireName])
        assertNotNull(span.endTimestamp)
        assertEquals(0, telemetry.health.value.failedBatches)
        telemetry.shutdown()
    }

    @Test fun `cancelled lock acquisition cannot leak mutex ownership`() = runTest {
        val files = MemoryFiles()
        val telemetry = LocalTelemetry(files, resource, StandardTestDispatcher(testScheduler))
        val mutex = Mutex(locked = true)
        val job = launch { telemetry.withLock(mutex, Operation.TaskLock) { error("not acquired") } }
        advanceUntilIdle()
        job.cancel()
        job.join()
        mutex.unlock()
        telemetry.withLock(mutex, Operation.TaskLock) { assertTrue(mutex.isLocked) }
        assertFalse(mutex.isLocked)
        assertTrue(telemetry.flush())
        assertEquals(2, files.traces().size)
        assertEquals(0, telemetry.health.value.failedBatches)
        telemetry.shutdown()
    }

    @Test fun `bounded queue drops records without blocking application work`() = runTest {
        val files = MemoryFiles()
        val telemetry = LocalTelemetry(files, resource, StandardTestDispatcher(testScheduler), queueCapacity = 2)
        repeat(10) { telemetry.start(Operation.ScreenLook).end() }
        assertEquals(8, telemetry.health.value.droppedRecords)
        assertTrue(telemetry.flush())
        assertEquals(2, files.traces().size)
        assertTrue(files.values.getValue("telemetry/v1/health.json").decodeToString().contains("\"droppedRecords\":8"))
        telemetry.shutdown()
    }

    @Test fun `broken disk is visible but does not fail or delay the task`() = runTest {
        val files = MemoryFiles(failWrites = true)
        val telemetry = LocalTelemetry(files, resource, StandardTestDispatcher(testScheduler))
        assertEquals(42, telemetry.trace(Operation.Task) { 42 })
        assertFalse(telemetry.flush())
        assertEquals(1, telemetry.health.value.droppedRecords)
        assertTrue(telemetry.health.value.failedBatches > 0)
        assertFalse(telemetry.shutdown())
        assertFalse(telemetry.flush())
    }

    @Test fun `no-op and failing telemetry preserve business results and exceptions`() = runTest {
        val broken = object : Telemetry {
            override fun start(operation: Operation, parent: TraceContext?, fields: Fields): TraceSpan = error("exporter broke")
        }
        assertEquals("done", broken.trace(Operation.Task) { "done" })
        assertEquals("done", Telemetry.None.trace(Operation.Task) { "done" })
        var cancelled = false
        try { broken.trace(Operation.Task) { throw CancellationException("stop") } }
        catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test fun `retention enforces age size count and survives reopening`() {
        val files = MemoryFiles()
        var now = 100L
        fun store() = TelemetryArchiveStore(files, TelemetryRetention(maxBytes = 10, maxAgeMillis = 20, maxFiles = 2)) { now }
        val first = store()
        first.write("traces", ByteArray(4))
        now++
        first.write("logs", ByteArray(4))
        now++
        first.write("traces", ByteArray(4))
        assertEquals(2, files.values.size)
        now++
        store().write("logs", ByteArray(8))
        assertEquals(1, files.values.size)
        now = 200
        store().prune()
        assertTrue(files.values.isEmpty())
    }

    private class MemoryFiles(private val failWrites: Boolean = false) : FileStore {
        val values = mutableMapOf<String, ByteArray>()
        override fun append(path: String, text: String) { error("unused") }
        override fun write(path: String, bytes: ByteArray) { check(!failWrites); values[path] = bytes }
        override fun read(path: String) = values[path]
        override fun list(dir: String) = values.keys.filter { it.startsWith("$dir/") }.map { it.removePrefix("$dir/") }
        override fun delete(path: String) { values.remove(path) }
        fun traces() = values.filterKeys { it.endsWith(".traces.pb") }.values.flatMap { it.toSpanDataList() }
        fun logs() = values.filterKeys { it.endsWith(".logs.pb") }.values.flatMap { it.toLogRecordDataList() }
    }
}
