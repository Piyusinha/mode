package com.piyush.mode.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlin.io.encoding.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * "Connect OpenRouter": the user signs in on OpenRouter's page and comes back
 * with a key, without copying it (OAuth with PKCE,
 * https://openrouter.ai/docs/use-cases/oauth-pkce).
 */
class OpenRouterAuth(
    private val http: HttpClient,
    private val site: String = "https://openrouter.ai",
) {
    /** Where to send the user, and the secret to finish with. */
    class Start(val url: String, val verifier: String)

    @OptIn(ExperimentalUuidApi::class)
    fun start(callbackUrl: String, keyLabel: String = "Mode"): Start {
        // 32 bytes from the platform's secure random generator.
        val verifier = base64Url(Uuid.random().toByteArray() + Uuid.random().toByteArray())
        val challenge = base64Url(Sha256.hash(verifier.encodeToByteArray()))
        val url = "$site/auth?callback_url=${callbackUrl.encodeURLParameter()}" +
            "&code_challenge=$challenge&code_challenge_method=S256&key_label=${keyLabel.encodeURLParameter()}"
        return Start(url, verifier)
    }

    /** Exchanges the code from the callback for an API key. */
    suspend fun exchange(code: String, verifier: String): String {
        val body = buildJsonObject {
            put("code", code)
            put("code_verifier", verifier)
            put("code_challenge_method", "S256")
        }
        val response = http.post("$site/api/v1/auth/keys") { setBody(TextContent(body.toString(), ContentType.Application.Json)) }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw LlmFailed("OpenRouter sign-in failed: HTTP ${response.status.value}: ${text.take(300)}")
        return Json.parseToJsonElement(text).jsonObject["key"]?.jsonPrimitive?.contentOrNull
            ?: throw LlmFailed("OpenRouter sign-in returned no key")
    }

    /** The code in a callback URL like mode://openrouter?code=…, or null. */
    fun codeFrom(callbackUrl: String): String? =
        Regex("""[?&]code=([^&#]+)""").find(callbackUrl)?.groupValues?.get(1)

    private fun base64Url(bytes: ByteArray) = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
}

/** SHA-256 (FIPS 180-4), for the PKCE challenge; small and platform-free. */
internal object Sha256 {
    private val K = intArrayOf(
        0x428a2f98, 0x71374491, -0x4a3f0431, -0x164a245b, 0x3956c25b, 0x59f111f1, -0x6dc07d5c, -0x54e3a12b,
        -0x27f85568, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, -0x7f214e02, -0x6423f959, -0x3e640e8c,
        -0x1b64963f, -0x1041b87a, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        -0x67c1aeae, -0x57ce3993, -0x4ffcd838, -0x40a68039, -0x391ff40d, -0x2a586eb9, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, -0x7e3d36d2, -0x6d8dd37b,
        -0x5d40175f, -0x57e599b5, -0x3db47490, -0x3893ae5d, -0x2e6d17e7, -0x2966f9dc, -0xbf1ca7b, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, -0x7b3787ec, -0x7338fdf8, -0x6f410006, -0x5baf9315, -0x41065c09, -0x398e870e,
    )

    fun hash(message: ByteArray): ByteArray {
        val h = intArrayOf(0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6, 0x510e527f, -0x64fa9774, 0x1f83d9ab, 0x5be0cd19)
        val bitLength = message.size.toLong() * 8
        val padded = ByteArray(((message.size + 9 + 63) / 64) * 64)
        message.copyInto(padded)
        padded[message.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
        val w = IntArray(64)
        for (chunk in padded.indices step 64) {
            for (i in 0 until 16) {
                w[i] = ((padded[chunk + 4 * i].toInt() and 0xff) shl 24) or ((padded[chunk + 4 * i + 1].toInt() and 0xff) shl 16) or
                    ((padded[chunk + 4 * i + 2].toInt() and 0xff) shl 8) or (padded[chunk + 4 * i + 3].toInt() and 0xff)
            }
            for (i in 16 until 64) {
                val s0 = w[i - 15].rotateRight(7) xor w[i - 15].rotateRight(18) xor (w[i - 15] ushr 3)
                val s1 = w[i - 2].rotateRight(17) xor w[i - 2].rotateRight(19) xor (w[i - 2] ushr 10)
                w[i] = w[i - 16] + s0 + w[i - 7] + s1
            }
            var (a, b, c, d) = h
            var e = h[4]; var f = h[5]; var g = h[6]; var hh = h[7]
            for (i in 0 until 64) {
                val s1 = e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25)
                val ch = (e and f) xor (e.inv() and g)
                val t1 = hh + s1 + ch + K[i] + w[i]
                val s0 = a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22)
                val maj = (a and b) xor (a and c) xor (b and c)
                val t2 = s0 + maj
                hh = g; g = f; f = e; e = d + t1; d = c; c = b; b = a; a = t1 + t2
            }
            h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e; h[5] += f; h[6] += g; h[7] += hh
        }
        return ByteArray(32) { i -> (h[i / 4] ushr (24 - 8 * (i % 4))).toByte() }
    }
}
