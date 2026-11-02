package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.feature.runs.data.RunRepository
import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.feature.settings.data.ClaudeAccount
import com.piyush.mode.feature.settings.data.ModelCatalog
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.ProviderModels
import com.piyush.mode.llm.ClaudeAccountApi
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.OpenRouterAccountApi
import com.piyush.mode.llm.UsageWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** One plan limit as shown: how much is used, and when it starts again. */
public data class UsageLimitRow(val session: Boolean, val usedFraction: Double, val resetsAt: Long?)

public data class ProviderDetailUiState(
    val id: String,
    val connected: Boolean = false,
    val inUse: Boolean = false,
    // Account
    val email: String? = null,
    val name: String? = null,
    val plan: String? = null,
    val keyEnding: String? = null,
    val keyLabel: String? = null,
    // The provider's own figures (where it gives them)
    val limits: List<UsageLimitRow> = emptyList(),
    val creditsLeft: Double? = null,
    val creditsTotal: Double? = null,
    /** ChatGPT: when its share for Mode last ran out (it gives no figures). */
    val limitReachedAt: Long? = null,
    /** True once the provider was asked (so "nothing" is an answer, not "still loading"). */
    val loaded: Boolean = false,
    // What Mode used with this provider, from its run history
    val todaySteps: Int = 0,
    val todayTasks: Int = 0,
    val weekSteps: Int = 0,
    val weekTasks: Int = 0,
    val todayCostUsd: Double = 0.0,
    val weekCostUsd: Double = 0.0,
    /** Steps on each day of this week, Monday first. */
    val daySteps: List<Int> = List(7) { 0 },
    /** Today, 0 for Monday. */
    val todayIndex: Int = 0,
    // Its models
    val phoneModel: String? = null,
    val phoneEffort: String? = null,
    val brainModel: String? = null,
    val brainEffort: String? = null,
    val maxCostPerTaskUsd: Double? = null,
)

/** One provider's page: who is signed in, how much of the plan or credit is used, its models, use or disconnect. */
public class ProviderDetailPresenter(
    parent: CoroutineScope,
    private val providerId: String,
    private val settings: SettingsRepository,
    private val chatGpt: ChatGptAccount,
    private val claude: ClaudeAccount?,
    private val claudeApi: ClaudeAccountApi,
    private val openRouterApi: OpenRouterAccountApi,
    private val runs: RunRepository,
    private val catalog: ModelCatalog,
    private val clock: ScreenClock,
) : Presenter<ProviderDetailUiState>(parent, ProviderDetailUiState(providerId)) {
    private val provider = ModelProvider.fromId(providerId)
    private val remote = MutableStateFlow(ProviderDetailUiState(providerId))

    init {
        scope.launch { settings.settings.combine(remote) { _, r -> local(r) }.collect { mutableState.value = it } }
        refresh()
    }

    /** Asks the provider again (account, limits, credits). */
    public fun refresh() {
        scope.launch {
            val r = ProviderDetailUiState(providerId, loaded = true)
            remote.value = runCatching {
                when (provider) {
                    ModelProvider.ChatGpt -> chatGpt.account().let { a ->
                        r.copy(email = a?.email, name = a?.name, plan = a?.plan, limitReachedAt = chatGpt.limitReachedAt())
                    }
                    ModelProvider.ClaudeOAuth -> {
                        val token = claude?.accessToken()
                        val profile = token?.let { claudeApi.profile(it) }
                        val windows = token?.let { claudeApi.usage(it) }.orEmpty()
                        r.copy(email = profile?.email, name = profile?.name, plan = profile?.plan,
                            limits = windows.map { UsageLimitRow(it.kind == UsageWindow.Kind.Session, it.usedFraction, it.resetsAtMillis) })
                    }
                    ModelProvider.OpenRouter -> settings.apiKey?.takeIf { it.isNotBlank() }?.let { key ->
                        openRouterApi.balance(key)?.let { b ->
                            r.copy(keyLabel = b.label, creditsLeft = b.creditsLeft, creditsTotal = b.creditsTotal ?: b.keyLimit)
                        }
                    } ?: r
                }
            }.getOrElse { r }
        }
    }

    /** Tasks use this provider from now on. */
    public fun use() { settings.selectProvider(provider) }

    /** Signs out or removes the key; if it was in use, the next connected provider takes over. */
    public fun disconnect() {
        when (provider) {
            ModelProvider.OpenRouter -> settings.apiKey = null
            ModelProvider.ChatGpt -> chatGpt.signOut()
            ModelProvider.ClaudeOAuth -> claude?.signOut()
        }
        if (settings.settings.value.provider == provider) {
            ModelProvider.entries.firstOrNull { it != provider && settings.hasAccess(it) }?.let(settings::selectProvider)
        }
        remote.value = ProviderDetailUiState(providerId, loaded = true)
    }

    @OptIn(ExperimentalTime::class)
    private fun local(r: ProviderDetailUiState): ProviderDetailUiState {
        val s = settings.settings.value
        val (main, strong) = settings.modelsFor(provider)
        val zone = clock.zone()
        val today = Instant.fromEpochMilliseconds(clock.now()).toLocalDateTime(zone).date
        val monday = today.minus(DatePeriod(days = today.dayOfWeek.isoDayNumber - DayOfWeek.MONDAY.isoDayNumber))
        val weekStart = monday.atStartOfDayIn(zone).toEpochMilliseconds()
        val todayStart = today.atStartOfDayIn(zone).toEpochMilliseconds()
        // Runs with this provider's models (a model id belongs to one provider only).
        val mine = runs.runs(500).filter { run ->
            run.startedAt >= weekStart && run.model?.let { ProviderModels.accepts(provider, it) } == true
        }
        val days = List(7) { i ->
            val start = monday.plus(DatePeriod(days = i)).atStartOfDayIn(zone).toEpochMilliseconds()
            val end = monday.plus(DatePeriod(days = i + 1)).atStartOfDayIn(zone).toEpochMilliseconds()
            mine.filter { it.startedAt in start until end }.sumOf { it.steps ?: 0 }
        }
        val todays = mine.filter { it.startedAt >= todayStart }
        return r.copy(
            connected = settings.hasAccess(provider),
            inUse = s.provider == provider,
            keyEnding = if (provider == ModelProvider.OpenRouter) settings.apiKey?.takeIf { it.length > 8 }?.takeLast(4) else null,
            todaySteps = todays.sumOf { it.steps ?: 0 }, todayTasks = todays.size,
            weekSteps = mine.sumOf { it.steps ?: 0 }, weekTasks = mine.size,
            todayCostUsd = todays.sumOf { it.costUsd ?: 0.0 }, weekCostUsd = mine.sumOf { it.costUsd ?: 0.0 },
            daySteps = days, todayIndex = today.dayOfWeek.isoDayNumber - 1,
            phoneModel = name(main), phoneEffort = ProviderModels.effortUsed(main),
            brainModel = strong?.let(::name), brainEffort = strong?.let(ProviderModels::effortUsed),
            maxCostPerTaskUsd = if (provider.reportsCurrencyCost) s.maxCostPerTaskUsd else null,
        )
    }

    private fun name(id: String): String {
        val base = ProviderModels.base(id)
        return when (provider) {
            ModelProvider.ChatGpt -> ProviderModels.displayName(base)
            ModelProvider.ClaudeOAuth -> ProviderModels.claude.firstOrNull { it.id == base }?.name ?: base
            ModelProvider.OpenRouter -> (catalog.state.value.recommended + catalog.state.value.all).firstOrNull { it.id == base }?.name ?: base.substringAfter('/')
        }
    }
}
