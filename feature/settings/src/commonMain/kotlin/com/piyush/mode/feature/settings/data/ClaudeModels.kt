package com.piyush.mode.feature.settings.data

import com.piyush.mode.core.common.AppLog
import com.piyush.mode.core.storage.KeyValueStore
import com.piyush.mode.llm.PlanModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The models the user's Claude account offers, as Anthropic last listed them (kept, so the list shows before
 * the next check). Nothing is trimmed: every model and every effort it takes is the user's to pick.
 */
public class ClaudeModels @OptIn(ExperimentalTime::class) constructor(
    private val prefs: KeyValueStore,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
    private val list: suspend () -> List<PlanModel>,
) {
    private val mutable = MutableStateFlow(
        prefs.getString(KEY)?.let { runCatching { Json.decodeFromString<List<PlanModel>>(it) }.getOrNull() }.orEmpty(),
    )
    public val known: StateFlow<List<PlanModel>> = mutable.asStateFlow()

    /** Asks Anthropic only when the list kept is missing or a day old: models change rarely. */
    public suspend fun refreshIfOld(): List<PlanModel> {
        val at = prefs.getString(AT_KEY)?.toLongOrNull()
        return if (known.value.isNotEmpty() && at != null && now() - at < MAX_AGE_MS) known.value else refresh()
    }

    /** Asks Anthropic again; the list kept stays if it fails. */
    public suspend fun refresh(): List<PlanModel> = list().also { found ->
        // Whether Anthropic marks a model as billed apart from the plan: its other fields, by model.
        found.filter { it.otherFields.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let { withMore ->
            AppLog.log("models", "Claude model fields: " + withMore.joinToString("; ") { "${it.id}: ${it.otherFields.joinToString(", ")}" }.take(2000))
        }
        if (found.isNotEmpty()) {
            prefs.putString(KEY, Json.encodeToString(found))
            prefs.putString(AT_KEY, now().toString())
            mutable.value = found
        }
    }

    private companion object {
        const val KEY = "claude_models"
        const val AT_KEY = "claude_models_at"
        const val MAX_AGE_MS = 24 * 60 * 60 * 1000L
    }
}
