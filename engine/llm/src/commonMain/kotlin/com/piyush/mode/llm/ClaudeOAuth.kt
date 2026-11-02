package com.piyush.mode.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import kotlin.io.encoding.Base64
import kotlin.time.Clock
import kotlin.uuid.Uuid
import kotlin.uuid.ExperimentalUuidApi

/** Native, experimental OAuth transport adapted from finnvoor/OAuthLanguageModels (CC0).
 * No Claude Code process or proxy. The browser's copy-code callback avoids an iOS background listener.
 * Never log a start URL, authorization code, token response, or these credentials.
 */
class ClaudeOAuth(private val http: HttpClient, private val tokenUrl: String = TOKEN_URL) {
    class Start(val url: String, val verifier: String, val state: String, val createdAt: Long)

    @Serializable
    class Credentials(val accessToken: String, val refreshToken: String, val expiresAt: Long)

    @OptIn(ExperimentalUuidApi::class)
    fun start(now: Long = Clock.System.now().toEpochMilliseconds()): Start {
        val verifier = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(Uuid.random().toByteArray() + Uuid.random().toByteArray())
        val challenge = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)
            .encode(Sha256.hash(verifier.encodeToByteArray()))
        val fields = listOf(
            "code" to "true", "client_id" to CLIENT_ID, "response_type" to "code",
            "redirect_uri" to REDIRECT_URI, "scope" to "user:profile user:inference",
            "code_challenge" to challenge, "code_challenge_method" to "S256", "state" to verifier,
        )
        return Start("https://claude.ai/oauth/authorize?" + fields.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }, verifier, verifier, now)
    }

    suspend fun exchange(code: String, start: Start, now: Long = Clock.System.now().toEpochMilliseconds()): Credentials {
        require(now - start.createdAt in 0..LOGIN_MAX_AGE_MS) { "Claude sign-in expired. Start again." }
        val parts = code.trim().split('#', limit = 2)
        require(parts.size == 2 && parts[0].isNotBlank() && parts[1] == start.state) {
            "Paste the complete code from this Claude sign-in, including the part after #."
        }
        return token(buildJsonObject {
            put("grant_type", "authorization_code"); put("client_id", CLIENT_ID)
            put("code", parts[0]); put("state", start.state)
            put("redirect_uri", REDIRECT_URI); put("code_verifier", start.verifier)
        }, previousRefresh = null)
    }

    suspend fun refresh(credentials: Credentials): Credentials = token(buildJsonObject {
        put("grant_type", "refresh_token"); put("client_id", CLIENT_ID)
        put("refresh_token", credentials.refreshToken)
    }, credentials.refreshToken)

    private suspend fun token(body: JsonObject, previousRefresh: String?): Credentials {
        val response = http.post(tokenUrl) { setBody(TextContent(body.toString(), ContentType.Application.Json)) }
        // Error bodies can echo credentials: report status only.
        if (!response.status.isSuccess()) throw LlmFailed("Claude sign-in failed (HTTP ${response.status.value}). Try signing in again.")
        val o = runCatching { Json.parseToJsonElement(response.bodyAsText()).jsonObject }
            .getOrElse { throw LlmFailed("Claude returned an unreadable sign-in response.") }
        val access = o["access_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: throw LlmFailed("Claude sign-in returned no access token.")
        val refresh = o["refresh_token"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: previousRefresh
            ?: throw LlmFailed("Claude sign-in returned no refresh token.")
        val seconds = o["expires_in"]?.jsonPrimitive?.longOrNull?.takeIf { it in 1..31_536_000 }
            ?: throw LlmFailed("Claude sign-in returned no valid token lifetime.")
        return Credentials(access, refresh, Clock.System.now().toEpochMilliseconds() + seconds * 1000)
    }

    companion object {
        const val CLIENT_ID = "9d1c250a-e61b-44d9-88ed-5944d1962f5e"
        const val TOKEN_URL = "https://platform.claude.com/v1/oauth/token"
        const val REDIRECT_URI = "https://platform.claude.com/oauth/code/callback"
        const val LOGIN_MAX_AGE_MS = 10 * 60_000L
    }
}
