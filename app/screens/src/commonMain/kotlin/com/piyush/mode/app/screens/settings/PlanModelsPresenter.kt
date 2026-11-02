package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.feature.settings.data.ModelBench
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.ModelOption
import com.piyush.mode.feature.settings.domain.ModelTag
import com.piyush.mode.feature.settings.domain.ProviderModels
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What a model is for: tapping and typing every step, or planning and deciding when stuck. */
public enum class ModelRole { PhoneControl, Brain }

/** What a model stands out for, from this iPhone's measurements (or the provider's picks before any). */
public enum class PlanTag { BestForPhoneControl, BestForBrain, Fastest, MostAccurate, Recommended, Strongest }

public data class EffortRow(val effort: String, val millis: Long?, val correct: Int?, val total: Int?, val selected: Boolean)

public data class PlanModelRow(
    /** The model, without its effort. */
    val id: String,
    val name: String,
    val tag: PlanTag?,
    val selected: Boolean,
    /** The effort used for this role when it is the selected model. */
    val effort: String?,
    /** At the effort shown (the selected one, or low): null when not measured. */
    val millis: Long?,
    val correct: Int?,
    val total: Int?,
    val efforts: List<EffortRow>,
)

public data class PlanModelsUiState(
    val provider: String = ModelProvider.ChatGpt.id,
    val plan: String? = null,
    val role: ModelRole = ModelRole.PhoneControl,
    val models: List<PlanModelRow> = emptyList(),
    val supportsEffort: Boolean = false,
    /** While measuring: done and total model-effort pairs. */
    val measuring: Pair<Int, Int>? = null,
    val error: String? = null,
)

/**
 * Models on a plan (ChatGPT, Claude): every model the plan offers, for phone control and for the brain,
 * each with its effort, and "Measure on this iPhone" for real speed and accuracy. No prices: the plan pays.
 */
