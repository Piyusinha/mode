package com.piyush.mode.ops

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * A phone for tests: it records every op it is sent and answers from [answers],
 * keyed by wire name. Ops outside [supported] raise [Unsupported], as a real
 * backend would.
 */
class FakeBackend(
    private val supported: Set<String>,
    private val answers: Map<String, JsonElement> = emptyMap(),
) : PhoneBackend {
    override val name = "fake"

    private val _sent = mutableListOf<Op>()
    val sent: List<Op> get() = _sent

    override suspend fun ops(): Set<String> = supported

    override suspend fun send(op: Op): JsonElement {
        val wire = op.wireName
        if (wire !in supported) throw Unsupported("$name cannot '$wire'")
        _sent += op
        return answers[wire] ?: JsonNull
    }
}
