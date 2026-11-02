package com.piyush.mode.app.screens.home

import com.piyush.mode.app.screens.common.PartOfDay
import com.piyush.mode.app.screens.common.Presenter
import com.piyush.mode.app.screens.common.RelativeDay
import com.piyush.mode.app.screens.common.RunningTask
import com.piyush.mode.app.screens.common.ScreenClock
import com.piyush.mode.app.screens.common.TaskBadge
import com.piyush.mode.app.screens.common.badge
import com.piyush.mode.app.screens.common.running
import com.piyush.mode.feature.conversations.data.ConversationRepository
import com.piyush.mode.feature.memory.data.MemoryRepository
import com.piyush.mode.feature.memory.domain.MemoryKind
import com.piyush.mode.feature.settings.data.SettingsRepository
import com.piyush.mode.feature.tasks.data.TaskRepository
import com.piyush.mode.feature.tasks.domain.TaskInbox
import com.piyush.mode.feature.tasks.domain.TaskSource
import com.piyush.mode.feature.tasks.presentation.TaskRunner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

public data class HomeUiState(
    val partOfDay: PartOfDay = PartOfDay.Morning,
    val name: String? = null,
    val suggestions: List<Suggestion> = emptyList(),
    val recent: List<RecentConversation> = emptyList(),
    val running: RunningTask? = null,
    /** Mode can't run anything until a key is added. */
    val needsKey: Boolean = false,
)

/** Something the user may want to ask again; tapping fills the message box with [text]. */
public data class Suggestion(val text: String, val reason: Reason) {
    public sealed interface Reason {
        /** Asked this many times lately. */
        public data class Often(val times: Int) : Reason
        /** A route Mode has learned, in this app. */
        public data class Learned(val app: String?) : Reason
    }
}

public data class RecentConversation(
    val conversationId: String,
    val title: String,
    val day: RelativeDay,
    /** When it was last active, epoch ms (for the time of day). */
    val atMs: Long,
    /** How long its latest task took, in seconds. */
    val durationSeconds: Long?,
    /** How its latest task ended; null while it is still going. */
    val badge: TaskBadge?,
)

/** Home: a greeting, what to ask, and the latest conversations. */
public class HomePresenter(
    parent: CoroutineScope,
    private val settings: SettingsRepository,
    private val runner: TaskRunner,
    private val inbox: TaskInbox,
    private val tasks: TaskRepository,
    private val conversations: ConversationRepository,
    private val memory: MemoryRepository,
    private val clock: ScreenClock = ScreenClock(),
) : Presenter<HomeUiState>(parent, HomeUiState()) {

    init {
        scope.launch {
            combine(settings.settings, runner.state, tasks.recent(RECENT_TASKS), conversations.recent(MAX_RECENT), memory.observe()) { s, run, recentTasks, convs, items ->
                val latestByConversation = recentTasks.filter { it.conversationId != null }.groupBy { it.conversationId }
                HomeUiState(
                    partOfDay = clock.partOfDay(),
                    name = s.userName,
                    suggestions = suggestions(recentTasks.map { it.text }, items.filter { it.kind == MemoryKind.Skill }.sortedByDescending { it.successes }.map { it.title to it.app }),
                    recent = convs.map { c ->
                        val latest = latestByConversation[c.id]?.firstOrNull()
                        val seconds = latest?.let { t -> t.startedAt?.let { s -> t.endedAt?.let { e -> (e - s) / 1000 } } }
                        RecentConversation(c.id, c.title, clock.relativeDay(c.updatedAt), c.updatedAt, seconds, latest?.status?.badge())
                    },
                    running = run.running(tasks),
                    needsKey = !s.hasModelAccess,
                )
            }.collect { mutableState.value = it }
        }
    }

    /** Sends a task from the message box; returns its conversation, to open. */
    public fun ask(text: String, private: Boolean = false): String? =
        inbox.submit(text, TaskSource.App, private = private)?.conversationId

    private fun suggestions(recentTexts: List<String>, skills: List<Pair<String, String?>>): List<Suggestion> {
        val often = recentTexts.groupingBy { it.trim().lowercase() }.eachCount()
            .filter { it.value >= 2 }
            .entries.sortedByDescending { it.value }
            .mapNotNull { (key, times) -> recentTexts.firstOrNull { it.trim().lowercase() == key }?.let { Suggestion(it.trim(), Suggestion.Reason.Often(times)) } }
        val learned = skills.map { (title, app) -> Suggestion(title, Suggestion.Reason.Learned(app)) }
        return (often + learned).distinctBy { it.text.lowercase() }.take(MAX_SUGGESTIONS)
    }

    private companion object {
        const val MAX_RECENT = 5L
        const val RECENT_TASKS = 40L
        const val MAX_SUGGESTIONS = 3
    }
}
