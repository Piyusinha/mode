package com.piyush.mode.agent

/**
 * Is the on-screen keyboard up? iOS doesn't say over CoreDevice, so it is read from the screen
 * text: a keyboard is a block of single letters low on the screen, a number pad a block of
 * digits with their letter groups ("2 ABC"). Seen on the phone: with the search field already
 * focused and the keyboard up, the agent tapped the field six times, and 23 of the 54 pieces of
 * text it was given were keyboard keys; with a quantity's number pad up (MyFitnessPal), nothing
 * said so, and it tapped the field, pressed digits one by one and deleted them for ten steps.
 */
internal object Keyboard {
    /** At least this many single-letter keys low on the screen means a keyboard. */
    private const val MIN_KEYS = 8
    /** A number pad: at least this many digit keys, with at least [MIN_GROUPS] of their letter groups. */
    private const val MIN_DIGITS = 6
    private const val MIN_GROUPS = 3
    private val GROUPS = setOf("ABC", "DEF", "GHI", "JKL", "MNO", "PQRS", "TUV", "WXYZ")
    /** Keys sit in the bottom part of the screen. */
    private const val LOW = 0.55
    /** Room above the top row of keys for the suggestion bar. */
    private const val ABOVE_KEYS = 0.035

    /** The top of the keyboard (or number pad) in screenshot pixels, or null when neither is up. */
    fun top(texts: List<Triple<String, Int, Int>>, imageHeight: Int): Int? {
        val low = texts.filter { it.third > imageHeight * LOW }
        val letters = low.filter { (text, _, _) -> text.trim().let { it.length == 1 && it[0].isLetter() } }
        if (letters.size >= MIN_KEYS) return letters.minOf { it.third } - (imageHeight * ABOVE_KEYS).toInt()

        // A key may be read as "2", "ABC", or both together ("2 ABC").
        val digits = low.filter { (text, _, _) -> text.trim().firstOrNull()?.isDigit() == true && text.trim().take(1).let { d -> text.trim().removePrefix(d).trim().let { it.isEmpty() || it in GROUPS } } }
        val groups = low.filter { (text, _, _) -> text.trim().split(' ').last() in GROUPS }
        if (digits.map { it.first.trim().take(1) }.distinct().size >= MIN_DIGITS && groups.size >= MIN_GROUPS) {
            return (digits + groups).minOf { it.third } - (imageHeight * ABOVE_KEYS).toInt()
        }
        return null
    }
}
