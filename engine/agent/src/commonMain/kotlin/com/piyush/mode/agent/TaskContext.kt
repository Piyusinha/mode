package com.piyush.mode.agent

/**
 * What the agent knows about a task from outside it, fixed for the whole task.
 * Every part is optional: an empty context gives the plain task.
 */
class TaskContext(
    /** The user's name, so messages can be signed and "me" understood. */
    val userName: String? = null,
    /** Facts about the user that matter to this task (people, preferences, places). */
    val aboutYou: List<String> = emptyList(),
    /** What worked or failed before in the apps this task uses. */
    val appNotes: List<String> = emptyList(),
    /** Earlier tasks in the same conversation and how they ended, oldest first. */
    val earlierTurns: List<EarlierTurn> = emptyList(),
) {
    class EarlierTurn(val task: String, val outcome: String)

    companion object {
        val Empty = TaskContext()
    }
}
