package com.piyush.mode.core.storage

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * How an encrypted secret is written down: one text, `v1:` then base64 of the
 * nonce followed by the ciphertext. The cipher itself is the platform's (AES-GCM
 * with a key the device keeps); this only packs and unpacks the two parts.
 */
@OptIn(ExperimentalEncodingApi::class)
public object SealedValue {
    private const val PREFIX = "v1:"

    public fun pack(nonce: ByteArray, ciphertext: ByteArray): String {
        require(nonce.isNotEmpty()) { "a sealed value needs a nonce" }
        return PREFIX + Base64.encode(byteArrayOf(nonce.size.toByte()) + nonce + ciphertext)
    }

    /** The nonce and ciphertext, or null when [packed] is not something [pack] wrote. */
    public fun unpack(packed: String): Pair<ByteArray, ByteArray>? {
        if (!packed.startsWith(PREFIX)) return null
        val bytes = runCatching { Base64.decode(packed.removePrefix(PREFIX)) }.getOrNull() ?: return null
        if (bytes.isEmpty()) return null
        val nonceLength = bytes[0].toInt() and 0xFF
        if (nonceLength == 0 || bytes.size < 1 + nonceLength) return null
        return bytes.copyOfRange(1, 1 + nonceLength) to bytes.copyOfRange(1 + nonceLength, bytes.size)
    }
}
