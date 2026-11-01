package com.piyush.mode.feature.memory.domain

/** The three kinds of things Mode remembers (docs/harness.md §4). */
public enum class MemoryKind(internal val stored: String) {
    /** About the user: people, preferences, places. Shown with every task, up to a budget. */
    Fact("fact"),

    /** A route that worked for one kind of task, with placeholders: "Message [Person] on WhatsApp". */
    Skill("skill"),

    /** A short note about one app: "Calendar: the list button is top right". */
    Tip("tip");

    internal companion object {
        fun of(stored: String): MemoryKind = entries.firstOrNull { it.stored == stored } ?: Fact
    }
}

/** One remembered item, as saved. */
public data class MemoryItem(
    val id: String,
    val kind: MemoryKind,
    /** Lower-case key: the person or topic for a fact, the intent for a skill, the app for a tip. */
    val subject: String,
    /** A short name for lists: "Sonali", "Turn off Dark Mode", "Calendar". */
    val title: String,
    val text: String,
    /** Where it was learned: the step or answer it came from. */
    val evidence: String,
    val keywords: List<String>,
    /** The app it belongs to, if any. */
    val app: String?,
    /** The task that last added or changed it. */
    val sourceTaskId: String?,
    /** The text before the last change, for undo. */
    val previousText: String?,
    val uses: Int,
    val successes: Int,
    val failures: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val lastUsedAt: Long?,
)

/** Something to remember, before it is saved. */
public data class MemoryDraft(
    val kind: MemoryKind,
    val subject: String,
    val title: String,
    val text: String,
    val evidence: String,
    val keywords: List<String> = emptyList(),
    val app: String? = null,
)

/** What saving a draft did. */
public sealed interface SaveResult {
    public data class Added(val item: MemoryItem) : SaveResult
    public data class Updated(val item: MemoryItem) : SaveResult
    /** Already known in the same words; nothing changed. */
    public data class Unchanged(val item: MemoryItem) : SaveResult
    public data class Rejected(val why: String) : SaveResult
}
