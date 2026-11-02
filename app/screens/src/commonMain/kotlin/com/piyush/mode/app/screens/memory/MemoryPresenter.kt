package com.piyush.mode.app.screens.memory

import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.common.RelativeDay
import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.feature.memory.data.MemoryApps
import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.domain.MemoryItem
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.settings.data.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

public data class MemoryUiState(
    val learn: Boolean = true,
    /** Facts about the user: people, places, preferences. */
    val aboutYou: List<MemoryEntry> = emptyList(),
    /** Routines that worked, by the app they run in. */
    val routines: List<MemoryAppGroup> = emptyList(),
    /** Apps with tips, the one with the most first. */
    val apps: List<MemoryAppGroup> = emptyList(),
) {
    val routineCount: Int get() = routines.sumOf { it.routines.size }
    val tipCount: Int get() = apps.sumOf { it.tips.size }
    val isEmpty: Boolean get() = aboutYou.isEmpty() && routines.isEmpty() && apps.isEmpty()

    /** One app's group, for its own screen. */
    public fun app(name: String): MemoryAppGroup? = apps.firstOrNull { it.name == name } ?: routines.firstOrNull { it.name == name }
}

/** What Mode learned in one app. */
public data class MemoryAppGroup(
    val name: String,
    val tips: List<MemoryEntry>,
    val routines: List<MemoryEntry>,
)

public data class MemoryEntry(
    val id: String,
    /** A short title; for a tip shown under its app, without the app's name in front. */
    val title: String,
    /** The full text, as Mode keeps it (a routine's numbered steps included): what the editor shows. */
    val text: String,
    /** A routine's steps, without their numbers; empty for facts and tips. */
    val steps: List<String>,
    /** What the text says besides the steps (a routine's "If it goes wrong: …"); the text for facts and tips. */
    val body: String,
    /** The app a routine or tip belongs to. */
    val app: String?,
    val learned: RelativeDay,
    val uses: Int,
    val successes: Int,
    /** Where Mode learned it: the step or answer it came from. */
    val evidence: String,
    /** The user taught it (in a chat, or on a step Mode got wrong). */
    val taught: Boolean = false,
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
                val groups = MemoryApps.of(items).map { g ->
                    MemoryAppGroup(g.name, g.tips.map { it.entry(MemoryApps.shortTitle(it, g.name), g.name) }, g.routines.map { it.entry(it.title, g.name) })
                }
                MemoryUiState(
                    learn = s.learnFromTasks,
                    aboutYou = items.filter { it.kind == MemoryKind.Fact }.map { it.entry(it.title, it.app) },
                    routines = groups.filter { it.routines.isNotEmpty() },
                    apps = groups.filter { it.tips.isNotEmpty() }.sortedByDescending { it.tips.size },
                )
            }.collect { mutableState.value = it }
        }
    }

    public fun setLearn(on: Boolean) {
        settings.update { it.copy(learnFromTasks = on) }
    }

    public fun delete(id: String) {
        memory.forget(id)
    }

    /** The user corrects what Mode remembered: its text, and its title when given. */
    public fun edit(id: String, text: String, title: String? = null) {
        memory.update(id, text, evidence = EDITED_BY_YOU, title = title)
    }

    public fun deleteMany(ids: Collection<String>) {
        ids.forEach(memory::forget)
    }

    public fun forgetEverything() {
        memory.forgetEverything()
    }

    private fun MemoryItem.entry(shownTitle: String, appName: String?): MemoryEntry {
        val steps = if (kind == MemoryKind.Skill) MemoryApps.steps(text) else emptyList()
        return MemoryEntry(
            id, shownTitle, text, steps, if (steps.isEmpty()) text else MemoryApps.notes(text), appName,
            clock.relativeDay(createdAt), uses, successes, evidence,
            taught = com.piyush.mode.feature.memory.data.Teacher.taught(this),
        )
    }

    public companion object {
        /** The evidence on an item the user wrote themselves. */
        public const val EDITED_BY_YOU: String = "edited by you"
    }
}
