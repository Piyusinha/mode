package com.piyush.mode.agent

/**
 * What memory knows, at the moment it applies, instead of all of it up front. Seen on the phone: up to
 * ten tips sat in every step's prompt whatever the screen (a diary tip on the search page), and a routine
 * that worked was prose the model had to map onto the screen again each time. No model call: words
 * and labels are matched against what is on screen.
 *
 * - Tips: those that speak about what is on screen now (a tip about "Serving Size" when it is there).
 * - A routine that worked: its steps quote what was tapped ("Tap 'Log' beside the meal"). When the next
 *   such label is on screen, say so with its id: replay where the screen matches, nothing where it
 *   does not (DeltaReplay's "replay or re-ground", arXiv 2610.11707).
 */
object JustInTime {
    const val MAX_TIPS = 3
    /** A tip needs this many of its words on screen to be about this screen. */
    private const val MIN_SHARED = 2
    private val WORD = Regex("""[\p{L}\p{N}]{4,}""")
    /** Words in almost every tip, which say nothing about which screen it is for. */
    private val COMMON = setOf(
        "then", "with", "when", "that", "this", "from", "your", "screen", "button", "after", "before", "shows", "open", "opens",
        "item", "items", "page", "list", "there", "into", "each", "more", "same", "only", "which", "they", "their", "what",
        "have", "will", "does", "above", "below", "under", "next", "back", "first", "again", "once", "user", "task", "mode",
        "tapping", "instead", "already", "should", "would", "where",
    )

    private fun words(text: String): Set<String> = WORD.findAll(text.lowercase()).map { it.value }.filter { it !in COMMON }.toSet()

    /** The tips about what is on screen now, the best match first; the app's own name does not count. */
    fun tips(tips: List<String>, screen: List<String>, app: String?): List<String> {
        if (tips.isEmpty()) return emptyList()
        val onScreen = words(screen.joinToString(" "))
        val appWords = app?.let(::words).orEmpty()
        return tips.map { tip -> tip to (words(tip) - appWords).count { it in onScreen } }
            .filter { it.second >= MIN_SHARED }
            .sortedByDescending { it.second }
            .take(MAX_TIPS)
            .map { it.first }
    }

    /** One numbered step of a routine and the on-screen labels it quotes. */
    class Step(val number: Int, val text: String, val labels: List<String>)

    private val NUMBERED = Regex("""^(\d{1,2})[.)]\s*(.+)$""")
    /** A quoted label, not an apostrophe ("Don't tap 'Log'" quotes Log). */
    private val QUOTED = Regex("""(?<![\p{L}])["“'‘]([^"”'’]{2,40})["”'’](?![\p{L}])""")

    /** The steps of a routine ("1. Tap 'Log' beside the meal") with the labels they quote. */
    fun steps(routine: String?): List<Step> = routine.orEmpty().lines().mapNotNull { line ->
        val m = NUMBERED.matchEntire(line.trim()) ?: return@mapNotNull null
        val text = m.groupValues[2].trim()
        Step(m.groupValues[1].toInt(), text, QUOTED.findAll(text).map { it.groupValues[1].trim() }.filter { it.length >= 2 }.toList())
    }

    class Hint(val step: Step, val label: String, val element: ScreenLayout.Element) {
        fun text(): String =
            "step ${step.number} (\"${step.text.take(120)}\"): \"$label\" is on screen as [${element.id}]. Use it if it fits " +
                "this task; the screen has the last word."
    }

    /** The first step from [from] on whose label is on screen now, with the element that shows it. */
    fun hint(steps: List<Step>, from: Int, layout: ScreenLayout): Hint? {
        for (step in steps.filter { it.number >= from }) {
            for (label in step.labels) {
                val wanted = normal(label)
                val element = layout.elements.firstOrNull { e ->
                    val shown = normal(e.text)
                    shown == wanted || (wanted.length >= 4 && shown.startsWith(wanted)) || (shown.length >= 4 && wanted.startsWith(shown))
                }
                if (element != null) return Hint(step, label, element)
            }
        }
        return null
    }

    private fun normal(s: String): String = s.lowercase().replace("…", "").replace("...", "").trim().replace(Regex("""\s+"""), " ")
}
