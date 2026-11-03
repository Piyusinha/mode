package com.piyush.mode.ops

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** A phone whose connection breaks on the next [breakOn] ops. */
private class FlakyPhone(private val id: Int, private var breakOn: MutableSet<String>) : PhoneBackend, AutoCloseable {
    val sent = mutableListOf<String>()
    var closed = false
    override val name = "flaky$id"
    override suspend fun ops() = setOf("input.tap", "screen.text", "nav.home")
    override suspend fun send(op: Op): JsonElement {
        if (breakOn.remove(op.wireName)) throw OpFailed("device socket io failed")
        sent += op.wireName
        return JsonPrimitive(id)
    }
    override fun close() { closed = true }
}

class ResilientBackendTest {
    private val phones = mutableListOf<FlakyPhone>()
    private val breaks = mutableSetOf<String>()
    private fun backend() = ResilientBackend({ FlakyPhone(phones.size + 1, breaks).also { phones += it } })

    @Test
    fun holdBlocksOpsAndDoesNotOpenAnotherConnection() = runTest {
        val b = backend()
        b.send(Op.Home)
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        launch {
            b.hold {
                entered.complete(Unit)
                release.await()
            }
        }
        entered.await()
        val send = launch { b.send(Op.Home) }
        yield()
        assertTrue(!send.isCompleted)
        release.complete(Unit)
        send.join()
        assertEquals(1, phones.size)
        assertEquals(listOf("nav.home", "nav.home"), phones[0].sent)
    }

    @Test
    fun aDroppedLookIsRetriedOnANewConnection() = runTest {
        val b = backend()
        breaks += "screen.text"
        assertEquals(JsonPrimitive(2), b.send(Op.ScreenText()))
        assertEquals(2, phones.size)
        assertTrue(phones[0].closed, "the broken connection is closed")
        assertEquals(listOf("screen.text"), phones[1].sent)
    }

    @Test
    fun aDroppedTapIsNotRepeated() = runTest {
        val b = backend()
        breaks += "input.tap"
        val e = assertFailsWith<OpFailed> { b.send(Op.Tap(1.0, 1.0)) }
        assertTrue("may not have happened" in e.message!!)
        assertEquals(2, phones.size, "it reconnected")
        assertTrue(phones[1].sent.isEmpty(), "but did not tap again")
        b.send(Op.Tap(1.0, 1.0))
        assertEquals(listOf("input.tap"), phones[1].sent, "the next tap uses the new connection")
    }

    @Test
    fun otherFailuresPassThroughWithoutReconnecting() = runTest {
        val b = ResilientBackend({ FakeBackend(supported = setOf("input.tap")).also { phones.size } })
        assertFailsWith<Unsupported> { b.send(Op.Home) }
    }

    @Test
    fun recognisesConnectionLossThroughCauses() {
        assertTrue(ResilientBackend.isConnectionLost(OpFailed("outer", RuntimeException("Broken pipe"))))
        assertTrue(!ResilientBackend.isConnectionLost(OpFailed("no installed app is called 'Ola'")))
    }
}
