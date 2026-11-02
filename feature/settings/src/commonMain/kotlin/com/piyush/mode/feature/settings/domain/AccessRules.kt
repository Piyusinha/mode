package com.piyush.mode.feature.settings.domain

/** Full access for one task, from a word the user starts (or ends) it with. */
public object AccessRules {
    /** The task as the agent should read it, and whether it was given full access. */
    public data class ForTask(val task: String, val fullAccess: Boolean)

    /**
     * [task] with a trigger word at its start ("just do it, order my usual") or end ("order my usual, just
     * do it") taken off, and full access; otherwise the task unchanged. The word must stand on its own and
     * leave something to do: "full access" alone is not a task.
     */
    public fun forTask(task: String, words: List<String>): ForTask {
        val text = task.trim()
        for (word in words.map { it.trim() }.filter { it.isNotEmpty() }.sortedByDescending { it.length }) {
            val w = Regex.escape(word)
            val start = Regex("""^$w(?:[\s,:;.!\-–—]+)(.+)$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(text)
            val end = Regex("""^(.+?)[\s,:;.\-–—]+$w[\s.!]*$""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)).find(text)
            val rest = (start ?: end)?.groupValues?.get(1)?.trim()?.trimEnd(',', ';', ':')?.trim()
            if (!rest.isNullOrEmpty()) return ForTask(rest.replaceFirstChar { it.uppercaseChar() }, true)
        }
        return ForTask(text, false)
    }
}
