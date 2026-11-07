package com.piyush.mode.core.storage

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SealedValueTest {
    @Test
    fun packsAndUnpacks() {
        val nonce = ByteArray(12) { it.toByte() }
        val ciphertext = byteArrayOf(9, 8, 7, 6, 5)
        val packed = SealedValue.pack(nonce, ciphertext)
        assertTrue(packed.startsWith("v1:"))
        val (n, c) = SealedValue.unpack(packed)!!
        assertContentEquals(nonce, n)
        assertContentEquals(ciphertext, c)
    }

    @Test
    fun emptyCiphertextStillRoundTrips() {
        val (n, c) = SealedValue.unpack(SealedValue.pack(byteArrayOf(1, 2, 3), ByteArray(0)))!!
        assertEquals(3, n.size)
        assertEquals(0, c.size)
    }

    @Test
    fun rejectsWhatItDidNotWrite() {
        assertNull(SealedValue.unpack("sk-or-plain-text"))
        assertNull(SealedValue.unpack("v1:"))
        assertNull(SealedValue.unpack("v1:not base64!"))
        assertNull(SealedValue.unpack("v1:AA=="))
    }
}
