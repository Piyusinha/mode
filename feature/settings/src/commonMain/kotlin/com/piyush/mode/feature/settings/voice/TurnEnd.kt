package com.piyush.mode.feature.settings.voice

/**
 * When the person has finished talking. People stop for 3–4 s to think mid-task, so only a pause that long
 * ends the turn (Done ends it at once); it is longer
 * when the words so far stop mid-sentence ("turn on the… dark mode", "message Asha and…"), so Mode
 * never cuts someone off while they think. Used by live listening on the iPhone.
 */
public object TurnEnd {
    /** Quiet after speech that ends a finished-sounding sentence. */
    public const val PAUSE_MS: Long = 3_000
    /** Quiet after words that stop mid-sentence, or before any words were recognised. */
    public const val UNFINISHED_PAUSE_MS: Long = 4_000
    /** In a noisy room the level never falls: the recognised words standing still this long end the turn. */
    public const val SETTLE_MS: Long = 3_500
    public const val UNFINISHED_SETTLE_MS: Long = 5_000

    /** How long a quiet pause must last to end the turn, after [words]. */
    public fun pauseMs(words: String): Long = if (looksUnfinished(words)) UNFINISHED_PAUSE_MS else PAUSE_MS

    /** How long [words] must stay the same to end the turn when the room is never quiet. */
    public fun settleMs(words: String): Long = if (looksUnfinished(words)) UNFINISHED_SETTLE_MS else SETTLE_MS

    /** The words so far stop where a sentence can't end: a joining word, an article, a filler, a comma. */
    public fun looksUnfinished(words: String): Boolean {
        val said = words.trim()
        if (said.isEmpty()) return true
        when (said.last()) {
            '.', '?', '!' -> return false
            ',', ';', ':', '-', '–', '—' -> return true
        }
        val last = said.substringAfterLast(' ').trim { !it.isLetterOrDigit() && it != '\'' }.lowercase()
        return last in OPEN_ENDINGS
    }

    private val OPEN_ENDINGS = setOf(
        // Joining words and prepositions: more is coming.
        "and", "or", "but", "so", "then", "also", "because", "if", "when", "while", "until", "unless", "that", "which", "who",
        "to", "for", "of", "with", "without", "on", "in", "at", "from", "into", "about", "by", "as", "like", "than",
        // Articles and possessives: a noun is coming.
        "the", "a", "an", "my", "your", "our", "his", "her", "their", "this", "these", "those", "some", "any",
        // Verbs that need what follows.
        "is", "are", "was", "be", "can", "could", "should", "would", "will", "please", "want", "need", "let's",
        // Fillers: still thinking.
        "um", "uh", "uhm", "er", "erm", "hmm", "mm", "like",
        // Hindi and Hinglish joining words.
        "aur", "ya", "ki", "ke", "ko", "se", "mein", "toh", "phir", "matlab", "jo",
    )
}
