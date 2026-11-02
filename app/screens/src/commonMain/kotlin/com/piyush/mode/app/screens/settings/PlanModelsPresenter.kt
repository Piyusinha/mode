package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.data.BrainProbe
import com.piyush.mode.feature.settings.data.ChatGptAccount
import com.piyush.mode.feature.settings.data.ClaudeModels
import com.piyush.mode.feature.settings.data.ModelBench
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.ModelOption
import com.piyush.mode.feature.settings.domain.ProviderModels
import com.piyush.mode.llm.LlmClient
import com.piyush.mode.llm.ModelProvider
import com.piyush.mode.llm.PlanModel
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

public data class EffortRow(
    val effort: String, val millis: Long?, val correct: Int?, val total: Int?, val selected: Boolean,
    /** Why it could not be measured at this effort (every call failed), if so. */
    val error: String? = null,
)

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
    /** Billed to the account's API credits, not the plan: not measured unless chosen. */
    val usesApiCredits: Boolean = false,
    /** Why the model could not be measured at the effort shown (every call failed: not on the plan, refused). */
    val error: String? = null,
    /** An extra allowance (Luna Reserve), not a model: never measured or compared. */
    val reserve: Boolean = false,
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
    /** The Claude account's own model list (null: Mode's two defaults). */
    private val claudeModels: ClaudeModels? = null,
) : Presenter<PlanModelsUiState>(parent, PlanModelsUiState()) {
    private val role = MutableStateFlow(ModelRole.PhoneControl)
    private val progress = MutableStateFlow<Pair<Int, Int>?>(null)
    private val error = MutableStateFlow<String?>(null)
    private val measured = MutableStateFlow(0)

    init {
        // The plan's own list, fresh (it changes when OpenAI or Anthropic adds a model).
        when (settings.settings.value.provider) {
            ModelProvider.ChatGpt -> scope.launch { runCatching { chatGpt.models() } }
            ModelProvider.ClaudeOAuth -> claudeModels?.let { c -> scope.launch { runCatching { c.refreshIfOld() } } }
            ModelProvider.OpenRouter -> Unit
        }
        val lists = combine(chatGpt.knownModels, chatGpt.modelInfo, claudeModels?.known ?: MutableStateFlow(emptyList())) { _, _, _ -> Unit }
        scope.launch {
            combine(settings.settings, lists, role, progress, combine(error, measured) { e, _ -> e }) { s, _, r, p, e ->
                build(s.provider, r, p, e)
            }.collect { mutableState.value = it }
        }
    }

    public fun role(role: ModelRole) { this.role.value = role }

    /**
     * Picks [id] for the role shown, keeping the effort it had if the new model takes it, else the role's
     * usual one, else the nearest the model takes. A model that takes none gets none.
     */
    public fun choose(id: String) {
        val provider = settings.settings.value.provider
        val option = options(provider).firstOrNull { it.id == id } ?: return
        // Luna Reserve is an allowance mentioned in the list, not a model to pick or compare.
        if (option.reserve) return
        val takes = ProviderModels.efforts(provider, option)
        val effort = listOfNotNull(currentEffort(), defaultEffort(role.value)).firstOrNull { it in takes } ?: nearest(defaultEffort(role.value), takes)
        set(ProviderModels.withEffort(id, effort))
    }

    public fun effort(effort: String) {
        val current = current() ?: return
        set(ProviderModels.withEffort(current, effort))
    }

    /**
     * Measures every model at every effort it takes, with the role's own test: phone control reads [probes]
     * (images the app makes); the brain plans and decides ([BrainProbe.ALL], text only). A model on API
     * credits only when chosen, at its chosen effort. One pair at a time; the screen updates as each finishes.
     */
    public fun measure(probes: List<ModelBench.Probe>) {
        val brain = role.value == ModelRole.Brain
        if (progress.value != null || (!brain && probes.isEmpty())) return
        val provider = settings.settings.value.provider
        val client = llm.forProvider(provider)
        val options = options(provider)
        val selected = current()?.let(ProviderModels::base)
        val pairs = buildList {
            // The selected model first, so its numbers come soonest; then every other one, each at every effort.
            // A model on API credits costs money to measure: only when chosen, and only at its chosen effort.
            options.sortedBy { if (it.id == selected) 0 else 1 }.forEach { o ->
                when {
                    o.reserve -> Unit
                    o.usesApiCredits -> if (o.id == selected) add(current()!!)
                    else -> ProviderModels.efforts(provider, o).ifEmpty { listOf(null) }.forEach { add(ProviderModels.withEffort(o.id, it)) }
                }
            }
        }.distinct()
        error.value = null
        scope.launch {
            try {
                pairs.forEachIndexed { i, model ->
                    progress.value = i to pairs.size
                    if (brain) bench.measureBrain(provider.id, client, model) else bench.measure(provider.id, client, model, probes)
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

    private fun currentEffort(): String? = current()?.let(ProviderModels::effortUsed)

    private fun set(model: String) {
        settings.update { if (role.value == ModelRole.PhoneControl) it.copy(model = model) else it.copy(strongModel = model) }
    }

    /** The provider's models worth listing, and always the ones in use (never hidden from the user). */
    private fun options(provider: ModelProvider): List<ModelOption> {
        val inUse = settings.settings.value.let { s -> listOfNotNull(s.model, s.strongModel).map(ProviderModels::base).toSet() }
        return when (provider) {
            ModelProvider.ChatGpt -> ProviderModels.chatGpt(chatGpt.knownModels.value, chatGpt.modelInfo.value, inUse)
            ModelProvider.ClaudeOAuth -> ProviderModels.claude(claudeModels?.known?.value.orEmpty(), inUse)
            ModelProvider.OpenRouter -> emptyList()
        }
    }

    private fun build(provider: ModelProvider, r: ModelRole, p: Pair<Int, Int>?, e: String?): PlanModelsUiState {
        val s = settings.settings.value
        val chosen = if (r == ModelRole.PhoneControl) s.model else s.strongModel
        val results = bench.results(provider.id, brain = r == ModelRole.Brain)
        val supportsEffort = ProviderModels.supportsEffort(provider)
        val options = options(provider)
        val tags = tags(provider, options, results, r)
        return PlanModelsUiState(
            provider = provider.id,
            plan = if (provider == ModelProvider.ChatGpt) chatGpt.account()?.plan else null,
            role = r,
            supportsEffort = supportsEffort,
            measuring = p,
            error = e,
            models = options.map { o ->
                val selected = chosen != null && ProviderModels.base(chosen) == o.id
                val usual = shownEffort(provider, o, r)
                val effort = if (selected) chosen?.let(ProviderModels::effortUsed) ?: usual else null
                val shown = results[ProviderModels.withEffort(o.id, effort ?: usual)]
                PlanModelRow(
                    id = o.id, name = o.name, tag = tags[o.id], selected = selected, effort = effort,
                    millis = shown?.millis.takeIf { shown?.error == null }, correct = shown?.correct, total = shown?.total,
                    efforts = if (o.reserve) emptyList() else ProviderModels.efforts(provider, o).map { ef ->
                        val m = results[ProviderModels.withEffort(o.id, ef)]
                        EffortRow(ef, m?.millis.takeIf { m?.error == null }, m?.correct, m?.total, selected = ef == effort, error = m?.error)
                    },
                    usesApiCredits = o.usesApiCredits,
                    error = shown?.error,
                    reserve = o.reserve,
                )
            },
        )
    }

    /** The effort [o] is shown and measured at for [r]: the role's usual one, or the nearest it takes. */
    private fun shownEffort(provider: ModelProvider, o: ModelOption, r: ModelRole): String? =
        ProviderModels.efforts(provider, o).let { takes -> defaultEffort(r).takeIf { it in takes } ?: nearest(defaultEffort(r), takes) }

    private companion object {
        /** Phone control acts every step: low effort. The brain decides: high. */
        fun defaultEffort(role: ModelRole) = if (role == ModelRole.PhoneControl) "low" else "high"

        /** Of [takes], the effort closest to [wanted] on the ladder (none when the model takes none). */
        fun nearest(wanted: String, takes: List<String>): String? {
            val at = PlanModel.LADDER.indexOf(wanted)
            return takes.minByOrNull { kotlin.math.abs(PlanModel.LADDER.indexOf(it).let { i -> if (i < 0) PlanModel.LADDER.size else i } - at) }
        }

        /**
         * From measurements at each model's shown effort: the fastest (of those right most of the time), the
         * most accurate (then faster), and the best for the role (phone control: accuracy minus a tenth per
         * second; brain: the most accurate). Before any, Mode's default for the role is marked.
         */
        fun tags(provider: ModelProvider, options: List<ModelOption>, results: Map<String, ModelBench.Result>, role: ModelRole): Map<String, PlanTag> {
            val measured = options.filterNot { it.reserve }.mapNotNull { o ->
                ProviderModels.efforts(provider, o).let { takes -> defaultEffort(role).takeIf { it in takes } ?: nearest(defaultEffort(role), takes) }
                    .let { results[ProviderModels.withEffort(o.id, it)] }?.let { o.id to it }
            }.filter { it.second.total > 0 && it.second.error == null }
            if (measured.size < 2) {
                val pick = ProviderModels.base(if (role == ModelRole.Brain) ProviderModels.strong(provider) else ProviderModels.main(provider))
                return options.filter { it.id == pick }.associate { it.id to PlanTag.Recommended }
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
