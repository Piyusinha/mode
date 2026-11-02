package com.piyush.mode.feature.settings.data

import com.piyush.mode.core.storage.SecretStore
import com.piyush.mode.llm.AccountInfo
import com.piyush.mode.llm.ChatGptAuth
import com.piyush.mode.llm.Jwt
import kotlinx.serialization.json.Json
import com.piyush.mode.llm.LlmFailed
import com.piyush.mode.llm.ResponsesClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/**
 * The user's ChatGPT account, for "Continue with ChatGPT": the client id Mode registered, this iPhone's
 * host id, and the tokens, all in the Keychain and never logged. The access token lasts an hour and is
 * refreshed when needed; each refresh brings a new refresh token, which replaces the old one.
 */
public class ChatGptAccount @OptIn(ExperimentalTime::class) constructor(
    private val auth: ChatGptAuth,
    private val secrets: SecretStore,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    /** Signed in or out: settings recompute which providers are ready. */
    private val onChange: () -> Unit = {},
) {
    private val mutableConnected = MutableStateFlow(secrets.get(REFRESH) != null)
    private val mutableModels = MutableStateFlow(secrets.get(MODELS)?.split(',')?.filter { it.isNotBlank() }.orEmpty())
    /** The plan's models as last asked (empty until the first check). */
    public val knownModels: StateFlow<List<String>> = mutableModels.asStateFlow()
    /** Signed in, with a plan Mode may use. */
    public val connected: StateFlow<Boolean> = mutableConnected.asStateFlow()

    private var pending: ChatGptAuth.Start? = null
    private val refreshing = Mutex()

    /** The loopback address the app listens on during sign-in. */
    public val redirectUri: String = "http://127.0.0.1:${ChatGptAuth.LOOPBACK_PORT}${ChatGptAuth.REDIRECT_PATH}"

    /** Starts a sign-in: the page to open. */
    public fun beginSignIn(): String = auth.start(redirectUri, secrets.get(CLIENT_ID), hostId()).also { pending = it }.url

    /** Finishes a sign-in from the URL the loopback received. Throws with a plain message when it can't be used. */
    public suspend fun finishSignIn(callbackUrl: String) {
        val start = pending ?: throw LlmFailed("ChatGPT sign-in had not started; please try again")
        pending = null
        val callback = auth.callback(callbackUrl, start.state)
        val clientId = callback.clientId ?: secrets.get(CLIENT_ID) ?: throw LlmFailed("ChatGPT sign-in returned no client id")
        val tokens = auth.exchange(callback.code, start, clientId)
        if (!tokens.canUsePlan) throw LlmFailed(ResponsesClient.NOT_ELIGIBLE)
        secrets.set(CLIENT_ID, clientId)
        keep(tokens)
        secrets.set(LIMIT_AT, null)
        mutableConnected.value = true
        onChange()
    }

    /** A token good for at least a minute, refreshed if needed; null when signed out. */
    public suspend fun accessToken(): String? = refreshing.withLock {
        val refresh = secrets.get(REFRESH) ?: return@withLock null
        val expires = secrets.get(EXPIRES)?.toLongOrNull() ?: 0
        secrets.get(ACCESS)?.takeIf { expires - now() > MARGIN_MS }?.let { return@withLock it }
        val clientId = secrets.get(CLIENT_ID) ?: return@withLock null
        try {
            auth.refresh(refresh, clientId).also(::keep).accessToken
        } catch (e: LlmFailed) {
            if ("sign in again" in e.message.orEmpty()) signOut()
            throw e
        }
    }

    /** The models the plan can use. */
    public suspend fun models(): List<String> =
        auth.models(accessToken() ?: throw LlmFailed(ResponsesClient.SIGN_IN_AGAIN)).also { list ->
            secrets.set(MODELS, list.joinToString(","))
            mutableModels.value = list
        }

    /** Who is signed in, from the sign-in's ID token (null before the first sign-in that brought one). */
    public fun account(): AccountInfo? = secrets.get(PROFILE)?.let { runCatching { Json.decodeFromString<AccountInfo>(it) }.getOrNull() }

    /** When the plan's share for Mode last ran out, if it has. */
    public fun limitReachedAt(): Long? = secrets.get(LIMIT_AT)?.toLongOrNull()

    /** The plan said its share for Mode is used up (ResponsesClient). */
    public fun limitReached() { secrets.set(LIMIT_AT, now().toString()) }

    public fun signOut() {
        listOf(ACCESS, REFRESH, EXPIRES, PROFILE, LIMIT_AT).forEach { secrets.set(it, null) }
        mutableConnected.value = false
        onChange()
    }

    private fun keep(tokens: ChatGptAuth.Tokens) {
        Jwt.chatGptAccount(tokens.idToken)?.let { secrets.set(PROFILE, Json.encodeToString(it)) }
        secrets.set(ACCESS, tokens.accessToken)
        tokens.refreshToken?.let { secrets.set(REFRESH, it) }
        secrets.set(EXPIRES, (now() + (tokens.expiresIn ?: DEFAULT_LIFETIME_S) * 1000).toString())
    }

    /**
     * This iPhone's id for OpenAI, made once: "urn:uuid:" and a standard UUID, as OpenAI's guide and working
     * clients send it. Seen on the phone: a bare hex id got "invalid auth request" from the sign-in page.
     * An id kept in the old form is replaced (it never registered, so nothing is lost).
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun hostId(): String =
        secrets.get(HOST_ID)?.takeIf { it.startsWith(HOST_PREFIX) }
            ?: (HOST_PREFIX + Uuid.random().toString()).also { secrets.set(HOST_ID, it) }

    public companion object {
        /** Present while signed in (SettingsRepository reads it to know ChatGPT is ready). */
        public const val CONNECTED_KEY: String = "chatgpt_refresh_token"
        private const val MODELS = "chatgpt_models"
        private const val PROFILE = "chatgpt_profile"
        private const val LIMIT_AT = "chatgpt_limit_at"
        private const val CLIENT_ID = "chatgpt_client_id"
        private const val HOST_ID = "chatgpt_host_id"
        private const val HOST_PREFIX = "urn:uuid:"
        private const val ACCESS = "chatgpt_access_token"
        private const val REFRESH = CONNECTED_KEY
        private const val EXPIRES = "chatgpt_access_expires_ms"
        private const val MARGIN_MS = 60_000L
        private const val DEFAULT_LIFETIME_S = 3600L
    }
}
