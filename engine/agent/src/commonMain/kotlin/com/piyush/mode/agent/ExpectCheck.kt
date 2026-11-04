package com.piyush.mode.agent

/**
 * Checks what the model said an action would do ("expect": "the cart shows 1 item") against the screen after it,
 * without a model call: the words it expected either show or they do not. On a miss, one line says what was
 * expected, what showed instead, and the usual causes, so the next step is not the same tap again. Research:
 * a per-action check of the intended screen change is the largest single gain for GUI agents (MobileUse,
 * Q-Reflector); running it only on a miss keeps it free.
 */
internal object ExpectCheck {
    class Outcome(val missed: Boolean, val hint: String)

    /**
     * [expect]: the model's words; [after]: the screen's text now; [changed]: whether the screen changed at
     * all (null: unknown); [keyboardUp]: now; [action]: how the step was described ("tap \"Place Order\"").
     */
    fun check(expect: String?, after: List<String>, changed: Boolean?, keyboardUp: Boolean, action: String?): Outcome? {
        val e = expect?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val screen = after.map(::norm)
        val wantsKeyboard = KEYBOARD.containsMatchIn(e)
        if (wantsKeyboard) {
            if (keyboardUp) return Outcome(false, "")
            return Outcome(true, miss(e, action, "the keyboard did not come up, so the field is not focused: tap inside the field itself (not its label), or give type_text its x and y"))
        }
        val words = words(e)
        if (words.isEmpty()) return null
        val found = words.filter { w -> screen.any { it.contains(w) } }
        if (found.size * 2 >= words.size || (found.isNotEmpty() && words.size <= 2)) return Outcome(false, "")
        val shows = after.map { it.trim() }.filter { it.length in 2..40 }.take(SHOWN).joinToString(", ") { "\"$it\"" }
        return when (changed) {
            false -> Outcome(true, miss(e, action, "the screen did not change. The tap may have landed on a label or an image, a dialog may be in the way, or this control needs a different gesture; do not repeat the same coordinates"))
            else -> Outcome(true, miss(e, action, "instead the screen shows $shows. Read it before going on: it may be a dialog to dismiss, a different page, or the app not being in front"))
        }
    }

    private fun miss(expect: String, action: String?, why: String): String =
        " → NOT what you expected (${action ?: "your last action"} should have: ${expect.take(80)}): $why"

    /** Content words of what was expected, lower-case, without stop words and short words. */
    internal fun words(expect: String): List<String> =
        norm(expect).split(' ').filter { it.length >= MIN_WORD && it !in STOP }.distinct()

    private fun norm(s: String) = s.lowercase().filter { it.isLetterOrDigit() || it == ' ' }.trim()

    private const val MIN_WORD = 3
    private const val SHOWN = 5
    private val KEYBOARD = Regex("\\b(keyboard|focus(ed|es)?|cursor)\\b", RegexOption.IGNORE_CASE)
    private val STOP = setOf(
        "the", "and", "for", "with", "that", "this", "its", "are", "was", "will", "should", "shows", "show", "showing", "shown",
        "opens", "open", "opened", "appears", "appear", "appeared", "page", "screen", "now", "then", "into", "from", "one",
        "list", "see", "sees", "visible", "displayed", "display", "loads", "loaded", "goes", "back", "new", "next",
    )
}
