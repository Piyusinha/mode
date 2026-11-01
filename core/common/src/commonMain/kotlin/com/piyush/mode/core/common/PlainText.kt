package com.piyush.mode.core.common

/**
 * Mode's replies are Markdown; notifications, status lines and Siri can't draw
 * it. This turns a reply into clean text: marks removed, list items as "• ",
 * table rows as "a · b", links as their words.
 */
public object PlainText {
    private val FENCE = Regex("""^\s*```.*$""")
    private val HEADING = Regex("""^\s*#{1,6}\s+""")
    private val QUOTE = Regex("""^\s*>\s?""")
    private val BULLET = Regex("""^\s*[-*+•]\s+""")
    private val TABLE_RULE = Regex("""^\s*\|?\s*:?-{2,}:?\s*(\|\s*:?-{2,}:?\s*)*\|?\s*$""")
    private val DIVIDER = Regex("""^\s*([-*_])(\s*\1){2,}\s*$""")
    private val LINK = Regex("""\[([^\]]+)]\([^)]*\)""")
    private val STRONG = Regex("""(\*\*|__)(.+?)\1""")
    private val EMPHASIS = Regex("""(?<![\w*])\*(?!\s)([^*\n]+?)\*(?![\w*])""")
    private val STRIKE = Regex("""~~(.+?)~~""")
    private val CODE = Regex("""`([^`]+)`""")

    public fun fromMarkdown(text: String, oneLine: Boolean = false): String {
        val lines = text.replace("\r\n", "\n").lines().mapNotNull { raw ->
            when {
                FENCE.matches(raw) || TABLE_RULE.matches(raw) || DIVIDER.matches(raw) -> null
                raw.trim().startsWith("|") -> raw.trim().removePrefix("|").removeSuffix("|").split('|').joinToString(" · ") { it.trim() }
                else -> raw.replace(HEADING, "").replace(QUOTE, "").replace(BULLET, "• ").trimEnd()
            }
        }.map { line ->
            line.replace(LINK, "$1").replace(STRONG, "$2").replace(STRIKE, "$1").replace(CODE, "$1").replace(EMPHASIS, "$1")
        }
        val kept = lines.dropWhile { it.isBlank() }.dropLastWhile { it.isBlank() }
        return if (oneLine) kept.filter { it.isNotBlank() }.joinToString(" ") { it.trim() }
        else kept.joinToString("\n").replace(Regex("\n{3,}"), "\n\n")
    }
}
