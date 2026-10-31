package com.piyush.mode.ops

import kotlinx.serialization.json.JsonElement

/**
 * The one seam every phone sits behind: CoreDevice remote control on iOS, an Android
 * accessibility service later, a fake in tests.
 *
 * A backend answers only the ops it can honour. What it cannot do raises
 * [Unsupported] rather than being emulated: a guessed Back gesture returns a
 * result the caller cannot tell from a real one.
 */
interface PhoneBackend {
    /** Short name for logs and errors, such as `ios-coredevice`. */
    val name: String

    /** Wire names of every op this phone can do right now. */
    suspend fun ops(): Set<String>

    /** Run one op and return the phone's answer. */
    suspend fun send(op: Op): JsonElement
}

suspend fun PhoneBackend.supports(op: Op): Boolean = op.wireName in ops()

/** This phone cannot do that op, and retrying will not help. */
class Unsupported(message: String) : RuntimeException(message)

/** The phone could not be reached or refused the op; the message says what the user has to do. */
class PhoneUnavailable(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
