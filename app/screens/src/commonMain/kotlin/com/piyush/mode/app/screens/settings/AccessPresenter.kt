package com.piyush.mode.app.screens.settings

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.settings.domain.ModeSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

public data class AccessUiState(
    /** Every task runs without asking; otherwise Mode asks before paying or sending. */
    val fullAccess: Boolean = false,
    /** Words that give one task full access. */
    val words: List<String> = emptyList(),
    val canAddWord: Boolean = true,
    /** The agent may read one-time codes (never kept). */
    val readCodes: Boolean = true,
    /** With full access, ask before paying more than this; null: never. */
    val askAbove: Double? = null,
)

/** How much Mode does on its own: full access or ask first, words for one task, codes, a spending line. */
public class AccessPresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
) : Presenter<AccessUiState>(parent, AccessUiState()) {
    init {
        scope.launch {
            settings.settings.collect { s ->
                mutableState.value = AccessUiState(
                    fullAccess = s.fullAccess,
                    words = s.triggerWords,
                    canAddWord = s.triggerWords.size < ModeSettings.MAX_TRIGGER_WORDS,
                    readCodes = s.readCodes,
                    askAbove = s.askAboveAmount,
                )
            }
        }
    }

    public fun setFullAccess(on: Boolean) { settings.update { it.copy(fullAccess = on) } }

    /** Adds [word] ("just do it"); blank or already there does nothing. */
    public fun addWord(word: String) {
        val w = word.trim().lowercase()
        if (w.isEmpty()) return
        settings.update { it.copy(triggerWords = it.triggerWords + w) }
    }

    public fun removeWord(word: String) { settings.update { it.copy(triggerWords = it.triggerWords - word) } }

    public fun setReadCodes(on: Boolean) { settings.update { it.copy(readCodes = on) } }

    /** Null (or 0) never asks. */
    public fun setAskAbove(amount: Double?) { settings.update { it.copy(askAboveAmount = amount) } }

    public companion object {
        /** The amounts offered for "Ask before paying more than". */
        public val AMOUNTS: List<Double> = listOf(500.0, 1_000.0, 2_000.0, 5_000.0, 10_000.0)
    }
}
