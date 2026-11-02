package com.piyush.mode.app.screens.settings

import com.piyush.mode.feature.settings.data.ModelSearch

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.data.ModelCatalog
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.CatalogSource
import com.piyush.mode.feature.settings.domain.ModeSettings
import com.piyush.mode.feature.settings.domain.ModelOption
import com.piyush.mode.feature.settings.domain.ModelTag
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.piyush.mode.feature.settings.data.ClaudeAccount
import com.piyush.mode.feature.settings.domain.ProviderModels
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.LlmClient

public data class SettingsUiState(
    val name: String = "",
    val hasKey: Boolean = false,
    /** The key's last four characters, to recognise it. */
    val keyEnding: String? = null,
    val model: ModelChoice? = null,
    /** Null: never step up to a stronger model. */
    val stuckModel: ModelChoice? = null,
    val picks: List<ModelChoice> = emptyList(),
    val query: String = "",
    val results: List<ModelChoice> = emptyList(),
    val catalogSource: CatalogSource = CatalogSource.BuiltIn,
    val maxCostPerTaskUsd: Double = ModeSettings.DEFAULT_MAX_COST_USD,
    val dailyCapUsd: Double? = null,
    val learn: Boolean = true,
    val useClaude: Boolean = false,
    val claudeConnected: Boolean = false,
    val claudeSigningIn: Boolean = false,
    val claudeAwaitingCode: Boolean = false,
    val claudeError: String? = null,
    val claudeChecking: Boolean = false,
    val claudeCheck: String? = null,
    /** The provider in use (ModelProvider id). */
    val provider: String = ModelProvider.OpenRouter.id,
    /** Cost limits apply: the provider bills per task (OpenRouter), not a plan. */
    val showsCost: Boolean = true,
    /** Makers to filter the full list by ("anthropic", "openai"…); OpenRouter only. */
    val makers: List<String> = emptyList(),
    val maker: String? = null,
    /** How many models there are to search. */
    val modelCount: Int = 0,
    /** Every task runs without asking (Settings → Access). */
    val fullAccess: Boolean = false,
    /** The effort picked for each role ("low"…), where the provider takes one. */
    val modelEffort: String? = null,
    val stuckEffort: String? = null,
)

public data class ModelChoice(
    val id: String,
    val name: String,
    val costPerTaskUsd: Double,
    val tag: ModelTag?,
    val note: String,
    val isMain: Boolean,
    val isStuck: Boolean,
)

