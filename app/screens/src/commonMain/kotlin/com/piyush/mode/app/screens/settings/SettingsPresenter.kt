package com.piyush.mode.app.screens.settings

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
) : Presenter<SettingsUiState>(parent, SettingsUiState()) {
    private val query = MutableStateFlow("")
    /** The key lives in the Keychain, outside the settings flow: bumped when it changes. */
    private val keyChanges = MutableStateFlow(0)

    init {
        scope.launch { catalog.refresh() }
        scope.launch {
            combine(settings.settings, catalog.state, query, keyChanges) { s, models, q, _ ->
                val known = (models.recommended + models.all).associateBy { it.id }
                fun choice(o: ModelOption) = ModelChoice(o.id, o.name, o.costPerTaskUsd, o.tag, o.note, o.id == s.model, o.id == s.strongModel)
                fun byId(id: String?) = id?.let { known[it]?.let(::choice) ?: ModelChoice(it, it.substringAfter('/'), 0.0, null, "", it == s.model, it == s.strongModel) }
                SettingsUiState(
                    name = s.userName.orEmpty(),
                    hasKey = s.hasApiKey,
                    keyEnding = settings.apiKey?.takeIf { it.length > 8 }?.takeLast(4),
                    model = byId(s.model),
                    stuckModel = byId(s.strongModel),
                    picks = models.recommended.map(::choice),
                    query = q,
                    results = if (q.isBlank()) emptyList() else catalog.search(q).take(MAX_RESULTS).map(::choice),
                    catalogSource = models.source,
                    maxCostPerTaskUsd = s.maxCostPerTaskUsd,
                    dailyCapUsd = s.dailyCapUsd,
                    learn = s.learnFromTasks,
                )
            }.collect { mutableState.value = it }
        }
    }

    public fun setName(name: String): Unit = settings.update { it.copy(userName = name) }

    /** Saves the OpenRouter key in the Keychain; blank removes it. */
    public fun setKey(key: String) {
        settings.apiKey = key
        keyChanges.value++
    }

    public fun chooseModel(id: String): Unit = settings.update { it.copy(model = id) }

    /** Null: never step up. */
    public fun chooseStuckModel(id: String?): Unit = settings.update { it.copy(strongModel = id) }

    public fun search(text: String) {
        query.value = text
    }

    /** Values are brought into range by the settings (0.005 to 5 dollars). */
    public fun setMaxCostPerTask(usd: Double): Unit = settings.update { it.copy(maxCostPerTaskUsd = usd) }

    /** Null switches the daily cap off. */
    public fun setDailyCap(usd: Double?): Unit = settings.update { it.copy(dailyCapUsd = usd) }

    public fun setLearn(on: Boolean): Unit = settings.update { it.copy(learnFromTasks = on) }

    private companion object {
        const val MAX_RESULTS = 20
    }
}
