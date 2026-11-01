package com.piyush.mode.agent

/**
 * Is the on-screen keyboard up? iOS doesn't say over CoreDevice, so it is read from the screen
 * text: a keyboard is a block of single letters low on the screen. Seen on the phone: with the
 * search field already focused and the keyboard up, the agent tapped the field six times, and
 * 23 of the 54 pieces of text it was given were keyboard keys.
 */
internal object Keyboard {
    /** At least this many single-letter keys low on the screen means a keyboard. */
    private const val MIN_KEYS = 8
    /** Keys sit in the bottom part of the screen. */
    private const val LOW = 0.55
    /** Room above the top row of keys for the suggestion bar. */
    private const val ABOVE_KEYS = 0.035

    /** The top of the keyboard in screenshot pixels, or null when it isn't up. */
    fun top(texts: List<Triple<String, Int, Int>>, imageHeight: Int): Int? {
        val keys = texts.filter { (text, _, y) ->
            val t = text.trim()
            y > imageHeight * LOW && t.length == 1 && t[0].isLetter()
        }
        if (keys.size < MIN_KEYS) return null
        return keys.minOf { it.third } - (imageHeight * ABOVE_KEYS).toInt()
    }
}
