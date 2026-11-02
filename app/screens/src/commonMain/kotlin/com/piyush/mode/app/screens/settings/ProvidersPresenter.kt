package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.feature.settings.data.ClaudeAccount
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.OpenRouterAuth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One AI provider as the lists show it (names and words come from the UI's strings, by [id]). */
public data class ProviderRow(
    /** ModelProvider id: "chatgpt", "claude_oauth", "openrouter". */
    val id: String,
    val connected: Boolean,
    /** The one tasks use. */
    val inUse: Boolean,
    /** For OpenRouter: the key's last four characters. */
    val keyEnding: String? = null,
    /** Who is signed in, when the provider said: "p@example.com · Plus". */
    val account: String? = null,
)

public data class ProvidersUiState(
    /** ChatGPT first (the default), then Claude, then OpenRouter. */
    val rows: List<ProviderRow> = emptyList(),
    val active: String = ModelProvider.OpenRouter.id,
    val openRouterSigningIn: Boolean = false,
    val openRouterError: String? = null,
    val claude: ClaudeConnectionUiState = ClaudeConnectionUiState(),
) {
    val activeConnected: Boolean get() = rows.firstOrNull { it.id == active }?.connected == true
    val connectedCount: Int get() = rows.count { it.connected }
}

/**
 * The AI providers, for setup and Settings: several can be connected at once (each keeps its own
 * sign-in or key in the Keychain, and its own model choices); the one picked is what tasks use.
 */
public class ProvidersPresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val chatGpt: ChatGptAccount,
    private val openRouterAuth: OpenRouterAuth,
    claude: ClaudeAccount?,
    claudeLlm: LlmClient?,
) : Presenter<ProvidersUiState>(parent, ProvidersUiState()) {
    private val claudeConnection = claude?.let { ClaudeConnectionPresenter(scope, it, claudeLlm) { select(ModelProvider.ClaudeOAuth.id) } }
    private val openRouter = MutableStateFlow(Triple(false, null as String?, 0))
    private var verifier: String? = null
    private var useWhenConnected = true

    init {
        scope.launch {
            combine(
                settings.settings, chatGpt.connected, openRouter,
                claudeConnection?.state ?: MutableStateFlow(ClaudeConnectionUiState()),
            ) { s, _, (signingIn, error, _), claudeState ->
                val providers = listOfNotNull(
                    ModelProvider.ChatGpt, ModelProvider.ClaudeOAuth.takeIf { claudeConnection != null }, ModelProvider.OpenRouter,
                )
                ProvidersUiState(
                    rows = providers.map { p ->
                        ProviderRow(
                            p.id, settings.hasAccess(p), p == s.provider,
                            keyEnding = if (p == ModelProvider.OpenRouter) settings.apiKey?.takeIf { it.length > 8 }?.takeLast(4) else null,
                            account = if (p == ModelProvider.ChatGpt && settings.hasAccess(p)) chatGpt.account()?.let { a ->
                                listOfNotNull(a.email, a.plan).joinToString(" · ").ifEmpty { null }
                            } else null,
                        )
                    },
                    active = s.provider.id,
                    openRouterSigningIn = signingIn,
                    openRouterError = error,
                    claude = claudeState,
                )
            }.collect { mutableState.value = it }
        }
    }

    /** Tasks use [id] from now on (its own models come back with it). */
    public fun select(id: String) {
        settings.selectProvider(ModelProvider.fromId(id))
    }

    // OpenRouter

    /** Starts "Sign in with OpenRouter"; returns the page to open. */
    public fun startOpenRouter(callbackUrl: String, useWhenConnected: Boolean = true): String {
        this.useWhenConnected = useWhenConnected
        val start = openRouterAuth.start(callbackUrl)
        verifier = start.verifier
        openRouter.value = Triple(true, null, openRouter.value.third)
        return start.url
    }

    /** OpenRouter came back to [callbackUrl] with a code, or the page closed ([error], or null when cancelled). */
    public fun finishOpenRouter(callbackUrl: String?, error: String? = null) {
        val code = callbackUrl?.let(openRouterAuth::codeFrom)
        val v = verifier
        if (code == null || v == null) {
            openRouter.value = Triple(false, error, openRouter.value.third)
            return
        }
        scope.launch {
            try {
                connectOpenRouter(openRouterAuth.exchange(code, v))
                openRouter.value = Triple(false, null, openRouter.value.third + 1)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                openRouter.value = Triple(false, e.message, openRouter.value.third)
            }
        }
    }

    /** A pasted OpenRouter key; it is used from now on. */
    public fun saveOpenRouterKey(key: String, useWhenConnected: Boolean = true) {
        if (key.isBlank()) return
        this.useWhenConnected = useWhenConnected
        connectOpenRouter(key.trim())
    }

    private fun connectOpenRouter(key: String) {
        settings.apiKey = key
        if (useWhenConnected) select(ModelProvider.OpenRouter.id)
        openRouter.update { it.copy(third = it.third + 1) }
    }

    // Claude (its sign-in ends with a code the user pastes)

    public fun startClaude(): String? = claudeConnection?.start()
    public fun finishClaude(code: String) { claudeConnection?.finish(code) }
    public fun cancelClaude() { claudeConnection?.cancel() }

    /** Removes [id]'s sign-in or key. If it was in use, the next connected provider takes over. */
    public fun disconnect(id: String) {
        when (ModelProvider.fromId(id)) {
            ModelProvider.OpenRouter -> settings.apiKey = null
            ModelProvider.ChatGpt -> chatGpt.signOut()
            ModelProvider.ClaudeOAuth -> claudeConnection?.disconnect()
        }
        if (settings.settings.value.provider.id == id) {
            ModelProvider.entries.firstOrNull { it.id != id && settings.hasAccess(it) }?.let { select(it.id) }
        }
        openRouter.update { it.copy(third = it.third + 1) }
    }
}
