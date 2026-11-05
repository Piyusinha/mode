package com.piyush.mode.agent

/**
 * A learned skill is a small package in one text: the numbered route, "Backup:" lines (another way to find
 * each thing), "If it goes wrong:" rules with a quoted on-screen trigger, and "Failed:" lines (what went
 * wrong before). The rules are shown only when their trigger is on the screen now, so a rule about
 * "CLOSED RIGHT NOW" costs nothing until a shop is closed. Research: skills with a recovery section lift
 * success 53 → 70% (EvoSkill-GUI); retrieval by the exact trigger beats word overlap.
 */
object Playbook {
    class Rule(val trigger: String, val response: String)

    private val RULE = Regex("""^\s*[-•*]?\s*(?:if|when)\s+["“'‘]([^"”'’]{2,60})["”'’]\s*[:,]?\s*(.+)$""", RegexOption.IGNORE_CASE)
    private val HEADER = Regex("""^\s*if it goes wrong\s*:?\s*$""", RegexOption.IGNORE_CASE)
    const val MAX_SHOWN = 2

    /** The "if "X": Y" rules in [text] (a skill's text), from its "If it goes wrong:" section or anywhere. */
    fun rules(text: String?): List<Rule> = text.orEmpty().lines().mapNotNull { line ->
        val m = RULE.matchEntire(line.trim()) ?: return@mapNotNull null
        Rule(m.groupValues[1].trim(), m.groupValues[2].trim().trimEnd('.'))
    }

    /** The rules whose trigger is on the screen now, as lines for the step ("PLAYBOOK: …"), at most [MAX_SHOWN]. */
    fun applicable(rules: List<Rule>, screen: List<String>): List<String> {
        if (rules.isEmpty()) return emptyList()
        val shown = screen.map(::normal)
        return rules.filter { r -> val t = normal(r.trigger); t.length >= 2 && shown.any { it == t || it.contains(t) } }
            .take(MAX_SHOWN)
            .map { "PLAYBOOK (seen before, \"${it.trigger}\" is on screen): ${it.response}" }
    }

    /** The lines that are not rules, backup or failure notes: the route itself. */
    fun routeOnly(text: String): String = text.lines().takeWhile { !HEADER.matches(it) && !it.trim().startsWith("Backup:", true) && !it.trim().startsWith("Failed:", true) }
        .joinToString("\n").trim()

    private fun normal(s: String): String = s.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim().replace(Regex("\\s+"), " ")
}
