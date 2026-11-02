package com.piyush.mode.feature.settings.data

import com.piyush.mode.core.storage.KeyValueStore
import com.piyush.mode.llm.PlanModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/**
 * The models the user's Claude account offers, as Anthropic last listed them (kept, so the list shows before
 * the next check). Nothing is trimmed: every model and every effort it takes is the user's to pick.
 */
public class ClaudeModels(
    private val prefs: KeyValueStore,
    private val list: suspend () -> List<PlanModel>,
) {
    private val mutable = MutableStateFlow(
        prefs.getString(KEY)?.let { runCatching { Json.decodeFromString<List<PlanModel>>(it) }.getOrNull() }.orEmpty(),
    )
    public val known: StateFlow<List<PlanModel>> = mutable.asStateFlow()

    /** Asks Anthropic again; the list kept stays if it fails. */
    public suspend fun refresh(): List<PlanModel> = list().also { found ->
        if (found.isNotEmpty()) {
            prefs.putString(KEY, Json.encodeToString(found))
            mutable.value = found
        }
    }

    private companion object {
        const val KEY = "claude_models"
    }
}
