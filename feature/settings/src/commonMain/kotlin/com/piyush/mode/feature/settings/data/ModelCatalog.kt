package com.piyush.mode.feature.settings.data

import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.storage.FileStore
import com.piyush.mode.feature.settings.domain.CatalogSource
import com.piyush.mode.feature.settings.domain.CatalogState
import com.piyush.mode.feature.settings.domain.ModelOption
import com.piyush.mode.feature.settings.domain.ModelTag
import com.piyush.mode.llm.ModelInfo
import com.piyush.mode.llm.OpenRouterModels
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The models the user picks from: OpenRouter's live catalogue, kept to models
 * that take screenshots and call tools, with an estimated cost per task.
 * Cached for a day; with no network it falls back to the cache, then to a
 * built-in list, so the picker is never empty.
 */
public class ModelCatalog @OptIn(ExperimentalTime::class) constructor(
    private val fetch: suspend () -> String,
    private val files: FileStore,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {
    private val mutableState = MutableStateFlow(load())
    public val state: StateFlow<CatalogState> = mutableState.asStateFlow()

    /** Fetches the catalogue if the copy is over a day old (or [force]); keeps the current list on failure. */
    public suspend fun refresh(force: Boolean = false) {
        val current = state.value
        val fresh = current.source != CatalogSource.BuiltIn && current.fetchedAt?.let { now() - it < MAX_AGE_MS } == true
        if (fresh && !force) return
        try {
            val body = fetch()
            val built = build(OpenRouterModels.parse(body), CatalogSource.Live, now())
            if (built.all.isEmpty()) return
            files.write(CACHE, body.encodeToByteArray())
            files.write(CACHE_TIME, built.fetchedAt.toString().encodeToByteArray())
            mutableState.value = built
        } catch (e: Exception) {
            AppLog.error("models", "could not refresh the model list", e)
        }
    }

    /** Usable models matching [query] (see ModelSearch), the closest first. */
    public fun search(query: String, maker: String? = null): List<ModelOption> = ModelSearch.search(state.value.all, query, maker)

    /** The makers in the list ("anthropic", "openai"…), the one with the most models first. */
    public fun makers(): List<String> =
        state.value.all.groupingBy(ModelSearch::maker).eachCount().entries.filter { it.key.isNotEmpty() }
            .sortedByDescending { it.value }.map { it.key }

    private fun load(): CatalogState {
        val cached = files.read(CACHE)?.decodeToString()
        val time = files.read(CACHE_TIME)?.decodeToString()?.toLongOrNull()
        if (cached != null) {
            runCatching { build(OpenRouterModels.parse(cached), CatalogSource.Cached, time) }.getOrNull()
                ?.takeIf { it.all.isNotEmpty() }?.let { return it }
        }
        return CatalogState(BUILT_IN, BUILT_IN.sortedBy { it.costPerTaskUsd }, CatalogSource.BuiltIn, null)
    }

    private fun build(models: List<ModelInfo>, source: CatalogSource, fetchedAt: Long?): CatalogState {
        val usable = models.filter { it.takesImages && it.toolCalls }.map { info ->
            val pick = PICKS.firstOrNull { it.id == info.id }
            ModelOption(
                id = info.id,
                name = info.name.substringAfter(": ").ifBlank { info.id },
                costPerTaskUsd = costPerTask(info),
                tag = pick?.tag,
                note = pick?.note.orEmpty(),
            )
        }
        val byId = usable.associateBy { it.id }
        val recommended = PICKS.map { pick -> byId[pick.id] ?: pick }
        return CatalogState(recommended, usable.sortedBy { it.costPerTaskUsd }, source, fetchedAt)
    }

    public companion object {
        /** A typical task: about 10 steps of 3,500 tokens in and 70 out. */
        public fun costPerTask(info: ModelInfo): Double =
            STEPS_PER_TASK * (TOKENS_IN * info.promptUsdPerToken + TOKENS_OUT * info.completionUsdPerToken)

        private const val STEPS_PER_TASK = 10
        private const val TOKENS_IN = 3500
        private const val TOKENS_OUT = 70
        private const val MAX_AGE_MS = 24L * 60 * 60 * 1000
        private const val CACHE = "cache/models.json"
        private const val CACHE_TIME = "cache/models.fetched"

        /** The hand-picked models, with prices from 2026-11-01 for when no list has been fetched yet. */
        private val PICKS = listOf(
            ModelOption("anthropic/claude-haiku-5.5", "Claude Haiku 5.5", 0.0039, ModelTag.Recommended, "Reliable and fast"),
            ModelOption("qwen/qwen3.8-27b", "Qwen 3.8 27B", 0.0030, null, "Strong on phone screens"),
            ModelOption("qwen/qwen3.7-flash", "Qwen 3.7 Flash", 0.0011, ModelTag.Cheapest, "Simple tasks"),
            ModelOption("openai/gpt-5-nano", "GPT-5 nano", 0.0020, null, "Cheap all-rounder"),
            ModelOption("anthropic/claude-sonnet-5.5", "Claude Sonnet 5.5", 0.0770, ModelTag.Strongest, "For hard steps"),
        )
        private val BUILT_IN = PICKS
    }
}
