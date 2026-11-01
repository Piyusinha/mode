package com.piyush.mode.app.screens.memory

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.common.RelativeDay
import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.domain.MemoryItem
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.settings.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

public data class MemoryUiState(
    val learn: Boolean = true,
    val aboutYou: List<MemoryEntry> = emptyList(),
    val skills: List<MemoryEntry> = emptyList(),
    val tips: List<MemoryEntry> = emptyList(),
) {
    val isEmpty: Boolean get() = aboutYou.isEmpty() && skills.isEmpty() && tips.isEmpty()
}

public data class MemoryEntry(
    val id: String,
    val title: String,
    val text: String,
    /** The app a skill or tip belongs to. */
    val app: String?,
    val learned: RelativeDay,
    val uses: Int,
    val successes: Int,
)

/** Memory: what Mode has learned, by kind; delete one, forget everything, stop learning. */
public class MemoryPresenter(
    parent: CoroutineScope,
    private val memory: MemoryRepository,
    private val settings: SettingsRepository,
    private val clock: ScreenClock = ScreenClock(),
) : Presenter<MemoryUiState>(parent, MemoryUiState()) {

    init {
        scope.launch {
            combine(memory.observe(), settings.settings) { items, s ->
                fun of(kind: MemoryKind) = items.filter { it.kind == kind }.map { it.entry() }
                MemoryUiState(s.learnFromTasks, of(MemoryKind.Fact), of(MemoryKind.Skill), of(MemoryKind.Tip))
            }.collect { mutableState.value = it }
        }
    }

    public fun setLearn(on: Boolean) {
        settings.update { it.copy(learnFromTasks = on) }
    }

    public fun delete(id: String) {
        memory.forget(id)
    }

    public fun forgetEverything() {
        memory.forgetEverything()
    }

    private fun MemoryItem.entry() = MemoryEntry(id, title, text, app, clock.relativeDay(createdAt), uses, successes)
}
