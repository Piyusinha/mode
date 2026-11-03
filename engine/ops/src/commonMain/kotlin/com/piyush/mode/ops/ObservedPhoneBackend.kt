package com.piyush.mode.ops

import com.piyush.mode.core.telemetry.Field
import com.piyush.mode.core.telemetry.Operation
import com.piyush.mode.core.telemetry.Telemetry
import com.piyush.mode.core.telemetry.trace
import kotlinx.serialization.json.JsonElement

/** Shared by every phone platform. Names only: coordinates, text and op results never enter OTel. */
class ObservedPhoneBackend(private val inner: PhoneBackend, private val telemetry: Telemetry) : PhoneBackend, AutoCloseable {
    override val name: String get() = inner.name
    override suspend fun ops(): Set<String> = inner.ops()
    override suspend fun send(op: Op): JsonElement =
        telemetry.trace(Operation.PhoneOp, mapOf(Field.Operation to op.wireName)) { inner.send(op) }
    override suspend fun pauseStream() = telemetry.trace(Operation.StreamPause) { inner.pauseStream() }
    override suspend fun resumeStream() = telemetry.trace(Operation.StreamResume) { inner.resumeStream() }
    override fun close() { (inner as? AutoCloseable)?.close() }
}
