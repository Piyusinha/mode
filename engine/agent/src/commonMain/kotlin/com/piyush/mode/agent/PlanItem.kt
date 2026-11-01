package com.piyush.mode.agent

/** One item of the model's plan: "Send message", and whether the model marked it done. */
data class PlanItem(val text: String, val done: Boolean) {
    companion object {
        private val NUMBER = Regex("""^\s*(?:\d{1,2}[.)]|[-*•])\s*""")
        private val DONE = Regex("""^\s*(?:\[(?:done|x|✓)]|✓|✔)\s*""", RegexOption.IGNORE_CASE)
        private val OPEN = Regex("""^\s*\[\s?]\s*""")

        /** "1. [done] turn off dark mode\n2. set brightness" → items, marks removed. */
        fun parse(plan: String): List<PlanItem> = plan.lines().mapNotNull { line ->
            val rest = line.replace(NUMBER, "")
            val done = DONE.containsMatchIn(rest)
            val text = rest.replace(DONE, "").replace(OPEN, "").trim().trimEnd('.')
            if (text.isEmpty()) null else PlanItem(text.replaceFirstChar(Char::uppercaseChar), done)
        }
    }
}
