package com.piyush.mode.feature.settings.data

import com.piyush.mode.core.storage.SecretStore
import com.piyush.mode.llm.ChatGptAuth
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
) {
    private val mutableConnected = MutableStateFlow(secrets.get(REFRESH) != null)
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
        mutableConnected.value = true
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
    public suspend fun models(): List<String> = auth.models(accessToken() ?: throw LlmFailed(ResponsesClient.SIGN_IN_AGAIN))

    public fun signOut() {
        listOf(ACCESS, REFRESH, EXPIRES).forEach { secrets.set(it, null) }
        mutableConnected.value = false
    }

    private fun keep(tokens: ChatGptAuth.Tokens) {
        secrets.set(ACCESS, tokens.accessToken)
        tokens.refreshToken?.let { secrets.set(REFRESH, it) }
        secrets.set(EXPIRES, (now() + (tokens.expiresIn ?: DEFAULT_LIFETIME_S) * 1000).toString())
    }

    @OptIn(ExperimentalUuidApi::class)
    private fun hostId(): String = secrets.get(HOST_ID) ?: Uuid.random().toHexString().also { secrets.set(HOST_ID, it) }

    private companion object {
        const val CLIENT_ID = "chatgpt_client_id"
        const val HOST_ID = "chatgpt_host_id"
        const val ACCESS = "chatgpt_access_token"
        const val REFRESH = "chatgpt_refresh_token"
        const val EXPIRES = "chatgpt_access_expires_ms"
        const val MARGIN_MS = 60_000L
        const val DEFAULT_LIFETIME_S = 3600L
    }
}