/** Settings: name, key, models (picked from the live list, never typed), limits. */
public class SettingsPresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val catalog: ModelCatalog,
    private val claude: ClaudeAccount? = null,
    private val claudeLlm: LlmClient? = null,
    /** The models on the user's ChatGPT plan, as last asked. */
    /** The models the user's Claude account offers, as last listed. */
    private val claudeModels: () -> List<com.piyush.mode.llm.PlanModel> = { emptyList() },
    private val chatGptModels: () -> List<String> = { emptyList() },
) : Presenter<SettingsUiState>(parent, SettingsUiState()) {
    private val connection = claude?.let { account ->
        ClaudeConnectionPresenter(scope, account, claudeLlm) { settings.selectProvider(ModelProvider.ClaudeOAuth) }
    }
    private val connectionState = connection?.state ?: MutableStateFlow(ClaudeConnectionUiState())
    private val query = MutableStateFlow("" to (null as String?))
    /** The key lives in the Keychain, outside the settings flow: bumped when it changes. */
    private val keyChanges = MutableStateFlow(0)

    init {
        scope.launch { catalog.refresh() }
        scope.launch {
            combine(settings.settings, catalog.state, query, keyChanges, connectionState) { s, models, (q, maker), _, auth ->
                val recommended = when (s.provider) {
                    ModelProvider.ClaudeOAuth -> ProviderModels.claude(claudeModels())
                    ModelProvider.ChatGpt -> ProviderModels.chatGpt(chatGptModels())
                    ModelProvider.OpenRouter -> models.recommended
                }
                val all = if (s.provider == ModelProvider.OpenRouter) models.all else recommended
                val known = (recommended + all).associateBy { it.id }
                // A plan model carries its effort ("gpt-6.1-terra@low"): matched and named without it.
                val main = s.model.let(ProviderModels::base)
                val strong = s.strongModel?.let(ProviderModels::base)
                fun choice(o: ModelOption) = ModelChoice(o.id, o.name, o.costPerTaskUsd, o.tag, o.note, o.id == main, o.id == strong)
                fun byId(id: String?) = id?.let(ProviderModels::base)?.let { b ->
                    known[b]?.let(::choice) ?: ModelChoice(b, if (s.provider == ModelProvider.ChatGpt) ProviderModels.displayName(b) else b.substringAfter('/'), 0.0, null, "", b == main, b == strong)
                }
                SettingsUiState(
                    name = s.userName.orEmpty(),
                    hasKey = s.hasApiKey,
                    keyEnding = settings.apiKey?.takeIf { it.length > 8 }?.takeLast(4),
                    model = byId(s.model),
                    stuckModel = byId(s.strongModel),
                    picks = recommended.map(::choice),
                    query = q,
                    results = if (q.isBlank() && maker == null) emptyList()
                        else (if (s.provider == ModelProvider.OpenRouter) catalog.search(q, maker?.ifEmpty { null }) else ModelSearch.search(all, q))
                            .take(MAX_RESULTS).map(::choice),
                    modelCount = all.size,
                    modelEffort = ProviderModels.effortUsed(s.model),
                    fullAccess = s.fullAccess,
                    stuckEffort = s.strongModel?.let(ProviderModels::effortUsed),
                    makers = if (s.provider == ModelProvider.OpenRouter) catalog.makers().take(MAX_MAKERS) else emptyList(),
                    maker = maker,
                    catalogSource = models.source,
                    maxCostPerTaskUsd = s.maxCostPerTaskUsd,
                    dailyCapUsd = s.dailyCapUsd,
                    learn = s.learnFromTasks,
                    useClaude = (s.provider == ModelProvider.ClaudeOAuth),
                    provider = s.provider.id,
                    showsCost = s.provider.reportsCurrencyCost,
                    claudeConnected = s.hasClaudeAccount,
                    claudeSigningIn = auth.signingIn,
                    claudeAwaitingCode = auth.awaitingCode,
                    claudeError = auth.error,
                    claudeChecking = auth.checking,
                    claudeCheck = auth.check,
                )
            }.collect { mutableState.value = it }
        }
    }

    public fun setName(name: String): Unit = settings.update { it.copy(userName = name) }

    /** UI adapter only; account lifecycle and connection checks have their own presenter. */
    public fun useClaude(on: Boolean): Unit = settings.selectProvider(if (on) ModelProvider.ClaudeOAuth else ModelProvider.OpenRouter)
    public fun startClaudeSignIn(): String? = connection?.start()
    public fun finishClaudeSignIn(code: String) { connection?.finish(code) }
    public fun cancelClaudeSignIn() { connection?.cancel() }
    public fun disconnectClaude() { connection?.disconnect() }
    public fun checkClaude(image: ByteArray) { connection?.check(image) }

    /** Saves the OpenRouter key in the Keychain; blank removes it. */
    public fun setKey(key: String) {
        settings.apiKey = key
        keyChanges.value++
    }

    public fun chooseModel(id: String) {
        if (!ProviderModels.accepts(settings.settings.value.provider, id)) return
        settings.update { it.copy(model = id) }
    }

    /** Null: never step up. */
    public fun chooseStuckModel(id: String?) {
        if (id != null && !ProviderModels.accepts(settings.settings.value.provider, id)) return
        settings.update { it.copy(strongModel = id) }
    }

    public fun search(text: String) {
        query.value = text to query.value.second
    }

    /** Shows one maker's models; "" lists every model; null goes back to the picks. */
    public fun filterMaker(maker: String?) {
        query.value = query.value.first to maker
    }

    /** Values are brought into range by the settings (0.005 to 5 dollars). */
    public fun setMaxCostPerTask(usd: Double): Unit = settings.update { it.copy(maxCostPerTaskUsd = usd) }

    /** Null switches the daily cap off. */
    public fun setDailyCap(usd: Double?): Unit = settings.update { it.copy(dailyCapUsd = usd) }

    public fun setLearn(on: Boolean): Unit = settings.update { it.copy(learnFromTasks = on) }

    private companion object {
        const val MAX_RESULTS = 40
        const val MAX_MAKERS = 6
    }
}
