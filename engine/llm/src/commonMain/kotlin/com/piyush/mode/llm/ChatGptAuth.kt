package com.piyush.mode.llm

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlin.io.encoding.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * "Continue with ChatGPT": the user signs in with their ChatGPT account and lets Mode use their plan
 * (OpenAI's Sign in with ChatGPT, open-source clients: developers.openai.com/siwc/token-sharing-open-source/sign-in).
 * OAuth with PKCE; the first sign-in registers Mode itself (client_id=dynamic_agent_client) and the callback
 * brings the client id to keep. The redirect is an HTTP loopback address the app listens on.
 */
class ChatGptAuth(
    private val http: HttpClient,
    private val issuer: String = "https://auth.openai.com",
    private val api: String = "https://api.openai.com/v1",
) {
    /** Where to send the user, and what to keep until the callback. */
    class Start(val url: String, val verifier: String, val state: String, val redirectUri: String)

    /** What the callback brought: the code, and the client id when this was the first sign-in. */
    class Callback(val code: String, val clientId: String?)

    class Tokens(
        val accessToken: String,
        val refreshToken: String?,
        /** Seconds the access token is good for. */
        val expiresIn: Long?,
        val scopes: Set<String>,
        /** Who signed in (email, name, plan), for showing the account; not checked here. */
        val idToken: String? = null,
    ) {
        /** Without this scope the plan can't be used, whatever else was granted. */
        val canUsePlan: Boolean get() = PLAN_SCOPE in scopes
    }

    /**
     * The sign-in address. [clientId] null registers Mode (first sign-in); [hostId] is this iPhone's own
     * id for OpenAI, made once and kept.
     */
    @OptIn(ExperimentalUuidApi::class)
    fun start(redirectUri: String, clientId: String?, hostId: String, appName: String = "Mode"): Start {
        val verifier = base64Url(Uuid.random().toByteArray() + Uuid.random().toByteArray())
        val challenge = base64Url(Sha256.hash(verifier.encodeToByteArray()))
        val state = Uuid.random().toHexString()
        val params = buildList {
            add("client_id" to (clientId ?: REGISTER))
            if (clientId == null) add("agent_name_hint" to appName)
            add("ext_agent_host_id" to hostId)
            add("response_type" to "code")
            add("redirect_uri" to redirectUri)
            add("scope" to SCOPES)
            add("resource" to api)
            add("state" to state)
            add("nonce" to Uuid.random().toHexString())
            add("code_challenge_method" to "S256")
            add("code_challenge" to challenge)
        }
        val url = "$issuer/api/accounts/authorize?" + params.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
        return Start(url, verifier, state, redirectUri)
    }

    /** Reads the callback; a refusal or a wrong state is an error, never a code to use. */
    fun callback(url: String, state: String): Callback {
        fun param(name: String) = Regex("""[?&]$name=([^&#]+)""").find(url)?.groupValues?.get(1)?.let(::decode)
        if (param("state") != state) throw LlmFailed("ChatGPT sign-in came back for another attempt; please try again")
        param("error")?.let { throw LlmFailed(if (it == "access_denied") "ChatGPT sign-in was cancelled" else "ChatGPT sign-in failed: $it") }
        val code = param("code") ?: throw LlmFailed("ChatGPT sign-in returned no code")
        return Callback(code, param("client_id"))
    }

    /** Exchanges the callback's code for tokens. */
    suspend fun exchange(code: String, start: Start, clientId: String): Tokens = token(
        "grant_type" to "authorization_code", "client_id" to clientId, "code" to code,
        "code_verifier" to start.verifier, "redirect_uri" to start.redirectUri, "resource" to api,
    )

    /** New tokens; the refresh token changes every time and the new one must be kept. */
    suspend fun refresh(refreshToken: String, clientId: String): Tokens = token(
        "grant_type" to "refresh_token", "client_id" to clientId, "refresh_token" to refreshToken, "resource" to api,
    )

    /** The models the plan can use, as OpenAI lists them, with their reasoning efforts when it says. */
    suspend fun models(accessToken: String): List<PlanModel> {
        val response = http.get("$api/models") { header("Authorization", "Bearer $accessToken") }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) throw LlmFailed("ChatGPT models: HTTP ${response.status.value}: ${text.take(300)}")
        return parseModels(text)
    }

    private suspend fun token(vararg fields: Pair<String, String>): Tokens {
        val form = fields.joinToString("&") { (k, v) -> "$k=${v.encodeURLParameter()}" }
        val response = http.post("$issuer/api/accounts/oauth/token") { setBody(TextContent(form, ContentType.Application.FormUrlEncoded)) }
        val text = response.bodyAsText()
        if (!response.status.isSuccess()) {
            // invalid_grant: the code or refresh token is spent or revoked: sign in again.
            val signInAgain = "invalid_grant" in text
            throw LlmFailed(if (signInAgain) "ChatGPT needs you to sign in again" else "ChatGPT sign-in failed: HTTP ${response.status.value}: ${text.take(300)}")
        }
        return parseTokens(Json.parseToJsonElement(text).jsonObject)
    }

    companion object {
        const val REGISTER = "dynamic_agent_client"
        const val PLAN_SCOPE = "chatgpt.tokens.use.direct"
        const val SCOPES = "openid profile email offline_access resource.invoke $PLAN_SCOPE"
        /** The loopback port OpenAI's guide uses; scheme, host and path must stay the same across sign-ins. */
        const val LOOPBACK_PORT = 1455
        const val REDIRECT_PATH = "/auth/callback"

        /**
         * A model list: "data" (or "models") entries with an id ("id" or "slug"), a name if given, and any field
         * about reasoning or effort read as its efforts, whatever its shape.
         */
        fun parseModels(text: String): List<PlanModel> {
            val root = Json.parseToJsonElement(text).jsonObject
            val entries = (root["data"] ?: root["models"])?.jsonArray.orEmpty()
            return entries.mapNotNull { e ->
                val o = e as? JsonObject ?: return@mapNotNull null
                val id = listOf("id", "slug").firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.contentOrNull } ?: return@mapNotNull null
                val name = listOf("display_name", "name").firstNotNullOfOrNull { (o[it] as? JsonPrimitive)?.contentOrNull }
                val efforts = o.entries.filter { (k, _) -> "reasoning" in k || "effort" in k }
                    .firstNotNullOfOrNull { (_, v) -> PlanModel.effortsIn(v) }
                PlanModel(id, name, efforts)
            }
        }

        internal fun parseTokens(o: JsonObject): Tokens = Tokens(
            accessToken = o["access_token"]?.jsonPrimitive?.contentOrNull ?: throw LlmFailed("ChatGPT sign-in returned no token"),
            refreshToken = o["refresh_token"]?.jsonPrimitive?.contentOrNull,
            expiresIn = o["expires_in"]?.jsonPrimitive?.longOrNull,
            scopes = o["scope"]?.jsonPrimitive?.contentOrNull.orEmpty().split(' ').filter { it.isNotBlank() }.toSet(),
            idToken = o["id_token"]?.jsonPrimitive?.contentOrNull,
        )

        private fun decode(s: String): String =
            Regex("%([0-9A-Fa-f]{2})").replace(s.replace('+', ' ')) { it.groupValues[1].toInt(16).toChar().toString() }

        private fun base64Url(bytes: ByteArray) = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT).encode(bytes)
    }
}