public class PlanModelsPresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val chatGpt: ChatGptAccount,
    private val bench: ModelBench,
    private val llm: LlmClient,
) : Presenter<PlanModelsUiState>(parent, PlanModelsUiState()) {
    private val role = MutableStateFlow(ModelRole.PhoneControl)
    private val progress = MutableStateFlow<Pair<Int, Int>?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val measured = MutableStateFlow(0)

    init {
        // The plan's own list, fresh (it can change when OpenAI adds a model).
        if (settings.settings.value.provider == ModelProvider.ChatGpt) scope.launch { runCatching { chatGpt.models() } }
        scope.launch {
            combine(settings.settings, chatGpt.knownModels, role, progress, combine(error, measured) { e, _ -> e }) { s, known, r, p, e ->
                build(s.provider, known, r, p, e)
            }.collect { mutableState.value = it }
        }
    }

    public fun role(role: ModelRole) { this.role.value = role }

    /** Picks [id] for the role shown, keeping the effort it had (or the role's usual one). */
    public fun choose(id: String) {
        val provider = settings.settings.value.provider
        val effort = if (ProviderModels.supportsEffort(provider)) currentEffort() ?: defaultEffort(role.value) else null
        set(ProviderModels.withEffort(id, effort))
    }

    public fun effort(effort: String) {
        val current = current() ?: return
        set(ProviderModels.withEffort(current, effort))
    }

    /**
     * Measures each model at the role's usual effort, and the selected one at every effort, with [probes]
     * (images the app makes). One pair at a time; the screen updates as each finishes.
     */
    public fun measure(probes: List<ModelBench.Probe>) {
        if (progress.value != null || probes.isEmpty()) return
        val provider = settings.settings.value.provider
        val client = llm.forProvider(provider)
        val models = options(provider).map { it.id }
        val selected = current()?.let(ProviderModels::base)
        val pairs = buildList {
            val usual = if (ProviderModels.supportsEffort(provider)) defaultEffort(role.value) else null
            models.forEach { add(ProviderModels.withEffort(it, usual)) }
            if (selected != null && ProviderModels.supportsEffort(provider)) {
                ProviderModels.EFFORTS.filter { it != usual }.forEach { add(ProviderModels.withEffort(selected, it)) }
            }
        }
        error.value = null
        scope.launch {
            try {
                pairs.forEachIndexed { i, model ->
                    progress.value = i to pairs.size
                    bench.measure(provider.id, client, model, probes)
                    measured.update { it + 1 }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message
            } finally {
                progress.value = null
            }
        }
    }

    private fun current(): String? = settings.settings.value.let { if (role.value == ModelRole.PhoneControl) it.model else it.strongModel }

    private fun currentEffort(): String? = current()?.let(ProviderModels::effort)

    private fun set(model: String) {
        settings.update { if (role.value == ModelRole.PhoneControl) it.copy(model = model) else it.copy(strongModel = model) }
    }

    private fun options(provider: ModelProvider): List<ModelOption> = when (provider) {
        ModelProvider.ChatGpt -> ProviderModels.chatGpt(chatGpt.knownModels.value)
        ModelProvider.ClaudeOAuth -> ProviderModels.claude
        ModelProvider.OpenRouter -> emptyList()
    }

    private fun build(provider: ModelProvider, known: List<String>, r: ModelRole, p: Pair<Int, Int>?, e: String?): PlanModelsUiState {
        val s = settings.settings.value
        val chosen = if (r == ModelRole.PhoneControl) s.model else s.strongModel
        val results = bench.results(provider.id)
        val supportsEffort = ProviderModels.supportsEffort(provider)
        val usual = if (supportsEffort) defaultEffort(r) else null
        val options = if (provider == ModelProvider.ChatGpt) ProviderModels.chatGpt(known) else options(provider)
        val tags = tags(options, results, usual, r)
        return PlanModelsUiState(
            provider = provider.id,
            plan = if (provider == ModelProvider.ChatGpt) chatGpt.account()?.plan else null,
            role = r,
            supportsEffort = supportsEffort,
            measuring = p,
            error = e,
            models = options.map { o ->
                val selected = chosen != null && ProviderModels.base(chosen) == o.id
                val effort = if (selected) chosen?.let(ProviderModels::effort) ?: usual else null
                val shown = results[ProviderModels.withEffort(o.id, effort ?: usual)]
                PlanModelRow(
                    id = o.id, name = o.name, tag = tags[o.id], selected = selected, effort = effort,
                    millis = shown?.millis, correct = shown?.correct, total = shown?.total,
                    efforts = if (!supportsEffort) emptyList() else ProviderModels.EFFORTS.map { ef ->
                        val m = results[ProviderModels.withEffort(o.id, ef)]
                        EffortRow(ef, m?.millis, m?.correct, m?.total, selected = ef == effort)
                    },
                )
            },
        )
    }

    private companion object {
        /** Phone control acts every step: low effort. The brain decides: high. */
        fun defaultEffort(role: ModelRole) = if (role == ModelRole.PhoneControl) "low" else "high"

        /**
         * From measurements at the role's usual effort: the fastest (of those right most of the time), the most
         * accurate (then faster), and the best for the role (phone control: accuracy minus a tenth per second;
         * brain: the most accurate). Before any, the provider's own picks.
         */
        fun tags(options: List<ModelOption>, results: Map<String, ModelBench.Result>, usual: String?, role: ModelRole): Map<String, PlanTag> {
            val measured = options.mapNotNull { o -> results[ProviderModels.withEffort(o.id, usual)]?.let { o.id to it } }
                .filter { it.second.total > 0 }
            if (measured.size < 2) {
                return options.mapNotNull { o ->
                    when (o.tag) { ModelTag.Recommended -> o.id to PlanTag.Recommended; ModelTag.Strongest -> o.id to PlanTag.Strongest; else -> null }
                }.toMap()
            }
            fun accuracy(r: ModelBench.Result) = r.correct.toDouble() / r.total
            val accurate = measured.sortedWith(compareByDescending<Pair<String, ModelBench.Result>> { accuracy(it.second) }.thenBy { it.second.millis }).first().first
            val fastest = measured.filter { accuracy(it.second) >= 0.6 }.minByOrNull { it.second.millis }?.first
            val best = if (role == ModelRole.Brain) accurate
                else measured.maxByOrNull { accuracy(it.second) - it.second.millis / 10_000.0 }!!.first
            val tags = mutableMapOf<String, PlanTag>()
            fastest?.let { tags[it] = PlanTag.Fastest }
            tags[accurate] = PlanTag.MostAccurate
            tags[best] = if (role == ModelRole.Brain) PlanTag.BestForBrain else PlanTag.BestForPhoneControl
            return tags
        }
    }
}
