package com.piyush.mode.agent

/**
 * Taps that change something for the user: add, log, save, send, book, buy, delete.
 * Known from the agent's own words for the step ("Adding it to Dinner") or the
 * button's label (Add, Send, a plus sign); nothing about any particular app.
 * Doing one twice adds twice, so each is checked before another is made.
 */
internal object Changes {
    private val VERBS = Regex(
        """\b(add|adding|added|log|logging|save|saving|send|sending|post|posting|book|booking|buy|buying|""" +
            """order|ordering|pay|paying|submit|submitting|create|creating|delete|deleting|remove|removing|""" +
            """track|tracking|record|recording|join|joining|accept|accepting)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Plus signs as screen text recognition reads them ("十" is how it read MyFitnessPal's +). */
    private val PLUS = setOf("+", "＋", "十", "⊕", "➕")

    fun isChange(reason: String, label: String?): Boolean {
        val l = label?.trim().orEmpty()
        return VERBS.containsMatchIn(reason) || l in PLUS || (l.length <= 12 && VERBS.containsMatchIn(l))
    }
}
