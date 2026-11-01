package com.piyush.mode.agent

/**
 * What the agent knows about a task from outside it, fixed when the task
 * starts (app tips can be added as apps open). Every part is optional: an
 * empty context gives the plain task.
 */
data class TaskContext(
    /** The user's name, so messages can be signed and "me" understood. */
    val userName: String? = null,
    /** Facts about the user (people, preferences, places). */
    val aboutYou: List<String> = emptyList(),
    /** Routes that worked before: the best match in full, then names of others. */
    val skills: List<String> = emptyList(),
    /** What worked or failed before in the apps this task uses. */
    val appNotes: List<String> = emptyList(),
    /** The last few tasks, one line each, newest first ("1 Nov: turned off dark mode"). */
    val recentTasks: List<String> = emptyList(),
    /** Earlier tasks in the same conversation and how they ended, oldest first. */
    val earlierTurns: List<EarlierTurn> = emptyList(),
    /** The weekday, date and time now ("Sunday 1 November 2026, 19:05"), so "today" means today. */
    val today: String? = null,
    /** A learned route that fits this task closely and has worked: numbered steps to follow, no brief needed. */
    val knownRoute: String? = null,
) {
    data class EarlierTurn(val task: String, val outcome: String)

    companion object {
        val Empty = TaskContext()
    }
}

/**
 * What Mode remembers, for the agent to look up in the middle of a task. The
 * memory feature implements it; without one the agent simply has no recall.
 */
interface AgentMemory {
    /** The best few remembered lines for [query]: a person, place, preference or a skill's name. */
    fun recall(query: String): List<String>

    /** The same, leaving out what belongs to apps other than [apps] (people and preferences stay). */
    fun recall(query: String, apps: List<String>): List<String> = recall(query)

    /** Tips remembered for [app], given when the agent opens it. */
    fun tipsFor(app: String): List<String>
}
