package com.piyush.mode.feature.tasks.domain

/** Where a task came from. */
public enum class TaskSource(internal val stored: String) {
    App("app"), Siri("siri"), Watch("watch"), Shortcut("shortcut"), Schedule("schedule"),
    /** Spoken to Mode ("Start Mode"): its questions and summary are spoken too. */
    Voice("voice");

    internal companion object {
        fun of(stored: String): TaskSource = entries.firstOrNull { it.stored == stored } ?: App
    }
}

public enum class TaskStatus(internal val stored: String) {
    Queued("queued"),
    Running("running"),
    WaitingForUser("waiting"),
    Done("done"),
    /** The agent finished without achieving it. */
    GaveUp("gave_up"),
    Stopped("stopped"),
    Failed("failed"),
    /** It never started (no key, no phone). */
    NotStarted("not_started");

    public val finished: Boolean get() = this !in setOf(Queued, Running, WaitingForUser)

    internal companion object {
        fun of(stored: String): TaskStatus = entries.firstOrNull { it.stored == stored } ?: Failed
    }
}

/** Something the user asked Mode to do. */
public data class Task(
    val id: String,
    val conversationId: String?,
    val text: String,
    val source: TaskSource,
    val status: TaskStatus,
    /** Epoch milliseconds. */
    val createdAt: Long,
    val startedAt: Long?,
    val endedAt: Long?,
    /** The recorded run (see feature:runs). */
    val runId: String?,
    /** What Mode answered when it finished. */
    val summary: String?,
    val steps: Int?,
    val costUsd: Double?,
    /** Neither uses nor adds to memory. */
    val private: Boolean = false,
)
