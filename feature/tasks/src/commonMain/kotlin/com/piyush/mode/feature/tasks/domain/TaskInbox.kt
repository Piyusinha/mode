package com.piyush.mode.feature.tasks.domain

/**
 * The one way in for tasks, from every source: the app, Siri, Shortcuts, the
 * Apple Watch and schedules. Adding a source never touches the agent.
 */
public interface TaskInbox {
    /**
     * Saves [text] as a task and queues it; it runs when the tasks before it
     * have finished (one phone, one task at a time). Null for blank text.
     * A [conversationId] makes it a follow-up there; [private] keeps it out of memory.
     */
    public fun submit(text: String, source: TaskSource, conversationId: String? = null, private: Boolean = false): Task?

    /** Takes a waiting task out of the queue, or stops it if it is the one running. */
    public fun cancel(taskId: String)
}
