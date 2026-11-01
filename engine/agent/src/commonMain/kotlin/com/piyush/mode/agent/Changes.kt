package com.piyush.mode.agent

/**
 * Taps that change something for the user: add, log, save, send, book, buy, delete.
 * Known from the agent's own words for the step ("Adding it to Dinner") or the
 * button's label (Add, Send, a plus sign); nothing about any particular app.
 * Doing one twice adds twice, so each is checked before another is made.
 */
internal object Changes {
    private val VERBS = Regex(
        """\b(add|adding|log|logging|save|saving|send|sending|post|posting|book|booking|buy|buying|""" +
            """order|ordering|pay|paying|submit|submitting|create|creating|delete|deleting|remove|removing|""" +
            """track|tracking|record|recording|join|joining|accept|accepting)\b""",
        RegexOption.IGNORE_CASE,
    )

    /** Words that mean the agent wants to look, not to change anything. */
    private val CHECKING = Regex("""\b(check|checking|verify|verifying|confirm whether|see if|see whether|look|looking)\b""", RegexOption.IGNORE_CASE)

    /** What apps say when a change went through, for a moment or for good. */
    private val CONFIRMED = Regex(
        """\b(logged|added|saved|sent|booked|ordered|posted|created|scheduled|confirmed|paid|deleted|removed|""" +
            """success|successful|successfully)\b|[✓✔]""",
        RegexOption.IGNORE_CASE,
    )

    /** Plus signs as screen text recognition reads them ("十" is how it read MyFitnessPal's +). */
    private val PLUS = setOf("+", "＋", "十", "⊕", "➕")

    /** A button that adds or sends: a plus sign, or a short label such as "Add" or "Send". */
    fun isChangeButton(label: String?): Boolean {
        val l = label?.trim().orEmpty()
        return l in PLUS || (l.length <= 12 && VERBS.containsMatchIn(l))
    }

    fun isChange(reason: String, label: String?): Boolean =
        isChangeButton(label) || (VERBS.containsMatchIn(reason) && !isChecking(reason))

    /** The agent says it is checking: a tap on an add button is then a mistake, not a check. */
    fun isChecking(reason: String): Boolean = CHECKING.containsMatchIn(reason)

    /** The first text that says a change went through ("Food logged!"), if any. */
    fun proof(texts: List<String>): String? = texts.firstOrNull { it.length <= 60 && CONFIRMED.containsMatchIn(it) }
}
