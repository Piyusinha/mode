package com.piyush.mode.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secrets (API keys, sign-ins) encrypted with a key that lives in the Android
 * Keystore and never leaves the device; the sealed values sit in their own
 * SharedPreferences file. Readable without the phone being unlocked, so a
 * background run can use them.
 */
public class KeystoreSecretStore(
    context: Context,
    private val alias: String = "mode.secrets",
    fileName: String = "mode.secrets",
) : SecretStore {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(fileName, Context.MODE_PRIVATE)

    override fun get(name: String): String? {
        val packed = prefs.getString(name, null) ?: return null
        val (nonce, ciphertext) = SealedValue.unpack(packed) ?: return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, nonce))
            cipher.doFinal(ciphertext).decodeToString()
        }.getOrNull()
    }

    override fun set(name: String, value: String?) {
        if (value.isNullOrBlank()) {
            prefs.edit().remove(name).commit()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val sealed = SealedValue.pack(cipher.iv, cipher.doFinal(value.encodeToByteArray()))
        check(prefs.edit().putString(name, sealed).commit()) { "Could not save a credential." }
    }

    override fun clear() {
        prefs.edit().clear().commit()
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val TAG_BITS = 128
    }
}
