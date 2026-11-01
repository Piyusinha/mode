package com.piyush.mode.llm

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.io.encoding.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenRouterAuthTest {
    private fun hex(b: ByteArray) = b.joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    @Test
    fun sha256MatchesTheStandardVectors() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex(Sha256.hash(ByteArray(0))))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex(Sha256.hash("abc".encodeToByteArray())))
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            hex(Sha256.hash("abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq".encodeToByteArray())),
        )
    }

    @Test
    fun theSignInUrlCarriesAnS256ChallengeOfTheVerifier() {
        val auth = OpenRouterAuth(HttpClient(MockEngine { respond("") }))
        val start = auth.start("mode://openrouter")
        assertTrue(start.url.startsWith("https://openrouter.ai/auth?callback_url=mode%3A%2F%2Fopenrouter&code_challenge="), start.url)
        val challenge = Regex("code_challenge=([^&]+)").find(start.url)!!.groupValues[1]
        val expected = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(Sha256.hash(start.verifier.encodeToByteArray()))
        assertEquals(expected, challenge)
        assertTrue("code_challenge_method=S256" in start.url && "key_label=Mode" in start.url)
        assertTrue(start.verifier.length >= 43)
        assertNotEquals(start.verifier, auth.start("mode://openrouter").verifier)
    }

    @Test
    fun theCodeIsExchangedForAKey() = runTest {
        var sent = ""
        val auth = OpenRouterAuth(HttpClient(MockEngine { request ->
            sent = (request.body as TextContent).text
            respond("""{"key":"sk-or-v1-new","user_id":"u"}""", HttpStatusCode.OK, headersOf("Content-Type", "application/json"))
        }))
        assertEquals("sk-or-v1-new", auth.exchange("abc", "verifier1"))
        assertTrue(""""code":"abc"""" in sent && """"code_verifier":"verifier1"""" in sent && "S256" in sent)
        assertEquals("abc123", auth.codeFrom("mode://openrouter?code=abc123"))
        assertNull(auth.codeFrom("mode://openrouter?error=denied"))
    }
}
