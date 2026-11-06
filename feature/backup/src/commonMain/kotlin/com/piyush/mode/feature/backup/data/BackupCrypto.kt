package com.piyush.mode.feature.backup.data

import dev.whyoleg.cryptography.BinarySize.Companion.bits
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.DelicateCryptographyApi
import dev.whyoleg.cryptography.algorithms.AES
import dev.whyoleg.cryptography.algorithms.HMAC
import dev.whyoleg.cryptography.algorithms.PBKDF2
import dev.whyoleg.cryptography.algorithms.SHA256
import dev.whyoleg.cryptography.random.CryptographyRandom

/**
 * The lock on a backup that holds sign-ins: PBKDF2-HMAC-SHA256 turns the password into a 256-bit master key
 * (random 16-byte salt, [ITERATIONS] rounds), and two keys come from it by HMAC: one for AES-256-GCM (random
 * 12-byte nonce, 128-bit tag, the header as associated data) and one whose hash, kept in the header, tells a
 * wrong password apart from a damaged file. That check gives an attacker nothing the ciphertext didn't: each
 * guess still costs the full key derivation.
 *
 * On iOS the provider is CryptoKit (AES-GCM) with CommonCrypto (PBKDF2, HMAC); on the JVM, the JDK.
 */
internal object BackupCrypto {
    /** OWASP's 2023 floor for PBKDF2-HMAC-SHA256. */
    const val ITERATIONS: Int = 310_000
    const val SALT_BYTES: Int = 16
    const val NONCE_BYTES: Int = 12

    private val provider get() = CryptographyProvider.Default

    fun random(size: Int): ByteArray = CryptographyRandom.nextBytes(size)

    class Keys(val cipher: ByteArray, val check: ByteArray)

    /** The cipher key and the password check for [password] with [salt]. */
    suspend fun keys(password: String, salt: ByteArray, iterations: Int): Keys {
        val master = provider.get(PBKDF2)
            .secretDerivation(digest = SHA256, iterations = iterations, outputSize = 256.bits, salt = salt)
            .deriveSecretToByteArray(password.encodeToByteArray())
        val hmac = provider.get(HMAC).keyDecoder(SHA256).decodeFromByteArray(HMAC.Key.Format.RAW, master)
        val cipher = hmac.signatureGenerator().generateSignature("mode-backup cipher key".encodeToByteArray())
        val checkKey = hmac.signatureGenerator().generateSignature("mode-backup password check".encodeToByteArray())
        return Keys(cipher, sha256(checkKey))
    }

    fun sha256(bytes: ByteArray): ByteArray = provider.get(SHA256).hasher().hashBlocking(bytes)

    @OptIn(DelicateCryptographyApi::class)
    suspend fun seal(key: ByteArray, nonce: ByteArray, plain: ByteArray, associated: ByteArray): ByteArray =
        gcm(key).encryptWithIv(nonce, plain, associated)

    /** Throws when the key is wrong or a byte was changed. */
    @OptIn(DelicateCryptographyApi::class)
    suspend fun open(key: ByteArray, nonce: ByteArray, sealed: ByteArray, associated: ByteArray): ByteArray =
        gcm(key).decryptWithIv(nonce, sealed, associated)

    private suspend fun gcm(key: ByteArray) =
        provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key).cipher(tagSize = 128.bits)

    /** Equal without stopping at the first difference. */
    fun same(a: ByteArray, b: ByteArray): Boolean {
        if (a.size != b.size) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].toInt() xor b[i].toInt())
        return diff == 0
    }
}
